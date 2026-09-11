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

from scanner.config import CURRENCIES
from scanner.csm import STRENGTH_PAIRS
from scanner.extend.agg_nyclose import aggregate_d1_nyclose_dated

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


def _d1_ny_close(h1_df) -> "pd.DataFrame":
    """
    D1 aggregation on the retail-platform convention: a daily candle runs 17:00 New York ->
    next 17:00 New York, not UTC midnight. `scanner/aggregator.py` (frozen, never edited)
    deliberately uses UTC midnight instead, and says so in its own docstring: "the small
    session-boundary difference is acceptable for trend/momentum signals" — true for a coarse
    bull/bear pill, not true for %B/touch, which reads the exact daily high/low/close. During a
    fast one-directional move (confirmed 2026-09-10: USDJPY's news-driven plunge, our %B vs.
    live LiteFinance read didn't match) the up-to-7h boundary gap means our "today" bar and a
    broker's "today" bar cover different hours, which is enough to disagree on whether a band
    was actually touched.

    DECISION-006 (2026-09-11) — delegates to `scanner.extend.agg_nyclose.aggregate_d1_nyclose_dated`
    instead of re-bucketing independently. There is now exactly ONE implementation of the D1
    NY-close boundary math in this codebase (agg_nyclose); this function is a thin adapter onto
    it, not a second aggregator. The 17:00-NY boundary itself is unchanged, and so is every
    open/high/low/close value this produces — DST handling and the date LABEL are what got
    standardized: this function used to label a session by its own OPEN day
    (`(ny - 17h).date`); agg_nyclose labels the SAME session by its CLOSE day
    (`floor(ny_walltime + 7h)`, DST-aware via `tz_convert`) — one calendar day later, identical
    bars. The `date` column below is therefore now the close-day label, converted back to plain
    `datetime.date` (this function's original dtype) so the three existing call sites'
    `.astype(str)` -> "YYYY-MM-DD" behaviour is unchanged.
    """
    d1 = aggregate_d1_nyclose_dated(h1_df)
    d1["date"] = d1["date"].dt.date
    return d1


