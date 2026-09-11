"""
ATOM FX — trend-pullback detector (Task 3, spec §4-§5).

Pure deterministic rule engine: evaluate(tfs) -> dict. A Daily-biased, H4-confirmed,
H1-executed trend-pullback-continuation setup. No I/O, no clock, no randomness — calling it
twice on the same frames returns byte-identical output (proven in tests/test_trend_pullback.py).

Gate order (all must pass to reach a state other than "none"):
  A  - D1 trend established (EMA50/200 + slope + structure not opposing) -> sets `direction`.
  B  - D1 trend strength (ADX >= min and rising).
  C  - H4 pullback quality (DECISION-002: BOTH the Fib-zone test AND the near-EMA50 test).
  D  - H1 entry trigger (BOTH a reversal candle AND a break of the prior H1 swing).
  rr - reward:risk >= min_rr, else "armed" (setup real, insufficient reward — no alert).

Rule #1: EXTEND. Imports frozen scanner.score._ema/_atr_series/_dmi and
scanner.structure.detect_structure read-only — every number here traces back to the same
frozen math the rest of the app uses; no indicator is re-implemented. Uses
scanner.extend.swings (Task 2) for pivots and scanner.extend.agg_nyclose (Task 1a/b) plus the
frozen scanner.aggregator for the h1->tfs assembly evaluate_from_h1() needs.

build_methodology_tfs() lives HERE, not in agg_nyclose.py — this task's own scope is "create
ONE new file"; adding a public function to agg_nyclose.py would be a second file touched for
no benefit, since the assembly is a two-line combination of an existing frozen helper
(aggregator.build_tfs, for h1/h4) and an existing EXTEND one (aggregate_d1_nyclose, for d1).

Alignment note: deliberately does NOT read the frozen `pills`/score modules (those score the
UTC-midnight D1 lineage) — direction comes from THIS module's own D1 read, and the pullback
leg is required to be in that same direction; that IS the alignment check. Self-contained on
the NY-close lineage throughout.
"""
import numpy as np
import pandas as pd

from scanner.score import _ema, _atr_series, _dmi
from scanner.structure import detect_structure
from scanner.aggregator import build_tfs as _frozen_build_tfs
from scanner.extend.agg_nyclose import aggregate_d1_nyclose
from scanner.extend.swings import find_swings, last_swing_high, last_swing_low

# PROPOSED defaults (spec §8) — UNVALIDATED. Tuned in Task 5's backtest before DECISION-005
# is ratified; do not treat any of these as settled until then.
PARAMS = {
    "adx_min": 22,
    "adx_rising_lookback": 3,
    "ema50_slope_lookback": 5,
    "fib_min": 0.382,
    "fib_max": 0.618,
    "fib_invalidation": 0.786,
    "ema50_near_atr": 1.0,
    "swing_n_d1": 10,
    "swing_n_h4": 5,
    "swing_n_h1": 5,
    "stop_buffer_atr": 0.5,
    "min_rr": 2.0,
}

_RESULT_KEYS = (
    "state", "direction", "entry", "stop", "target", "rr", "stop_pips", "adx",
    "fib_pct", "ema50_dist_atr", "trigger", "leg_high", "leg_low", "blocked_at",
)


def _empty_result() -> dict:
    return {k: None for k in _RESULT_KEYS} | {"state": "none"}


def _safe(x):
    """NaN -> None; everything else -> plain float. Keeps the return dict JSON-safe."""
    if x is None:
        return None
    x = float(x)
    return None if np.isnan(x) else x


def build_methodology_tfs(h1_df: pd.DataFrame) -> dict:
    """
    Assemble {"d1", "h4", "h1"} from one H1 fetch, for evaluate_from_h1() below. h1/h4 come
    from the frozen scanner.aggregator.build_tfs (H1 normalized, H4 exact UTC 4h blocks — no
    reason to duplicate that math). d1 is OVERRIDDEN with the NY-close EXTEND aggregation
    (DECISION-001) instead of build_tfs's own UTC-midnight d1 — this methodology's D1 bias
    gate must match the 17:00-NY close convention, not the coarse UTC-midnight one.
    """
    tfs = _frozen_build_tfs(h1_df)
    tfs["d1"] = aggregate_d1_nyclose(h1_df)
    return tfs


def _pip_size(pair: str | None) -> float:
    """0.01 for a JPY-quoted pair, else 0.0001 — including when `pair` isn't given at all."""
    if pair and "JPY" in pair.upper():
        return 0.01
    return 0.0001


