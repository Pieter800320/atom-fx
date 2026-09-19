"""
ATOM FX research — Experiment 7: the opposite background switching ON right after a stretched score.

Pieter's observation (2026-09-19, from six TradingView screenshots): the strong-trend shading is painted LATE (ADX, EMA200 slope
and a 3-bar debounce all lag) and tops/bottoms seem to happen right as the opposite shading appears. Descriptive checks (no
outcomes) confirmed the co-occurrence: for flags with no shading yet, the opposite shading switched on within 10 bars for 45%
(D1) / 63% (H4) of them, against a chance rate of 7-8%. But the shading appears AFTER the flag, so it can only be used as a
CONFIRMATION, and the tradeable moment is the bar where the shading turns on. That is what this experiment measures.

  EVENT (top):    the GREEN shading turns on at bar i (latch False -> True) AND the raw top score was > 30 on at least one bar of [i-N, i].
  EVENT (bottom): the RED shading turns on at bar i AND the raw bottom score was > 30 on at least one bar of [i-N, i].
  OUTCOME:        the Experiments 2-6 barrier race started from bar i's close (1 x ATR, 20 bars, same-bar-both -> continuation).
  BASELINE:       every OTHER onset bar of the same colour (same pair, side, month), whatever the score before it. The lift therefore
                  answers: does a stretched score just before the shading turns on add anything beyond the shading turning on?

Arms:  confirm10 (N=10, THE STRATEGY, gated) | confirm5 (N=5, informational) | bare_onset (every onset, matched to all same-regime
       bars, informational: does the onset moment itself matter?)

    py -m tools.backtest_crowded_reversal_onset --timeframe d1utc --tag exp7_d1utc_2026-09 --mode full
    py -m tools.backtest_crowded_reversal_onset --timeframe h4utc --tag exp7_h4utc_2026-09 --mode full
"""
import argparse
import datetime
import json
import platform
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

THRESHOLD = 30.0
ARMS = {"confirm10": 10, "confirm5": 5, "bare_onset": None}
GATES = {"min_events": 100, "min_lift": 0.05, "flagged_rate_gt": 0.50}


def recent_any(hi, n):
    """True at i if hi[i-n .. i] has any True."""
    c = np.concatenate(([0], np.cumsum(hi.astype(int))))
    idx = np.arange(len(hi))
    lo = np.maximum(0, idx - n)
    return (c[idx + 1] - c[lo]) > 0


def run(args):
    cfg = bt.TIMEFRAMES[args.timeframe]
    rep = bt.load_cot()
    stores = {}
    for pair in args.pairs:
        df = bt.load_bars(pair, args.timeframe, Path(args.data_dir))
        if len(df) >= args.warmup + 70:
            stores[pair] = df
    if not stores:
        sys.exit("no usable pairs")
    calendar = pd.bdate_range(rep["date"].min(), max(d["cot_day"].max() for d in stores.values()) + pd.Timedelta(days=10))
    p = dict(cr.PINE_DEFAULTS)
    studies = {a: br.Study(a, 20) for a in ARMS}
    eval_ranges, onsets = {}, {"green": 0, "red": 0}
    for pair, df in stores.items():
        dates = pd.to_datetime(df["date"]).to_numpy()
        cot = bt.cot_combined_for_pair(pair, rep, calendar, p["cot_lookback_weeks"]).reindex(pd.DatetimeIndex(df["cot_day"])).to_numpy()
        flags = cr.compute_flags(df, cot_combined_pctl=cot, params=p)
        result, k = br.race_all(df["high"].to_numpy(float), df["low"].to_numpy(float), df["close"].to_numpy(float),
                                flags["atr"].to_numpy(), horizon=20, mult=1.0)
        eval_mask = np.arange(len(df)) >= args.warmup
        if args.eval_start:
            eval_mask &= dates >= np.datetime64(args.eval_start)
        if args.eval_end:
            eval_mask &= dates <= np.datetime64(args.eval_end)
        eval_mask &= ~np.isnan(cot)
        if not eval_mask.any():
            continue
        wi = np.flatnonzero(eval_mask)
        eval_ranges[pair] = [str(pd.Timestamp(dates[wi[0]]).date()), str(pd.Timestamp(dates[wi[-1]]).date())]
        months = np.asarray(pd.to_datetime(dates).strftime("%Y-%m"))
        top, bot = cr.apply_regime(flags, "off", p["regime_penalty"])
        top, bot = np.asarray(top, float), np.asarray(bot, float)
        up, dn = flags["strong_up"].to_numpy(bool), flags["strong_dn"].to_numpy(bool)
        on_up = up & ~np.concatenate(([False], up[:-1]))
        on_dn = dn & ~np.concatenate(([False], dn[:-1]))
        onsets["green"] += int((on_up & eval_mask).sum())
        onsets["red"] += int((on_dn & eval_mask).sum())
        for arm, n in ARMS.items():
            if n is None:                       # bare onset: every onset, baseline = all same-regime bars (as Experiment 6)
                top_new, bot_new = on_up, on_dn
                base = side_baseline(result, eval_mask, months, up, dn)
            else:
                top_new = on_up & recent_any(top > THRESHOLD, n)
                bot_new = on_dn & recent_any(bot > THRESHOLD, n)
                base = side_baseline(result, eval_mask, months, on_up, on_dn)     # baseline = the other onset bars
            studies[arm].add_pair(pair, dates, result, k, top_new, bot_new, eval_mask, base)
        print(f"  {pair}: {len(df)} bars, eval {eval_ranges[pair][0]} .. {eval_ranges[pair][1]}")
    return studies, eval_ranges, onsets


