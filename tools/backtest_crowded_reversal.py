"""
ATOM FX research — Crowded Market reversal indicator: barrier-race studies (Experiments 2 and 3).

Ports pine/crowded_reversal.pine (scanner/extend/crowded_reversal.py) and measures, on persisted
NY-close D1 or H4 bars, whether the indicator predicts reversals. Two complementary tests share one
reversal definition (scanner/extend/barrier_race.py: 1 x ATR barrier race, 20 bars, same-bar-both ->
continuation, timeout -> not a reversal):

  GRADIENT  (scanner/extend/score_gradient.py) every bar: does the reversal rate rise with the score?
            Pair x side fixed effects, month-clustered paired bootstrap, gates G1-G7.
  FLAGGED   de-clustered events at score >= 60 vs a like-for-like measured baseline, gates F1-F6
            (incl. beating the best single factor's own flagged lift).

  --primary says which one issues THE verdict (Experiment 2: gradient; Experiment 3: flagged). The other
  is reported as secondary. Exploratory strata (ranging vs trending) never carry a gate.

    py -m tools.backtest_crowded_reversal --timeframe d1    --primary gradient --tag exp2_2026-09 --mode full
    py -m tools.backtest_crowded_reversal --timeframe h4utc --primary flagged  --tag exp3_h4utc_2026-09 --mode full
    py -m tools.backtest_crowded_reversal --timeframe h4ny  --primary flagged  --tag exp3_h4ny_2026-09  --mode full

--mode smoke  PLUMBING CHECK ONLY. Verdicts forced to "PLUMBING ONLY"; draw no edge conclusion.
--mode full   The pre-registered run (docs/RESEARCH_LOG.md). Gates applied mechanically.

Nothing is tuned here: every parameter is a Pine default or a pre-registered value. Changing one is a
new experiment, not a re-run.

Outputs to data/backtest/crowded_reversal_<tag>/:
    params.json, summary.md, bucket_table.csv, events.csv
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

# timeframe -> (store dir, warm-up bars, kind, bars per day for pair-year maths)
TIMEFRAMES = {
    "d1":    {"dir": "data/d1_nyclose_long", "warmup": 260, "kind": "d1"},
    "h4utc": {"dir": "data/h4_utc_long",     "warmup": 400, "kind": "h4"},
    "h4ny":  {"dir": "data/h4_ny_long",      "warmup": 400, "kind": "h4"},
    # Twelvedata NATIVE daily bars (UTC-based, weekend rows dropped at load). NOT the NY-close convention:
    # used only for the earlier-era replication (Experiment 5), never mixed with the NY-close stores.
    "d1utc": {"dir": "data/d1_utc_daily",    "warmup": 260, "kind": "d1utc"},
}

# Experiment 5 pre-registered decision constants
NONINF_MARGIN = 0.01          # a single factor is "as good" if its rho is not worse than the composite's by more than this
REPLICATION_MIN_SLOPE = 0.02  # same effect-size floor as Experiment 2's gate

# Pre-registered (mirrored verbatim in docs/RESEARCH_LOG.md, Experiments 2 and 3)
DEFAULT_RACE = {"horizon": 20, "mult": 1.0, "tie_rule": "same-bar both barriers -> continuation",
                "timeout": "counts as not-a-reversal"}
GRADIENT_GATES = {"min_months": 48, "min_slope_per_20pts": 0.02, "ci_lo_gt_zero": True,
                  "both_halves_slope_gt_zero": True, "ex_jpy_slope_gt_zero": True,
                  "every_leave_one_pair_out_slope_gt_zero": True, "composite_rho_beats_best_single_factor": True}
FLAG_GATES = {"min_events": 100, "min_lift": 0.05, "ci_lo_gt_zero": True, "both_halves_lift_gt_zero": True,
              "ex_jpy_lift_gt_zero": True, "ex_best_pair_lift_gt_zero": True,
              "beats_best_single_factor_lift": True}
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


def load_bars(pair, tf, data_dir):
    """One pair's bars as a DataFrame: date (bar timestamp), open/high/low/close, cot_day (the
    NY-close trading day used to pick the COT week — Sunday reopen bars roll into Monday)."""
    kind = TIMEFRAMES[tf]["kind"]
    if kind == "d1":
        df = load_store(pair, str(data_dir)).reset_index(drop=True)
        df["cot_day"] = pd.to_datetime(df["date"])
    elif kind == "d1utc":
        df = pd.read_csv(Path(data_dir) / f"{pair}.csv", parse_dates=["date"])
        df = df[df["date"].dt.dayofweek < 5].reset_index(drop=True)       # drop Saturday/Sunday fragments
        df["cot_day"] = df["date"]
    else:
        df = pd.read_csv(Path(data_dir) / f"{pair}.csv", parse_dates=["datetime", "trading_day"])
        df = df.rename(columns={"datetime": "date"})
        df["cot_day"] = df["trading_day"]
    return df


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
    tf = TIMEFRAMES[args.timeframe]
    data_dir = Path(args.data_dir)
    rep = load_cot()
    stores = {}
    for pair in args.pairs:
        df = load_bars(pair, args.timeframe, data_dir)
        if len(df) < args.warmup + args.horizon + 50:
            print(f"  ! {pair}: only {len(df)} bars — skipped")
            continue
        stores[pair] = df
    if not stores:
        sys.exit("no usable pairs")

    calendar = pd.bdate_range(rep["date"].min(), max(df["cot_day"].max() for df in stores.values()) + pd.Timedelta(days=10))
    p = dict(cr.PINE_DEFAULTS)
    arms = args.regime_arms

    flag_studies = {f"score {arm}": br.Study(f"score>=60 regime={arm}", args.horizon) for arm in arms}
    for f in SINGLE_FACTORS:
        flag_studies[f"single {f}"] = br.Study(f"single factor: {f}", args.horizon)
    grad = sg.GradientStudy()
    strata_grad = {s: sg.GradientStudy() for s in ("trending", "ranging")}
    strata_flag = {s: br.Study(f"score>=60 regime=off, {s}", args.horizon) for s in ("trending", "ranging")}
    price_ranges, eval_ranges = {}, {}

    for pair, df in stores.items():
        dates = pd.to_datetime(df["date"]).to_numpy()
        cot = cot_combined_for_pair(pair, rep, calendar, p["cot_lookback_weeks"]).reindex(
            pd.DatetimeIndex(df["cot_day"])).to_numpy()
        flags = cr.compute_flags(df, cot_combined_pctl=cot, params=p)
        result, k = br.race_all(df["high"].to_numpy(float), df["low"].to_numpy(float),
                                df["close"].to_numpy(float), flags["atr"].to_numpy(),
                                horizon=args.horizon, mult=args.mult)

        eval_mask = np.arange(len(df)) >= args.warmup
        if args.eval_start:
            eval_mask &= dates >= np.datetime64(args.eval_start)
        if args.eval_end:
            eval_mask &= dates <= np.datetime64(args.eval_end)
        eval_mask &= ~np.isnan(cot)       # COT resolvable, or the score's ceiling silently drops to 75
        if not eval_mask.any():
            print(f"  ! {pair}: empty evaluation window — skipped")
            continue
        price_ranges[pair] = [str(pd.Timestamp(dates[0])), str(pd.Timestamp(dates[-1])), len(df)]
        wi = np.flatnonzero(eval_mask)
        eval_ranges[pair] = [str(pd.Timestamp(dates[wi[0]]).date()), str(pd.Timestamp(dates[wi[-1]]).date())]

        preds = {}
        months = np.asarray(pd.to_datetime(dates).strftime("%Y-%m"))
        base_counts = br.baseline_counts(result, eval_mask, months)
        adj = {}
        for arm in arms:
            top_adj, bot_adj = cr.apply_regime(flags, arm, p["regime_penalty"])
            adj[arm] = (top_adj, bot_adj)
            preds[f"composite {arm}"] = (bot_adj, top_adj)
            flag_studies[f"score {arm}"].add_pair(
                pair, dates, result, k, cr.rising_edge(top_adj >= p["top_threshold"]),
                cr.rising_edge(bot_adj >= p["bottom_threshold"]), eval_mask, base_counts)
        for f in SINGLE_FACTORS:
            preds[f"single {f}"] = (factor_contribution(flags, "bot", f, p), factor_contribution(flags, "top", f, p))
            flag_studies[f"single {f}"].add_pair(
                pair, dates, result, k, cr.rising_edge(flags[f"top_{f}"].to_numpy()),
                cr.rising_edge(flags[f"bot_{f}"].to_numpy()), eval_mask, base_counts)
        grad.add_pair(pair, dates, result, eval_mask, preds)

        # exploratory strata (filter-off score only): regime state AT the bar
        if "off" in adj:
            trending = (flags["strong_up"] | flags["strong_dn"]).to_numpy(bool)
            top_o, bot_o = adj["off"]
            for name, sm in (("trending", trending), ("ranging", ~trending)):
                m = eval_mask & sm
                strata_grad[name].add_pair(pair, dates, result, m, {"composite off": (bot_o, top_o)})
                strata_flag[name].add_pair(
                    pair, dates, result, k, cr.rising_edge(top_o >= p["top_threshold"]),
                    cr.rising_edge(bot_o >= p["bottom_threshold"]), m,
                    br.baseline_counts(result, m, months))
        print(f"  {pair}: {len(df)} bars, eval {eval_ranges[pair][0]} .. {eval_ranges[pair][1]}")

    return grad, flag_studies, strata_grad, strata_flag, price_ranges, eval_ranges, rep


# ------------------------------------------------------------------------------------------
# gradient evaluation (unchanged from Experiment 2)
# ------------------------------------------------------------------------------------------
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
        g = GRADIENT_GATES
        res["checks"] = {
            f"n_months >= {g['min_months']}": len(months) >= g["min_months"],
            f"slope per +20 pts >= {g['min_slope_per_20pts']}": pos(pt) and pt >= g["min_slope_per_20pts"],
            "95% CI lower bound > 0": pos(res["slope_ci"][0]),
            "both halves slope > 0": pos(res["h1"]) and pos(res["h2"]),
            "excluding JPY crosses slope > 0": pos(res["ex_jpy"]),
            f"every leave-one-pair-out slope > 0 (worst: {worst_pair})": pos(res["loo_min"]),
            f"composite rho beats best single factor ({best_single})": pos(res["rho_diff"]),
        }
        out["arms"][arm] = res
        out["arms"][arm]["_slope_b"] = sb

    if "off" in out["arms"]:
        # Experiment 5: replication verdict for the composite (filter off) and paired single-vs-composite rho
        off = out["arms"]["off"]
        lo, hi = off["slope_ci"]
        out["replication"] = ("REPLICATES" if (lo > 0 and off["slope"] >= REPLICATION_MIN_SLOPE)
                              else "CONTRADICTED" if hi < REPLICATION_MIN_SLOPE else "INCONCLUSIVE")
        rho_comp_pt, rho_comp_b = off["rho"], grad.rho("composite off", counts)
        out["single_vs_composite"] = {}
        for f, n in singles.items():
            d_b = grad.rho(n, counts) - rho_comp_b
            d_lo, d_hi = grad.ci(d_b)
            label = ("composite BETTER" if d_hi < 0
                     else "single factor AS GOOD (non-inferior)" if d_lo > -NONINF_MARGIN
                     else "INCONCLUSIVE")
            out["single_vs_composite"][f] = {"rho_single": rho_single_pt[f], "diff": rho_single_pt[f] - rho_comp_pt,
                                             "ci": (d_lo, d_hi), "label": label}

    if "suppress" in out["arms"] and "off" in out["arms"]:
        d = out["arms"]["suppress"]["_slope_b"] - out["arms"]["off"]["_slope_b"]
        lo, hi = grad.ci(d)
        pt = out["arms"]["suppress"]["slope"] - out["arms"]["off"]["slope"]
        out["regime_filter"] = {"diff": pt, "ci": (lo, hi),
                                "reading": ("filter IMPROVES the gradient (CI excludes 0) — evidence for keeping it" if lo > 0
                                            else "filter WORSENS the gradient (CI entirely below 0) — evidence against it" if hi < 0
                                            else "NO demonstrated value: CI includes 0 — the data cannot justify the ADX filter")}
    return out


# ------------------------------------------------------------------------------------------
# flagged-event evaluation (Experiment 3 primary)
# ------------------------------------------------------------------------------------------
def evaluate_flags(flag_studies, arms, n_boot):
    singles = {f: flag_studies[f"single {f}"].lift(n_boot=n_boot, seed=BOOT_SEED) for f in SINGLE_FACTORS}
    best_single = max(singles, key=lambda f: -np.inf if np.isnan(singles[f]["lift"]) else singles[f]["lift"])
    out = {"singles": singles, "best_single": best_single, "arms": {}}
    for arm in arms:
        s = flag_studies[f"score {arm}"]
        months = sorted({m for (_, _, m) in s.base})
        split = months[(len(months) - 1) // 2] if months else ""
        pair_list = sorted({q for (q, _, _) in s.base})
        overall = s.lift(n_boot=n_boot, seed=BOOT_SEED)
        pp = s.per_pair()
        best_pair = pp.sort_values("contribution", ascending=False).iloc[0]["pair"] if len(pp) else None
        h1 = s.lift(month_filter=lambda m: m <= split, n_boot=0)
        h2 = s.lift(month_filter=lambda m: m > split, n_boot=0)
        exj = s.lift(pairs=[q for q in pair_list if "JPY" not in q], n_boot=0)
        exb = s.lift(pairs=[q for q in pair_list if q != best_pair], n_boot=0)
        pos = lambda r: (not np.isnan(r["lift"])) and r["lift"] > 0
        g = FLAG_GATES
        checks = {
            f"n de-clustered events >= {g['min_events']}": overall["n_events"] >= g["min_events"],
            f"lift >= {g['min_lift']:.2f}": (not np.isnan(overall["lift"])) and overall["lift"] >= g["min_lift"],
            "95% CI lower bound > 0": (not np.isnan(overall["ci_lo"])) and overall["ci_lo"] > 0,
            "both halves lift > 0": pos(h1) and pos(h2),
            "excluding JPY crosses lift > 0": pos(exj),
            f"excluding best pair ({best_pair}) lift > 0": pos(exb),
            f"beats best single-factor flagged lift ({best_single})":
                (not np.isnan(overall["lift"])) and overall["lift"] > singles[best_single]["lift"],
        }
        out["arms"][arm] = {"overall": overall, "counts": s.outcome_counts(), "per_pair": pp, "h1": h1, "h2": h2,
                            "ex_jpy": exj, "ex_best": exb, "best_pair": best_pair, "split": split,
                            "checks": checks, "conflicts": s.conflicts, "censored_flags": s.censored_flags}
    return out


def evaluate_strata(strata_grad, strata_flag, n_boot):
    out = {}
    for name in ("trending", "ranging"):
        g, f = strata_grad[name], strata_flag[name]
        if not g.pairs:
            continue
        counts = g.draw_counts(n_boot, BOOT_SEED)
        sb = g.slope("composite off", counts)
        fl = f.lift(n_boot=n_boot, seed=BOOT_SEED)
        n_obs = int(g.buckets("composite off", g.ones())[0].sum())
        out[name] = {"n_obs": n_obs, "slope": g.slope("composite off", g.ones())[0], "slope_ci": g.ci(sb),
                     "flag": fl, "counts": f.outcome_counts()}
    return out


def verdict_gradient(arm_res, mode, n_months):
    if mode == "smoke":
        return "PLUMBING ONLY — no verdict (smoke run)"
    if n_months < GRADIENT_GATES["min_months"]:
        return f"INCONCLUSIVE — only {n_months} months < {GRADIENT_GATES['min_months']}"
    failed = [k for k, v in arm_res["checks"].items() if not v]
    return "PASS" if not failed else "FAIL — " + "; ".join(failed)


def verdict_flags(arm_res, mode):
    if mode == "smoke":
        return "PLUMBING ONLY — no verdict (smoke run)"
    n = arm_res["overall"]["n_events"]
    if n < FLAG_GATES["min_events"]:
        return f"INCONCLUSIVE — {n} de-clustered events < {FLAG_GATES['min_events']}"
    failed = [k for k, v in arm_res["checks"].items() if not v]
    return "PASS" if not failed else "FAIL — " + "; ".join(failed)


def _f(x, pct=False, nd=3):
    if x is None or (isinstance(x, float) and np.isnan(x)):
        return "n/a"
    return f"{x * 100:.1f}%" if pct else f"{x:.{nd}f}"


def write_outputs(args, gres, fres, sres, flag_studies, price_ranges, eval_ranges, rep, out_dir):
    out_dir.mkdir(parents=True, exist_ok=True)
    tf = TIMEFRAMES[args.timeframe]

    ev_rows = [{"study": n, **{k: e[k] for k in ("pair", "date", "side", "outcome", "tie", "k")}}
               for n, s in flag_studies.items() for e in s.events]
    pd.DataFrame(ev_rows).to_csv(out_dir / "events.csv", index=False)
    pd.concat([r["buckets"].assign(arm=arm) for arm, r in gres["arms"].items()]).to_csv(
        out_dir / "bucket_table.csv", index=False)

    store_dir = Path(args.data_dir)
    params = {
        "experiment": f"crowded_reversal barrier-race studies, timeframe={args.timeframe}, primary={args.primary}",
        "mode": args.mode, "tag": args.tag, "timeframe": args.timeframe,
        "run_utc": datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds"),
        "code_commit": _git_head(), "python": platform.python_version(),
        "numpy": np.__version__, "pandas": pd.__version__,
        "pine_source": "pine/crowded_reversal.pine (main) as ported by scanner/extend/crowded_reversal.py",
        "pine_params": cr.PINE_DEFAULTS, "regime_arms": args.regime_arms,
        "race": {**DEFAULT_RACE, "horizon": args.horizon, "mult": args.mult},
        "gates": {"gradient": GRADIENT_GATES, "flagged": FLAG_GATES, "primary": args.primary},
        "gradient": {"slope_unit_points": sg.SLOPE_UNIT, "bucket_edges": sg.BUCKET_EDGES[:-1] + [100],
                     "fixed_effects": "pair x side", "split_month": gres["split_month"]},
        "eval": {"warmup_bars": args.warmup, "eval_start": args.eval_start, "eval_end": args.eval_end,
                 "requires_cot_available": True},
        "experiment5_constants": {"noninferiority_margin": NONINF_MARGIN, "replication_min_slope": REPLICATION_MIN_SLOPE},
        "bootstrap": {"kind": "month-clustered; all statistics recomputed per resample; resamples shared (paired)",
                      "n": args.boot, "seed": BOOT_SEED, "ci": "2.5-97.5"},
        "sources": {
            "price": {"store": str(store_dir), "timeframe": args.timeframe,
                      "per_pair [first, last, bars]": price_ranges,
                      "meta": (json.loads((store_dir / "_meta.json").read_text())
                               if (store_dir / "_meta.json").exists() else None)},
            "cot": {"file": str(COT_CSV.relative_to(ROOT)), "sha256": _sha256(COT_CSV),
                    "report": "CFTC Legacy futures-only, Non-Commercial long/short",
                    "as_of_range": [str(rep["date"].min().date()), str(rep["date"].max().date())],
                    "alignment": "bar in week k uses Tuesday-of-week-(k-1) report (by NY-close trading day); Pine close[1]+lookahead_on"},
        },
        "eval_window_per_pair": eval_ranges,
    }
    (out_dir / "params.json").write_text(json.dumps(params, indent=2, default=str))

    L = []
    if args.mode == "smoke":
        L += ["> **PLUMBING CHECK ONLY.** Ran the ported logic end-to-end to prove it executes.",
              "> **No edge conclusion may be drawn from any number below.**", ""]
    L += [f"# Crowded Market reversal — {args.tag}", "",
          f"Mode `{args.mode}` · timeframe `{args.timeframe}` · primary = **{args.primary}** · reversal = 1 x ATR(14) barrier race, "
          f"{args.horizon} bars, same-bar-both -> continuation, timeout -> not a reversal · bootstrap {args.boot} "
          f"(month-clustered, seed {BOOT_SEED})", "",
          f"Price: `{args.data_dir}` ({len(price_ranges)} pairs), {gres['n_months']} months in the evaluation window. "
          f"COT: Legacy futures-only {rep['date'].min().date()} .. {rep['date'].max().date()}. Commit `{_git_head()[:10]}`.", "",
          "## Verdicts", ""]
    for arm in args.regime_arms:
        fv = verdict_flags(fres["arms"][arm], args.mode)
        gv = verdict_gradient(gres["arms"][arm], args.mode, gres["n_months"])
        first, second = ("flagged events", "gradient") if args.primary == "flagged" else ("gradient", "flagged events")
        vals = {"flagged events": fv, "gradient": gv}
        L.append(f"- **regime = {arm}** — **{first} (PRIMARY): {vals[first]}** · {second} (secondary): {vals[second]}")
    if "regime_filter" in gres:
        rf = gres["regime_filter"]
        L += ["", f"**ADX regime filter (gradient slope, suppress minus off, per +20 pts):** {_f(rf['diff'])} "
              f"(95% CI {_f(rf['ci'][0])} .. {_f(rf['ci'][1])}) -> {rf['reading']}."]
    L.append("")

    # ---- flagged events
    L += ["## Flagged events (score >= 60, rising edge, de-clustered per pair)", ""]
    for arm, r in fres["arms"].items():
        o, c = r["overall"], r["counts"]
        L += [f"### regime = {arm}", "",
              f"- **{o['n_events']} de-clustered events** (reversal {c['reversal']}, continuation {c['continuation']}, "
              f"timeout {c['timeout']}; same-bar ties {c['ties']}). Top+bottom same-bar conflicts skipped: {r['conflicts']}; "
              f"flags on censored bars: {r['censored_flags']}.",
              f"- Flagged reversal rate {_f(o['flagged_rate'], True)} vs like-for-like baseline {_f(o['baseline_rate'], True)} "
              f"-> **lift {_f(o['lift'], True)}** (95% CI {_f(o['ci_lo'], True)} .. {_f(o['ci_hi'], True)}).",
              f"- Halves (split after {r['split']}): first {_f(r['h1']['lift'], True)} (n={r['h1']['n_events']}), "
              f"second {_f(r['h2']['lift'], True)} (n={r['h2']['n_events']}). Excluding JPY crosses: {_f(r['ex_jpy']['lift'], True)} "
              f"(n={r['ex_jpy']['n_events']}). Excluding best pair ({r['best_pair']}): {_f(r['ex_best']['lift'], True)} "
              f"(n={r['ex_best']['n_events']}).",
              "", "| gate | result |", "|---|---|"]
        L += [f"| {k} | {'pass' if v else 'FAIL'} |" for k, v in r["checks"].items()]
        L += ["", "| pair | events | lift |", "|---|---|---|"]
        L += [f"| {row.pair} | {row.n_events} | {_f(row.lift, True)} |" for row in r["per_pair"].itertuples()]
        L.append("")
    L += ["### Single factors, same flagged-event test (each factor's own rising edge)", "",
          "| factor | events | flagged | baseline | lift | 95% CI |", "|---|---|---|---|---|---|"]
    for f, o in fres["singles"].items():
        L.append(f"| {f} | {o['n_events']} | {_f(o['flagged_rate'], True)} | {_f(o['baseline_rate'], True)} | "
                 f"{_f(o['lift'], True)} | {_f(o['ci_lo'], True)} .. {_f(o['ci_hi'], True)} |")
    L.append("")

    # ---- gradient
    if "single_vs_composite" in gres:
        L += ["## Single factor vs the whole composite (filter off) — paired month-clustered bootstrap on within-stratum rho", "",
              f"Replication of the composite's gradient (slope >= {REPLICATION_MIN_SLOPE} with CI lower > 0): **{gres['replication']}**.",
              f"Rule (pre-registered, Experiment 5): single factor AS GOOD if the CI of rho(single) - rho(composite) has lower bound > "
              f"-{NONINF_MARGIN}; composite BETTER if the CI upper bound < 0; otherwise INCONCLUSIVE.", "",
              "| single factor | rho | rho(single) - rho(composite) | 95% CI | reading |", "|---|---|---|---|---|"]
        for f, s in gres["single_vs_composite"].items():
            L.append(f"| {f} | {_f(s['rho_single'], nd=4)} | {_f(s['diff'], nd=4)} | {_f(s['ci'][0], nd=4)} .. {_f(s['ci'][1], nd=4)} | {s['label']} |")
        L.append("")

    L += ["## Score gradient (every bar)", ""]
    for arm, r in gres["arms"].items():
        L += [f"### regime = {arm}", "",
              f"- Slope **{_f(r['slope'])}** reversal-probability per +20 score points (95% CI {_f(r['slope_ci'][0])} .. "
              f"{_f(r['slope_ci'][1])}); rho = {_f(r['rho'], nd=4)}.",
              f"- Halves (split after {gres['split_month']}): {_f(r['h1'])} / {_f(r['h2'])}. Excluding JPY crosses: {_f(r['ex_jpy'])}. "
              f"Weakest leave-one-pair-out: {_f(r['loo_min'])} (dropping {r['loo_worst_pair']}).",
              f"- rho vs best single factor ({gres['best_single']}): {_f(r['rho_diff'], nd=4)} "
              f"(paired 95% CI {_f(r['rho_diff_ci'][0], nd=4)} .. {_f(r['rho_diff_ci'][1], nd=4)}).",
              "", "| gate | result |", "|---|---|"]
        L += [f"| {k} | {'pass' if v else 'FAIL'} |" for k, v in r["checks"].items()]
        L += ["", "| score bucket | obs | reversal rate | baseline | lift | 95% CI |", "|---|---|---|---|---|---|"]
        L += [f"| {b.bucket} | {b.n_obs} | {_f(b.reversal_rate, True)} | {_f(b.baseline, True)} | "
              f"{_f(b.lift, True)} | {_f(b.ci_lo, True)} .. {_f(b.ci_hi, True)} |" for b in r["buckets"].itertuples()]
        L.append("")

    if sres:
        L += ["## Exploratory: ranging vs trending bars (filter-off score; NO gates, cannot promote or rescue a verdict)", "",
              "'Trending' = the Pine's debounced ADX>=30 + EMA200-slope state (either direction) at the bar; 'ranging' = neither. "
              "Baselines are computed inside each stratum.", "",
              "| stratum | observations | gradient slope per +20 pts (95% CI) | flagged events | flagged lift (95% CI) |",
              "|---|---|---|---|---|"]
        for name, s in sres.items():
            L.append(f"| {name} | {s['n_obs']} | {_f(s['slope'])} ({_f(s['slope_ci'][0])} .. {_f(s['slope_ci'][1])}) | "
                     f"{s['flag']['n_events']} | {_f(s['flag']['lift'], True)} ({_f(s['flag']['ci_lo'], True)} .. {_f(s['flag']['ci_hi'], True)}) |")
        L.append("")

    L += ["## Known limits", "",
          "- D1/H4 OHLC cannot sequence intrabar: a bar touching both barriers is scored continuation, for every bar alike.",
          "- Gradient observations are ALL bars, so consecutive observations overlap; the month-clustered bootstrap absorbs same-month "
          "dependence, not every cross-pair dependency (12 pairs share USD/JPY legs and COT legs).",
          "- One macro era (~2020-2026, incl. the 2022 USD surge); nothing here speaks to earlier regimes.",
          "- COT percentile is computed on a business-day calendar and reproduced TradingView's label to within ~0.3 percentile points.",
          "- H4 stores: the provider omits about one H1 bar most Monday mornings (3-bar blocks); the UTC alignment keeps the "
          "Sunday-reopen and Friday-close STUB blocks (1-3 H1 bars), as the app's own aggregator does."]
    (out_dir / "summary.md").write_text("\n".join(L) + "\n", encoding="utf-8")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--tag", required=True)
    ap.add_argument("--mode", choices=["smoke", "full"], required=True)
    ap.add_argument("--timeframe", choices=list(TIMEFRAMES), default="d1")
    ap.add_argument("--primary", choices=["gradient", "flagged"], default="gradient")
    ap.add_argument("--data-dir", default=None)
    ap.add_argument("--pairs", nargs="*", default=PAIRS)
    ap.add_argument("--horizon", type=int, default=DEFAULT_RACE["horizon"])
    ap.add_argument("--mult", type=float, default=DEFAULT_RACE["mult"])
    ap.add_argument("--warmup", type=int, default=None, help="bars skipped for EMA200/ADX/z warm-up (default per timeframe)")
    ap.add_argument("--eval-start", default=None)
    ap.add_argument("--eval-end", default=None)
    ap.add_argument("--boot", type=int, default=5000)
    ap.add_argument("--regime-arms", nargs="*", default=["suppress", "off"])
    args = ap.parse_args()
    args.data_dir = args.data_dir or TIMEFRAMES[args.timeframe]["dir"]
    args.warmup = args.warmup if args.warmup is not None else TIMEFRAMES[args.timeframe]["warmup"]

    print(f"crowded_reversal [{args.mode}] tf={args.timeframe} primary={args.primary} tag={args.tag}")
    grad, flag_studies, strata_grad, strata_flag, price_ranges, eval_ranges, rep = run(args)
    gres = evaluate_gradient(grad, args.regime_arms, args.boot)
    fres = evaluate_flags(flag_studies, args.regime_arms, args.boot)
    sres = evaluate_strata(strata_grad, strata_flag, args.boot) if "off" in args.regime_arms else {}
    out_dir = ROOT / "data" / "backtest" / f"crowded_reversal_{args.tag}"
    write_outputs(args, gres, fres, sres, flag_studies, price_ranges, eval_ranges, rep, out_dir)
    for arm in args.regime_arms:
        o = fres["arms"][arm]["overall"]
        print(f"[{arm}] gradient slope={_f(gres['arms'][arm]['slope'])} | flagged n={o['n_events']} lift={_f(o['lift'], True)} "
              f"CI {_f(o['ci_lo'], True)}..{_f(o['ci_hi'], True)}")
        print(f"        flagged: {verdict_flags(fres['arms'][arm], args.mode)}")
        print(f"        gradient: {verdict_gradient(gres['arms'][arm], args.mode, gres['n_months'])}")
    print(f"wrote {out_dir.relative_to(ROOT)}/")


if __name__ == "__main__":
    main()
