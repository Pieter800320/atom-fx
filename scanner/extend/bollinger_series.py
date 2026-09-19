"""
ATOM FX — standard-Bollinger %B / BandWidth time series  (EXTEND)

The second half of the 4-indicator glance panel (Design §19.4b). RSI and MACD
shipped first (`momentum_series.py`); this module supplies the other two —
**stock-standard 20-period %B** and **BandWidth as a numeric series** — per pair,
per D1/H4/H1.

Why a separate module rather than extending `bb_touch.py`:

`bb_touch.py` is the BB **touch alert's** own band math, deliberately parameterised
12-period and D1-only — Pieter specified 12 explicitly, against the conventional
20 (see that file's own doc comment and Signals Roadmap §5). That parameter choice
belongs to the alert, and the alert's numbers must not move because a chart wanted
a different period. This module is the opposite job: the textbook 20-period read
every platform draws, so a value here can be compared against LiteFinance directly.
The two coexist on purpose — never "unify" them by re-parameterising `bb_touch.py`.

Both series come from one pass over the same closes:
- **%B** = (close − lower) / (upper − lower) × 100, plus its own 20-period SMA as a
  signal line (same period as the bands it derives from, the same convention
  `bb_touch.PCTB_SIGNAL_PERIOD` already follows). Not clamped to 0–100 — a real
  "walk along the band" pierces past either end; only the chart clamps for display.
- **BandWidth** = (upper − lower) / middle × 100. Exactly the formula
  `bb_touch.compute_bb_d1` already uses for its single-value `width_pct`, kept as a
  series instead of collapsed to today's number plus an expanding/converging word.
- **squeeze** — John Bollinger's own published definition: BandWidth at its lowest
  reading in the trailing `SQUEEZE_LOOKBACK` bars. Pieter's explicit call
  (2026-09-18) to use the standard rather than invent a percentile cut-off this
  project has no data to tune yet — the same caution `bb_touch.WIDTH_TREND_THRESHOLD`
  flags about its own first-pass numbers.

Bar conventions mirror `momentum_series.py` exactly: D1 on `_d1_ny_close()`'s
17:00-New-York session bars (the boundary retail platforms close D1 on), H4/H1 on
the frozen aggregator's own bars, dates via `tf_dates.py`. See that module's doc
comment for why D1 differs and H4/H1 deliberately do not.

Rule #1: reads frozen OHLCV only, never modifies it, never touches a frozen file or
a frozen calculation. New additive `signals.json` keys only. Same EXTEND-tier
standing as `bb_touch.py`'s own band math, which this deliberately does not reuse.
"""
import pandas as pd

from scanner.extend.bb_touch import _d1_ny_close
from scanner.extend import tf_dates

BB_PERIOD = 20          # the stock-standard period, NOT bb_touch's alert-specific 12
BB_SIGMA = 2.0
PCTB_SIGNAL_PERIOD = 20  # matches BB_PERIOD by construction, as bb_touch's own 12 does

# Bollinger's published Squeeze: the lowest BandWidth in the last 125 bars (~6 months
# of D1). Applied per timeframe against that timeframe's own bars, so H4/H1 each get
# 125 of their own. Pieter's call, 2026-09-18 — the standard definition, not a
# threshold tuned against data this project doesn't have yet.
SQUEEZE_LOOKBACK = 125

# Points kept for the chart, oldest-first — same display cap momentum_series uses, so
# the four glance-panel charts share one X-axis depth.
#
# Note on D1 specifically: 5000 H1 bars is only ~208 D1 bars (`config.TF_BARS`' own
# comment), so a 125-bar rolling minimum leaves ~84 bars able to carry a squeeze
# verdict at all. The oldest few points of a 90-point D1 window therefore always read
# squeeze=False — not a miss, just "not enough history behind that bar to judge."
# Fail-quiet, the same convention every other lookback metric here uses.
SERIES_LOOKBACK = 90

_EMPTY = {"dates": [], "pctb": [], "pctb_sma": [], "bandwidth": [], "squeeze": []}


