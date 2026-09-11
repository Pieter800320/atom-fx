"""
ATOM FX — NY-close D1 aggregation (DECISION-001).

docs/ATOM_FX_TREND_METHODOLOGY_SPEC.md §3: the frozen `scanner/aggregator.py` buckets D1 on
UTC calendar dates (documented there as an accepted approximation for the coarse bull/bear
pills). This module is a NEW, additive EXTEND layer building the same H1 -> D1 aggregation on
the retail/broker convention instead — a daily candle runs 17:00 -> next 17:00
America/New_York, DST-aware, with the Sunday reopen rolled into Monday's bar (no phantom
~2-hour Sunday stub). Needed because the trend-pullback methodology's D1 EMA50/EMA200 bias
gate must match the convention traders actually see (Rule 1: faithful or not at all).

Rule #1: EXTEND. Reads the same raw H1 contract scanner/aggregator.py takes; never touches a
frozen file. Imports only the frozen `scanner.score._ema` (read-only) for EMA math so these
numbers are identical to the rest of the app.
"""
import pandas as pd

from scanner.score import _ema

NY_CLOSE_HOUR = 17          # daily close/open, America/New_York wall-clock
_NY_CLOSE_OFFSET = pd.Timedelta(hours=24 - NY_CLOSE_HOUR)  # 7h: shifts 17:00 NY onto the next midnight


def _trading_days(h1_df: pd.DataFrame) -> pd.DataFrame:
    """
    Internal: parse + sort h1_df ascending by time, and attach a 'trading_day' column —
    the calendar day each H1 bar belongs to under the 17:00 America/New_York close.

    trading_day = floor_to_day(ny_walltime + 7h), computed on a TZ-DROPPED naive NY
    timestamp (tz_convert first, for DST-correctness, then tz_localize(None) before the
    +7h/floor arithmetic, so the offset step itself is plain wall-clock addition, not
    absolute-time arithmetic that could itself cross a DST transition).

    A bar exactly at 17:00 NY starts the NEXT trading day (17:00 opens tomorrow's session).
    Exposed (single leading underscore) so tests can assert on trading-day labels directly —
    aggregate_d1_nyclose() below returns OHLC only, no date column, per its documented
    contract (mirrors scanner/aggregator.py's output shape).
    """
    df = h1_df.copy()
    df["dt"] = pd.to_datetime(df["datetime"], utc=True)
    df = df.sort_values("dt").set_index("dt")
    for col in ("open", "high", "low", "close"):
        df[col] = pd.to_numeric(df[col], errors="coerce")

    ny_naive = df.index.tz_convert("America/New_York").tz_localize(None)
    df["trading_day"] = (ny_naive + _NY_CLOSE_OFFSET).floor("D")
    return df


def _aggregate(tagged_df: pd.DataFrame) -> pd.DataFrame:
    """Internal: the one groupby/agg step shared by both public aggregators below —
    tagged_df is _trading_days()'s output (has a 'trading_day' column)."""
    return tagged_df.groupby("trading_day").agg(
        open=("open", "first"),
        high=("high", "max"),
        low=("low", "min"),
        close=("close", "last"),
    ).dropna(subset=["open", "close"])


def aggregate_d1_nyclose(h1_df: pd.DataFrame) -> pd.DataFrame:
    """
    Aggregate H1 -> D1 on a 17:00 America/New_York close (DST-aware); Sunday's reopen bars
    roll into Monday's trading day (DECISION-001).

    Input: the same H1 contract as scanner/aggregator.py — a DataFrame with a 'datetime'
    column plus open/high/low/close; need not be pre-sorted.

    Output: integer-indexed (0..n, oldest first), columns open/high/low/close (float).
    The incomplete current trading day is included (matches the frozen aggregator's
    behaviour) — it is simply whatever the last group happens to contain.
    """
    d1 = _aggregate(_trading_days(h1_df)).reset_index(drop=True)
    for col in ("open", "high", "low", "close"):
        d1[col] = d1[col].astype(float)
    return d1


def aggregate_d1_nyclose_dated(h1_df: pd.DataFrame) -> pd.DataFrame:
    """
    Same aggregation as aggregate_d1_nyclose() (identical grouping via _trading_days/
    _aggregate — no duplicated boundary math), but keeps the trading-day key as a 'date'
    column instead of discarding it. For Task 1b's persisted store (scanner/extend/d1_store.py),
    which needs the date to merge/append correctly across scans.

    Output columns: date, open, high, low, close (date first, oldest row first).
    """
    d1 = _aggregate(_trading_days(h1_df)).reset_index().rename(columns={"trading_day": "date"})
    for col in ("open", "high", "low", "close"):
        d1[col] = d1[col].astype(float)
    return d1[["date", "open", "high", "low", "close"]]


def with_emas(d1_df: pd.DataFrame) -> pd.DataFrame:
    """Copy of d1_df with ema50/ema200 columns, via the frozen scanner.score._ema (Rule #1) —
    span 50/200, adjust=False, identical math to the rest of the app."""
    out = d1_df.copy()
    out["ema50"] = _ema(out["close"], 50)
    out["ema200"] = _ema(out["close"], 200)
    return out
