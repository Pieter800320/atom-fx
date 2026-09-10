"""
ATOM FX — Bollinger Band D1 touch detection  (EXTEND, Signals Roadmap §5)

Pieter's own redesign (2026-09-09) of the original Phase 4 plan: no auto-confirmation, no
midline-retest logic, no persisted state file. A wick touch of the band IS the whole signal —
judging whether a reversal is actually developing is a manual review process (the Watchlist,
app-side), not something this module tries to score or gate. He doesn't yet know which
thresholds separate a good touch from a bad one, so nothing here hardcodes a pass/fail; it only
computes the raw band state every scan, per pair, per §5's own doc comment.

12-period SMA ± 2σ on D1 closes — Pieter specified 12-period explicitly, not the 20-period
default the original `fx_technical/scanner/bb.py` port used.

width_trend is the one genuinely new idea here: today's band width vs. width WIDTH_LOOKBACK
bars ago. Pieter's own framing — a touch arriving via *expanding* bands (a breakout candle)
argues against fading it; a touch on *narrow/converging* bands is a better reversal candidate.
WIDTH_LOOKBACK/WIDTH_TREND_THRESHOLD are a first-pass approximation, tune once real data exists.

Rule #1: reads frozen OHLCV only, never modifies it. Pure function, no state, no I/O.
"""
import pandas as pd

BB_PERIOD = 12
BB_SIGMA = 2.0
WIDTH_LOOKBACK = 5           # D1 bars back to compare band width against
WIDTH_TREND_THRESHOLD = 0.10  # +/-10% width change counts as expanding/converging, else flat

# %B (2026-09-10, Pieter's ask) — (close - lower) / (upper - lower) * 100, using the SAME rolling
# sma/upper/lower this file already builds for the touch/width read above; not clamped to 0-100
# here (a real, meaningful "walk along the band" pierces past 0 or 100 — the chart that draws this
# clamps for display, the number itself stays real). PCTB_SIGNAL_PERIOD matches BB_PERIOD by
# construction (a %B "signal line" is conventionally the same period as the bands it's derived
# from), not a coincidence needing its own justification.
PCTB_SIGNAL_PERIOD = 12
PCTB_LINE_BARS = 56          # matches potential_config.SPARK_BARS's own convention


def _d1_dates(h1_df) -> list[str]:
    """
    2026-09-10 (Pieter's ask, dates on the %B charts) — `scanner/aggregator.py` (frozen, never
    edited) returns D1 bars integer-indexed with the real dates stripped by design (its own
    docstring: "Integer-indexed (0..n), oldest first"). This independently mirrors its
    `aggregate_d1()` exact resample parameters (D, closed=left, label=left, dropna on open/close)
    against the SAME raw H1 fetch scan_h1.py already has (`raw_ohlcv`), to recover the dates —
    computes no price/indicator value itself, pure date-bucketing, Rule #1 safe. Row count/order
    must match the frozen d1_df exactly for `compute_bb_d1`'s own alignment check below to pass.
    """
    df = h1_df.copy()
    df["dt"] = pd.to_datetime(df["datetime"], utc=True)
    df = df.sort_values("dt").set_index("dt")
    for col in ("open", "close"):
        df[col] = pd.to_numeric(df[col], errors="coerce")
    d1 = df.resample("D", closed="left", label="left").agg(open=("open", "first"), close=("close", "last"))
    d1 = d1.dropna(subset=["open", "close"])
    return [ts.strftime("%Y-%m-%d") for ts in d1.index]


