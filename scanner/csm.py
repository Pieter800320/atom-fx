"""
FX Signal Board — Currency Strength Model
Ported from Forex1212 scanner/csm.py for calculation coherence.

D1 CSM : ATR-normalised 14-bar return, D1×0.7 + H4×0.3 blend, 18 pairs
H4 CSM : ATR-normalised 5-bar H4 return, H4×0.8 + H1×0.2 (if H1 available), 18 pairs

These parameters exactly match Forex1212 so both dashboards show the same values, except
STRENGTH_PAIRS itself — see its own comment below (2026-09-10, Pieter's sign-off).
"""
import numpy as np
import pandas as pd
from scanner.config import CURRENCIES

# ── Parameters — match Forex1212 exactly ─────────────────────────────────────
LOOKBACK   = 14     # D1 CSM: 14-bar price return window
ATR_PERIOD = 14     # ATR smoothing period
D1_WEIGHT  = 0.7   # D1 return weight in combined score
H4_WEIGHT  = 0.3   # H4 return weight in combined score

H4_LOOKBACK = 5    # H4 CSM: 5 H4 bars ≈ 20 hours
H1_LOOKBACK = 8    # H1 component lookback in H4 CSM
H4_CSM_W    = 0.8
H1_CSM_W    = 0.2

# 2026-09-10 (Pieter's sign-off) — was the verbatim 16-pair Forex1212 STRENGTH_PAIRS set,
# which silently excluded EUR/JPY and GBP/JPY: both are fully-tracked pairs in this app
# (config.PAIRS, already fetched every scan — zero new API cost) but never fed CSM or
# currency breadth for EUR/GBP/JPY. No comment in the original ever explained the omission,
# and it's the specific gap that made JPY's own H4 breadth read (2/4 that day) blind to two
# of its six real crosses. Appearance counts per currency: USD 7 (unchanged), AUD 5
# (unchanged), CHF/CAD/NZD 3 each (unchanged), GBP 4->5, JPY 4->6, EUR 3->4.
STRENGTH_PAIRS = [
    "EUR/USD", "GBP/USD", "USD/JPY", "USD/CHF",
    "AUD/USD", "USD/CAD", "NZD/USD",
    "AUD/JPY", "NZD/JPY", "CAD/JPY",
    "EUR/GBP", "EUR/CHF", "GBP/CHF",
    "AUD/NZD", "AUD/CAD", "GBP/AUD",
    "EUR/JPY", "GBP/JPY",
]


# ── ATR helper ────────────────────────────────────────────────────────────────
def _atr(df: pd.DataFrame, period: int = ATR_PERIOD) -> float:
    """Simple rolling ATR — matches Forex1212 _atr14() which sums last 14 TRs."""
    h = df["high"].astype(float)
    l = df["low"].astype(float)
    c = df["close"].astype(float)
    tr = pd.concat([
        h - l,
        (h - c.shift()).abs(),
        (l - c.shift()).abs(),
    ], axis=1).max(axis=1)
    val = tr.iloc[-period:].mean()
    return float(val) if not np.isnan(val) else 0.0


# ── ATR-normalised return ─────────────────────────────────────────────────────
def _adj_return(df: pd.DataFrame, lookback: int = LOOKBACK) -> float | None:
    """
    ATR-normalised percentage return over `lookback` bars.
    Matches Forex1212: (close[-1] - close[-lookback-1]) / close[-lookback-1] * 100 / ATR
    """
    if df is None or len(df) < lookback + ATR_PERIOD + 1:
        return None
    c = df["close"].astype(float)
    ret = (c.iloc[-1] - c.iloc[-(lookback + 1)]) / c.iloc[-(lookback + 1)] * 100
    atr = _atr(df)
    return ret / atr if atr > 0 else None


# ── D1 CSM ────────────────────────────────────────────────────────────────────
def _raw_d1(ohlcv: dict) -> dict:
    """ohlcv keys like "EURUSD" → {"d1": df, "h4": df}. D1 (70%) + H4 (30%) blend."""
    raw = {c: [] for c in CURRENCIES}

    for pair in STRENGTH_PAIRS:
        key   = pair.replace("/", "")
        base  = pair.split("/")[0]
        quote = pair.split("/")[1]

        d1_ret = _adj_return(ohlcv.get(key, {}).get("d1"))
        h4_ret = _adj_return(ohlcv.get(key, {}).get("h4"))

        if d1_ret is None:
            continue

        combined = (D1_WEIGHT * d1_ret + H4_WEIGHT * h4_ret
                    if h4_ret is not None else d1_ret)

        if base in raw:
            raw[base].append(combined)
        if quote in raw:
            raw[quote].append(-combined)

    return raw


def compute_csm_d1(ohlcv: dict) -> dict:
    """D1 currency strength (0–100, 100=strongest). See _raw_d1 for the underlying blend."""
    normalised, _spread = _normalise(_raw_d1(ohlcv))
    return normalised


