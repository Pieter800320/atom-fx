"""
ATOM FX research — Experiments 9-11 (Pieter's ideas, 2026-09-20): inside bars, COT holds, carry.

  --exp inside   Exp 9: does a breakout from an INSIDE BAR continue more than an ordinary prior-bar-range breakout?  (barrier race, like Experiments 2-8)
  --exp cot      Exp 10: contrarian COT extremes (combined percentile >= 90 / <= 10) held for h weeks: signed forward return vs the pair's own drift.
  --exp carry    Exp 11: monthly per-pair carry (long the higher-yielding currency) with BIS policy rates: spot + carry accrual.

    py -m tools.backtest_ideas --exp inside --timeframe d1utc --tag exp9_d1utc_2026-09 --mode full
    py -m tools.backtest_ideas --exp cot --weeks 8 --tag exp10_h8_2026-09 --mode full
    py -m tools.backtest_ideas --exp carry --tag exp11_2026-09 --mode full

--mode smoke = plumbing only (one pair, few bootstraps, verdict forced to PLUMBING ONLY).  Nothing is tuned: every parameter is fixed in docs/RESEARCH_LOG.md before the run.
"""
import argparse
import io
import json
import sys
import zipfile
from pathlib import Path

import numpy as np
import pandas as pd

ROOT = Path(__file__).parent.parent
sys.path.insert(0, str(ROOT))

from scanner.extend import barrier_race as br
from scanner.extend import crowded_reversal as cr
from tools import backtest_crowded_reversal as bt
from tools.backtest_crowded_reversal_regime import side_baseline, _f

SEED = bt.BOOT_SEED
BIS_ZIP = ROOT / "data" / "_bis_cbpol.zip"
CCY_AREA = {"USD": "US", "EUR": "XM", "GBP": "GB", "JPY": "JP", "CHF": "CH", "CAD": "CA", "AUD": "AU", "NZD": "NZ"}


def month_boot(values, months, n_boot, seed=SEED):
    """Mean of `values` and its month-clustered bootstrap 95% CI (months resampled with replacement)."""
    values, months = np.asarray(values, float), np.asarray(months)
    ok = ~np.isnan(values)
    values, months = values[ok], months[ok]
    if len(values) == 0:
        return np.nan, np.nan, np.nan
    uniq, inv = np.unique(months, return_inverse=True)
    S = np.bincount(inv, weights=values, minlength=len(uniq))
    C = np.bincount(inv, minlength=len(uniq)).astype(float)
    if n_boot <= 0:
        return float(values.mean()), np.nan, np.nan
    rng = np.random.default_rng(seed)
    out = np.empty(n_boot)
    for b in range(n_boot):
        w = np.bincount(rng.integers(0, len(uniq), len(uniq)), minlength=len(uniq))
        out[b] = (S @ w) / (C @ w) if (C @ w) > 0 else np.nan
    lo, hi = np.nanpercentile(out, [2.5, 97.5])
    return float(values.mean()), float(lo), float(hi)


# ------------------------------------------------------------------------------------------------------------------ Exp 9: inside bars
def inside_events(h, l, c, window=3):
    n = len(h)
    inside = np.zeros(n, bool)
    inside[1:] = (h[1:] < h[:-1]) & (l[1:] > l[:-1])
    up_new, dn_new = np.zeros(n, bool), np.zeros(n, bool)
    for i in np.flatnonzero(inside):
        if i < 2 or inside[i - 1]:
            continue                                   # only the FIRST inside bar of a sequence; the mother is the bar before it
        mh, ml = h[i - 1], l[i - 1]
        for j in range(i + 1, min(i + 1 + window, n)):
            if c[j] > mh:
                up_new[j] = True
                break
            if c[j] < ml:
                dn_new[j] = True
                break
    return up_new, dn_new, inside