def compute_bb_d1(d1_df, dates: list[str] | None = None) -> dict | None:
    """
    d1_df : a pair's D1 OHLC dataframe (frozen aggregator output — needs high/low/close).
    dates : optional, from _d1_dates() — one date per d1_df row, same order. Only attached to
            pctb/pctb_sma if its length matches d1_df exactly (fail-quiet to no dates rather than
            risk a silently misaligned one).
    Returns None if there isn't enough history yet (< BB_PERIOD + WIDTH_LOOKBACK bars) — same
    "no read yet" convention every other percentile/lookback metric in this codebase uses.
    """
    if d1_df is None or len(d1_df) < BB_PERIOD + WIDTH_LOOKBACK:
        return None

    closes = d1_df["close"].astype(float)
    sma = closes.rolling(BB_PERIOD).mean()
    std = closes.rolling(BB_PERIOD).std()
    upper = sma + BB_SIGMA * std
    lower = sma - BB_SIGMA * std

    cur_sma = float(sma.iloc[-1])
    cur_upper = float(upper.iloc[-1])
    cur_lower = float(lower.iloc[-1])
    last_high = float(d1_df["high"].iloc[-1])
    last_low = float(d1_df["low"].iloc[-1])

    # Wick touch — a candle can pierce a band with its high/low while closing back inside it;
    # that's still a real touch (Pieter: "a candle wick touch is fine").
    if last_high >= cur_upper:
        touching = "upper"
    elif last_low <= cur_lower:
        touching = "lower"
    else:
        touching = "none"

    width = upper - lower
    cur_width = float(width.iloc[-1])
    past_width = float(width.iloc[-1 - WIDTH_LOOKBACK])
    width_pct = round(cur_width / cur_sma * 100, 3) if cur_sma else 0.0

    if past_width <= 0:
        width_trend = "flat"
    else:
        change = (cur_width - past_width) / past_width
        if change > WIDTH_TREND_THRESHOLD:
            width_trend = "expanding"
        elif change < -WIDTH_TREND_THRESHOLD:
            width_trend = "converging"
        else:
            width_trend = "flat"

    pctb = (closes - lower) / (upper - lower) * 100
    pctb_sma = pctb.rolling(PCTB_SIGNAL_PERIOD).mean()
    pctb_valid = pctb.dropna().tail(PCTB_LINE_BARS)
    pctb_sma_valid = pctb_sma.dropna().tail(PCTB_LINE_BARS)

    pctb_dates: list[str] = []
    if dates is not None and len(dates) == len(closes):
        pctb_dates = pd.Series(dates, index=closes.index).loc[pctb_valid.index].tolist()

    return {
        "touching": touching,
        "sma": round(cur_sma, 6),
        "upper": round(cur_upper, 6),
        "lower": round(cur_lower, 6),
        "width_pct": width_pct,
        "width_trend": width_trend,
        "pctb": [round(v, 2) for v in pctb_valid],
        "pctb_sma": [round(v, 2) for v in pctb_sma_valid],
        "pctb_dates": pctb_dates,
    }


def compute_board_percent_b(pairs_out: dict) -> dict:
    """
    Market-wide %B (2026-09-10) — the pointwise mean of every pair's own %B / %B-signal line,
    across whichever pairs currently have a bb_d1 read. Reads what attach_bb_d1() already wrote
    onto pairs_out; no new band math, single source of truth stays compute_bb_d1() above.

    Pairs' lines can differ in length (different D1 history depth); trimmed to the shortest
    common length (right-aligned — most recent bars) so the average stays aligned across pairs.
    Returns {"line": [...], "signal": [...]} (empty lists if no pair has a bb_d1 read yet).
    """
    bb_blocks = [block.get("bb_d1") for block in pairs_out.values()]
    lines = [b["pctb"] for b in bb_blocks if b and b.get("pctb")]
    signals = [b["pctb_sma"] for b in bb_blocks if b and b.get("pctb_sma")]
    date_lists = [b["pctb_dates"] for b in bb_blocks if b and b.get("pctb_dates")]

    def _pointwise_mean(series_list: list[list[float]]) -> list[float]:
        if not series_list:
            return []
        n = min(len(s) for s in series_list)
        trimmed = [s[-n:] for s in series_list]
        return [round(sum(vals) / len(vals), 2) for vals in zip(*trimmed)]

    line = _pointwise_mean(lines)
    signal = _pointwise_mean(signals)
    # All 12 pairs' D1 bars cover the same trailing UTC calendar days — the longest available
    # per-pair date list, trimmed to the board line's own final length, stands in for all of them
    # rather than re-deriving a separate board-wide date series from scratch.
    dates = max(date_lists, key=len)[-len(line):] if date_lists and line else []

    return {"line": line, "signal": signal, "dates": dates}


def attach_bb_d1(pairs_out: dict, ohlcv: dict, raw_ohlcv: dict | None = None) -> None:
    """Mutate pairs_out in place, adding a 'bb_d1' sub-key to each pair block — same pattern
    structure_expose.py's attach_structure() already uses. raw_ohlcv (optional, scan_h1.py's own
    pre-aggregation H1 fetch — see _d1_dates()'s own doc comment) lets compute_bb_d1 attach real
    D1 bar dates alongside pctb; omitted, bb_d1 still computes, just without pctb_dates."""
    for key, block in pairs_out.items():
        d1_df = (ohlcv.get(key) or {}).get("d1")
        dates = _d1_dates(raw_ohlcv[key]) if raw_ohlcv and key in raw_ohlcv else None
        block["bb_d1"] = compute_bb_d1(d1_df, dates)