def compute_bb_d1(d1_df, dates: list[str] | None = None) -> dict | None:
    """
    d1_df : a pair's D1 OHLC dataframe (needs high/low/close) — the frozen aggregator's own D1,
            or (2026-09-10) `_d1_ny_close()`'s NY-session D1, per the caller.
    dates : optional, one date per d1_df row, same order. Only attached to
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


def _pointwise_mean(series_list: list[list[float]]) -> list[float]:
    """Shared by compute_board_percent_b and compute_currency_percent_b below — trims every
    series to the shortest common length (right-aligned, most recent bars) so an average across
    series of different length stays aligned, then averages point by point."""
    if not series_list:
        return []
    n = min(len(s) for s in series_list)
    trimmed = [s[-n:] for s in series_list]
    return [round(sum(vals) / len(vals), 2) for vals in zip(*trimmed)]


def compute_board_percent_b(pairs_out: dict) -> dict:
    """
    Market-wide %B (2026-09-10) — the pointwise mean of every pair's own %B / %B-signal line,
    across whichever pairs currently have a bb_d1 read. Reads what attach_bb_d1() already wrote
    onto pairs_out; no new band math, single source of truth stays compute_bb_d1() above.

    Deliberately NOT currency-direction aware — see compute_currency_percent_b()'s own doc
    comment for why that matters and what this one actually measures instead (a raw pairwise
    average, blind to which currency is base/quote on each pair).

    Pairs' lines can differ in length (different D1 history depth); trimmed to the shortest
    common length (right-aligned — most recent bars) so the average stays aligned across pairs.
    Returns {"line": [...], "signal": [...]} (empty lists if no pair has a bb_d1 read yet).
    """
    bb_blocks = [block.get("bb_d1") for block in pairs_out.values()]
    lines = [b["pctb"] for b in bb_blocks if b and b.get("pctb")]
    signals = [b["pctb_sma"] for b in bb_blocks if b and b.get("pctb_sma")]
    date_lists = [b["pctb_dates"] for b in bb_blocks if b and b.get("pctb_dates")]

    line = _pointwise_mean(lines)
    signal = _pointwise_mean(signals)
    # All 12 pairs' D1 bars cover the same trailing NY-session trading days (_d1_ny_close, same
    # 17:00 NY boundary for every pair) — the longest available per-pair date list, trimmed to
    # the board line's own final length, stands in for all of them rather than re-deriving a
    # separate board-wide date series from scratch.
    dates = max(date_lists, key=len)[-len(line):] if date_lists and line else []

    return {"line": line, "signal": signal, "dates": dates}


def compute_currency_percent_b(raw_ohlcv: dict) -> dict:
    """
    Per-currency %B (2026-09-10, Pieter's own catch) — compute_board_percent_b() above averages
    every pair's raw %B with no regard for which side of the pair is base vs. quote. That's fine
    while a pair's %B stays inside one pair's own story, but it breaks down the moment you average
    ACROSS pairs: EUR/USD falling means EUR weak / USD strong, while USD/CAD falling means USD
    weak / CAD strong — opposite USD stories producing the same "%B went down." Board %B mixes
    both without correction, so it isn't a clean read of any one currency's stretch.

    This fixes that the same way csm.py's own compute_csm_d1 already does for currency STRENGTH
    (raw[base].append(combined); raw[quote].append(-combined)) — except %B lives on a 0-100
    *position* scale, not a signed return, so the quote-side correction is a mirror around the
    midpoint (100 - value) rather than a sign flip: a pair sitting at %B=80 means the BASE
    currency is near its own upper band (stretched high); from the QUOTE currency's own
    perspective that's the mirror-image reading, %B=20 (stretched low). Every pair contributes
    exactly once, to both of its currencies, in whichever direction is correct for each.

    Uses `csm.py`'s own STRENGTH_PAIRS (18 pairs, not just the wheel's 12) for the same reason
    CSM itself does — better per-currency coverage, especially CHF (only USD/CHF touches CHF in
    the wheel's own 12 pairs; STRENGTH_PAIRS adds EUR/CHF and GBP/CHF too). Independently builds
    each pair's D1 (via `_d1_ny_close`, same NY-session convention `compute_bb_d1` elsewhere in
    this file uses) straight from `raw_ohlcv` rather than reading `pairs_out` — STRENGTH_PAIRS
    reaches beyond the wheel's 12 pairs, which don't all have a `pairs_out` entry to read from.

    Returns {currency: {"line": [...], "signal": [...], "dates": [...]}} for all 8 CURRENCIES —
    a currency with no history yet (or too little) gets empty lists, same "no read yet" fail-quiet
    convention every other lookback metric in this codebase uses.
    """
    base_lines: dict[str, list[list[float]]] = {c: [] for c in CURRENCIES}
    base_signals: dict[str, list[list[float]]] = {c: [] for c in CURRENCIES}
    quote_lines: dict[str, list[list[float]]] = {c: [] for c in CURRENCIES}
    quote_signals: dict[str, list[list[float]]] = {c: [] for c in CURRENCIES}
    date_lists: list[list[str]] = []

    for pair in STRENGTH_PAIRS:
        key = pair.replace("/", "")
        base, quote = pair.split("/")
        h1_df = raw_ohlcv.get(key)
        if h1_df is None:
            continue

        d1_ny = _d1_ny_close(h1_df)
        dates = d1_ny["date"].astype(str).tolist()
        bb = compute_bb_d1(d1_ny, dates)
        if bb is None:
            continue

        date_lists.append(bb["pctb_dates"])
        if base in base_lines:
            base_lines[base].append(bb["pctb"])
            base_signals[base].append(bb["pctb_sma"])
        if quote in quote_lines:
            quote_lines[quote].append([round(100 - v, 2) for v in bb["pctb"]])
            quote_signals[quote].append([round(100 - v, 2) for v in bb["pctb_sma"]])

    longest_dates = max(date_lists, key=len) if date_lists else []

    result: dict[str, dict] = {}
    for currency in CURRENCIES:
        line = _pointwise_mean(base_lines[currency] + quote_lines[currency])
        signal = _pointwise_mean(base_signals[currency] + quote_signals[currency])
        result[currency] = {
            "line": line,
            "signal": signal,
            "dates": longest_dates[-len(line):] if line else [],
        }
    return result


def attach_bb_d1(pairs_out: dict, ohlcv: dict, raw_ohlcv: dict | None = None) -> None:
    """Mutate pairs_out in place, adding a 'bb_d1' sub-key to each pair block — same pattern
    structure_expose.py's attach_structure() already uses.

    2026-09-10 (%B/touch precision fix) — when raw_ohlcv is available (scan_h1.py always passes
    it), bb_d1 is now built from `_d1_ny_close(raw_ohlcv[key])`, NOT the frozen `ohlcv[key]["d1"]`
    (UTC-midnight boundary) — see that function's own doc comment for why. dates come from the
    same call, always exactly aligned. Falls back to the frozen UTC-midnight `ohlcv[key]["d1"]`
    with no dates only when raw_ohlcv is unavailable (kept for callers/tests that don't have H1
    data on hand) — production always has raw_ohlcv, so this fallback shouldn't fire in practice.
    """
    for key, block in pairs_out.items():
        if raw_ohlcv and key in raw_ohlcv:
            d1_ny = _d1_ny_close(raw_ohlcv[key])
            dates = d1_ny["date"].astype(str).tolist()
            block["bb_d1"] = compute_bb_d1(d1_ny, dates)
        else:
            d1_df = (ohlcv.get(key) or {}).get("d1")
            block["bb_d1"] = compute_bb_d1(d1_df, None)