def _series_for(close, dates_full=None) -> dict:
    """One timeframe's %B + BandWidth series from that timeframe's closes.

    Returns the `_EMPTY` shape when there isn't enough history to form even one band
    reading — same "no read yet" convention `compute_bb_d1` returning None expresses.
    """
    if close is None:
        return dict(_EMPTY)
    # Not dropna'd — kept index-aligned with `dates_full` so the two can be matched by
    # length below, exactly as `compute_bb_d1` keeps its own `closes` aligned with the
    # `dates` list it's handed. The NaN-tolerance is in the rolling math and the
    # `pctb.dropna()` alignment step further down, not in a reindex here.
    closes = pd.Series(close).astype(float).reset_index(drop=True)
    if closes.count() < BB_PERIOD + PCTB_SIGNAL_PERIOD:
        return dict(_EMPTY)

    sma = closes.rolling(BB_PERIOD).mean()
    std = closes.rolling(BB_PERIOD).std()
    upper = sma + BB_SIGMA * std
    lower = sma - BB_SIGMA * std

    pctb = (closes - lower) / (upper - lower) * 100
    pctb_sma = pctb.rolling(PCTB_SIGNAL_PERIOD).mean()
    bandwidth = (upper - lower) / sma * 100

    # A bar is a squeeze when its own BandWidth is the minimum of the trailing
    # SQUEEZE_LOOKBACK bars (inclusive). min_periods is strict on purpose: a bar
    # without a full lookback behind it cannot be called a 125-bar low, so it reads
    # False rather than being judged against a shorter window it never earned.
    rolling_min = bandwidth.rolling(SQUEEZE_LOOKBACK, min_periods=SQUEEZE_LOOKBACK).min()
    squeeze = (bandwidth <= rolling_min) & rolling_min.notna()

    # %B and BandWidth share the band math, so they go NaN on exactly the same leading
    # rows; pctb_sma drops a further PCTB_SIGNAL_PERIOD-1. Align every series to the
    # shortest of them so one `dates` list labels all four with no off-by-one — same
    # alignment momentum_series._series_for does across RSI vs MACD.
    pctb_valid = pctb.dropna()
    n = min(len(pctb_valid), SERIES_LOOKBACK)
    idx = pctb_valid.index[-n:]

    dates: list[str] = []
    if dates_full is not None and len(dates_full) == len(closes):
        dates = [str(d) for d in pd.Series(list(dates_full), index=closes.index).loc[idx]]

    return {
        "dates": dates,
        "pctb": [round(float(v), 2) for v in pctb.loc[idx]],
        # Right-aligns under pctb's own tail, shorter by its smoothing window — the
        # exact contract `PercentBOscillator` already draws bb_d1's signal line under.
        "pctb_sma": [round(float(v), 2) for v in pctb_sma.loc[idx].dropna()],
        "bandwidth": [round(float(v), 4) for v in bandwidth.loc[idx]],
        "squeeze": [bool(v) for v in squeeze.loc[idx]],
    }


def bollinger_series_for_pair(tfs: dict, raw_h1_df=None) -> dict:
    """tfs = ohlcv["EURUSD"] = {"d1": df, "h4": df, "h1": df}. Returns
    {"d1": {...}, "h4": {...}, "h1": {...}}, each shaped like _EMPTY above.

    raw_h1_df, when given, is that pair's own raw H1 fetch (scan_h1.py's
    raw_ohlcv[key]) — used to rebuild D1 on the NY-session boundary via
    `_d1_ny_close()` and to recover H4/H1 dates the frozen aggregator discards.
    Falls back to the frozen UTC-midnight `ohlcv[key]["d1"]` and no dates when
    unavailable (tests/callers without H1 data on hand); production always has it.
    Deliberately the same signature and fallback behaviour as
    `momentum_series.momentum_series_for_pair` — the two are called side by side.
    """
    out = {}
    for tf in ("d1", "h4", "h1"):
        if raw_h1_df is not None:
            if tf == "d1":
                d1_ny = _d1_ny_close(raw_h1_df)
                close, dates_full = d1_ny["close"], d1_ny["date"]
            elif tf == "h4":
                df = tfs.get(tf) if tfs else None
                close = df["close"] if df is not None else None
                dates_full = tf_dates.h4_dates(raw_h1_df, trading_clock=(df is not None and df.attrs.get("clock") == "ny"))
            else:
                df = tfs.get(tf) if tfs else None
                close = df["close"] if df is not None else None
                dates_full = tf_dates.h1_dates(raw_h1_df)
        else:
            df = tfs.get(tf) if tfs else None
            close = df["close"] if df is not None else None
            dates_full = None
        out[tf] = _series_for(close, dates_full)
    return out


def attach_bollinger_series(pairs_out: dict, ohlcv: dict, raw_ohlcv: dict | None = None,
                             prev_pairs: dict | None = None) -> None:
    """Mutate pairs_out in place, adding a 'bollinger_series' sub-key to each pair
    block — same pattern `attach_momentum_series`/`attach_bb_d1` already use.

    2026-09-18 (bug found live in production, same day M15 shipped) — see
    `attach_momentum_series`'s own doc comment (`momentum_series.py`) for the full story:
    this function REPLACES the whole bollinger_series dict every call (d1/h4/h1 only),
    which was silently erasing scan_m15.py's separately-written "m15" key on every
    subsequent scan_h1.py run. `prev_pairs` (optional — `prev.get("pairs")`) carries that
    key forward untouched; this function still never computes or touches M15 data itself."""
    for key, block in pairs_out.items():
        raw_h1_df = raw_ohlcv.get(key) if raw_ohlcv else None
        series = bollinger_series_for_pair(ohlcv.get(key), raw_h1_df)
        prev_m15 = ((prev_pairs or {}).get(key) or {}).get("bollinger_series", {}).get("m15")
        if prev_m15:
            series["m15"] = prev_m15
        block["bollinger_series"] = series
