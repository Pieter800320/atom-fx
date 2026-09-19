"""
ATOM FX research — H4 aggregation that KEEPS timestamps and drops closed-market bars.

EXTEND tier, research use only. The app's own scanner.aggregator.aggregate_h4 (frozen, never edited)
groups H1 into UTC 4-hour blocks but returns an integer-indexed frame with no timestamps and no
FX-week filter. A backtest needs both, so this module reproduces its grouping and adds them:

  1. Drop bars outside the real FX week [Sunday 17:00 ET, Friday 17:00 ET) with the repo's own
     DECISION-007 rule (scanner.extend.agg_nyclose._trading_days) — Twelvedata emits flat,
     market-closed weekend bars, and the D1 pipeline already removes them.
  2. Group into 4-hour blocks, keeping the block start time.

Two alignments (the choice is part of the Experiment 3 pre-registration):
  'utc' — the APP's convention: blocks 00/04/08/12/16/20 UTC (scanner.aggregator.aggregate_h4).
          After the FX-week filter the Sunday reopen and the Friday close leave short STUB blocks
          (1-3 H1 bars); they are kept as bars, exactly as the app's aggregator keeps them.
  'ny'  — TradingView's FX H4 convention: blocks start 17:00, 21:00, 01:00, 05:00, 09:00, 13:00
          America/New_York wall-clock, so every block is a full 4 hours and 6 blocks make one
          17:00-NY-close trading day (consistent with the D1 store). No stubs.

Output columns: datetime (block start; UTC-naive for 'utc', NY-wall-clock-naive for 'ny'), open,
high, low, close, n_h1 (H1 bars in the block), trading_day (the NY-close trading-day label the block
belongs to — used to pick the COT week; the Sunday reopen rolls into Monday).
"""
import pandas as pd

from scanner.extend.agg_nyclose import _NY_CLOSE_OFFSET, _trading_days

ALIGNMENTS = ("utc", "ny")


def aggregate_h4_ts(h1_df: pd.DataFrame, alignment: str = "utc") -> pd.DataFrame:
    if alignment not in ALIGNMENTS:
        raise ValueError(f"alignment must be one of {ALIGNMENTS}")
    df = _trading_days(h1_df)                       # UTC tz-aware index, FX-week filtered, trading_day
    if df.empty:
        return pd.DataFrame(columns=["datetime", "open", "high", "low", "close", "n_h1", "trading_day"])

    if alignment == "utc":
        block = df.index.floor("4h").tz_localize(None)
    else:
        ny_naive = df.index.tz_convert("America/New_York").tz_localize(None)
        block = (ny_naive + _NY_CLOSE_OFFSET).floor("4h") - _NY_CLOSE_OFFSET

    g = df.assign(block=block).groupby("block")
    out = g.agg(open=("open", "first"), high=("high", "max"), low=("low", "min"),
                close=("close", "last"), n_h1=("close", "size"), trading_day=("trading_day", "last"))
    out = out.dropna(subset=["open", "close"]).reset_index().rename(columns={"block": "datetime"})
    for col in ("open", "high", "low", "close"):
        out[col] = out[col].astype(float)
    return out[["datetime", "open", "high", "low", "close", "n_h1", "trading_day"]]