def _reversal_trigger(prior: dict, cur: dict, direction: str) -> str | None:
    """prior/cur: {'open','high','low','close'} floats for h1.iloc[-2]/h1.iloc[-1]. Returns
    'engulfing', 'pin', or None. 'pin' also covers a bearish shooting star (SHORT) — the
    signals.json contract (spec §6) has only two trigger values regardless of direction."""
    po, ph, pl, pc = prior["open"], prior["high"], prior["low"], prior["close"]
    co, ch, cl, cc = cur["open"], cur["high"], cur["low"], cur["close"]
    body = abs(cc - co)

    if direction == "long":
        engulfing = pc < po and cc > co and cc >= po and co <= pc
        lower_wick = min(co, cc) - cl
        upper_wick = ch - max(co, cc)
        pin = lower_wick >= 2 * body and upper_wick <= body
    else:
        engulfing = pc > po and cc < co and cc <= po and co >= pc
        upper_wick = ch - max(co, cc)
        lower_wick = min(co, cc) - cl
        pin = upper_wick >= 2 * body and lower_wick <= body

    if engulfing:
        return "engulfing"
    if pin:
        return "pin"
    return None


def _impulse_leg(swings_h4: dict, direction: str):
    """
    Returns (leg_high, leg_low), or (None, None) if the leg isn't found or isn't sane.
    LONG:  end = last swing high; start = most recent swing low BEFORE end's index.
    SHORT: end = last swing low;  start = most recent swing high BEFORE end's index.
    """
    highs, lows = swings_h4["highs"], swings_h4["lows"]

    if direction == "long":
        if not highs:
            return None, None
        end = highs[-1]
        candidates = [lo for lo in lows if lo[0] < end[0]]
        if not candidates:
            return None, None
        start = candidates[-1]
        leg_high, leg_low = end[1], start[1]
        if leg_high <= leg_low:
            return None, None
    else:
        if not lows:
            return None, None
        end = lows[-1]
        candidates = [hi for hi in highs if hi[0] < end[0]]
        if not candidates:
            return None, None
        start = candidates[-1]
        leg_high, leg_low = start[1], end[1]
        if leg_low >= leg_high:
            return None, None

    return leg_high, leg_low


