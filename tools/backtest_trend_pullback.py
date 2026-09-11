#!/usr/bin/env python3
"""
tools/backtest_trend_pullback.py
=========================================================================
ATOM FX — trend-pullback baseline backtest (Task 5, spec §7 Task 5, DECISION-008).

PURPOSE
    Task 3 (scanner/extend/trend_pullback.py) implements the detector; Task 5 validates it
    has a real edge on history BEFORE Task 4 wires it into the live hourly scan. This tool
    replays each pair's own H1 history bar by bar, calling the REAL evaluate_from_h1() at
    every step (never a re-implementation of any gate), and reports whether the resulting
    trades show a positive expectancy and in what volume.

    GATE (spec §7): no live wiring, no push, no signals.json key until these results are
    reviewed with Pieter and DECISION-005 (parameter defaults) is ratified or revised.

MODELING ASSUMPTIONS (DECISION-008 — docs/ATOM_FX_TREND_METHODOLOGY_SPEC.md §1)
    - No look-ahead: at each step the detector sees only H1 bars up to and including the
      current bar (enforced by the rolling window below — nothing later is ever in it).
    - Entry = the detector's own returned `entry` (the signal bar's close). Stop/target =
      the detector's own `stop`/`target` — never recomputed here.
    - Trade resolution walks forward bar by bar (H1): LONG loses if bar.low <= stop, wins
      if bar.high >= target; a bar hitting BOTH resolves as a STOP (conservative). SHORT
      mirrors (bar.high >= stop is a loss, bar.low <= target is a win).
    - MAX_HOLD_BARS = 360 (~15 trading days of continuous H1). If neither hits by then, exit
      at that bar's close; realized_R uses the same (exit-entry)/(entry-stop) formula, just
      with the timeout close standing in for the target/stop price. exit_reason="timeout".
    - One open position per pair at a time; scanning resumes on the bar AFTER the exit bar.
    - Params = trend_pullback.PARAMS, the detector's own defaults — NO sweep in this task.

HOW TO RUN
    export TWELVEDATA_KEY=your_key_here        # (Windows: set TWELVEDATA_KEY=...)
    python -m tools.backtest_trend_pullback                  # all scanner.config.PAIRS
    python -m tools.backtest_trend_pullback EUR/USD GBP/USD  # just these pairs
    python -m tools.backtest_trend_pullback --refresh         # force a full H1 re-fetch

    Part 1 (H1 history): pages Twelvedata H1 backwards per pair to ~TARGET_H1_BARS, reusing
    tools.bootstrap_d1_nyclose's paginated/URL-encoded/rate-limited fetch (_fetch_h1_page) --
    not duplicated here. Cached to data/h1_cache/<PAIR>.json; idempotent (a pair whose cache
    already has >= TARGET_H1_BARS is skipped unless --refresh).

    Part 2 (simulate): steps at H1 resolution once enough data exists (D1 >= 210 bars), only
    while flat on that pair, handing evaluate_from_h1() a bounded rolling WINDOW (not the
    full growing history) so each call stays O(WINDOW) instead of O(t) -- see the spot-check
    below, which proves the window doesn't change which signals fire. On state=="fired",
    opens and resolves the trade per the assumptions above, then resumes after the exit bar.

    Writes data/backtest/trades.csv and prints a per-pair + aggregate stats table plus a
    plain-language read of the result. Both output directories are runtime artifacts, not
    committed by this change.

Rule #1: EXTEND. Calls the real scanner.extend.trend_pullback.evaluate_from_h1 — never
reimplements a gate. Reuses tools.bootstrap_d1_nyclose's _fetch_h1_page (read-only import,
not edited) for H1 fetching, and scanner.extend.agg_nyclose.aggregate_d1_nyclose for the one
piece of bookkeeping this tool needs beyond the detector itself (finding where D1 history
first reaches 210 bars, to know where scanning may start — NOT a gate, purely "is there
enough data yet to bother calling evaluate_from_h1 at all").
=========================================================================
"""
import argparse
import json
import os
import sys
import time

import numpy as np
import pandas as pd

