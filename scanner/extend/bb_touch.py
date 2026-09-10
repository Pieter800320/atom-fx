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


def compute_bb_d1(d1_df) -> dict | None:
    """
    d1_df : a pair's D1 OHLC dataframe (frozen aggregator output — needs high/low/close).
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

    return {
        "touching": touching,
        "sma": round(cur_sma, 6),
        "upper": round(cur_upper, 6),
        "lower": round(cur_lower, 6),
        "width_pct": width_pct,
        "width_trend": width_trend,
        "pctb": [round(v, 2) for v in pctb.dropna().tail(PCTB_LINE_BARS)],
        "pctb_sma": [round(v, 2) for v in pctb_sma.dropna().tail(PCTB_LINE_BARS)],
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
    lines = [b["pctb"] for b in (block.get("bb_d1") for block in pairs_out.values()) if b and b.get("pctb")]
    signals = [b["pctb_sma"] for b in (block.get("bb_d1") for block in pairs_out.values()) if b and b.get("pctb_sma")]

    def _pointwise_mean(series_list: list[list[float]]) -> list[float]:
        if not series_list:
            return []
        n = min(len(s) for s in series_list)
        trimmed = [s[-n:] for s in series_list]
        return [round(sum(vals) / len(vals), 2) for vals in zip(*trimmed)]

    return {"line": _pointwise_mean(lines), "signal": _pointwise_mean(signals)}


def attach_bb_d1(pairs_out: dict, ohlcv: dict) -> None:
    """Mutate pairs_out in place, adding a 'bb_d1' sub-key to each pair block — same pattern
    structure_expose.py's attach_structure() already uses."""
    for key, block in pairs_out.items():
        d1_df = (ohlcv.get(key) or {}).get("d1")
        block["bb_d1"] = compute_bb_d1(d1_df)
