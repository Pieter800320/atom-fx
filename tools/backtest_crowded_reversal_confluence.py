"""
ATOM FX research — Experiment 8: do the D1 and H4 Crowd scores COINCIDE, and is there an edge when they do?

Pieter, 2026-09-19: "What happens when the D1 and H4 crowded scores coincide? Any statistical edge there?"  On the app's own grid (corrected bars: D1 = 17:00-NY close,
H4 = NY-session blocks; runner timeframes d1c and h4cny).

  D1 STATE at an H4 bar = the score of the most recent D1 bar COMPLETED BEFORE that H4 bar's trading day began (no look-ahead; conservative: even the last block of a day uses the previous day's D1).
  EVENT (top):    rising edge of [H4 top score > T  AND  D1-state top score > T].   EVENT (bottom): the same for the bottom score.  T = 30 (Pieter's number) primary; T = 60 informational.
  OUTCOME:        Experiments 2-7's barrier race on H4 bars (1 x H4 ATR, 20 bars, same-bar-both -> continuation, timeout -> not a reversal), from the event bar's close.
  BASELINES:      every H4 bar of the same pair, side and month (unconditional, primary) — and, informational, only H4 bars whose D1 state is > T on that side (does the H4 score add anything to the D1 state?).
  ARMS:           coincide (gated) | coincide_dmatched (same events, D1-state-matched baseline) | h4_alone (H4 > T while the D1 state is NOT > T; comparison for gate F7)

    py -m tools.backtest_crowded_reversal_confluence --threshold 30 --tag exp8_t30_2026-09 --mode count|smoke|full
"""
import argparse
import datetime
import json
import sys
from pathlib import Path

import numpy as np
import pandas as pd

ROOT = Path(__file__).parent.parent
sys.path.insert(0, str(ROOT))

from scanner.extend import barrier_race as br
from scanner.extend import crowded_reversal as cr
from tools import backtest_crowded_reversal as bt
from tools.backtest_crowded_reversal_regime import side_baseline, _f

GATES = {"min_events": 100, "min_lift": 0.05, "flagged_rate_gt": 0.50}
ARMS = ["coincide", "coincide_dmatched", "h4_alone"]


def fixed_count(flag, mask, gap=20):
    n, last = 0, -10 ** 9
    for i in np.flatnonzero(flag & mask):
        if i - last >= gap:
            n += 1
            last = i
    return n


def load(pair, tf, rep, calendar, p):
    cfg = bt.TIMEFRAMES[tf]
    df = bt.load_bars(pair, tf, ROOT / cfg["dir"])
    cot = bt.cot_combined_for_pair(pair, rep, calendar, p["cot_lookback_weeks"]).reindex(pd.DatetimeIndex(df["cot_day"])).to_numpy()
    fl = cr.compute_flags(df, cot_combined_pctl=cot, params=p)
    top, bot = cr.apply_regime(fl, "off", p["regime_penalty"])
    return df, fl, np.asarray(top, float), np.asarray(bot, float), cot, cfg