def evaluate(studies, n_boot):
    out = {}
    for arm, s in studies.items():
        months = sorted({m for (_, _, m) in s.base})
        split = months[(len(months) - 1) // 2] if months else ""
        pair_list = sorted({q for (q, _, _) in s.base})
        overall = s.lift(n_boot=n_boot, seed=bt.BOOT_SEED)
        pp = s.per_pair()
        best_pair = pp.sort_values("contribution", ascending=False).iloc[0]["pair"] if len(pp) else None
        out[arm] = {"overall": overall, "counts": s.outcome_counts(), "per_pair": pp, "best_pair": best_pair, "split": split,
                    "h1": s.lift(month_filter=lambda m: m <= split, n_boot=0), "h2": s.lift(month_filter=lambda m: m > split, n_boot=0),
                    "ex_jpy": s.lift(pairs=[q for q in pair_list if "JPY" not in q], n_boot=0),
                    "ex_best": s.lift(pairs=[q for q in pair_list if q != best_pair], n_boot=0)}
    o = out["confirm10"]
    pos = lambda r: (not np.isnan(r["lift"])) and r["lift"] > 0
    ov = o["overall"]
    o["checks"] = {
        f"n de-clustered events >= {GATES['min_events']}": ov["n_events"] >= GATES["min_events"],
        f"lift >= {GATES['min_lift']:.2f}": (not np.isnan(ov["lift"])) and ov["lift"] >= GATES["min_lift"],
        "95% CI lower bound > 0": (not np.isnan(ov["ci_lo"])) and ov["ci_lo"] > 0,
        "both halves lift > 0": pos(o["h1"]) and pos(o["h2"]),
        "excluding JPY crosses lift > 0": pos(o["ex_jpy"]),
        f"excluding best pair ({o['best_pair']}) lift > 0": pos(o["ex_best"]),
        f"F6 flagged reversal rate > {GATES['flagged_rate_gt']:.0%}": (not np.isnan(ov["flagged_rate"])) and ov["flagged_rate"] > GATES["flagged_rate_gt"],
    }
    return out


def verdict(res, mode):
    if mode == "smoke":
        return "PLUMBING ONLY — no verdict (smoke run)"
    n = res["confirm10"]["overall"]["n_events"]
    if n < GATES["min_events"]:
        return f"INCONCLUSIVE — {n} de-clustered events < {GATES['min_events']}"
    failed = [k for k, v in res["confirm10"]["checks"].items() if not v]
    return "PASS" if not failed else "FAIL — " + "; ".join(failed)


def write_outputs(args, res, studies, eval_ranges, onsets, out_dir):
    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / "params.json").write_text(json.dumps({
        "experiment": 7, "tag": args.tag, "mode": args.mode, "timeframe": args.timeframe, "threshold_strictly_above": THRESHOLD,
        "arms_N": ARMS, "gates": GATES, "boot": args.boot, "seed": bt.BOOT_SEED, "eval_start": args.eval_start, "eval_end": args.eval_end,
        "git_head": bt._git_head(), "cot_sha256": bt._sha256(bt.COT_CSV), "python": platform.python_version(),
        "run_at": datetime.datetime.now().isoformat(timespec="seconds"), "eval_ranges": eval_ranges, "onsets_in_window": onsets,
    }, indent=2, default=str), encoding="utf-8")
    pd.DataFrame([dict(e, arm=a) for a, s in studies.items() for e in s.events]).to_csv(out_dir / "events.csv", index=False)
    L = [f"# Experiment 7 — shading turns on after a stretched score ({args.timeframe}, {args.mode})\n",
         f"Verdict (`confirm10`, pre-registered gates): **{verdict(res, args.mode)}**\n",
         f"Onset bars in the window: green {onsets['green']}, red {onsets['red']}\n",
         "| arm | events | flagged reversal rate | matched baseline | lift (95% CI) |", "|---|---|---|---|---|"]
    for a in ARMS:
        o = res[a]["overall"]
        L.append(f"| {a} | {o['n_events']} | {_f(o['flagged_rate'], True)} | {_f(o['baseline_rate'], True)} | "
                 f"{_f(o['lift'], True)} ({_f(o['ci_lo'], True)} .. {_f(o['ci_hi'], True)}) |")
    o = res["confirm10"]
    L.append("\n## Gates (`confirm10`)\n")
    L += [f"- {'PASS' if v else 'FAIL'}: {k_}" for k_, v in o["checks"].items()]
    L.append(f"\nHalves (split after {o['split']}): first {_f(o['h1']['lift'], True)} (n={o['h1']['n_events']}), second {_f(o['h2']['lift'], True)} "
             f"(n={o['h2']['n_events']}). Ex-JPY {_f(o['ex_jpy']['lift'], True)} (n={o['ex_jpy']['n_events']}). "
             f"Ex-best-pair ({o['best_pair']}) {_f(o['ex_best']['lift'], True)}.")
    L.append(f"Outcomes: {o['counts']}.\n\n## Per pair (`confirm10`)\n\n" + o["per_pair"].to_string(index=False))
    (out_dir / "summary.md").write_text("\n".join(L) + "\n", encoding="utf-8")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--tag", required=True)
    ap.add_argument("--mode", choices=["smoke", "full"], required=True)
    ap.add_argument("--timeframe", choices=list(bt.TIMEFRAMES), required=True)
    ap.add_argument("--data-dir", default=None)
    ap.add_argument("--pairs", nargs="*", default=bt.PAIRS)
    ap.add_argument("--warmup", type=int, default=None)
    ap.add_argument("--eval-start", default=None)
    ap.add_argument("--eval-end", default=None)
    ap.add_argument("--boot", type=int, default=5000)
    args = ap.parse_args()
    args.data_dir = args.data_dir or bt.TIMEFRAMES[args.timeframe]["dir"]
    args.warmup = args.warmup if args.warmup is not None else bt.TIMEFRAMES[args.timeframe]["warmup"]
    print(f"crowded_reversal_onset [{args.mode}] tf={args.timeframe} tag={args.tag}")
    studies, eval_ranges, onsets = run(args)
    res = evaluate(studies, args.boot)
    out_dir = ROOT / "data" / "backtest" / f"crowded_reversal_{args.tag}"
    write_outputs(args, res, studies, eval_ranges, onsets, out_dir)
    o = res["confirm10"]["overall"]
    print(f"[confirm10] n={o['n_events']} rate={_f(o['flagged_rate'], True)} baseline={_f(o['baseline_rate'], True)} "
          f"lift={_f(o['lift'], True)} CI {_f(o['ci_lo'], True)}..{_f(o['ci_hi'], True)}")
    print(f"            {verdict(res, args.mode)}")
    print(f"wrote {out_dir.relative_to(ROOT)}/")


if __name__ == "__main__":
    main()
