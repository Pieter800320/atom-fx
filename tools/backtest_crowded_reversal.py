"""
ATOM FX research — Crowded Market reversal indicator: barrier-race event study runner.

Ports pine/crowded_reversal.pine (scanner/extend/crowded_reversal.py), runs it over the persisted
NY-close D1 store, and measures whether flagged bars reverse more often than like-for-like
baseline bars (scanner/extend/barrier_race.py — read its docstring for the pre-registered test).

    py -m tools.backtest_crowded_reversal --tag smoke_2026-09 --mode smoke
    py -m tools.backtest_crowded_reversal --tag exp2_2026-09  --mode full --data-dir <long-history>

--mode smoke  PLUMBING CHECK ONLY. Prints and writes a banner; the verdict is forced to
              "PLUMBING ONLY". Draw no edge conclusion from it — the cache is ~2 years.
--mode full   The pre-registered run. Verdict = the gates below, applied mechanically.

Nothing is tuned here. Every parameter is a Pine default or a pre-registered value recorded in
docs/RESEARCH_LOG.md; changing one is a new experiment, not a re-run.

Outputs to data/backtest/crowded_reversal_<tag>/:  params.json, events.csv, summary.md
"""
import argparse
import datetime
import hashlib
import json
import platform
import subprocess
import sys
from pathlib import Path

import numpy as np
import pandas as pd

ROOT = Path(__file__).parent.parent
sys.path.insert(0, str(ROOT))

from scanner.extend import barrier_race as br
from scanner.extend import crowded_reversal as cr
from scanner.extend.d1_store import load_store

PAIRS = ["EURUSD", "GBPUSD", "USDJPY", "USDCAD", "NZDUSD", "AUDUSD", "USDCHF",
         "EURJPY", "GBPJPY", "CADJPY", "AUDJPY", "NZDJPY"]
SINGLE_FACTORS = ["pct_b", "z", "stretch", "rsi", "div", "climax", "cot"]
COT_CSV = ROOT / "data" / "cot_legacy" / "legacy_nc.csv"

# Pre-registered defaults (mirrored in docs/RESEARCH_LOG.md, Experiment 2)
DEFAULT_RACE = {"horizon": 20, "mult": 1.0, "tie_rule": "same-bar both barriers -> continuation",
                "timeout": "counts as not-a-reversal, kept in denominator"}
DEFAULT_GATES = {"min_events": 100, "min_lift": 0.05, "ci_lo_gt_zero": True,
                 "both_halves_lift_gt_zero": True, "ex_jpy_lift_gt_zero": True,
                 "ex_best_pair_lift_gt_zero": True, "beats_best_single_factor": True}
BOOT_SEED = 20260919


def _git_head():
    try:
        return subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    except Exception:
        return "unknown"