# ── H4 CSM ────────────────────────────────────────────────────────────────────
def _raw_h4(ohlcv: dict) -> dict:
    """H4 5-bar (80%) + H1 8-bar (20%) blend."""
    raw = {c: [] for c in CURRENCIES}

    for pair in STRENGTH_PAIRS:
        key   = pair.replace("/", "")
        base  = pair.split("/")[0]
        quote = pair.split("/")[1]

        h4_ret = _adj_return(ohlcv.get(key, {}).get("h4"), lookback=H4_LOOKBACK)
        h1_ret = _adj_return(ohlcv.get(key, {}).get("h1"), lookback=H1_LOOKBACK)

        if h4_ret is None:
            continue

        combined = (H4_CSM_W * h4_ret + H1_CSM_W * h1_ret
                    if h1_ret is not None else h4_ret)

        if base in raw:
            raw[base].append(combined)
        if quote in raw:
            raw[quote].append(-combined)

    return raw


def compute_csm_h4(ohlcv: dict) -> dict:
    """H4 currency strength (0–100, 100=strongest). See _raw_h4 for the underlying blend."""
    normalised, _spread = _normalise(_raw_h4(ohlcv))
    return normalised


# ── Normalise to 0-100 ────────────────────────────────────────────────────────
def _normalise(raw: dict) -> tuple[dict, float]:
    """
    Returns (normalised 0-100 dict, raw_spread).

    raw_spread is the PRE-normalisation gap between the strongest and weakest currency's
    average ATR-normalised return — 2026-09-06 addition (Rule #1 sign-off), not previously
    exposed. Min-max rescaling always stretches whatever gap exists to fill exactly 0-100
    every scan, so the 0-100 values alone can't tell "today's real dispersion is wide" from
    "today's basket is thin and got stretched anyway" — that's what raw_spread is for. See
    `scanner/extend/csm_dispersion.py` for how it's turned into a trustworthy signal (a
    fixed floor on raw_spread doesn't work — calibration on synthetic data showed its
    distribution looks the same whether there's real cross-currency signal or none, because
    ATR-normalisation is deliberately volatility-invariant; it needs percentile-ranking
    against its own recent history instead, the same idea atr_percentile() already uses).
    """
    avg        = {c: float(np.mean(v)) if v else 0.0 for c, v in raw.items()}
    vals       = list(avg.values())
    min_v      = min(vals)
    max_v      = max(vals)
    raw_spread = max_v - min_v
    spread     = raw_spread if raw_spread != 0 else 1.0
    normalised = {c: round((avg[c] - min_v) / spread * 100, 1) for c in CURRENCIES}
    return normalised, raw_spread


H1_ONLY_LOOKBACK = 6   # H1 CSM: 6 H1 bars ≈ 6 hours
H1_ONLY_W        = 1.0 # pure H1, no blend


# ── H1 CSM ────────────────────────────────────────────────────────────────────
def _raw_h1(ohlcv: dict) -> dict:
    """Pure H1, no blend."""
    raw = {c: [] for c in CURRENCIES}

    for pair in STRENGTH_PAIRS:
        key   = pair.replace("/", "")
        base  = pair.split("/")[0]
        quote = pair.split("/")[1]

        h1_ret = _adj_return(ohlcv.get(key, {}).get("h1"), lookback=H1_ONLY_LOOKBACK)

        if h1_ret is None:
            continue

        if base in raw:
            raw[base].append(h1_ret)
        if quote in raw:
            raw[quote].append(-h1_ret)

    return raw


def compute_csm_h1(ohlcv: dict) -> dict:
    """H1 currency strength (0–100, 100=strongest). See _raw_h1 for the underlying return."""
    normalised, _spread = _normalise(_raw_h1(ohlcv))
    return normalised


# ── Public entry point ────────────────────────────────────────────────────────
def compute_csm(ohlcv: dict) -> dict:
    """
    Compute D1, H4 and H1 CSM.
    Returns {"d1": {cur: 0-100}, "h4": {cur: 0-100}, "h1": {cur: 0-100},
             "dispersion": {"d1": float, "h4": float, "h1": float}}

    "dispersion" (2026-09-06 addition) is additive — every existing key/value is unchanged
    byte-for-byte, this only adds a new one. See _normalise's own doc comment for what it is.
    """
    d1_norm, d1_spread = _normalise(_raw_d1(ohlcv))
    h4_norm, h4_spread = _normalise(_raw_h4(ohlcv))
    h1_norm, h1_spread = _normalise(_raw_h1(ohlcv))
    return {
        "d1": d1_norm,
        "h4": h4_norm,
        "h1": h1_norm,
        "dispersion": {
            "d1": round(d1_spread, 4),
            "h4": round(h4_spread, 4),
            "h1": round(h1_spread, 4),
        },
    }