from scanner import fetch
from scanner.config import PAIRS
from scanner.extend.agg_nyclose import aggregate_d1_nyclose
from scanner.extend.trend_pullback import evaluate_from_h1, PARAMS
from tools.bootstrap_d1_nyclose import _fetch_h1_page, PAGE_SIZE, CALL_DELAY_S

H1_CACHE_DIR = "data/h1_cache"
BACKTEST_DIR = "data/backtest"
TARGET_H1_BARS = 14000   # ~5000 to warm D1 EMA200 + ~18 months tradeable (spec §7 Task 5)
WINDOW = 7000            # rolling window handed to evaluate_from_h1 at each step
MAX_HOLD_BARS = 360      # DECISION-008: ~15 trading days
MIN_D1_BARS = 210        # mirrors trend_pullback.evaluate()'s own data-guard threshold —
                          # NOT a gate; purely where-to-start-scanning bookkeeping. Kept in
                          # sync by hand since evaluate() has no exported constant for it.


# ── Part 1: H1 history cache ────────────────────────────────────────────────────
def _cache_path(pair: str) -> str:
    return os.path.join(H1_CACHE_DIR, f"{pair.replace('/', '')}.json")


def load_h1_cache(pair: str) -> pd.DataFrame:
    path = _cache_path(pair)
    if not os.path.exists(path):
        return pd.DataFrame(columns=["datetime", "open", "high", "low", "close"])
    with open(path) as f:
        rows = json.load(f)
    if not rows:
        return pd.DataFrame(columns=["datetime", "open", "high", "low", "close"])
    return pd.DataFrame(rows)


def save_h1_cache(pair: str, df: pd.DataFrame) -> None:
    os.makedirs(H1_CACHE_DIR, exist_ok=True)
    cols = ["datetime", "open", "high", "low", "close"]
    with open(_cache_path(pair), "w") as f:
        json.dump(df[cols].to_dict(orient="records"), f)


def fetch_h1_history(pair: str, target_bars: int = TARGET_H1_BARS, refresh: bool = False) -> pd.DataFrame:
    """
    Page H1 backwards (reusing tools.bootstrap_d1_nyclose._fetch_h1_page) until >=
    target_bars are cached, or the provider runs out of history. Idempotent: a cache that
    already has >= target_bars rows is returned as-is unless refresh=True.
    """
    existing = load_h1_cache(pair)
    if not refresh and len(existing) >= target_bars:
        print(f"{pair}: cache already has {len(existing)} bars (>= {target_bars}) — skipping fetch")
        return existing

    pages = []
    end_date = None
    total = 0
    while total < target_bars:
        page = _fetch_h1_page(pair, end_date)
        time.sleep(CALL_DELAY_S)
        if page is None or page.empty:
            break
        pages.append(page)
        total = sum(len(p) for p in pages)
        if len(page) < PAGE_SIZE:
            break   # provider is exhausted -- no more history available for this pair
        end_date = str(page["datetime"].iloc[0])

    if not pages:
        print(f"{pair}: no new data fetched, using existing {len(existing)}-bar cache")
        return existing

    combined = (pd.concat(pages, ignore_index=True)
                .drop_duplicates(subset="datetime")
                .sort_values("datetime")
                .reset_index(drop=True))
    save_h1_cache(pair, combined)
    print(f"{pair}: cached {len(combined)} H1 bars, "
          f"{combined['datetime'].iloc[0]} -> {combined['datetime'].iloc[-1]}")
    return combined


