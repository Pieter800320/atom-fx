"""
ATOM FX — H4/H1 bar dates (EXTEND)

The frozen aggregator (`scanner/aggregator.py`) discards its own H4/D1 timestamp index
before handing a DataFrame back (`reset_index(drop=True)`) — by design, `score.py` only
ever needs a price, never a date. Anything that wants to LABEL a bar (a chart X-axis)
has to recover that timestamp independently, the same way `bb_touch.py`'s own
`_d1_ny_close()` already does for D1 (NY-session convention, a different boundary).

This module does the H1/H4 equivalents, purely for LABELS:
- H1 needs no aggregation at all — just the raw fetch's own `datetime` column, parsed
  and sorted the same way `aggregator.py`'s own `_prepare()` already does.
- H4 mirrors `aggregate_h4()`'s own resample call exactly (same interval, same
  closed/label edges), so the recovered timestamps line up with the frozen H4 df's own
  rows by construction, not by luck — `tests/test_extend.py` cross-checks the recovered
  H4 CLOSE values against the frozen ones to prove it, not just the row count.

Rule #1: reads raw H1 OHLCV only and produces date strings — never a price, indicator,
or trading number. Not a second aggregator; a pure label-recovery pass alongside it.
"""
import pandas as pd


def h1_dates(raw_h1_df) -> pd.Series:
    """Oldest-first date strings, one per row of the frozen `ohlcv[key]["h1"]` — same
    parse-and-sort `aggregator._prepare()` applies before building that frozen frame."""
    df = raw_h1_df.copy()
    df["dt"] = pd.to_datetime(df["datetime"], utc=True)
    df = df.sort_values("dt").reset_index(drop=True)
    return df["dt"].dt.strftime("%Y-%m-%d")


def h4_dates(raw_h1_df, trading_clock: bool = False) -> pd.Series:
    """Oldest-first date strings, one per row of the frozen `ohlcv[key]["h4"]` — same
    4-hour floor (`closed="left", label="left"`) as `aggregator.aggregate_h4()`.

    `trading_clock=True` (2026-09-19, fx_week_v3) mirrors the frozen H4 frame that `scan_h1.py` now builds from the H1 rows re-labelled
    as New York wall time + 7 h (`fx_week.to_trading_clock`): the same shift is applied here so the row counts and boundaries line up
    again, and each date is that block's TRADING day. Callers pass True when the frame carries `attrs["clock"] == "ny"`."""
    df = raw_h1_df.copy()
    if trading_clock:
        from scanner.extend import fx_week
        df = fx_week.to_trading_clock(df)
    df["dt"] = pd.to_datetime(df["datetime"], utc=True)
    df = df.sort_values("dt").set_index("dt")
    for col in ("open", "close"):
        df[col] = pd.to_numeric(df[col], errors="coerce")
    h4 = df.resample("4h", closed="left", label="left").agg(
        open=("open", "first"),
        close=("close", "last"),
    ).dropna(subset=["open", "close"])
    return pd.Series(h4.index.strftime("%Y-%m-%d"), index=range(len(h4)))