def run_inside(args):
    cfg = bt.TIMEFRAMES[args.timeframe]
    studies = {"inside_break": br.Study("inside_break", 20), "inside_vs_all": br.Study("inside_vs_all", 20)}
    n_inside = 0
    for pair in args.pairs:
        df = bt.load_bars(pair, args.timeframe, ROOT / cfg["dir"])
        h, l, c = (df[k].to_numpy(float) for k in ("high", "low", "close"))
        atr = cr.atr(h, l, c, 14)
        result, k = br.race_all(h, l, c, atr, horizon=20, mult=1.0)
        dates = pd.to_datetime(df["date"]).to_numpy()
        months = np.asarray(pd.to_datetime(dates).strftime("%Y-%m"))
        eval_mask = np.arange(len(df)) >= 100
        up_new, dn_new, inside = inside_events(h, l, c)
        n_inside += int((inside & eval_mask).sum())
        up_mask = np.concatenate(([False], c[1:] > h[:-1]))          # ordinary prior-bar-range breakouts (the matched baseline)
        dn_mask = np.concatenate(([False], c[1:] < l[:-1]))
        # side semantics of br.Study: 'bottom' expects UP first, 'top' expects DOWN first -> an up-breakout continues if price goes UP
        studies["inside_break"].add_pair(pair, dates, result, k, dn_new, up_new, eval_mask, side_baseline(result, eval_mask, months, dn_mask, up_mask))
        studies["inside_vs_all"].add_pair(pair, dates, result, k, dn_new, up_new, eval_mask, br.baseline_counts(result, eval_mask, months))
        print(f"  {pair}: {len(df)} bars, inside bars {int((inside & eval_mask).sum())}")
    return studies, n_inside