# ── Part 2: simulate ─────────────────────────────────────────────────────────────
def resolve_trade(h1_df: pd.DataFrame, entry_idx: int, direction: str, entry: float,
                   stop: float, target: float, max_hold_bars: int = MAX_HOLD_BARS) -> dict:
    """
    Walk h1_df forward from entry_idx+1 (the bar AFTER the signal bar whose close is
    `entry`), resolving the trade per DECISION-008: LONG loses on bar.low <= stop, wins on
    bar.high >= target (a bar hitting both is a STOP, conservative); SHORT mirrors. If
    neither hits within max_hold_bars, exits at that bar's close as a timeout.

    Pure function of its inputs — no I/O, no clock — so it's unit-testable in isolation
    (tests/test_backtest_trend_pullback.py) without needing evaluate_from_h1 at all.

    Returns {"exit_idx", "exit_price", "exit_reason", "bars_held", "realized_R"}.
    """
    n = len(h1_df)
    last_j = min(entry_idx + max_hold_bars, n - 1)

    exit_idx = exit_price = exit_reason = None
    for j in range(entry_idx + 1, last_j + 1):
        bar = h1_df.iloc[j]
        if direction == "long":
            hit_stop = float(bar["low"]) <= stop
            hit_target = float(bar["high"]) >= target
        else:
            hit_stop = float(bar["high"]) >= stop
            hit_target = float(bar["low"]) <= target

        if hit_stop:          # stop-first tie-break when a bar hits both
            exit_idx, exit_price, exit_reason = j, stop, "stop"
            break
        if hit_target:
            exit_idx, exit_price, exit_reason = j, target, "target"
            break

    if exit_idx is None:
        exit_idx = last_j
        exit_price = float(h1_df.iloc[exit_idx]["close"])
        exit_reason = "timeout"

    bars_held = exit_idx - entry_idx
    if direction == "long":
        realized_R = (exit_price - entry) / (entry - stop)
    else:
        realized_R = (entry - exit_price) / (stop - entry)

    return {
        "exit_idx": exit_idx, "exit_price": exit_price,
        "exit_reason": exit_reason, "bars_held": bars_held, "realized_R": realized_R,
    }


def _first_index_with_min_d1(h1_full: pd.DataFrame, min_d1: int = MIN_D1_BARS) -> int | None:
    """Smallest H1 prefix length k such that aggregate_d1_nyclose(h1_full.iloc[:k]) has >=
    min_d1 rows -- binary search (D1 count is monotone non-decreasing as the prefix grows),
    so this costs O(log n) full-aggregation passes instead of one per bar. Returns the
    0-based INDEX of the first bar scanning may start at (k-1), or None if the pair's whole
    history never reaches min_d1 D1 bars."""
    n = len(h1_full)
    if len(aggregate_d1_nyclose(h1_full)) < min_d1:
        return None
    lo, hi = 1, n
    while lo < hi:
        mid = (lo + hi) // 2
        if len(aggregate_d1_nyclose(h1_full.iloc[:mid])) >= min_d1:
            hi = mid
        else:
            lo = mid + 1
    return lo - 1


def simulate_pair(pair: str, h1_full: pd.DataFrame, window: int = WINDOW) -> list[dict]:
    """
    Step through h1_full at H1 resolution from the first bar with >= MIN_D1_BARS of D1
    history, only while flat, handing evaluate_from_h1 the trailing `window` H1 bars ending
    at the current bar (DECISION-008: no look-ahead — nothing after the current bar is ever
    in that window). On a "fired" state, opens and resolves the trade, then resumes on the
    bar after its exit.
    """
    start_idx = _first_index_with_min_d1(h1_full)
    if start_idx is None:
        return []

    trades = []
    n = len(h1_full)
    i = start_idx
    while i < n:
        lo = max(0, i - window + 1)
        w = h1_full.iloc[lo:i + 1]
        result = evaluate_from_h1(w, pair=pair)

        if result["state"] == "fired":
            res = resolve_trade(h1_full, entry_idx=i, direction=result["direction"],
                                 entry=result["entry"], stop=result["stop"], target=result["target"])
            trades.append({
                "pair": pair, "direction": result["direction"], "entry": result["entry"],
                "stop": result["stop"], "target": result["target"], "planned_rr": result["rr"],
                "realized_R": res["realized_R"], "bars_held": res["bars_held"],
                "exit_reason": res["exit_reason"],
            })
            i = res["exit_idx"] + 1   # one open position at a time; resume after the exit
        else:
            i += 1

    return trades


