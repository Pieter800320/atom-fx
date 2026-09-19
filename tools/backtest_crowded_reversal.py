"""
ATOM FX research — Crowded Market reversal indicator: Experiment 2 runner.

Ports pine/crowded_reversal.pine (scanner/extend/crowded_reversal.py) and measures, on the
persisted NY-close D1 store, whether the indicator's SCORE predicts reversals:

  PRIMARY   score-gradient test (scanner/extend/score_gradient.py): across EVERY bar, does the
            barrier-race reversal rate rise with the score? Pair x side fixed effects,
            month-clustered bootstrap, pre-registered gates -> PASS / FAIL / INCONCLUSIVE.
  SECONDARY flagged-event test (scanner/extend/barrier_race.py): de-clustered events at score >= 60
            vs a like-for-like baseline. Reported for information only — count-only estimates
            (2026-09-19) showed it cannot reach the 100-event floor with the available history.

    py -m tools.backtest_crowded_reversal --tag smoke_2026-09 --mode smoke
    py -m tools.backtest_crowded_reversal --tag exp2_2026-09  --mode full --data-dir data/d1_nyclose_long

--mode smoke  PLUMBING CHECK ONLY. Verdict forced to "PLUMBING ONLY"; draw no edge conclusion.
--mode full   The pre-registered run (docs/RESEARCH_LOG.md, Experiment 2). Gates applied mechanically.

Nothing is tuned here: every parameter is a Pine default or a pre-registered value. Changing one
is a new experiment, not a re-run.

Outputs to data/backtest/crowded_reversal_<tag>/:
    params.json, summary.md, bucket_table.csv, events.csv (secondary flagged events)
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
from scanner.extend import score_gradient as sg
from scanner.extend.d1_store import load_store

PAIRS = ["EURUSD", "GBPUSD", "USDJPY", "USDCAD", "NZDUSD", "AUDUSD", "USDCHF",
         "EURJPY", "GBPJPY", "CADJPY", "AUDJPY", "NZDJPY"]
SINGLE_FACTORS = ["pct_b", "z", "stretch", "rsi", "div", "climax", "cot"]
COT_CSV = ROOT / "data" / "cot_legacy" / "legacy_nc.csv"

# Pre-registered (mirrored verbatim in docs/RESEARCH_LOG.md, Experiment 2)
DEFAULT_RACE = {"horizon": 20, "mult": 1.0, "tie_rule": "same-bar both barriers -> continuation",
                "timeout": "counts as not-a-reversal"}
GATES = {"min_months": 48, "min_slope_per_20pts": 0.02, "ci_lo_gt_zero": True,
         "both_halves_slope_gt_zero": True, "ex_jpy_slope_gt_zero": True,
         "every_leave_one_pair_out_slope_gt_zero": True, "composite_rho_beats_best_single_factor": True}
SECONDARY_MIN_EVENTS = 100
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


def factor_contribution(flags, side, factor, p):
    """The factor's own share of the 0-100 composite score (its weight / total active weight),
    exactly as the Pine adds it — so single-factor and composite predictors share one scale."""
    total_w = sum([p["w_bb"], p["w_z"], p["w_stretch"], p["w_rsi"], p["w_rsi_x"], p["w_div"],
                   p["w_climax"], p["w_cot"] if p["cot_enabled"] else 0.0])
    if factor == "rsi":
        on = flags[f"{side}_rsi"].to_numpy(float) * p["w_rsi"] + flags[f"{side}_rsi_x"].to_numpy(float) * p["w_rsi_x"]
    else:
        w = {"pct_b": p["w_bb"], "z": p["w_z"], "stretch": p["w_stretch"], "div": p["w_div"],
             "climax": p["w_climax"], "cot": p["w_cot"] if p["cot_enabled"] else 0.0}[factor]
        on = flags[f"{side}_{factor}"].to_numpy(float) * w
    return 100.0 * on / total_w


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

    calendar = pd.bdate_range(rep["date"].min(), max(df["date"].max() for df in stores.values()) + pd.Timedelta(days=10))
    p = dict(cr.PINE_DEFAULTS)
    arms = args.regime_arms

    flag_studies = {f"score {arm}": br.Study(f"score>=60 regime={arm}", args.horizon) for arm in arms}
    grad = sg.GradientStudy()
    price_ranges, eval_ranges = {}, {}

    for pair, df in stores.items():
        dates = pd.to_datetime(df["date"]).to_numpy()
        cot = cot_combined_for_pair(pair, rep, calendar, p["cot_lookback_weeks"]).reindex(pd.DatetimeIndex(dates)).to_numpy()
        flags = cr.compute_flags(df, cot_combined_pctl=cot, params=p)
        result, k = br.race_all(df["high"].to_numpy(float), df["low"].to_numpy(float),
                                df["close"].to_numpy(float), flags["atr"].to_numpy(),
                                horizon=args.horizon, mult=args.mult)

        eval_mask = np.arange(len(df)) >= args.warmup
        if args.eval_start:
            eval_mask &= dates >= np.datetime64(args.eval_start)
        eval_mask &= ~np.isnan(cot)       # COT resolvable, or the score's ceiling silently drops to 75
        if not eval_mask.any():
            print(f"  ! {pair}: empty evaluation window — skipped")
            continue
        price_ranges[pair] = [str(pd.Timestamp(dates[0]).date()), str(pd.Timestamp(dates[-1]).date()), len(df)]
        wi = np.flatnonzero(eval_mask)
        eval_ranges[pair] = [str(pd.Timestamp(dates[wi[0]]).date()), str(pd.Timestamp(dates[wi[-1]]).date())]

        preds = {}
        months = np.asarray(pd.to_datetime(dates).strftime("%Y-%m"))
        base_counts = br.baseline_counts(result, eval_mask, months)
        for arm in arms:
            top_adj, bot_adj = cr.apply_regime(flags, arm, p["regime_penalty"])
            preds[f"composite {arm}"] = (bot_adj, top_adj)
            flag_studies[f"score {arm}"].add_pair(
                pair, dates, result, k, cr.rising_edge(top_adj >= p["top_threshold"]),
                cr.rising_edge(bot_adj >= p["bottom_threshold"]), eval_mask, base_counts)
        for f in SINGLE_FACTORS:
            preds[f"single {f}"] = (factor_contribution(flags, "bot", f, p), factor_contribution(flags, "top", f, p))
        grad.add_pair(pair, dates, result, eval_mask, preds)
        print(f"  {pair}: {len(df)} bars, eval {eval_ranges[pair][0]} .. {eval_ranges[pair][1]}")

    return grad, flag_studies, price_ranges, eval_ranges, rep


def evaluate_gradient(grad, arms, n_boot):
    counts = grad.draw_counts(n_boot, BOOT_SEED)
    one = grad.ones()
    months = grad.months
    split = months[(len(months) - 1) // 2]
    first, second = grad.month_masks(split)
    pairs = grad.pairs
    ex_jpy = [("JPY" not in q) for q in pairs]
    singles = {f: f"single {f}" for f in SINGLE_FACTORS}
    rho_single_pt = {f: grad.rho(n, one)[0] for f, n in singles.items()}
    best_single = max(rho_single_pt, key=lambda f: -np.inf if np.isnan(rho_single_pt[f]) else rho_single_pt[f])
    rho_single_b = grad.rho(singles[best_single], counts)

    out = {"split_month": split, "n_months": len(months), "arms": {}, "singles": {}, "best_single": best_single}
    for f, n in singles.items():
        out["singles"][f] = {"rho": rho_single_pt[f], "slope": grad.slope(n, one)[0],
                             "slope_ci": grad.ci(grad.slope(n, counts))}
    for arm in arms:
        name = f"composite {arm}"
        sb = grad.slope(name, counts)
        pt = grad.slope(name, one)[0]
        loo = {q: grad.slope(name, one, pair_mask=[o != q for o in pairs])[0] for q in pairs}
        worst_pair = min(loo, key=lambda q: loo[q])
        rho_pt, rho_b = grad.rho(name, one)[0], grad.rho(name, counts)
        n_obs, rate, base, lift = grad.buckets(name, one)
        _, _, _, lift_b = grad.buckets(name, counts)
        bucket_rows = []
        labels = [f"[{int(a)},{int(b) if b < 100 else 100}{']' if b > 100 else ')'}" for a, b in
                  zip(sg.BUCKET_EDGES[:-1], sg.BUCKET_EDGES[1:])]
        for j, lab in enumerate(labels):
            lo, hi = grad.ci(lift_b[:, j])
            bucket_rows.append({"bucket": lab, "n_obs": int(n_obs[0, j]), "reversal_rate": rate[0, j],
                                "baseline": base[0, j], "lift": lift[0, j], "ci_lo": lo, "ci_hi": hi})
        res = {
            "slope": pt, "slope_ci": grad.ci(sb),
            "h1": grad.slope(name, one, month_mask=first)[0], "h2": grad.slope(name, one, month_mask=second)[0],
            "ex_jpy": grad.slope(name, one, pair_mask=ex_jpy)[0],
            "loo": loo, "loo_min": loo[worst_pair], "loo_worst_pair": worst_pair,
            "rho": rho_pt, "rho_diff": rho_pt - rho_single_pt[best_single],
            "rho_diff_ci": grad.ci(rho_b - rho_single_b), "buckets": pd.DataFrame(bucket_rows),
        }
        pos = lambda v: (not np.isnan(v)) and v > 0
        res["checks"] = {
            f"n_months >= {GATES['min_months']}": len(months) >= GATES["min_months"],
            f"slope per +20 pts >= {GATES['min_slope_per_20pts']}": pos(pt) and pt >= GATES["min_slope_per_20pts"],
            "95% CI lower bound > 0": pos(res["slope_ci"][0]),
            "both halves slope > 0": pos(res["h1"]) and pos(res["h2"]),
            "excluding JPY crosses slope > 0": pos(res["ex_jpy"]),
            f"every leave-one-pair-out slope > 0 (worst: {worst_pair})": pos(res["loo_min"]),
            f"composite rho beats best single factor ({best_single})": pos(res["rho_diff"]),
        }
        out["arms"][arm] = res
        out["arms"][arm]["_slope_b"] = sb

    if "suppress" in out["arms"] and "off" in out["arms"]:
        d = out["arms"]["suppress"]["_slope_b"] - out["arms"]["off"]["_slope_b"]
        lo, hi = grad.ci(d)
        pt = out["arms"]["suppress"]["slope"] - out["arms"]["off"]["slope"]
        out["regime_filter"] = {"diff": pt, "ci": (lo, hi),
                                "reading": ("filter IMPROVES the gradient (CI excludes 0) — evidence for keeping it" if lo > 0
                                            else "filter WORSENS the gradient (CI entirely below 0) — evidence against it" if hi < 0
                                            else "NO demonstrated value: CI includes 0 — the data cannot justify the ADX filter")}
    return out


def verdict(arm_res, mode, n_months):
    if mode == "smoke":
        return "PLUMBING ONLY — no verdict (smoke run on a short cache)"
    if n_months < GATES["min_months"]:
        return f"INCONCLUSIVE — only {n_months} months < {GATES['min_months']}"
    failed = [k for k, v in arm_res["checks"].items() if not v]
    return "PASS" if not failed else "FAIL — " + "; ".join(failed)


def _f(x, pct=False, nd=3):
    if x is None or (isinstance(x, float) and np.isnan(x)):
        return "n/a"
    return f"{x * 100:.1f}%" if pct else f"{x:.{nd}f}"


def write_outputs(args, grad, gres, flag_studies, price_ranges, eval_ranges, rep, out_dir):
    out_dir.mkdir(parents=True, exist_ok=True)

    ev_rows = [{"study": n, **{k: e[k] for k in ("pair", "date", "side", "outcome", "tie", "k")}}
               for n, s in flag_studies.items() for e in s.events]
    pd.DataFrame(ev_rows).to_csv(out_dir / "events.csv", index=False)
    pd.concat([r["buckets"].assign(arm=arm) for arm, r in gres["arms"].items()]).to_csv(
        out_dir / "bucket_table.csv", index=False)

    params = {
        "experiment": "crowded_reversal - score-gradient test (primary) + flagged events (secondary)",
        "mode": args.mode, "tag": args.tag,
        "run_utc": datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds"),
        "code_commit": _git_head(), "python": platform.python_version(),
        "numpy": np.__version__, "pandas": pd.__version__,
        "pine_source": "pine/crowded_reversal.pine (main) as ported by scanner/extend/crowded_reversal.py",
        "pine_params": cr.PINE_DEFAULTS, "regime_arms": args.regime_arms,
        "race": {**DEFAULT_RACE, "horizon": args.horizon, "mult": args.mult},
        "gradient": {"slope_unit_points": sg.SLOPE_UNIT, "bucket_edges": sg.BUCKET_EDGES[:-1] + [100],
                     "fixed_effects": "pair x side", "split_month": gres["split_month"], "gates": GATES},
        "eval": {"warmup_bars": args.warmup, "eval_start": args.eval_start, "requires_cot_available": True},
        "bootstrap": {"kind": "month-clustered; all statistics recomputed per resample; resamples shared (paired)",
                      "n": args.boot, "seed": BOOT_SEED, "ci": "2.5-97.5"},
        "sources": {
            "price": {"store": str(Path(args.data_dir)),
                      "convention": "NY-close D1 (17:00 ET) via scanner.extend.agg_nyclose, FX-week filtered",
                      "per_pair [first, last, bars]": price_ranges,
                      "meta": (json.loads((Path(args.data_dir) / "_meta.json").read_text())
                               if (Path(args.data_dir) / "_meta.json").exists() else None)},
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
        L += ["> **PLUMBING CHECK ONLY.** Ran the ported logic end-to-end on a short cache to prove it",
              "> executes. **No edge conclusion may be drawn from any number below.**", ""]
    L += [f"# Crowded Market reversal — Experiment 2 ({args.tag})", "",
          f"Mode `{args.mode}` · reversal = 1 x ATR(14) barrier race, {args.horizon} bars, same-bar-both -> continuation, "
          f"timeout -> not a reversal · bootstrap {args.boot} (month-clustered, seed {BOOT_SEED})", "",
          f"Price: `{args.data_dir}` ({len(price_ranges)} pairs), {gres['n_months']} months in the evaluation window. "
          f"COT: Legacy futures-only {rep['date'].min().date()} .. {rep['date'].max().date()}. Commit `{_git_head()[:10]}`.", "",
          "## Verdicts (primary: score-gradient gates)", ""]
    for arm, r in gres["arms"].items():
        L.append(f"- **regime = {arm}:** {verdict(r, args.mode, gres['n_months'])}")
    if "regime_filter" in gres:
        rf = gres["regime_filter"]
        L += ["", f"**ADX regime filter (suppress minus off, slope per +20 pts):** {_f(rf['diff'])} "
              f"(95% CI {_f(rf['ci'][0])} .. {_f(rf['ci'][1])}) -> {rf['reading']}."]
    L.append("")

    for arm, r in gres["arms"].items():
        L += [f"## Composite score, regime = {arm}", "",
              f"- Gradient slope: **{_f(r['slope'])}** reversal-probability per +20 score points "
              f"(95% CI {_f(r['slope_ci'][0])} .. {_f(r['slope_ci'][1])}); within-stratum correlation rho = {_f(r['rho'], nd=4)}.",
              f"- Halves (split after {gres['split_month']}): first {_f(r['h1'])}, second {_f(r['h2'])}. "
              f"Excluding JPY crosses: {_f(r['ex_jpy'])}. Weakest leave-one-pair-out: {_f(r['loo_min'])} (dropping {r['loo_worst_pair']}).",
              f"- rho vs best single factor ({gres['best_single']}): {_f(r['rho_diff'], nd=4)} "
              f"(paired 95% CI {_f(r['rho_diff_ci'][0], nd=4)} .. {_f(r['rho_diff_ci'][1], nd=4)}).",
              "", "| gate | result |", "|---|---|"]
        L += [f"| {k} | {'pass' if v else 'FAIL'} |" for k, v in r["checks"].items()]
        L += ["", "| score bucket | obs | reversal rate | baseline | lift | 95% CI |", "|---|---|---|---|---|---|"]
        L += [f"| {b.bucket} | {b.n_obs} | {_f(b.reversal_rate, True)} | {_f(b.baseline, True)} | "
              f"{_f(b.lift, True)} | {_f(b.ci_lo, True)} .. {_f(b.ci_hi, True)} |" for b in r["buckets"].itertuples()]
        L.append("")

    L += ["## Single factors, same statistic (each factor's own contribution to the 0-100 scale)", "",
          "| factor | slope per +20 pts | 95% CI | rho |", "|---|---|---|---|"]
    for f, s in gres["singles"].items():
        L.append(f"| {f} | {_f(s['slope'])} | {_f(s['slope_ci'][0])} .. {_f(s['slope_ci'][1])} | {_f(s['rho'], nd=4)} |")

    L += ["", "## Secondary (informational): flagged events, score >= 60", ""]
    for name, s in flag_studies.items():
        o, c = s.lift(n_boot=args.boot, seed=BOOT_SEED), s.outcome_counts()
        floor = "" if o["n_events"] >= SECONDARY_MIN_EVENTS else f" — below the {SECONDARY_MIN_EVENTS}-event floor, read as INCONCLUSIVE"
        L.append(f"- **{name}:** {o['n_events']} de-clustered events (reversal {c['reversal']}, continuation "
                 f"{c['continuation']}, timeout {c['timeout']}, same-bar ties {c['ties']}); flagged {_f(o['flagged_rate'], True)} "
                 f"vs baseline {_f(o['baseline_rate'], True)} -> lift {_f(o['lift'], True)} "
                 f"(95% CI {_f(o['ci_lo'], True)} .. {_f(o['ci_hi'], True)}){floor}.")

    L += ["", "## Known limits", "",
          "- D1 OHLC cannot sequence intrabar: a bar touching both barriers is scored continuation, for every bar alike.",
          "- Observations are ALL bars, so consecutive observations overlap and are serially dependent; the month-clustered "
          "bootstrap absorbs same-month dependence, not every cross-pair dependency (12 pairs share USD/JPY legs and COT legs).",
          "- COT percentile is computed on a business-day calendar and reproduced TradingView's label to within ~0.3 percentile "
          "points on 2026-09-19 (net positions matched exactly); attributed, not verified, to TradingView counting chart bars.",
          "- Evaluation history is what Twelvedata's H1 depth allows (~2020-01 onward) — one macro regime era, including the "
          "2022 USD surge; a positive result here says nothing about earlier regimes."]
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

    print(f"crowded_reversal experiment 2 [{args.mode}] tag={args.tag}")
    grad, flag_studies, price_ranges, eval_ranges, rep = run(args)
    gres = evaluate_gradient(grad, args.regime_arms, args.boot)
    out_dir = ROOT / "data" / "backtest" / f"crowded_reversal_{args.tag}"
    write_outputs(args, grad, gres, flag_studies, price_ranges, eval_ranges, rep, out_dir)
    for arm, r in gres["arms"].items():
        print(f"[{arm}] slope/+20pts={_f(r['slope'])} CI {_f(r['slope_ci'][0])}..{_f(r['slope_ci'][1])} "
              f"-> {verdict(r, args.mode, gres['n_months'])}")
    print(f"wrote {out_dir.relative_to(ROOT)}/")


if __name__ == "__main__":
    main()
