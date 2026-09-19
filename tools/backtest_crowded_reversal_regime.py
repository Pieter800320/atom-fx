"""
ATOM FX research — Experiment 6: Crowded Market score > 30 shown against the OPPOSITE background colour.

Pieter's strategy (2026-09-19): a Crowd TOP score above 30 while the strong-trend shading is GREEN (strong uptrend), or a
Crowd BOTTOM score above 30 while it is RED (strong downtrend) — a counter-trend fade inside a strong trend, i.e. exactly
the flags the Pine's "Suppress" mode removes. Outcome, race and de-clustering are Experiments 2-4's (barrier_race.py:
1 x ATR, 20 bars, same-bar-both -> continuation, timeout -> not a reversal). The ONE new element is the baseline:

  REGIME-MATCHED baseline. In a strong uptrend a downward 1 x ATR move is rarer than in an average bar, so a flagged top
  must be compared with other bars in the SAME regime, not with all bars. For the primary arm the top-side baseline is every
  valid bar of the same pair with the green shading (any score), the bottom-side baseline every bar with the red shading.
  The lift therefore answers: does score > 30 add anything beyond the background colour itself?

Arms (all on the same bars; only "opposite" carries gates):
  opposite     top: score>30 & green   | bottom: score>30 & red         (baseline: same-regime bars)     <- THE STRATEGY
  with_trend   top: score>30 & red     | bottom: score>30 & green      (baseline: same-regime bars)     informational
  no_shading   score>30 with no shading                               (baseline: unshaded bars)        informational
  any          score>30, any background                               (baseline: all bars)             gate F7 comparison

    py -m tools.backtest_crowded_reversal_regime --timeframe d1utc --tag exp6_d1utc_2026-09 --mode full
    py -m tools.backtest_crowded_reversal_regime --timeframe h4utc --tag exp6_h4utc_2026-09 --mode full

--mode smoke  PLUMBING CHECK ONLY (verdicts forced to "PLUMBING ONLY").  --mode full  the pre-registered run.
Nothing here is tuned: 30 is Pieter's number, the regime is the Pine's own debounced strong-trend latch, the race is fixed.
Outputs: data/backtest/crowded_reversal_<tag>/{params.json, summary.md, events.csv}
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

THRESHOLD = 30.0            # strictly above (Pieter: "above 30")
ARMS = ["opposite", "with_trend", "no_shading", "any"]
GATES = {"min_events": 100, "min_lift": 0.05, "flagged_rate_gt": 0.50}


def side_baseline(result, eval_mask, months, top_mask, bot_mask):
    """Per (month) reversal counts like br.baseline_counts, but the TOP side counts only bars where top_mask holds and the
    BOTTOM side only bars where bot_mask holds (the regime-matched baseline)."""
    out = {}
    valid = eval_mask & (result != br.CENSORED)
    for i in np.flatnonzero(valid):
        rec = out.setdefault(months[i], {"bottom": [0, 0], "top": [0, 0]})
        if top_mask[i]:
            rec["top"][1] += 1
            rec["top"][0] += int(br.is_reversal(result[i], "top"))
        if bot_mask[i]:
            rec["bottom"][1] += 1
            rec["bottom"][0] += int(br.is_reversal(result[i], "bottom"))
    return out


def run(args):
    cfg = bt.TIMEFRAMES[args.timeframe]
    data_dir = Path(args.data_dir)
    rep = bt.load_cot()
    stores = {}
    for pair in args.pairs:
        df = bt.load_bars(pair, args.timeframe, data_dir)
        if len(df) < args.warmup + 20 + 50:
            print(f"  ! {pair}: only {len(df)} bars — skipped")
            continue
        stores[pair] = df
    if not stores:
        sys.exit("no usable pairs")
    calendar = pd.bdate_range(rep["date"].min(), max(d["cot_day"].max() for d in stores.values()) + pd.Timedelta(days=10))
    p = dict(cr.PINE_DEFAULTS)
    studies = {a: br.Study(f"score>{THRESHOLD:.0f} {a}", 20) for a in ARMS}
    bars_by_regime = {"green": 0, "red": 0, "none": 0}
    eval_ranges = {}

    for pair, df in stores.items():
        dates = pd.to_datetime(df["date"]).to_numpy()
        cot = bt.cot_combined_for_pair(pair, rep, calendar, p["cot_lookback_weeks"]).reindex(
            pd.DatetimeIndex(df["cot_day"])).to_numpy()
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
            print(f"  ! {pair}: empty evaluation window — skipped")
            continue
        wi = np.flatnonzero(eval_mask)
        eval_ranges[pair] = [str(pd.Timestamp(dates[wi[0]]).date()), str(pd.Timestamp(dates[wi[-1]]).date())]
        months = np.asarray(pd.to_datetime(dates).strftime("%Y-%m"))

        top, bot = cr.apply_regime(flags, "off", p["regime_penalty"])          # the raw composite score, no regime adjustment
        top, bot = np.asarray(top, float), np.asarray(bot, float)
        up, dn = flags["strong_up"].to_numpy(bool), flags["strong_dn"].to_numpy(bool)
        none = ~up & ~dn
        allb = np.ones(len(df), bool)
        bars_by_regime["green"] += int((up & eval_mask).sum())
        bars_by_regime["red"] += int((dn & eval_mask).sum())
        bars_by_regime["none"] += int((none & eval_mask).sum())

        # arm -> (top flag mask, bottom flag mask) on the regime at the SAME bar; the baseline uses the same two masks
        defs = {"opposite": (up, dn), "with_trend": (dn, up), "no_shading": (none, none), "any": (allb, allb)}
        for arm, (tm, bm) in defs.items():
            top_new = cr.rising_edge((top > THRESHOLD) & tm)
            bot_new = cr.rising_edge((bot > THRESHOLD) & bm)
            studies[arm].add_pair(pair, dates, result, k, top_new, bot_new, eval_mask,
                                  side_baseline(result, eval_mask, months, tm, bm))
        print(f"  {pair}: {len(df)} bars, eval {eval_ranges[pair][0]} .. {eval_ranges[pair][1]}")
    return studies, eval_ranges, bars_by_regime, rep


def evaluate(studies, n_boot):
    """F1-F5 are Experiment 3's gate logic (evaluate_flags) applied to the 'opposite' arm; F6 and F7 are this experiment's."""
    out = {}
    for arm, s in studies.items():
        months = sorted({m for (_, _, m) in s.base})
        split = months[(len(months) - 1) // 2] if months else ""
        pair_list = sorted({q for (q, _, _) in s.base})
        overall = s.lift(n_boot=n_boot, seed=bt.BOOT_SEED)
        pp = s.per_pair()
        best_pair = pp.sort_values("contribution", ascending=False).iloc[0]["pair"] if len(pp) else None
        h1 = s.lift(month_filter=lambda m: m <= split, n_boot=0)
        h2 = s.lift(month_filter=lambda m: m > split, n_boot=0)
        exj = s.lift(pairs=[q for q in pair_list if "JPY" not in q], n_boot=0)
        exb = s.lift(pairs=[q for q in pair_list if q != best_pair], n_boot=0)
        by_side = {sd: {"n": int(sum(e["side"] == sd for e in s.events)),
                        "rate": float(np.mean([e["y"] for e in s.events if e["side"] == sd])) if any(e["side"] == sd for e in s.events) else np.nan}
                   for sd in ("top", "bottom")}
        out[arm] = {"overall": overall, "counts": s.outcome_counts(), "per_pair": pp, "h1": h1, "h2": h2, "ex_jpy": exj,
                    "ex_best": exb, "best_pair": best_pair, "split": split, "conflicts": s.conflicts,
                    "censored_flags": s.censored_flags, "by_side": by_side}
    o = out["opposite"]
    pos = lambda r: (not np.isnan(r["lift"])) and r["lift"] > 0
    ov, anyv = o["overall"], out["any"]["overall"]
    o["checks"] = {
        f"n de-clustered events >= {GATES['min_events']}": ov["n_events"] >= GATES["min_events"],
        f"lift >= {GATES['min_lift']:.2f}": (not np.isnan(ov["lift"])) and ov["lift"] >= GATES["min_lift"],
        "95% CI lower bound > 0": (not np.isnan(ov["ci_lo"])) and ov["ci_lo"] > 0,
        "both halves lift > 0": pos(o["h1"]) and pos(o["h2"]),
        "excluding JPY crosses lift > 0": pos(o["ex_jpy"]),
        f"excluding best pair ({o['best_pair']}) lift > 0": pos(o["ex_best"]),
        f"F6 flagged reversal rate > {GATES['flagged_rate_gt']:.0%} (a 1:1 race needs it before any cost)":
            (not np.isnan(ov["flagged_rate"])) and ov["flagged_rate"] > GATES["flagged_rate_gt"],
        "F7 lift beats score>30 with ANY background (the colour must add something)":
            (not np.isnan(ov["lift"])) and (not np.isnan(anyv["lift"])) and ov["lift"] > anyv["lift"],
    }
    return out


def verdict(res, mode):
    if mode == "smoke":
        return "PLUMBING ONLY — no verdict (smoke run)"
    n = res["opposite"]["overall"]["n_events"]
    if n < GATES["min_events"]:
        return f"INCONCLUSIVE — {n} de-clustered events < {GATES['min_events']}"
    failed = [k for k, v in res["opposite"]["checks"].items() if not v]
    return "PASS" if not failed else "FAIL — " + "; ".join(failed)


def _f(x, pct=False, nd=3):
    if x is None or (isinstance(x, float) and np.isnan(x)):
        return "n/a"
    return f"{x * 100:.1f}%" if pct else f"{x:.{nd}f}"


def write_outputs(args, res, studies, eval_ranges, bars_by_regime, rep, out_dir):
    out_dir.mkdir(parents=True, exist_ok=True)
    params = {
        "experiment": 6, "tag": args.tag, "mode": args.mode, "timeframe": args.timeframe,
        "score_threshold_strictly_above": THRESHOLD, "regime": "Pine debounced strong-trend latch (ADX>=30, EMA200 slope over 20, persist 3)",
        "race": bt.DEFAULT_RACE, "gates": GATES, "boot": args.boot, "seed": bt.BOOT_SEED, "pine_defaults": cr.PINE_DEFAULTS,
        "eval_start": args.eval_start, "eval_end": args.eval_end, "warmup": args.warmup, "pairs": args.pairs,
        "git_head": bt._git_head(), "cot_sha256": bt._sha256(bt.COT_CSV),
        "python": platform.python_version(), "run_at": datetime.datetime.now().isoformat(timespec="seconds"),
        "eval_ranges": eval_ranges, "eval_bars_by_background": bars_by_regime,
    }
    (out_dir / "params.json").write_text(json.dumps(params, indent=2, default=str), encoding="utf-8")
    ev = [dict(e, arm=a) for a, s in studies.items() for e in s.events]
    pd.DataFrame(ev).to_csv(out_dir / "events.csv", index=False)

    L = [f"# Experiment 6 — score > {THRESHOLD:.0f} against the OPPOSITE background ({args.timeframe}, {args.mode})\n"]
    L.append(f"Verdict (`opposite` arm, pre-registered gates): **{verdict(res, args.mode)}**\n")
    L.append(f"Bars evaluated by background: green {bars_by_regime['green']}, red {bars_by_regime['red']}, none {bars_by_regime['none']}\n")
    L.append("| arm | events | flagged reversal rate | matched baseline | lift (95% CI) | top n / rate | bottom n / rate |")
    L.append("|---|---|---|---|---|---|---|")
    for a in ARMS:
        o, c = res[a]["overall"], res[a]["by_side"]
        L.append(f"| {a} | {o['n_events']} | {_f(o['flagged_rate'], True)} | {_f(o['baseline_rate'], True)} | "
                 f"{_f(o['lift'], True)} ({_f(o['ci_lo'], True)} .. {_f(o['ci_hi'], True)}) | "
                 f"{c['top']['n']} / {_f(c['top']['rate'], True)} | {c['bottom']['n']} / {_f(c['bottom']['rate'], True)} |")
    o = res["opposite"]
    L.append("\n## Gates (`opposite` arm)\n")
    for k_, v in o["checks"].items():
        L.append(f"- {'PASS' if v else 'FAIL'}: {k_}")
    L.append(f"\nHalves (split after {o['split']}): first {_f(o['h1']['lift'], True)} (n={o['h1']['n_events']}), "
             f"second {_f(o['h2']['lift'], True)} (n={o['h2']['n_events']}). Ex-JPY {_f(o['ex_jpy']['lift'], True)} "
             f"(n={o['ex_jpy']['n_events']}). Ex-best-pair ({o['best_pair']}) {_f(o['ex_best']['lift'], True)}.")
    L.append(f"Outcomes: {o['counts']}; conflicts {o['conflicts']}; censored flags {o['censored_flags']}.")
    L.append("\n## Per pair (`opposite`)\n")
    L.append(o["per_pair"].to_string(index=False))
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
    print(f"crowded_reversal_regime [{args.mode}] tf={args.timeframe} tag={args.tag}")
    studies, eval_ranges, bars_by_regime, rep = run(args)
    res = evaluate(studies, args.boot)
    out_dir = ROOT / "data" / "backtest" / f"crowded_reversal_{args.tag}"
    write_outputs(args, res, studies, eval_ranges, bars_by_regime, rep, out_dir)
    o = res["opposite"]["overall"]
    print(f"[opposite] n={o['n_events']} rate={_f(o['flagged_rate'], True)} baseline={_f(o['baseline_rate'], True)} "
          f"lift={_f(o['lift'], True)} CI {_f(o['ci_lo'], True)}..{_f(o['ci_hi'], True)}")
    print(f"           {verdict(res, args.mode)}")
    print(f"wrote {out_dir.relative_to(ROOT)}/")


if __name__ == "__main__":
    main()