def spotcheck_window_equivalence(pair: str, h1_full: pd.DataFrame, window: int = WINDOW,
                                  n_samples: int = 20, seed: int = 0) -> dict:
    """
    DECISION-008's performance shortcut (a bounded rolling window instead of the full,
    growing history) must not change WHICH signals fire. Samples n_samples indices from the
    valid scanning range and compares evaluate_from_h1's `state` using the full prefix
    (h1_full.iloc[:i+1]) against the bounded window (h1_full.iloc[i-window+1:i+1]) at each.
    Returns {"n_checked", "n_mismatched", "mismatches": [...]}.
    """
    start_idx = _first_index_with_min_d1(h1_full)
    if start_idx is None or start_idx >= len(h1_full) - 1:
        return {"n_checked": 0, "n_mismatched": 0, "mismatches": []}

    rng = np.random.default_rng(seed)
    candidates = np.arange(start_idx, len(h1_full))
    sample = rng.choice(candidates, size=min(n_samples, len(candidates)), replace=False)

    mismatches = []
    for i in sample:
        i = int(i)
        full_state = evaluate_from_h1(h1_full.iloc[:i + 1], pair=pair)["state"]
        lo = max(0, i - window + 1)
        windowed_state = evaluate_from_h1(h1_full.iloc[lo:i + 1], pair=pair)["state"]
        if full_state != windowed_state:
            mismatches.append({"index": i, "full_state": full_state, "windowed_state": windowed_state})

    return {"n_checked": len(sample), "n_mismatched": len(mismatches), "mismatches": mismatches}


# ── Reporting ─────────────────────────────────────────────────────────────────────
def _compute_stats(trades: list[dict]) -> dict:
    """win = realized_R > 0 (a timeout that closed in profit still counts); loss =
    realized_R < 0. Profit factor = gross win-R / gross loss-R."""
    n = len(trades)
    if n == 0:
        return {
            "n_trades": 0, "win_pct": None, "avg_planned_rr": None, "avg_realized_R": None,
            "total_R": None, "profit_factor": None, "max_consec_losses": None,
            "avg_bars_held": None, "pct_timeouts": None,
        }
    df = pd.DataFrame(trades)
    wins = df[df["realized_R"] > 0]
    losses = df[df["realized_R"] < 0]
    gross_profit = float(wins["realized_R"].sum())
    gross_loss = float(-losses["realized_R"].sum())

    max_consec = cur = 0
    for is_loss in (df["realized_R"] < 0):
        cur = cur + 1 if is_loss else 0
        max_consec = max(max_consec, cur)

    return {
        "n_trades": n,
        "win_pct": round(len(wins) / n * 100, 1),
        "avg_planned_rr": round(float(df["planned_rr"].mean()), 3),
        "avg_realized_R": round(float(df["realized_R"].mean()), 3),
        "total_R": round(float(df["realized_R"].sum()), 2),
        "profit_factor": round(gross_profit / gross_loss, 2) if gross_loss > 0 else (
            float("inf") if gross_profit > 0 else float("nan")),
        "max_consec_losses": max_consec,
        "avg_bars_held": round(float(df["bars_held"].mean()), 1),
        "pct_timeouts": round((df["exit_reason"] == "timeout").mean() * 100, 1),
    }


def print_report(trades_by_pair: dict[str, list[dict]]) -> None:
    cols = ["pair", "n_trades", "win_pct", "avg_planned_rr", "avg_realized_R", "total_R",
            "profit_factor", "max_consec_losses", "avg_bars_held", "pct_timeouts"]
    header = f"{'pair':10s} {'n':>5s} {'win%':>6s} {'avgRR':>7s} {'avgR':>7s} {'totR':>8s} {'PF':>6s} {'maxCL':>6s} {'avgBH':>7s} {'to%':>6s}"
    print(header)
    print("-" * len(header))

    all_trades = []
    for pair, trades in trades_by_pair.items():
        all_trades.extend(trades)
        s = _compute_stats(trades)
        print(f"{pair:10s} {s['n_trades']:>5} {_fmt(s['win_pct']):>6} {_fmt(s['avg_planned_rr']):>7} "
              f"{_fmt(s['avg_realized_R']):>7} {_fmt(s['total_R']):>8} {_fmt(s['profit_factor']):>6} "
              f"{_fmt(s['max_consec_losses']):>6} {_fmt(s['avg_bars_held']):>7} {_fmt(s['pct_timeouts']):>6}")

    print("-" * len(header))
    agg = _compute_stats(all_trades)
    print(f"{'TOTAL':10s} {agg['n_trades']:>5} {_fmt(agg['win_pct']):>6} {_fmt(agg['avg_planned_rr']):>7} "
          f"{_fmt(agg['avg_realized_R']):>7} {_fmt(agg['total_R']):>8} {_fmt(agg['profit_factor']):>6} "
          f"{_fmt(agg['max_consec_losses']):>6} {_fmt(agg['avg_bars_held']):>7} {_fmt(agg['pct_timeouts']):>6}")

    print()
    _print_honest_read(agg)