def run(args):
    rep = bt.load_cot()
    p = dict(cr.PINE_DEFAULTS)
    T = args.threshold
    studies = {a: br.Study(a, 20) for a in ARMS}
    counts = {a: 0 for a in ARMS}
    cal = pd.bdate_range(rep["date"].min(), pd.Timestamp("2026-10-05"))
    eval_ranges = {}
    for pair in args.pairs:
        d1, _, d1_top, d1_bot, d1_cot, d1cfg = load(pair, "d1c", rep, cal, p)
        h4, fl4, h4_top, h4_bot, h4_cot, h4cfg = load(pair, "h4cny", rep, cal, p)
        d1_dates = pd.DatetimeIndex(pd.to_datetime(d1["date"]))
        valid_d1 = (np.arange(len(d1)) >= d1cfg["warmup"]) & ~np.isnan(d1_cot)
        td = pd.DatetimeIndex(pd.to_datetime(h4["trading_day"]))
        pos = d1_dates.searchsorted(td, side="left") - 1           # last D1 bar strictly BEFORE the H4 bar's trading day
        ok = pos >= 0
        pos = np.where(ok, pos, 0)
        st_top = np.where(ok & valid_d1[pos], d1_top[pos], np.nan)
        st_bot = np.where(ok & valid_d1[pos], d1_bot[pos], np.nan)
        dates = pd.to_datetime(h4["date"]).to_numpy()
        eval_mask = (np.arange(len(h4)) >= h4cfg["warmup"]) & ~np.isnan(h4_cot) & ~np.isnan(st_top)
        if args.eval_start:
            eval_mask &= dates >= np.datetime64(args.eval_start)
        if not eval_mask.any():
            continue
        wi = np.flatnonzero(eval_mask)
        eval_ranges[pair] = [str(pd.Timestamp(dates[wi[0]]).date()), str(pd.Timestamp(dates[wi[-1]]).date())]
        d_top, d_bot = st_top > T, st_bot > T
        coin_top, coin_bot = cr.rising_edge((h4_top > T) & d_top), cr.rising_edge((h4_bot > T) & d_bot)
        alone_top, alone_bot = cr.rising_edge((h4_top > T) & ~d_top), cr.rising_edge((h4_bot > T) & ~d_bot)
        if args.mode == "count":
            counts["coincide"] += fixed_count(coin_top, eval_mask) + fixed_count(coin_bot, eval_mask)
            counts["h4_alone"] += fixed_count(alone_top, eval_mask) + fixed_count(alone_bot, eval_mask)
            continue
        result, k = br.race_all(h4["high"].to_numpy(float), h4["low"].to_numpy(float), h4["close"].to_numpy(float),
                                fl4["atr"].to_numpy(), horizon=20, mult=1.0)
        months = np.asarray(pd.to_datetime(dates).strftime("%Y-%m"))
        base_all = br.baseline_counts(result, eval_mask, months)
        base_dm = side_baseline(result, eval_mask, months, d_top, d_bot)
        studies["coincide"].add_pair(pair, dates, result, k, coin_top, coin_bot, eval_mask, base_all)
        studies["coincide_dmatched"].add_pair(pair, dates, result, k, coin_top, coin_bot, eval_mask, base_dm)
        studies["h4_alone"].add_pair(pair, dates, result, k, alone_top, alone_bot, eval_mask, base_all)
        print(f"  {pair}: H4 {len(h4)} bars, eval {eval_ranges[pair][0]} .. {eval_ranges[pair][1]}")
    return studies, counts, eval_ranges


