#!/usr/bin/env python3
"""
tools/analyze_backtest_trades.py
=========================================================================
Read-only diagnostic over an EXISTING data/backtest/trades.csv (produced by
tools/backtest_trend_pullback.py). Does NOT run the backtest, does NOT touch
the detector, does NOT recompute realized_R -- it only re-slices the numbers
the backtest already wrote, to check whether the aggregate edge is real or an
artifact (pair concentration, time concentration, or a handful of outlier
trades carrying the total).

Column mapping (data/backtest/trades.csv header:
  pair,direction,entry,stop,target,planned_rr,realized_R,bars_held,exit_reason):
  - pair           -> "pair"
  - realized R     -> "realized_R" (used as-is; never recomputed here)
  - entry datetime -> ABSENT. The file has no timestamp column of any kind
    (bars_held is a duration, not a clock time). Analysis B (time / calendar
    split / out-of-sample) is therefore reported as BLOCKED, not guessed at.

Ordering note: with no timestamp, "consecutive trades" (for maxCL) uses the
CSV's own row order. Within a pair that order IS chronological (simulate_pair
walks each pair forward in time and appends in that order); across pairs the
file is simply one pair's whole run followed by the next's, not a true
global interleave. Analyses here only compute maxCL within a single group's
row order (TOTAL, a JPY/non-JPY group, or a single pair), which is
chronological within each contributing pair, never claimed as a cross-pair
global sequence.

Usage: python -m tools.analyze_backtest_trades [path-to-trades.csv]
=========================================================================
"""
import sys

import pandas as pd

DEFAULT_PATH = "data/backtest/trades.csv"
REPORT_PATH = "data/backtest/diagnostics.txt"


def _pf(pos_sum: float, neg_sum_abs: float) -> str:
    if neg_sum_abs == 0:
        return "∞" if pos_sum > 0 else "nan"
    return f"{pos_sum / neg_sum_abs:.2f}"


def _max_consec_le_zero(r_series: pd.Series) -> int:
    max_run = cur = 0
    for r in r_series:
        if r <= 0:
            cur += 1
            max_run = max(max_run, cur)
        else:
            cur = 0
    return max_run


def group_stats(df: pd.DataFrame) -> dict:
    n = len(df)
    if n == 0:
        return {"n": 0, "win_pct": None, "totR": None, "avgR": None, "pf": None, "maxCL": None}
    r = df["realized_R"]
    wins = r[r > 0]
    losses = r[r < 0]
    return {
        "n": n,
        "win_pct": round(len(wins) / n * 100, 2),
        "totR": round(float(r.sum()), 1),
        "avgR": round(float(r.mean()), 2),
        "pf": _pf(float(wins.sum()), float(-losses.sum())),
        "maxCL": _max_consec_le_zero(r),
    }


def _fmt_row(label: str, s: dict) -> str:
    if s["n"] == 0:
        return f"  {label:12s} n=0"
    return (f"  {label:12s} n={s['n']:>4}  win%={s['win_pct']:>6}  "
            f"totR={s['totR']:>8}  avgR={s['avgR']:>6}  PF={s['pf']:>6}  maxCL={s['maxCL']:>3}")


def is_jpy(pair: str) -> bool:
    return "JPY" in pair.upper()


def section_a(df: pd.DataFrame, lines: list[str]) -> None:
    lines.append("=" * 70)
    lines.append("SECTION A -- JPY vs non-JPY split")
    lines.append("=" * 70)
    jpy = df[df["pair"].apply(is_jpy)]
    non_jpy = df[~df["pair"].apply(is_jpy)]
    lines.append(_fmt_row("JPY", group_stats(jpy)))
    lines.append(_fmt_row("non-JPY", group_stats(non_jpy)))
    lines.append(_fmt_row("TOTAL", group_stats(df)))
    lines.append("")
    lines.append("  Per-pair breakdown:")
    for pair, sub in df.groupby("pair", sort=True):
        lines.append(_fmt_row(pair, group_stats(sub)))
    lines.append("")