def _fmt(v) -> str:
    return "-" if v is None else (f"{v:.2f}" if isinstance(v, float) else str(v))


def _print_honest_read(agg: dict) -> None:
    n = agg["n_trades"]
    avg_r = agg["avg_realized_R"]
    if n == 0 or avg_r is None:
        print("No trades fired across the whole backtest window — there is nothing here to "
              "read an edge from yet; check the H1 cache and param thresholds before anything else.")
        return
    edge = "POSITIVE" if avg_r > 0 else ("BREAKEVEN" if avg_r == 0 else "NEGATIVE")
    enough = "large enough to be worth reading" if n >= 100 else (
        "small — treat this as a rough first look, not a verdict" if n >= 30 else
        "far too small to draw any conclusion from")
    print(
        f"Aggregate expectancy (avg realized R across {n} trades) is {avg_r:.3f}R -> {edge}. "
        f"Sample size ({n} trades) is {enough}. This is a BASELINE edge check on the detector's "
        f"PROPOSED default params (spec §8) — it is not a validation of any tuned variant, and "
        f"the params must NOT be adjusted to make THIS run look better (that is overfitting to "
        f"one historical sample, not evidence of a real edge). Any parameter change belongs in a "
        f"separate, out-of-sample sweep, reviewed before DECISION-005 is ratified."
    )


def write_trades_csv(trades_by_pair: dict[str, list[dict]]) -> str:
    os.makedirs(BACKTEST_DIR, exist_ok=True)
    path = os.path.join(BACKTEST_DIR, "trades.csv")
    all_trades = [t for trades in trades_by_pair.values() for t in trades]
    cols = ["pair", "direction", "entry", "stop", "target", "planned_rr",
            "realized_R", "bars_held", "exit_reason"]
    pd.DataFrame(all_trades, columns=cols).to_csv(path, index=False)
    return path


# ── CLI ────────────────────────────────────────────────────────────────────────
def main(argv=None) -> None:
    parser = argparse.ArgumentParser(description="ATOM FX trend-pullback baseline backtest")
    parser.add_argument("pairs", nargs="*", default=None, help="pairs to backtest, e.g. EUR/USD")
    parser.add_argument("--refresh", action="store_true", help="force a full H1 re-fetch")
    args = parser.parse_args(argv)

    if not fetch.API_KEY:
        sys.exit("Set TWELVEDATA_KEY in your environment first.")

    pairs = args.pairs or PAIRS
    print(f"Backtesting {len(pairs)} pair(s) with detector defaults: {PARAMS}\n")

    trades_by_pair: dict[str, list[dict]] = {}
    for pair in pairs:
        try:
            h1 = fetch_h1_history(pair, refresh=args.refresh)
            if len(h1) < MIN_D1_BARS:
                print(f"{pair}: only {len(h1)} H1 bars cached, not enough to backtest — skipped")
                continue

            check = spotcheck_window_equivalence(pair, h1)
            print(f"{pair}: window-vs-full-history spot-check — "
                  f"{check['n_checked'] - check['n_mismatched']}/{check['n_checked']} matched")
            if check["n_mismatched"]:
                print(f"  ⚠ {pair}: {check['n_mismatched']} mismatch(es): {check['mismatches']}")

            trades = simulate_pair(pair, h1)
            trades_by_pair[pair] = trades
            print(f"{pair}: {len(trades)} trades")
        except Exception as e:
            print(f"{pair}: ERROR - {e}")
            continue

    print()
    print_report(trades_by_pair)
    path = write_trades_csv(trades_by_pair)
    print(f"\nTrades written to {path}")


if __name__ == "__main__":
    main()
