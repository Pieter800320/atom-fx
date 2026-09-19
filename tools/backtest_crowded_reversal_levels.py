"""
ATOM FX research — Experiment 4: do flagged bars trade BACK to the previous support/resistance?

Same flags as Experiments 2/3 (the Pine port, score >= 60, rising edge, de-clustered), a different OUTCOME:
did price reach the most recent confirmed swing level (resistance for a bottom flag, support for a top flag)
before an equal adverse move within 20 bars — measured against a baseline matched on pair, side and the
level's distance in ATR (scanner/extend/level_race.py explains why the matching is essential).

The pre-registered gate logic (F1-F6) is REUSED unchanged from tools.backtest_crowded_reversal.evaluate_flags.

    py -m tools.backtest_crowded_reversal_levels --timeframe h4utc --tag exp4_h4utc_2026-09 --mode full
    py -m tools.backtest_crowded_reversal_levels --timeframe h4ny   --tag exp4_h4ny_2026-09  --mode full
    py -m tools.backtest_crowded_reversal_levels --timeframe d1utc  --tag exp4_d1utc_2026-09 --mode full
    py -m tools.backtest_crowded_reversal_levels --timeframe d1     --tag exp4_d1ny_2026-09  --mode full

--mode smoke  PLUMBING CHECK ONLY (verdicts forced to "PLUMBING ONLY").
Outputs to data/backtest/crowded_reversal_<tag>/: params.json, summary.md, events.csv
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

from scanner.extend import crowded_reversal as cr
from scanner.extend import level_race as lr
from tools import backtest_crowded_reversal as bt

LEVELS = {"swing_n": 5, "lookback": 120, "min_d": 0.5, "max_d": 5.0, "horizon": 20}


def run(args):
    data_dir = Path(args.data_dir)
    rep = bt.load_cot()
    stores = {}
    for pair in args.pairs:
        df = bt.load_bars(pair, args.timeframe, data_dir)
        if len(df) < args.warmup + LEVELS["horizon"] + 50:
            print(f"  ! {pair}: only {len(df)} bars — skipped")
            continue
        stores[pair] = df
    if not stores:
        sys.exit("no usable pairs")
    calendar = pd.bdate_range(rep["date"].min(), max(d["cot_day"].max() for d in stores.values()) + pd.Timedelta(days=10))
    p = dict(cr.PINE_DEFAULTS)
    arms = args.regime_arms

    studies = {f"score {arm}": lr.LevelStudy(f"score>=60 regime={arm}", LEVELS["horizon"]) for arm in arms}
    for f in bt.SINGLE_FACTORS:
        studies[f"single {f}"] = lr.LevelStudy(f"single factor: {f}", LEVELS["horizon"])
    diag = {"bars": 0, "valid_up": 0, "valid_down": 0}
    price_ranges, eval_ranges = {}, {}

    for pair, df in stores.items():
        dates = pd.to_datetime(df["date"]).to_numpy()
        cot = bt.cot_combined_for_pair(pair, rep, calendar, p["cot_lookback_weeks"]).reindex(
            pd.DatetimeIndex(df["cot_day"])).to_numpy()
        flags = cr.compute_flags(df, cot_combined_pctl=cot, params=p)
        h, l, c = (df[k].to_numpy(float) for k in ("high", "low", "close"))
        atr = flags["atr"].to_numpy()
        up, down = lr.level_targets(h, l, c, atr, LEVELS["swing_n"], LEVELS["lookback"], LEVELS["min_d"], LEVELS["max_d"])
        lv = lr.level_race_all(h, l, c, atr, up, down, LEVELS["horizon"])

        eval_mask = np.arange(len(df)) >= args.warmup
        if args.eval_start:
            eval_mask &= dates >= np.datetime64(args.eval_start)
        if args.eval_end:
            eval_mask &= dates <= np.datetime64(args.eval_end)
        eval_mask &= ~np.isnan(cot)
        if not eval_mask.any():
            print(f"  ! {pair}: empty evaluation window — skipped")
            continue
        price_ranges[pair] = [str(pd.Timestamp(dates[0])), str(pd.Timestamp(dates[-1])), len(df)]
        wi = np.flatnonzero(eval_mask)
        eval_ranges[pair] = [str(pd.Timestamp(dates[wi[0]]).date()), str(pd.Timestamp(dates[wi[-1]]).date())]
        diag["bars"] += int(eval_mask.sum())
        diag["valid_up"] += int((eval_mask & (lv["code_b"] != lr.INVALID)).sum())
        diag["valid_down"] += int((eval_mask & (lv["code_t"] != lr.INVALID)).sum())

        months = np.asarray(pd.to_datetime(dates).strftime("%Y-%m"))
        base_counts = lr.LevelStudy.baseline_counts(lv, eval_mask, months)
        for arm in arms:
            top_adj, bot_adj = cr.apply_regime(flags, arm, p["regime_penalty"])
            studies[f"score {arm}"].add_pair(pair, dates, lv, cr.rising_edge(top_adj >= p["top_threshold"]),
                                             cr.rising_edge(bot_adj >= p["bottom_threshold"]), eval_mask, base_counts)
        for f in bt.SINGLE_FACTORS:
            studies[f"single {f}"].add_pair(pair, dates, lv, cr.rising_edge(flags[f"top_{f}"].to_numpy()),
                                            cr.rising_edge(flags[f"bot_{f}"].to_numpy()), eval_mask, base_counts)
        print(f"  {pair}: {len(df)} bars, eval {eval_ranges[pair][0]} .. {eval_ranges[pair][1]}")
    return studies, diag, price_ranges, eval_ranges, rep


def bucket_table(study):
    """Per distance bucket: events, flagged success rate, baseline success rate (all valid bars)."""
    ev = pd.DataFrame(study.events)
    rows = []
    base_k, base_n = {}, {}
    for (_, stratum, _), (succ, tot) in study.base.items():
        b = int(stratum.split("|")[1])
        base_k[b] = base_k.get(b, 0) + succ
        base_n[b] = base_n.get(b, 0) + tot
    labels = ["[0.5,1) ATR", "[1,2) ATR", "[2,3) ATR", "[3,5] ATR"]
    for b in range(lr.N_BUCKETS):
        sub = ev[ev["bucket"] == b] if len(ev) else ev
        rows.append({"distance": labels[b], "events": int(len(sub)),
                     "flagged": float(sub["y"].mean()) if len(sub) else np.nan,
                     "baseline": base_k.get(b, 0) / base_n[b] if base_n.get(b) else np.nan,
                     "baseline_bars": int(base_n.get(b, 0))})
    return pd.DataFrame(rows)


def write_outputs(args, studies, fres, diag, price_ranges, eval_ranges, rep, out_dir):
    out_dir.mkdir(parents=True, exist_ok=True)
    pd.DataFrame([{"study": n, **{k: e[k] for k in ("pair", "date", "side", "outcome", "tie", "k", "bucket")}}
                  for n, s in studies.items() for e in s.events]).to_csv(out_dir / "events.csv", index=False)
    store_dir = Path(args.data_dir)
    params = {
        "experiment": f"Experiment 4: flagged bars vs the previous support/resistance, timeframe={args.timeframe}",
        "mode": args.mode, "tag": args.tag, "timeframe": args.timeframe,
        "run_utc": datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds"),
        "code_commit": bt._git_head(), "python": platform.python_version(),
        "numpy": np.__version__, "pandas": pd.__version__,
        "pine_source": "pine/crowded_reversal.pine (main) as ported by scanner/extend/crowded_reversal.py",
        "pine_params": cr.PINE_DEFAULTS, "regime_arms": args.regime_arms,
        "levels": {**LEVELS, "rr": "1:1 (stop = same distance as the target)",
                   "tie_rule": "same-bar target+stop -> failure", "timeout": "failure",
                   "distance_buckets_atr": lr.D_EDGES[:-1] + [5.0],
                   "swings": "scanner.extend.swings.find_swings (strict-left/tolerant-right); a swing is usable only from bar i+swing_n"},
        "gates": {"flagged": bt.FLAG_GATES},
        "eval": {"warmup_bars": args.warmup, "eval_start": args.eval_start, "eval_end": args.eval_end,
                 "requires_cot_available": True},
        "bootstrap": {"kind": "month-clustered; every bucket baseline recomputed per resample", "n": args.boot,
                      "seed": bt.BOOT_SEED, "ci": "2.5-97.5"},
        "sources": {"price": {"store": str(store_dir), "timeframe": args.timeframe, "per_pair [first, last, bars]": price_ranges,
                              "meta": (json.loads((store_dir / "_meta.json").read_text())
                                       if (store_dir / "_meta.json").exists() else None)},
                    "cot": {"file": str(bt.COT_CSV.relative_to(ROOT)), "sha256": bt._sha256(bt.COT_CSV)}},
        "eval_window_per_pair": eval_ranges, "level_availability": diag,
    }
    (out_dir / "params.json").write_text(json.dumps(params, indent=2, default=str))

    f = bt._f
    L = []
    if args.mode == "smoke":
        L += ["> **PLUMBING CHECK ONLY.** No edge conclusion may be drawn from any number below.", ""]
    L += [f"# Crowded Market flags -> previous support/resistance — {args.tag}", "",
          f"Mode `{args.mode}` · timeframe `{args.timeframe}` · target = most recent confirmed swing level (swing_n {LEVELS['swing_n']}, "
          f"0.5-5 ATR away, <= {LEVELS['lookback']} bars old), stop = equal distance on the other side (1:1), {LEVELS['horizon']} bars, "
          f"same-bar-both -> failure, timeout -> failure · baseline matched on pair x side x distance bucket · bootstrap {args.boot} "
          f"(month-clustered, seed {bt.BOOT_SEED})", "",
          f"Price: `{args.data_dir}` ({len(price_ranges)} pairs). Bars in window {diag['bars']}; a valid level exists on "
          f"{diag['valid_up'] / max(diag['bars'], 1):.0%} of bars (resistance above) and {diag['valid_down'] / max(diag['bars'], 1):.0%} (support below). "
          f"COT: Legacy futures-only. Commit `{bt._git_head()[:10]}`.", "", "## Verdicts (flagged-event gates F1-F6)", ""]
    for arm in args.regime_arms:
        L.append(f"- **regime = {arm}:** {bt.verdict_flags(fres['arms'][arm], args.mode)}")
    L.append("")
    for arm, r in fres["arms"].items():
        o, c = r["overall"], r["counts"]
        L += [f"## Flagged events, regime = {arm}", "",
              f"- **{o['n_events']} de-clustered events** (reached the level {c['reversal']}, stopped {c['continuation']}, "
              f"timeout {c['timeout']}; same-bar ties {c['ties']}). Top+bottom same-bar conflicts skipped: {r['conflicts']}; "
              f"flags with no valid level / no forward bars (excluded): {r['censored_flags']}.",
              f"- Flagged success rate {f(o['flagged_rate'], True)} vs distance-matched baseline {f(o['baseline_rate'], True)} "
              f"-> **lift {f(o['lift'], True)}** (95% CI {f(o['ci_lo'], True)} .. {f(o['ci_hi'], True)}).",
              f"- Halves (split after {r['split']}): first {f(r['h1']['lift'], True)} (n={r['h1']['n_events']}), second "
              f"{f(r['h2']['lift'], True)} (n={r['h2']['n_events']}). Excluding JPY crosses: {f(r['ex_jpy']['lift'], True)} "
              f"(n={r['ex_jpy']['n_events']}). Excluding best pair ({r['best_pair']}): {f(r['ex_best']['lift'], True)} "
              f"(n={r['ex_best']['n_events']}).",
              "", "| gate | result |", "|---|---|"]
        L += [f"| {k} | {'pass' if v else 'FAIL'} |" for k, v in r["checks"].items()]
        L += ["", "| pair | events | lift |", "|---|---|---|"]
        L += [f"| {row.pair} | {row.n_events} | {f(row.lift, True)} |" for row in r["per_pair"].itertuples()]
        L.append("")
    bt_ = bucket_table(studies[f"score {args.regime_arms[-1] if 'off' not in args.regime_arms else 'off'}"])
    L += ["## What the matching is matching (filter-off events by level distance)", "",
          "| level distance | events | flagged success | baseline success (all valid bars) | baseline bars |", "|---|---|---|---|---|"]
    L += [f"| {b.distance} | {b.events} | {f(b.flagged, True)} | {f(b.baseline, True)} | {b.baseline_bars} |" for b in bt_.itertuples()]
    L += ["", "## Single factors, same test (each factor's own rising edge)", "",
          "| factor | events | flagged | baseline | lift | 95% CI |", "|---|---|---|---|---|---|"]
    for name, o in fres["singles"].items():
        L.append(f"| {name} | {o['n_events']} | {f(o['flagged_rate'], True)} | {f(o['baseline_rate'], True)} | "
                 f"{f(o['lift'], True)} | {f(o['ci_lo'], True)} .. {f(o['ci_hi'], True)} |")
    L += ["", "## Known limits", "",
          "- 'Previous support/resistance' is defined as the most recent confirmed swing (a judgment call); round numbers, "
          "multi-touch zones or a chart reader's discretion are different definitions and different experiments.",
          "- Success = the level was reached before an equal adverse move within 20 bars. It is not profit: no costs, entry rule, sizing or discretionary exit.",
          "- Bars overlap and the 12 pairs share USD/JPY and COT legs; one macro era per store; OHLC cannot sequence intrabar (tie -> failure)."]
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
    ap.add_argument("--regime-arms", nargs="*", default=["suppress", "off"])
    args = ap.parse_args()
    args.data_dir = args.data_dir or bt.TIMEFRAMES[args.timeframe]["dir"]
    args.warmup = args.warmup if args.warmup is not None else bt.TIMEFRAMES[args.timeframe]["warmup"]

    print(f"levels study [{args.mode}] tf={args.timeframe} tag={args.tag}")
    studies, diag, price_ranges, eval_ranges, rep = run(args)
    fres = bt.evaluate_flags(studies, args.regime_arms, args.boot)
    out_dir = ROOT / "data" / "backtest" / f"crowded_reversal_{args.tag}"
    write_outputs(args, studies, fres, diag, price_ranges, eval_ranges, rep, out_dir)
    for arm in args.regime_arms:
        o = fres["arms"][arm]["overall"]
        print(f"[{arm}] events={o['n_events']} flagged={bt._f(o['flagged_rate'], True)} baseline={bt._f(o['baseline_rate'], True)} "
              f"lift={bt._f(o['lift'], True)} CI {bt._f(o['ci_lo'], True)}..{bt._f(o['ci_hi'], True)}")
        print(f"        {bt.verdict_flags(fres['arms'][arm], args.mode)}")
    print(f"wrote {out_dir.relative_to(ROOT)}/")


if __name__ == "__main__":
    main()