def section_b(df: pd.DataFrame, lines: list[str]) -> None:
    lines.append("=" * 70)
    lines.append("SECTION B -- Time / out-of-sample")
    lines.append("=" * 70)
    lines.append("  BLOCKED: data/backtest/trades.csv has no entry-datetime column.")
    lines.append(f"  Columns present: {list(df.columns)}")
    lines.append("  Cannot identify per-year splits, a calendar-midpoint split, or a")
    lines.append("  chronological JPY/non-JPY breakdown within each half without a real")
    lines.append("  timestamp per trade. Not guessed at (e.g. via row order alone, which is")
    lines.append("  chronological WITHIN a pair but not a true global cross-pair sequence).")
    lines.append("  This constraint says do not re-run the backtest -- so this section is")
    lines.append("  left blocked rather than worked around. Fix would be adding an")
    lines.append("  entry-datetime (or entry_idx timestamp) column to the backtest's own")
    lines.append("  _TRADE_COLS/trade dict in tools/backtest_trend_pullback.py and re-running.")
    lines.append("")


def section_c(df: pd.DataFrame, lines: list[str]) -> None:
    lines.append("=" * 70)
    lines.append("SECTION C -- Fat-tail / outlier robustness")
    lines.append("=" * 70)

    sorted_df = df.sort_values("realized_R", ascending=False).reset_index(drop=True)
    r = sorted_df["realized_R"]
    n = len(sorted_df)

    baseline_mean = float(r.mean())
    baseline_tot = float(r.sum())
    lines.append(f"  Full sample:        n={n:>4}  totR={baseline_tot:>8.1f}  avgR={baseline_mean:>6.2f}")

    for k in (1, 3, 5):
        rest = r.iloc[k:]
        rest_n = len(rest)
        rest_mean = float(rest.mean()) if rest_n else float("nan")
        rest_tot = float(rest.sum()) if rest_n else 0.0
        lines.append(f"  Excl. top {k} winner(s):  n={rest_n:>4}  totR={rest_tot:>8.1f}  "
                      f"avgR={rest_mean:>6.2f}  (baseline: n={n}, totR={baseline_tot:.1f}, avgR={baseline_mean:.2f})")

    pos_sum = float(r[r > 0].sum())
    top3_sum = float(r.iloc[:3].clip(lower=0).sum())
    top3_share = (top3_sum / pos_sum * 100) if pos_sum > 0 else float("nan")
    lines.append(f"\n  Top-3 winners' share of total positive R: {top3_share:.1f}%  "
                 f"(top3_sum={top3_sum:.2f}, total_positive_R={pos_sum:.2f})")

    lines.append("\n  R distribution:")
    lines.append(f"    min={r.min():.2f}  p25={r.quantile(0.25):.2f}  median={r.median():.2f}  "
                 f"p75={r.quantile(0.75):.2f}  max={r.max():.2f}")
    bins = {
        "R <= -0.9 (~full stop)": (r <= -0.9).sum(),
        "-0.9 < R <= 0":          ((r > -0.9) & (r <= 0)).sum(),
        "0 < R <= 3":             ((r > 0) & (r <= 3)).sum(),
        "3 < R <= 10":            ((r > 3) & (r <= 10)).sum(),
        "R > 10":                 (r > 10).sum(),
    }
    for label, count in bins.items():
        lines.append(f"    {label:24s} {int(count):>4}")

    top = sorted_df.iloc[0]
    lines.append(f"\n  Single largest winner: R={top['realized_R']:.2f}  pair={top['pair']}  "
                 f"entry_date=N/A (no entry-datetime column in trades.csv)")
    lines.append("")


def main(argv=None) -> None:
    argv = argv if argv is not None else sys.argv[1:]
    path = argv[0] if argv else DEFAULT_PATH

    df = pd.read_csv(path)

    header_lines = []
    header_lines.append("=" * 70)
    header_lines.append("STEP 0 -- Schema")
    header_lines.append("=" * 70)
    header_lines.append(f"Source file: {path}")
    header_lines.append(f"Columns: {list(df.columns)}")
    header_lines.append(f"Row count: {len(df)}")
    header_lines.append("First 3 rows:")
    header_lines.append(df.head(3).to_string(index=False))
    header_lines.append("")
    header_lines.append("Column mapping:")
    header_lines.append('  pair           -> "pair"')
    header_lines.append('  entry datetime -> ABSENT (no timestamp column in this file at all)')
    header_lines.append('  realized R     -> "realized_R" (used as written by the backtest, not recomputed)')
    header_lines.append("")

    body_lines: list[str] = []
    section_a(df, body_lines)
    section_b(df, body_lines)
    section_c(df, body_lines)

    report = "\n".join(header_lines + body_lines)
    print(report)

    with open(REPORT_PATH, "w", encoding="utf-8") as f:
        f.write(report + "\n")
    print(f"\n(also written to {REPORT_PATH})")


if __name__ == "__main__":
    main()