def gates_flag(s, n_boot, extra=None):
    months = sorted({m for (_, _, m) in s.base})
    split = months[(len(months) - 1) // 2] if months else ""
    pairs = sorted({q for (q, _, _) in s.base})
    pp = s.per_pair()
    best = pp.sort_values("contribution", ascending=False).iloc[0]["pair"] if len(pp) else None
    ov = s.lift(n_boot=n_boot, seed=SEED)
    h1, h2 = s.lift(month_filter=lambda m: m <= split, n_boot=0), s.lift(month_filter=lambda m: m > split, n_boot=0)
    exj, exb = s.lift(pairs=[q for q in pairs if "JPY" not in q], n_boot=0), s.lift(pairs=[q for q in pairs if q != best], n_boot=0)
    pos = lambda r: (not np.isnan(r["lift"])) and r["lift"] > 0
    checks = {"F1 >= 100 de-clustered events": ov["n_events"] >= 100,
              "F2 lift >= 5 points": (not np.isnan(ov["lift"])) and ov["lift"] >= 0.05,
              "F2 95% CI lower bound > 0": (not np.isnan(ov["ci_lo"])) and ov["ci_lo"] > 0,
              "F3 both halves lift > 0": pos(h1) and pos(h2), "F4 excluding JPY crosses lift > 0": pos(exj),
              f"F5 excluding best pair ({best}) lift > 0": pos(exb),
              "F6 reversal (continuation) rate > 50%": (not np.isnan(ov["flagged_rate"])) and ov["flagged_rate"] > 0.5}
    return ov, checks, {"h1": h1, "h2": h2, "exj": exj, "exb": exb, "split": split, "counts": s.outcome_counts()}


# ------------------------------------------------------------------------------------------------------------------ Exp 10: COT holds
def run_cot(args):
    rep = bt.load_cot()
    p = dict(cr.PINE_DEFAULTS)
    cal = pd.bdate_range(rep["date"].min(), pd.Timestamp("2026-10-05"))
    H = 5 * args.weeks
    rows = []
    for pair in args.pairs:
        df = bt.load_bars(pair, "d1utc", ROOT / bt.TIMEFRAMES["d1utc"]["dir"])
        comb = bt.cot_combined_for_pair(pair, rep, cal, p["cot_lookback_weeks"]).reindex(pd.DatetimeIndex(df["cot_day"])).to_numpy()
        c = df["close"].to_numpy(float)
        n = len(c)
        fwd = np.full(n, np.nan)
        fwd[:n - H] = c[H:] / c[:n - H] - 1.0
        elig = ~np.isnan(fwd) & ~np.isnan(comb) & (np.arange(n) >= 260)
        if elig.sum() < 300:
            continue
        sigma = np.nanstd(fwd[elig])
        dates = pd.to_datetime(df["date"])
        for side, flag, sign in (("top", cr.rising_edge(comb >= 90), -1.0), ("bottom", cr.rising_edge(comb <= 10), +1.0)):
            z_all = sign * fwd[elig] / sigma
            base_z, base_hit = float(z_all.mean()), float((z_all > 0).mean())
            busy = -1
            for i in np.flatnonzero(flag & elig):
                if i <= busy:
                    continue
                busy = i + H
                z = sign * fwd[i] / sigma
                rows.append({"pair": pair, "side": side, "date": dates.iloc[i], "month": dates.iloc[i].strftime("%Y-%m"),
                             "z": z, "hit": float(z > 0), "base_z": base_z, "base_hit": base_hit})
        print(f"  {pair}: eligible days {int(elig.sum())}")
    return pd.DataFrame(rows)


def eval_cot(ev, n_boot):
    def lift(sub, boot):
        m, lo, hi = month_boot(sub["z"] - sub["base_z"], sub["month"], n_boot if boot else 0)
        return {"n": len(sub), "z_lift": m, "lo": lo, "hi": hi, "hit": float(sub["hit"].mean()) if len(sub) else np.nan,
                "hit_base": float(sub["base_hit"].mean()) if len(sub) else np.nan}
    ov = lift(ev, True)
    months = sorted(ev["month"].unique())
    split = months[(len(months) - 1) // 2]
    h1, h2 = lift(ev[ev["month"] <= split], False), lift(ev[ev["month"] > split], False)
    exj = lift(ev[~ev["pair"].str.contains("JPY")], False)
    contrib = ev.assign(c=ev["z"] - ev["base_z"]).groupby("pair")["c"].sum()
    best = contrib.idxmax()
    exb = lift(ev[ev["pair"] != best], False)
    checks = {"F1 >= 100 de-clustered events": ov["n"] >= 100,
              "F2 mean standardised lift CI lower bound > 0": (not np.isnan(ov["lo"])) and ov["lo"] > 0,
              "F2 hit-rate lift >= 5 points": ov["hit"] - ov["hit_base"] >= 0.05,
              "F3 both halves lift > 0": h1["z_lift"] > 0 and h2["z_lift"] > 0,
              "F4 excluding JPY crosses lift > 0": exj["z_lift"] > 0, f"F5 excluding best pair ({best}) lift > 0": exb["z_lift"] > 0,
              "F6 hit rate > 50%": ov["hit"] > 0.5}
    by_side = {s: lift(ev[ev["side"] == s], False) for s in ("top", "bottom")}
    return ov, checks, {"h1": h1, "h2": h2, "exj": exj, "exb": exb, "split": split, "by_side": by_side}


# ------------------------------------------------------------------------------------------------------------------ Exp 11: carry
def load_rates():
    df = pd.read_csv(zipfile.ZipFile(BIS_ZIP).open("WS_CBPOL_csv_flat.csv"), low_memory=False)
    fc, ac, tc, oc = ("FREQ:Frequency", "REF_AREA:Reference area", "TIME_PERIOD:Time period or range", "OBS_VALUE:Observation Value")
    df = df[df[fc].astype(str).str.startswith("D")]
    df["code"] = df[ac].astype(str).str.split(":").str[0].str.strip()
    out = {}
    for ccy, code in CCY_AREA.items():
        g = df[df["code"] == code]
        out[ccy] = pd.Series(pd.to_numeric(g[oc]).to_numpy(), index=pd.to_datetime(g[tc])).sort_index()
    return out


def run_carry(args, min_diff=0.5):
    rates = load_rates()
    rows = []
    for pair in args.pairs:
        base, quote = pair[:3], pair[3:]
        df = bt.load_bars(pair, "d1utc", ROOT / bt.TIMEFRAMES["d1utc"]["dir"]).set_index(pd.to_datetime(bt.load_bars(pair, "d1utc", ROOT / bt.TIMEFRAMES["d1utc"]["dir"])["date"]))
        me = df["close"].groupby(df.index.to_period("M")).last()                      # month-end closes
        dates = df.index.to_series().groupby(df.index.to_period("M")).last()
        for a, b in zip(me.index[:-1], me.index[1:]):
            d0 = dates[a]
            rb = rates[base].asof(d0)
            rq = rates[quote].asof(d0)
            if np.isnan(rb) or np.isnan(rq):
                continue
            diff = rb - rq
            s = 0.0 if abs(diff) < min_diff else float(np.sign(diff))
            spot = me[b] / me[a] - 1.0
            rows.append({"pair": pair, "month": str(b), "year": b.year, "s": s, "diff": diff, "spot": s * spot, "carry": s * diff / 1200.0})
        print(f"  {pair}: {len(me)} month-ends")
    return pd.DataFrame(rows)


def eval_carry(m, n_boot):
    act = m[m["s"] != 0].copy()
    act["tot"] = act["spot"] + act["carry"]
    port = act.groupby("month").agg(tot=("tot", "mean"), spot=("spot", "mean"), carry=("carry", "mean"), n=("s", "size")).reset_index()

    def stats(p):
        mean, lo, hi = month_boot(p["tot"], p["month"], n_boot)
        sd = p["tot"].std()
        return {"months": len(p), "ann_mean": 12 * mean, "ci_lo": 12 * lo if not np.isnan(lo) else np.nan, "ci_hi": 12 * hi if not np.isnan(hi) else np.nan,
                "ann_vol": sd * np.sqrt(12), "sharpe": (12 * mean) / (sd * np.sqrt(12)) if sd > 0 else np.nan,
                "spot_ann": 12 * p["spot"].mean(), "carry_ann": 12 * p["carry"].mean(), "pos_share": float((p["tot"] > 0).mean())}
    ov = stats(port)
    mid = sorted(port["month"])[len(port) // 2]
    h1, h2 = stats(port[port["month"] <= mid]), stats(port[port["month"] > mid])

    def port_of(sub):
        return sub.groupby("month").agg(tot=("tot", "mean"), spot=("spot", "mean"), carry=("carry", "mean")).reset_index()
    exj = stats(port_of(act[~act["pair"].str.contains("JPY")]))
    yr = act.groupby(act["month"].str[:4])["tot"].mean()
    best_year = yr.idxmax()
    exy = stats(port_of(act[act["month"].str[:4] != best_year]))
    eq = (1 + port["tot"]).cumprod()
    dd = float((eq / eq.cummax() - 1).min())
    checks = {"F1 >= 100 monthly observations": ov["months"] >= 100,
              "F2 annualised total return CI lower bound > 0": (not np.isnan(ov["ci_lo"])) and ov["ci_lo"] > 0,
              "F3 both halves mean > 0": h1["ann_mean"] > 0 and h2["ann_mean"] > 0,
              "F4 excluding JPY pairs mean > 0": exj["ann_mean"] > 0,
              f"F5 excluding the best year ({best_year}) mean > 0": exy["ann_mean"] > 0,
              "F6 Sharpe >= 0.3": (not np.isnan(ov["sharpe"])) and ov["sharpe"] >= 0.3}
    return ov, checks, {"h1": h1, "h2": h2, "exj": exj, "exy": exy, "max_dd": dd, "best_year": best_year, "active_pairs_avg": float(port["n"].mean()), "mid": mid}


# ------------------------------------------------------------------------------------------------------------------ output
def verdict(ov_n_key, checks, mode, floor_key):
    if mode == "smoke":
        return "PLUMBING ONLY — no verdict (smoke run)"
    if not checks[floor_key]:
        return "INCONCLUSIVE — F1 (too few observations)"
    failed = [k for k, v in checks.items() if not v]
    return "PASS" if not failed else "FAIL — " + "; ".join(failed)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--exp", choices=["inside", "cot", "carry"], required=True)
    ap.add_argument("--tag", required=True)
    ap.add_argument("--mode", choices=["smoke", "full"], required=True)
    ap.add_argument("--timeframe", default="d1utc")
    ap.add_argument("--weeks", type=int, default=8)
    ap.add_argument("--pairs", nargs="*", default=bt.PAIRS)
    ap.add_argument("--boot", type=int, default=5000)
    args = ap.parse_args()
    if args.mode == "smoke":
        args.pairs, args.boot = ["EURUSD", "AUDUSD"], 200
    out_dir = ROOT / "data" / "backtest" / f"ideas_{args.tag}"
    out_dir.mkdir(parents=True, exist_ok=True)
    L = [f"# Experiment {'9 inside bars' if args.exp == 'inside' else '10 COT holds' if args.exp == 'cot' else '11 carry'} — {args.tag} ({args.mode})\n"]
    if args.exp == "inside":
        studies, n_inside = run_inside(args)
        ov, checks, x = gates_flag(studies["inside_break"], args.boot)
        alt = studies["inside_vs_all"].lift(n_boot=args.boot, seed=SEED)
        v = verdict(None, checks, args.mode, "F1 >= 100 de-clustered events")
        L += [f"Verdict: **{v}**\n", f"Inside bars in the window: {n_inside}\n", "| arm | events | continuation rate | baseline | lift (95% CI) |", "|---|---|---|---|---|",
              f"| inside_break (vs ordinary prior-bar breakouts) | {ov['n_events']} | {_f(ov['flagged_rate'], True)} | {_f(ov['baseline_rate'], True)} | {_f(ov['lift'], True)} ({_f(ov['ci_lo'], True)} .. {_f(ov['ci_hi'], True)}) |",
              f"| inside_vs_all (vs all bars, info) | {alt['n_events']} | {_f(alt['flagged_rate'], True)} | {_f(alt['baseline_rate'], True)} | {_f(alt['lift'], True)} ({_f(alt['ci_lo'], True)} .. {_f(alt['ci_hi'], True)}) |"]
        L.append(f"\nHalves (after {x['split']}): {_f(x['h1']['lift'], True)} (n={x['h1']['n_events']}) / {_f(x['h2']['lift'], True)} (n={x['h2']['n_events']}); ex-JPY {_f(x['exj']['lift'], True)}; ex-best {_f(x['exb']['lift'], True)}; outcomes {x['counts']}")
        print(f"[inside_break] n={ov['n_events']} rate={_f(ov['flagged_rate'], True)} baseline={_f(ov['baseline_rate'], True)} lift={_f(ov['lift'], True)} CI {_f(ov['ci_lo'], True)}..{_f(ov['ci_hi'], True)} | vs all bars {_f(alt['lift'], True)}")
    elif args.exp == "cot":
        ev = run_cot(args)
        ov, checks, x = eval_cot(ev, args.boot)
        v = verdict(None, checks, args.mode, "F1 >= 100 de-clustered events")
        ev.to_csv(out_dir / "events.csv", index=False)
        L += [f"Verdict: **{v}**\n", f"h = {args.weeks} weeks. Events {ov['n']}; standardised signed return lift {ov['z_lift']:.3f} sigma (95% CI {ov['lo']:.3f} .. {ov['hi']:.3f}); hit rate {ov['hit']:.1%} vs baseline {ov['hit_base']:.1%}\n",
              f"Halves (after {x['split']}): {x['h1']['z_lift']:.3f} (n={x['h1']['n']}) / {x['h2']['z_lift']:.3f} (n={x['h2']['n']}); ex-JPY {x['exj']['z_lift']:.3f}; ex-best {x['exb']['z_lift']:.3f}",
              f"By side: top {x['by_side']['top']['z_lift']:.3f} (n={x['by_side']['top']['n']}), bottom {x['by_side']['bottom']['z_lift']:.3f} (n={x['by_side']['bottom']['n']})"]
        print(f"[cot h={args.weeks}w] n={ov['n']} z_lift={ov['z_lift']:.3f} CI {ov['lo']:.3f}..{ov['hi']:.3f} hit {ov['hit']:.1%} vs {ov['hit_base']:.1%}")
    else:
        m = run_carry(args)
        m.to_csv(out_dir / "pair_months.csv", index=False)
        ov, checks, x = eval_carry(m, args.boot)
        v = verdict(None, checks, args.mode, "F1 >= 100 monthly observations")
        L += [f"Verdict: **{v}**\n", f"Months {ov['months']} (avg active pairs {x['active_pairs_avg']:.1f}); annualised TOTAL return {ov['ann_mean']:.2%} (95% CI {ov['ci_lo']:.2%} .. {ov['ci_hi']:.2%}); vol {ov['ann_vol']:.2%}; Sharpe {ov['sharpe']:.2f}; positive months {ov['pos_share']:.0%}",
              f"Split: spot part {ov['spot_ann']:.2%} + carry accrual {ov['carry_ann']:.2%}; max drawdown {x['max_dd']:.1%}",
              f"Halves (after {x['mid']}): {x['h1']['ann_mean']:.2%} / {x['h2']['ann_mean']:.2%}; ex-JPY {x['exj']['ann_mean']:.2%}; ex-best-year ({x['best_year']}) {x['exy']['ann_mean']:.2%}"]
        print(f"[carry] months={ov['months']} ann_total={ov['ann_mean']:.2%} CI {ov['ci_lo']:.2%}..{ov['ci_hi']:.2%} sharpe={ov['sharpe']:.2f} spot={ov['spot_ann']:.2%} carry={ov['carry_ann']:.2%} maxDD={x['max_dd']:.1%}")
    L.append("\n## Gates\n")
    L += [f"- {'PASS' if val else 'FAIL'}: {k_}" for k_, val in checks.items()]
    (out_dir / "summary.md").write_text("\n".join(L) + "\n", encoding="utf-8")
    (out_dir / "params.json").write_text(json.dumps({"exp": args.exp, "tag": args.tag, "mode": args.mode, "timeframe": args.timeframe, "weeks": args.weeks, "boot": args.boot,
                                                     "seed": SEED, "git_head": bt._git_head()}, indent=2), encoding="utf-8")
    print("           " + v)


if __name__ == "__main__":
    main()
