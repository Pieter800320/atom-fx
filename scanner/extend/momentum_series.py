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

Rule #1: `_rsi`/`_macd` are FROZEN (`scanner/score.py`, unedited). No new
formula, no re-derived trading number — a pure read of more of an existing
frozen function's own output.
"""
from scanner.score import _rsi, _macd

# Bars of history to keep for the chart, oldest-first. Comfortably short of a
# %B chart's ~56 D1 bars — RSI/MACD are shown at all three timeframes here (not
# D1-only), so keeping each series compact matters more than reaching far back.
SERIES_LOOKBACK = 50

_EMPTY = {"rsi": [], "macd_line": [], "macd_signal": [], "macd_histogram": []}


def _series_for(close) -> dict:
    if close is None or len(close.dropna()) < 30:
        return dict(_EMPTY)
    rsi_s = _rsi(close).dropna()
    macd_line, macd_signal, macd_hist = _macd(close)
    macd_line = macd_line.dropna()
    macd_signal = macd_signal.dropna()
    macd_hist = macd_hist.dropna()
    return {
        "rsi": [round(float(v), 2) for v in rsi_s.tail(SERIES_LOOKBACK)],
        "macd_line": [round(float(v), 6) for v in macd_line.tail(SERIES_LOOKBACK)],
        "macd_signal": [round(float(v), 6) for v in macd_signal.tail(SERIES_LOOKBACK)],
        "macd_histogram": [round(float(v), 6) for v in macd_hist.tail(SERIES_LOOKBACK)],
    }


def momentum_series_for_pair(tfs: dict) -> dict:
    """tfs = ohlcv["EURUSD"] = {"d1": df, "h4": df, "h1": df}. Returns
    {"d1": {...}, "h4": {...}, "h1": {...}}, each shaped like _EMPTY above."""
    out = {}
    for tf in ("d1", "h4", "h1"):
        df = tfs.get(tf) if tfs else None
        close = df["close"] if df is not None else None
        out[tf] = _series_for(close)
    return out


def attach_momentum_series(pairs_out: dict, ohlcv: dict) -> None:
    """Mutate pairs_out in place, adding a 'momentum_series' sub-key to each pair block."""
    for key, block in pairs_out.items():
        block["momentum_series"] = momentum_series_for_pair(ohlcv.get(key))