def _sha256(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def load_cot():
    rep = pd.read_csv(COT_CSV, dtype={"code": str}, parse_dates=["date"])
    rep["net"] = rep["nc_long"] - rep["nc_short"]
    return rep


def cot_combined_for_pair(pair, rep, calendar, lookback_weeks, week_lag=1):
    """Pine cotCombinedPctl on the business-day calendar, for one pair."""
    base, quote = cr.pair_legs(pair)

    def leg(ccy):
        code = cr.CFTC_CODES.get(ccy)
        if code is None or not (rep["code"] == code).any():
            return np.full(len(calendar), np.nan)
        return cr.cot_percentile_daily(rep[rep["code"] == code][["date", "net"]], calendar,
                                       lookback_weeks, week_lag).to_numpy()

    return pd.Series(cr.combine_cot_pctl(leg(base), leg(quote)), index=calendar)


def run(args):
    data_dir = Path(args.data_dir)
    rep = load_cot()
    stores = {}
    for pair in args.pairs:
        df = load_store(pair, str(data_dir)).reset_index(drop=True)
        if len(df) < args.warmup + args.horizon + 50:
            print(f"  ! {pair}: only {len(df)} bars — skipped")
            continue
        stores[pair] = df
    if not stores:
        sys.exit("no usable pairs")

    last_price = max(df["date"].max() for df in stores.values())
    calendar = pd.bdate_range(rep["date"].min(), last_price + pd.Timedelta(days=10))
    pine_params = dict(cr.PINE_DEFAULTS)

    arms = args.regime_arms
    studies = {f"score {arm}": br.Study(f"score>={pine_params['top_threshold']:.0f} regime={arm}", args.horizon)
               for arm in arms}
    for f in SINGLE_FACTORS:
        studies[f"single {f}"] = br.Study(f"single factor: {f}", args.horizon)

    eval_ranges, price_ranges = {}, {}
    for pair, df in stores.items():
        dates = pd.to_datetime(df["date"]).to_numpy()
        cot = cot_combined_for_pair(pair, rep, calendar, pine_params["cot_lookback_weeks"]).reindex(
            pd.DatetimeIndex(dates)).to_numpy()
        flags = cr.compute_flags(df, cot_combined_pctl=cot, params=pine_params)
        result, k = br.race_all(df["high"].to_numpy(float), df["low"].to_numpy(float),
                                df["close"].to_numpy(float), flags["atr"].to_numpy(),
                                horizon=args.horizon, mult=args.mult)

        eval_mask = np.arange(len(df)) >= args.warmup
        if args.eval_start:
            eval_mask &= dates >= np.datetime64(args.eval_start)
        # the COT factor must be resolvable inside the window, or the score's ceiling silently drops to 75
        eval_mask &= ~np.isnan(cot)
        if not eval_mask.any():
            print(f"  ! {pair}: empty evaluation window — skipped")
            continue
        price_ranges[pair] = [str(pd.Timestamp(dates[0]).date()), str(pd.Timestamp(dates[-1]).date()), len(df)]
        w = np.flatnonzero(eval_mask)
        eval_ranges[pair] = [str(pd.Timestamp(dates[w[0]]).date()), str(pd.Timestamp(dates[w[-1]]).date())]

        months = np.asarray(pd.to_datetime(dates).strftime("%Y-%m"))
        base_counts = br.baseline_counts(result, eval_mask, months)     # once per pair, shared

        for arm in arms:
            top_adj, bot_adj = cr.apply_regime(flags, arm, pine_params["regime_penalty"])
            top_new = cr.rising_edge(top_adj >= pine_params["top_threshold"])
            bot_new = cr.rising_edge(bot_adj >= pine_params["bottom_threshold"])
            studies[f"score {arm}"].add_pair(pair, dates, result, k, top_new, bot_new, eval_mask, base_counts)
        for f in SINGLE_FACTORS:
            top_new = cr.rising_edge(flags[f"top_{f}"].to_numpy())
            bot_new = cr.rising_edge(flags[f"bot_{f}"].to_numpy())
            studies[f"single {f}"].add_pair(pair, dates, result, k, top_new, bot_new, eval_mask, base_counts)
        print(f"  {pair}: {len(df)} bars, eval {eval_ranges[pair][0]} .. {eval_ranges[pair][1]}")

    return studies, price_ranges, eval_ranges, rep


def evaluate(studies, arms, gates, eval_ranges, n_boot):
    """Everything the summary and the verdict need, computed once."""
    lo = min(pd.Timestamp(v[0]) for v in eval_ranges.values())
    hi = max(pd.Timestamp(v[1]) for v in eval_ranges.values())
    mid_month = (lo + (hi - lo) / 2).strftime("%Y-%m")
    is_jpy = lambda p: "JPY" in p
    all_pairs = sorted(eval_ranges)

    single = {name: s.lift(n_boot=n_boot, seed=BOOT_SEED) for name, s in studies.items() if name.startswith("single ")}
    best_single_name = max(single, key=lambda n: (-np.inf if np.isnan(single[n]["lift"]) else single[n]["lift"]))
    best_single = single[best_single_name]["lift"]

    out = {"mid_month": mid_month, "single": single, "best_single": (best_single_name, best_single), "arms": {}}
    for arm in arms:
        s = studies[f"score {arm}"]
        overall = s.lift(n_boot=n_boot, seed=BOOT_SEED)
        pp = s.per_pair()
        best_pair = pp.sort_values("contribution", ascending=False).iloc[0]["pair"] if len(pp) else None
        h1 = s.lift(month_filter=lambda m: m <= mid_month, n_boot=0)
        h2 = s.lift(month_filter=lambda m: m > mid_month, n_boot=0)
        exj = s.lift(pairs=[p for p in all_pairs if not is_jpy(p)], n_boot=0)
        exb = s.lift(pairs=[p for p in all_pairs if p != best_pair], n_boot=0)
        pos = lambda r: (not np.isnan(r["lift"])) and r["lift"] > 0
        checks = {
            f"n_events >= {gates['min_events']}": overall["n_events"] >= gates["min_events"],
            f"lift >= {gates['min_lift']:.2f}": (not np.isnan(overall["lift"])) and overall["lift"] >= gates["min_lift"],
            "CI lower bound > 0": (not np.isnan(overall["ci_lo"])) and overall["ci_lo"] > 0,
            "both halves lift > 0": pos(h1) and pos(h2),
            "excluding JPY crosses lift > 0": pos(exj),
            f"excluding best pair ({best_pair}) lift > 0": pos(exb),
            f"beats best single factor ({best_single_name.split()[-1]})": (not np.isnan(overall["lift"])) and overall["lift"] > best_single,
        }
        out["arms"][arm] = {"overall": overall, "counts": s.outcome_counts(), "per_pair": pp,
                            "h1": h1, "h2": h2, "ex_jpy": exj, "ex_best": exb, "best_pair": best_pair,
                            "checks": checks, "conflicts": s.conflicts, "censored_flags": s.censored_flags}
    return out


def verdict(arm_res, gates, mode):
    if mode == "smoke":
        return "PLUMBING ONLY — no verdict (smoke run on a short cache)"
    if arm_res["overall"]["n_events"] < gates["min_events"]:
        return f"INCONCLUSIVE — {arm_res['overall']['n_events']} de-clustered events < {gates['min_events']}"
    failed = [k for k, v in arm_res["checks"].items() if not v]
    return "PASS" if not failed else "FAIL — " + "; ".join(failed)


def _f(x, pct=False, nd=3):
    if x is None or (isinstance(x, float) and np.isnan(x)):
        return "n/a"
    return f"{x * 100:.1f}%" if pct else f"{x:.{nd}f}"


def write_outputs(args, studies, res, price_ranges, eval_ranges, rep, out_dir):
    out_dir.mkdir(parents=True, exist_ok=True)

    ev_rows = []
    for name, s in studies.items():
        for e in s.events:
            ev_rows.append({"study": name, **{k: e[k] for k in ("pair", "date", "side", "outcome", "tie", "k")}})
    pd.DataFrame(ev_rows).to_csv(out_dir / "events.csv", index=False)

    params = {
        "experiment": "crowded_reversal barrier-race event study", "mode": args.mode, "tag": args.tag,
        "run_utc": datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds"),
        "code_commit": _git_head(), "python": platform.python_version(),
        "numpy": np.__version__, "pandas": pd.__version__,
        "pine_source": "pine/crowded_reversal.pine (main) as ported by scanner/extend/crowded_reversal.py",
        "pine_params": cr.PINE_DEFAULTS, "regime_arms": args.regime_arms,
        "race": {**DEFAULT_RACE, "horizon": args.horizon, "mult": args.mult},
        "eval": {"warmup_bars": args.warmup, "eval_start": args.eval_start,
                 "requires_cot_available": True},
        "bootstrap": {"kind": "month-clustered, baseline recomputed per replicate",
                      "n": args.boot, "seed": BOOT_SEED, "ci": "2.5-97.5"},
        "gates": DEFAULT_GATES,
        "sources": {
            "price": {"store": str(Path(args.data_dir)), "convention": "NY-close D1 (17:00 ET), FX-week filtered",
                      "per_pair [first, last, bars]": price_ranges},
            "cot": {"file": str(COT_CSV.relative_to(ROOT)), "sha256": _sha256(COT_CSV),
                    "report": "CFTC Legacy futures-only, Non-Commercial long/short",
                    "as_of_range": [str(rep["date"].min().date()), str(rep["date"].max().date())],
                    "alignment": "week k uses Tuesday-of-week-(k-1) report; Pine close[1]+lookahead_on"},
        },
        "eval_window_per_pair": eval_ranges,
    }
    (out_dir / "params.json").write_text(json.dumps(params, indent=2, default=str))

    L = []
    if args.mode == "smoke":
        L += ["> **PLUMBING CHECK ONLY.** This ran the ported logic end-to-end on a short cache to prove",
              "> it executes and fires. **No edge conclusion may be drawn from any number below.**", ""]
    L += [f"# Crowded Market reversal — barrier-race event study ({args.tag})", "",
          f"Mode `{args.mode}` · horizon {args.horizon} bars · barrier {args.mult} x ATR(14) · "
          f"tie -> continuation · bootstrap {args.boot} (month-clustered, seed {BOOT_SEED})", "",
          f"Price: {args.data_dir}, {len(price_ranges)} pairs. COT: Legacy futures-only "
          f"{rep['date'].min().date()} .. {rep['date'].max().date()}. Commit `{_git_head()[:10]}`.", ""]
    L += ["## Verdicts", ""]
    for arm, r in res["arms"].items():
        L.append(f"- **regime = {arm}:** {verdict(r, DEFAULT_GATES, args.mode)}")
    L.append("")

    for arm, r in res["arms"].items():
        o, c = r["overall"], r["counts"]
        L += [f"## Score >= 60, regime = {arm}", "",
              f"- De-clustered events: **{o['n_events']}** (reversal {c['reversal']}, continuation "
              f"{c['continuation']}, timeout {c['timeout']}; of which same-bar ties {c['ties']}). "
              f"Same-bar top+bottom conflicts skipped: {r['conflicts']}. Flags on censored bars: {r['censored_flags']}.",
              f"- Flagged reversal rate {_f(o['flagged_rate'], True)} vs like-for-like baseline "
              f"{_f(o['baseline_rate'], True)} -> **lift {_f(o['lift'], True)}** "
              f"(95% CI {_f(o['ci_lo'], True)} .. {_f(o['ci_hi'], True)}).",
              f"- Halves (split {res['mid_month']}): first {_f(r['h1']['lift'], True)} (n={r['h1']['n_events']}), "
              f"second {_f(r['h2']['lift'], True)} (n={r['h2']['n_events']}).",
              f"- Excluding JPY crosses: {_f(r['ex_jpy']['lift'], True)} (n={r['ex_jpy']['n_events']}). "
              f"Excluding best pair ({r['best_pair']}): {_f(r['ex_best']['lift'], True)} (n={r['ex_best']['n_events']}).",
              "", "| gate | result |", "|---|---|"]
        L += [f"| {k} | {'pass' if v else 'FAIL'} |" for k, v in r["checks"].items()]
        L += ["", "| pair | events | lift |", "|---|---|---|"]
        L += [f"| {row.pair} | {row.n_events} | {_f(row.lift, True)} |" for row in r["per_pair"].itertuples()]
        L.append("")

    L += ["## Single-factor comparators (same events rules, same baseline)", "",
          "| factor | events | flagged | baseline | lift | 95% CI |", "|---|---|---|---|---|---|"]
    for name, o in res["single"].items():
        L.append(f"| {name.split()[-1]} | {o['n_events']} | {_f(o['flagged_rate'], True)} | "
                 f"{_f(o['baseline_rate'], True)} | {_f(o['lift'], True)} | "
                 f"{_f(o['ci_lo'], True)} .. {_f(o['ci_hi'], True)} |")
    L += ["", "## Known limits of this harness", "",
          "- D1 OHLC cannot sequence intrabar: a bar touching both barriers is scored continuation, for flagged AND baseline.",
          "- COT percentile is computed on a business-day calendar; on 2026-09-19 it reproduced TradingView's label to within "
          "~0.3 percentile points (net positions matched exactly). Attributed, not verified, to TradingView counting chart bars.",
          "- 12 pairs are not independent (USD legs, JPY legs): the month-clustered bootstrap absorbs same-month clustering "
          "but not every cross-pair dependency."]
    (out_dir / "summary.md").write_text("\n".join(L) + "\n", encoding="utf-8")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--tag", required=True)
    ap.add_argument("--mode", choices=["smoke", "full"], required=True)
    ap.add_argument("--data-dir", default="data/d1_nyclose")
    ap.add_argument("--pairs", nargs="*", default=PAIRS)
    ap.add_argument("--horizon", type=int, default=DEFAULT_RACE["horizon"])
    ap.add_argument("--mult", type=float, default=DEFAULT_RACE["mult"])
    ap.add_argument("--warmup", type=int, default=260, help="bars skipped for EMA200/ADX/z warm-up")
    ap.add_argument("--eval-start", default=None)
    ap.add_argument("--boot", type=int, default=5000)
    ap.add_argument("--regime-arms", nargs="*", default=["suppress", "off"])
    args = ap.parse_args()

    print(f"crowded_reversal study [{args.mode}] tag={args.tag}")
    studies, price_ranges, eval_ranges, rep = run(args)
    res = evaluate(studies, args.regime_arms, DEFAULT_GATES, eval_ranges, args.boot)
    out_dir = ROOT / "data" / "backtest" / f"crowded_reversal_{args.tag}"
    write_outputs(args, studies, res, price_ranges, eval_ranges, rep, out_dir)
    for arm, r in res["arms"].items():
        o = r["overall"]
        print(f"[{arm}] events={o['n_events']} flagged={_f(o['flagged_rate'], True)} "
              f"baseline={_f(o['baseline_rate'], True)} lift={_f(o['lift'], True)} -> {verdict(r, DEFAULT_GATES, args.mode)}")
    print(f"wrote {out_dir.relative_to(ROOT)}/")


if __name__ == "__main__":
    main()
