"""
ATOM FX — RSI / MACD time series exposure  (EXTEND)

`score.py::score_pair` already computes RSI (Wilder, 14-period) and MACD (12/26/9
EMA) as part of its own frozen scoring, for every pair at every timeframe — but
only the LATEST bar's value ever leaves that function (its own "raw" dict, used
to build the 5-state score). A chart needs a short history, not one number.

This module calls the SAME two frozen functions (`_rsi`, `_macd` — imported
read-only, never reimplemented) across the close-price history already fetched
this scan, to produce a short oldest-first SERIES per pair per timeframe. Same
exact formula the score itself is built on; this only asks for more of its
output, the way `bb_touch.py`'s own D1 %B series does for Bollinger Bands.

2026-09-18 (Pieter's catch, "skewed vs LiteFinance") — D1 specifically is now
run on `_d1_ny_close()`'s 17:00-New-York-session D1 bars, not the frozen
aggregator's `ohlcv[key]["d1"]` (UTC-midnight boundary). Same root cause as the
%B fix (`bb_touch.py`'s own doc comment): the frozen aggregator's own D1 is
"acceptable for trend/momentum signals" internally, but wrong for a D1 chart
compared directly against a retail platform's D1 close. H4/H1 are left as the
frozen aggregator's own bars — H4 already matches the data vendor's own H4
boundary exactly (`aggregator.py`'s own comment), and any remaining H4/H1 gap
against a specific broker is a different-data-vendor basis difference, not a
bucketing bug — there's no universal "correct" H4/H1 boundary to re-derive.

Rule #1: `_rsi`/`_macd` are FROZEN (`scanner/score.py`, unedited). No new
formula, no re-derived trading number — a pure read of more of an existing
frozen function's own output, on the same NY-session D1 bars `bb_touch.py`
already establishes as the correct D1 convention for this app.

2026-09-18 (2nd, Pieter's restyle ask) — each series now carries its own
`dates` (oldest-first, one per RSI/MACD point), from `tf_dates.py`'s
independent H4/H1 label recovery plus `_d1_ny_close()`'s own D1 dates.
`SERIES_LOOKBACK` 50 -> 90 (only a display cap, not a fetch limit — 5000 H1
bars already covers ~208 D1 / ~1250 H4 / 5000 H1 bars, see `config.TF_BARS`).
"""
from scanner.score import _rsi, _macd
from scanner.extend.bb_touch import _d1_ny_close
from scanner.extend import tf_dates

# Bars of history to keep for the chart, oldest-first. A display cap only —
# see the module doc comment above for how much history is actually on hand.
SERIES_LOOKBACK = 90

_EMPTY = {"dates": [], "rsi": [], "macd_line": [], "macd_signal": [], "macd_histogram": []}


def _series_for(close, dates_full=None, lookback=SERIES_LOOKBACK) -> dict:
    if close is None or len(close.dropna()) < 30:
        return dict(_EMPTY)
    rsi_s = _rsi(close).dropna()
    macd_line, macd_signal, macd_hist = _macd(close)
    macd_line = macd_line.dropna()
    macd_signal = macd_signal.dropna()
    macd_hist = macd_hist.dropna()

    # RSI drops exactly one leading row more than MACD (close.diff()'s own first NaN;
    # _macd's ewm-based inputs never produce a NaN) -- align both to the shorter one so
    # a single "dates" list can label both without an off-by-one.
    n = min(len(rsi_s), len(macd_line), lookback)

    dates = []
    if dates_full is not None and len(dates_full) == len(close):
        dates = [str(d) for d in dates_full.tail(n)]

    return {
        "dates": dates,
        "rsi": [round(float(v), 2) for v in rsi_s.tail(n)],
        "macd_line": [round(float(v), 6) for v in macd_line.tail(n)],
        "macd_signal": [round(float(v), 6) for v in macd_signal.tail(n)],
        "macd_histogram": [round(float(v), 6) for v in macd_hist.tail(n)],
    }


def momentum_series_for_pair(tfs: dict, raw_h1_df=None) -> dict:
    """tfs = ohlcv["EURUSD"] = {"d1": df, "h4": df, "h1": df}. Returns
    {"d1": {...}, "h4": {...}, "h1": {...}}, each shaped like _EMPTY above.

    raw_h1_df, when given, is that pair's own raw H1 fetch (scan_h1.py's
    raw_ohlcv[key]) — used to rebuild D1 on the NY-session boundary via
    _d1_ny_close(), same pattern attach_bb_d1() below already uses, and to
    recover H4/H1 dates the frozen aggregator itself discards (tf_dates.py).
    Falls back to the frozen UTC-midnight ohlcv[key]["d1"] and no dates at all
    when unavailable (tests/callers without H1 data on hand) — production
    always has it.
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
        out[tf] = _series_for(close, dates_full, tf_dates.lookback(tf))
    return out


def attach_momentum_series(pairs_out: dict, ohlcv: dict, raw_ohlcv: dict | None = None,
                            prev_pairs: dict | None = None) -> None:
    """Mutate pairs_out in place, adding a 'momentum_series' sub-key to each pair block.

    2026-09-18 (bug found live in production, same day M15 shipped) — this function REPLACES
    the whole momentum_series dict every call, d1/h4/h1 only. scan_m15.py (its own faster
    cadence, never invoked from here) separately writes an "m15" key into that same dict —
    every subsequent scan_h1.py run was silently erasing it, since scan_h1.py's own pairs_out
    is built fresh each run with no memory of what scan_m15.py had just added. `prev_pairs`
    (optional — the previous scan's own signals.json "pairs" block, i.e. `prev.get("pairs")`)
    is used ONLY to carry that "m15" key forward untouched; this function still never computes
    or touches M15 data itself — see scan_m15.py's own module doc comment for that."""
    for key, block in pairs_out.items():
        raw_h1_df = raw_ohlcv.get(key) if raw_ohlcv else None
        series = momentum_series_for_pair(ohlcv.get(key), raw_h1_df)
        prev_m15 = ((prev_pairs or {}).get(key) or {}).get("momentum_series", {}).get("m15")
        if prev_m15:
            series["m15"] = prev_m15
        block["momentum_series"] = series