def evaluate(studies, n_boot):
    out = {}
    for arm, s in studies.items():
        months = sorted({m for (_, _, m) in s.base})
        split = months[(len(months) - 1) // 2] if months else ""
        pairs = sorted({q for (q, _, _) in s.base})
        pp = s.per_pair()
        best = pp.sort_values("contribution", ascending=False).iloc[0]["pair"] if len(pp) else None
        out[arm] = {"overall": s.lift(n_boot=n_boot, seed=bt.BOOT_SEED), "counts": s.outcome_counts(), "best_pair": best, "split": split,
                    "h1": s.lift(month_filter=lambda m: m <= split, n_boot=0), "h2": s.lift(month_filter=lambda m: m > split, n_boot=0),
                    "ex_jpy": s.lift(pairs=[q for q in pairs if "JPY" not in q], n_boot=0),
                    "ex_best": s.lift(pairs=[q for q in pairs if q != best], n_boot=0)}
    o, pos = out["coincide"], (lambda r: (not np.isnan(r["lift"])) and r["lift"] > 0)
    ov, av = o["overall"], out["h4_alone"]["overall"]
    o["checks"] = {
        f"n de-clustered events >= {GATES['min_events']}": ov["n_events"] >= GATES["min_events"],
        f"lift >= {GATES['min_lift']:.2f}": (not np.isnan(ov["lift"])) and ov["lift"] >= GATES["min_lift"],
        "95% CI lower bound > 0": (not np.isnan(ov["ci_lo"])) and ov["ci_lo"] > 0,
        "both halves lift > 0": pos(o["h1"]) and pos(o["h2"]),
        "excluding JPY crosses lift > 0": pos(o["ex_jpy"]),
        f"excluding best pair ({o['best_pair']}) lift > 0": pos(o["ex_best"]),
        f"F6 reversal rate > {GATES['flagged_rate_gt']:.0%}": (not np.isnan(ov["flagged_rate"])) and ov["flagged_rate"] > GATES["flagged_rate_gt"],
        "F7 lift beats H4-alone (the D1 coincidence must add something)": (not np.isnan(ov["lift"])) and (not np.isnan(av["lift"])) and ov["lift"] > av["lift"],
    }
    return out


def verdict(res, mode):
    if mode == "smoke":
        return "PLUMBING ONLY"
    n = res["coincide"]["overall"]["n_events"]
    if n < GATES["min_events"]:
        return f"INCONCLUSIVE — {n} de-clustered events < {GATES['min_events']}"
    failed = [k for k, v in res["coincide"]["checks"].items() if not v]
    return "PASS" if not failed else "FAIL — " + "; ".join(failed)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--tag", required=True)
    ap.add_argument("--mode", choices=["count", "smoke", "full"], required=True)
    ap.add_argument("--threshold", type=float, default=30.0)
    ap.add_argument("--pairs", nargs="*", default=bt.PAIRS)
    ap.add_argument("--eval-start", default=None)
    ap.add_argument("--boot", type=int, default=5000)
    args = ap.parse_args()
    print(f"confluence [{args.mode}] T={args.threshold} tag={args.tag}")
    studies, counts, eval_ranges = run(args)
    if args.mode == "count":
        print(f"COUNT-ONLY (fixed 20-bar de-clustering, no outcome read): coincide {counts['coincide']}, h4_alone {counts['h4_alone']}")
        return
    res = evaluate(studies, args.boot)
    out_dir = ROOT / "data" / "backtest" / f"crowded_reversal_{args.tag}"
    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / "params.json").write_text(json.dumps({"experiment": 8, "tag": args.tag, "mode": args.mode, "threshold_strictly_above": args.threshold,
                                                     "gates": GATES, "seed": bt.BOOT_SEED, "boot": args.boot, "git_head": bt._git_head(),
                                                     "run_at": datetime.datetime.now().isoformat(timespec="seconds"), "eval_ranges": eval_ranges}, indent=2), encoding="utf-8")
    pd.DataFrame([dict(e, arm=a) for a, s in studies.items() for e in s.events]).to_csv(out_dir / "events.csv", index=False)
    L = [f"# Experiment 8 — D1 and H4 Crowd scores coincide (T={args.threshold:.0f}, {args.mode})\n", f"Verdict (`coincide`): **{verdict(res, args.mode)}**\n",
         "| arm | events | reversal rate | baseline | lift (95% CI) |", "|---|---|---|---|---|"]
    for a in ARMS:
        o = res[a]["overall"]
        L.append(f"| {a} | {o['n_events']} | {_f(o['flagged_rate'], True)} | {_f(o['baseline_rate'], True)} | {_f(o['lift'], True)} ({_f(o['ci_lo'], True)} .. {_f(o['ci_hi'], True)}) |")
    o = res["coincide"]
    L.append("\n## Gates (`coincide`)\n")
    L += [f"- {'PASS' if v else 'FAIL'}: {k_}" for k_, v in o["checks"].items()]
    L.append(f"\nHalves (after {o['split']}): {_f(o['h1']['lift'], True)} (n={o['h1']['n_events']}) / {_f(o['h2']['lift'], True)} (n={o['h2']['n_events']}). "
             f"Ex-JPY {_f(o['ex_jpy']['lift'], True)}. Ex-best-pair ({o['best_pair']}) {_f(o['ex_best']['lift'], True)}. Outcomes {o['counts']}.")
    (out_dir / "summary.md").write_text("\n".join(L) + "\n", encoding="utf-8")
    ov = res["coincide"]["overall"]
    print(f"[coincide] n={ov['n_events']} rate={_f(ov['flagged_rate'], True)} baseline={_f(ov['baseline_rate'], True)} lift={_f(ov['lift'], True)} CI {_f(ov['ci_lo'], True)}..{_f(ov['ci_hi'], True)}")
    print(f"           {verdict(res, args.mode)}")


if __name__ == "__main__":
    main()