def evaluate(tfs: dict, params: dict | None = None, pair: str | None = None) -> dict:
    """
    tfs  : {"d1": DataFrame, "h4": DataFrame, "h1": DataFrame} — clean OHLC frames, integer-
           indexed, oldest-first (the shape build_methodology_tfs() produces).
    pair : optional, e.g. "USD/JPY" — only used to pick stop_pips's pip size (JPY pairs:
           0.01, else 0.0001). Defaults to 0.0001 when not given.

    Pure function: no I/O, no clock, no randomness. Returns a dict per spec §6.
    """
    p = dict(PARAMS)
    if params:
        p.update(params)

    d1, h4, h1 = tfs["d1"], tfs["h4"], tfs["h1"]

    if len(d1) < 210 or len(h4) < 60 or len(h1) < (p["swing_n_h1"] * 2 + 2):
        out = _empty_result()
        out["blocked_at"] = "data"
        return out

    result = _empty_result()

    # ── Compute (frozen helpers only) ───────────────────────────────────────────
    d1_ema50 = _ema(d1["close"], 50)
    d1_ema200 = _ema(d1["close"], 200)
    _, _, d1_adx_s = _dmi(d1["high"], d1["low"], d1["close"])
    d1_atr_s = _atr_series(d1["high"], d1["low"], d1["close"])
    atr_d1_last = float(d1_atr_s.iloc[-1])
    struct_d1 = detect_structure(d1, atr_d1_last, swing_n=p["swing_n_d1"])

    h4_ema50 = _ema(h4["close"], 50)
    h4_atr_s = _atr_series(h4["high"], h4["low"], h4["close"])
    swings_h4 = find_swings(h4, p["swing_n_h4"])

    h1_atr_s = _atr_series(h1["high"], h1["low"], h1["close"])
    h1_swing_high = last_swing_high(h1, p["swing_n_h1"])
    h1_swing_low = last_swing_low(h1, p["swing_n_h1"])

    current_price = float(h1["close"].iloc[-1])
    adx_now = float(d1_adx_s.iloc[-1])
    result["adx"] = _safe(adx_now)

    # ── Gate A: D1 trend (sets `direction`) ─────────────────────────────────────
    ema50_now, ema200_now = float(d1_ema50.iloc[-1]), float(d1_ema200.iloc[-1])
    ema50_then = float(d1_ema50.iloc[-1 - p["ema50_slope_lookback"]])
    d1_close_now = float(d1["close"].iloc[-1])

    long_a = (ema50_now > ema200_now and d1_close_now > ema200_now
              and ema50_now > ema50_then and struct_d1["direction"] != "bear")
    short_a = (ema50_now < ema200_now and d1_close_now < ema200_now
               and ema50_now < ema50_then and struct_d1["direction"] != "bull")

    if long_a:
        direction = "long"
    elif short_a:
        direction = "short"
    else:
        result["blocked_at"] = "A"
        return result

    result["direction"] = direction

    # ── Gate B: D1 strength ──────────────────────────────────────────────────────
    adx_then = float(d1_adx_s.iloc[-1 - p["adx_rising_lookback"]])
    if not (adx_now >= p["adx_min"] and adx_now > adx_then):
        result["blocked_at"] = "B"
        return result

    # ── Gate C: H4 pullback quality (DECISION-002: both sub-tests) ─────────────
    leg_high, leg_low = _impulse_leg(swings_h4, direction)
    if leg_high is None:
        result["blocked_at"] = "C"
        return result
    result["leg_high"], result["leg_low"] = _safe(leg_high), _safe(leg_low)

    if direction == "long":
        retr = (leg_high - current_price) / (leg_high - leg_low)
    else:
        retr = (current_price - leg_low) / (leg_high - leg_low)
    result["fib_pct"] = _safe(retr * 100)

    h4_ema50_now = float(h4_ema50.iloc[-1])
    h4_atr_now = float(h4_atr_s.iloc[-1])
    ema50_dist_atr = abs(current_price - h4_ema50_now) / h4_atr_now
    result["ema50_dist_atr"] = _safe(ema50_dist_atr)

    # fib_max < fib_invalidation always (0.618 < 0.786), so this single range check already
    # excludes the ">fib_invalidation" (too-deep) case as well as the "<fib_min" (not-pulled-
    # back-enough) case — both are simply outside [fib_min, fib_max].
    fib_ok = p["fib_min"] <= retr <= p["fib_max"]
    near_ok = ema50_dist_atr <= p["ema50_near_atr"]
    if not (fib_ok and near_ok):
        result["blocked_at"] = "C"
        return result

    # ── Gate D: H1 entry trigger (BOTH required) ────────────────────────────────
    # Both swing points are needed regardless of direction: one for the break-check below,
    # the other for the stop reference in the risk outputs — treat either missing as a D-fail.
    if h1_swing_high is None or h1_swing_low is None:
        result["blocked_at"] = "D"
        return result

    prior_row, cur_row = h1.iloc[-2], h1.iloc[-1]
    prior = {k: float(prior_row[k]) for k in ("open", "high", "low", "close")}
    cur = {k: float(cur_row[k]) for k in ("open", "high", "low", "close")}

    trigger = _reversal_trigger(prior, cur, direction)
    if direction == "long":
        broke = cur["close"] > h1_swing_high[1]
    else:
        broke = cur["close"] < h1_swing_low[1]

    if trigger is None or not broke:
        result["blocked_at"] = "D"
        return result
    result["trigger"] = trigger

    # ── Risk outputs (DECISION-003/004) ─────────────────────────────────────────
    entry = current_price
    h1_atr_now = float(h1_atr_s.iloc[-1])
    if direction == "long":
        stop = h1_swing_low[1] - p["stop_buffer_atr"] * h1_atr_now
        target = leg_high
        rr = (target - entry) / (entry - stop)
    else:
        stop = h1_swing_high[1] + p["stop_buffer_atr"] * h1_atr_now
        target = leg_low
        rr = (entry - target) / (stop - entry)

    stop_pips = abs(entry - stop) / _pip_size(pair)

    result["entry"] = _safe(entry)
    result["stop"] = _safe(stop)
    result["target"] = _safe(target)
    result["rr"] = _safe(rr)
    result["stop_pips"] = _safe(stop_pips)

    if rr >= p["min_rr"]:
        result["state"] = "fired"
        result["blocked_at"] = None
    else:
        result["state"] = "armed"
        result["blocked_at"] = "rr"

    return result


def evaluate_from_h1(h1_df: pd.DataFrame, params: dict | None = None, pair: str | None = None) -> dict:
    """Thin wrapper: builds {d1,h4,h1} from one H1 fetch via build_methodology_tfs(), then
    evaluate()s it. This is the function Task 4's hourly scan calls per pair; it fetches
    nothing itself."""
    tfs = build_methodology_tfs(h1_df)
    return evaluate(tfs, params=params, pair=pair)
