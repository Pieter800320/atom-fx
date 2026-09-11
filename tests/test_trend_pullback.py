"""
ATOM FX — trend_pullback.py tests (Task 3, spec §4-§5).

Synthetic d1/h4/h1 fixtures only — no network, no clock. Style follows tests/test_extend.py.
Every fixture below was constructed and numerically verified (gate-by-gate) before being
locked in here, so each assertion reflects a fixture actually engineered to hit that exact
gate — not a guess at what "should" happen.

This file asserts EXTEND behaviour only; it never touches a frozen key. tests/test_rule1_frozen
stays the proof that no frozen file changed.

Run:  python -m tests.test_trend_pullback      (or: pytest tests/test_trend_pullback.py)
"""
import numpy as np
import pandas as pd

from scanner.extend.trend_pullback import evaluate


def _ohlc(close, high=None, low=None, open_=None):
    close = np.asarray(close, dtype=float)
    if open_ is None:
        open_ = np.empty_like(close)
        open_[0] = close[0]
        open_[1:] = close[:-1]
    if high is None:
        high = np.maximum(open_, close) + 0.0005
    if low is None:
        low = np.minimum(open_, close) - 0.0005
    return pd.DataFrame({"open": open_, "high": high, "low": low, "close": close})


# ── D1 fixtures ────────────────────────────────────────────────────────────────
def _d1_long(accelerate=True):
    """Rising trend, 240 bars: EMA50>EMA200, close>EMA200, EMA50 rising, structure bull.
    The last-30-bar acceleration also drives ADX >= adx_min and rising (Gate B)."""
    n = 240
    t = np.arange(n)
    base = np.linspace(1.00, 1.30, n)
    osc = 0.02 * np.sin(t / 6.0)
    accel = np.zeros(n)
    if accelerate:
        accel[-30:] = np.linspace(0, 0.15, 30)
    close = base + osc + accel
    return _ohlc(close, high=close + 0.005, low=close - 0.005, open_=close)


def _d1_short(accelerate=True):
    """Mirror of _d1_long: falling trend for a SHORT setup."""
    n = 240
    t = np.arange(n)
    base = np.linspace(1.30, 1.00, n)
    osc = 0.02 * np.sin(t / 6.0)
    accel = np.zeros(n)
    if accelerate:
        accel[-30:] = np.linspace(0, 0.15, 30)
    close = base + osc - accel
    return _ohlc(close, high=close + 0.005, low=close - 0.005, open_=close)


def _d1_flat_choppy():
    """No clear trend -- EMA50/EMA200 stay tangled. Fails Gate A."""
    n = 240
    rng = np.random.default_rng(1)
    close = 1.15 + np.cumsum(rng.normal(0, 0.001, n))
    close = close - np.linspace(0, close[-1] - close[0], n)  # detrend to stay flat overall
    return _ohlc(close, high=close + 0.003, low=close - 0.003, open_=close)


def _d1_weak_trend():
    """A real (if gentle) uptrend -- Gate A passes -- but too choppy for ADX to hold >= 22
    and rising: ADX peaks near the middle of the fixture then declines into the last bars."""
    n = 240
    t = np.arange(n)
    close = np.linspace(1.00, 1.06, n) + 0.035 * np.sin(t / 4.0)
    return _ohlc(close, high=close + 0.003, low=close - 0.003, open_=close)


# ── H4 fixtures (the pullback leg) ──────────────────────────────────────────────
def _h4_long(pull_end=1.425, tail_flat=30):
    """Up-leg 1.380(low, idx~7) -> 1.473(high, idx~66), pullback to `pull_end` then a
    flat tail (lets EMA50 drift toward the pullback zone -- needed for the near-value test)."""
    down = np.linspace(1.400, 1.380, 8)
    up = np.linspace(1.380, 1.470, 60)[1:]
    pullback = np.linspace(1.470, pull_end, 20)[1:]
    if tail_flat:
        pullback = np.concatenate([pullback, np.full(tail_flat, pull_end)])
    close = np.concatenate([down, up, pullback])
    return _ohlc(close, high=close + 0.003, low=close - 0.003, open_=close)


def _h4_short(bounce_end=1.375, tail_flat=30):
    """Mirror: down-leg 1.420(high) -> 1.330(low), bounce to `bounce_end` then a flat tail."""
    up0 = np.linspace(1.400, 1.420, 8)
    down = np.linspace(1.420, 1.330, 60)[1:]
    bounce = np.linspace(1.330, bounce_end, 20)[1:]
    if tail_flat:
        bounce = np.concatenate([bounce, np.full(tail_flat, bounce_end)])
    close = np.concatenate([up0, down, bounce])
    return _ohlc(close, high=close + 0.003, low=close - 0.003, open_=close)


def _h4_small_leg():
    """A much smaller up-leg (1.380 -> 1.4015) than _h4_long -- same retracement/near-EMA50
    shape, but tiny reward potential, for the RR-too-low ('armed') case."""
    down = np.linspace(1.400, 1.380, 8)
    up = np.linspace(1.380, 1.400, 60)[1:]
    pullback = np.linspace(1.400, 1.389, 20)[1:]
    pullback = np.concatenate([pullback, np.full(30, 1.389)])
    close = np.concatenate([down, up, pullback])
    return _ohlc(close, high=close + 0.0015, low=close - 0.0015, open_=close)


# ── H1 fixtures (the entry trigger) ─────────────────────────────────────────────
def _h1_long_trigger(entry_close=1.4250, swing_high=1.4210, no_break=False, no_candle=False):
    """Micro zigzag: swing low ~idx7 (1.4150ish), swing high ~idx7' (`swing_high`), a small
    consolidation, then a final 2-bar bullish engulfing closing at `entry_close`."""
    up1 = np.linspace(1.4100, swing_high, 8)
    down1 = np.linspace(swing_high, 1.4150, 8)[1:]
    chop = np.linspace(1.4150, 1.4170, 10)[1:]
    if no_candle:
        prior_open, prior_close = 1.4160, 1.4220   # prior itself already bullish
        cur_open, cur_close = 1.4220, entry_close    # small up move, not engulfing/pin
    else:
        prior_open, prior_close = 1.4185, 1.4160     # a down bar
        cur_open, cur_close = 1.4150, entry_close    # engulfs it
    close = np.concatenate([up1, down1, chop, [prior_close, cur_close]])
    open_ = np.empty_like(close)
    open_[0] = close[0]
    open_[1:] = close[:-1]
    open_[-2] = prior_open
    open_[-1] = cur_open
    if no_candle:
        high = np.maximum(open_, close) + 0.0002
        low = np.minimum(open_, close) - 0.0002
    else:
        high = np.maximum(open_, close) + 0.0005
        low = np.minimum(open_, close) - 0.0005
    return pd.DataFrame({"open": open_, "high": high, "low": low, "close": close})


def _h1_short_trigger(entry_close=1.3750, swing_low=1.3800):
    """Mirror: bounce to a swing high, back down to `swing_low`, consolidation, then a
    final 2-bar bearish engulfing closing at `entry_close` (below the swing low)."""
    swing_high = swing_low + 0.006
    down1 = np.linspace(swing_high, swing_low, 8)
    up1 = np.linspace(swing_low, swing_high, 8)[1:]
    chop = np.linspace(swing_high, swing_high - 0.002, 10)[1:]
    prior_open, prior_close = swing_high - 0.0045, swing_high - 0.002   # an up bar
    cur_open, cur_close = swing_high - 0.0002, entry_close               # bearish engulfing
    close = np.concatenate([down1, up1, chop, [prior_close, cur_close]])
    open_ = np.empty_like(close)
    open_[0] = close[0]
    open_[1:] = close[:-1]
    open_[-2] = prior_open
    open_[-1] = cur_open
    high = np.maximum(open_, close) + 0.0005
    low = np.minimum(open_, close) - 0.0005
    return pd.DataFrame({"open": open_, "high": high, "low": low, "close": close})


def _h1_small(entry_close=1.389):
    """Same shape as _h1_long_trigger, offset down to match _h4_small_leg's price scale."""
    off = entry_close - 1.4250
    up1 = np.linspace(1.4100 + off, 1.4210 + off, 8)
    down1 = np.linspace(1.4210 + off, 1.4150 + off, 8)[1:]
    chop = np.linspace(1.4150 + off, 1.4170 + off, 10)[1:]
    prior_close = 1.4160 + off
    close = np.concatenate([up1, down1, chop, [prior_close, entry_close]])
    open_ = np.empty_like(close)
    open_[0] = close[0]
    open_[1:] = close[:-1]
    open_[-2] = 1.4185 + off
    open_[-1] = 1.4150 + off
    high = np.maximum(open_, close) + 0.0005
    low = np.minimum(open_, close) - 0.0005
    return pd.DataFrame({"open": open_, "high": high, "low": low, "close": close})


# ── 1. Valid LONG -> fired ────────────────────────────────────────────────────
def test_valid_long_fires():
    tfs = {"d1": _d1_long(), "h4": _h4_long(), "h1": _h1_long_trigger()}
    r = evaluate(tfs)
    assert r["state"] == "fired"
    assert r["direction"] == "long"
    assert r["blocked_at"] is None
    assert r["rr"] >= 2.0
    assert r["trigger"] == "engulfing"
    assert r["stop"] < r["entry"] < r["target"]
    assert r["stop_pips"] > 0


# ── 2. Valid SHORT (mirror) -> fired ──────────────────────────────────────────
def test_valid_short_fires():
    tfs = {"d1": _d1_short(), "h4": _h4_short(), "h1": _h1_short_trigger()}
    r = evaluate(tfs)
    assert r["state"] == "fired"
    assert r["direction"] == "short"
    assert r["blocked_at"] is None
    assert r["rr"] >= 2.0
    assert r["trigger"] == "engulfing"
    assert r["target"] < r["entry"] < r["stop"]
    assert r["stop_pips"] > 0


# ── 3. Gate A fail: tangled EMAs (no clear D1 trend) ──────────────────────────
def test_gate_a_fail_tangled_emas():
    tfs = {"d1": _d1_flat_choppy(), "h4": _h4_long(), "h1": _h1_long_trigger()}
    r = evaluate(tfs)
    assert r["state"] == "none"
    assert r["blocked_at"] == "A"
    assert r["direction"] is None


# ── 4. Gate B fail: ADX below min / not rising ────────────────────────────────
def test_gate_b_fail_adx_not_rising():
    tfs = {"d1": _d1_weak_trend(), "h4": _h4_long(), "h1": _h1_long_trigger()}
    r = evaluate(tfs)
    assert r["state"] == "none"
    assert r["blocked_at"] == "B"
    assert r["direction"] == "long"   # Gate A already picked a side before B failed


# ── 5. Gate C fail: three ways ────────────────────────────────────────────────
def test_gate_c_fail_not_pulled_back():
    # shallow pullback: retr ~13.5%, well under fib_min (38.2%)
    h4 = _h4_long(pull_end=1.460, tail_flat=30)
    tfs = {"d1": _d1_long(), "h4": h4, "h1": _h1_long_trigger(entry_close=1.460)}
    r = evaluate(tfs)
    assert r["state"] == "none"
    assert r["blocked_at"] == "C"
    assert r["fib_pct"] < 38.2


def test_gate_c_fail_too_deep():
    # deep pullback: retr ~86.5%, beyond fib_invalidation (78.6%)
    h4 = _h4_long(pull_end=1.390, tail_flat=30)
    tfs = {"d1": _d1_long(), "h4": h4, "h1": _h1_long_trigger(entry_close=1.390)}
    r = evaluate(tfs)
    assert r["state"] == "none"
    assert r["blocked_at"] == "C"
    assert r["fib_pct"] > 78.6


def test_gate_c_fail_far_from_ema50():
    # in the fib zone (retr ~50%) but no flat tail -- EMA50 hasn't caught up, dist >> 1.0 ATR
    h4 = _h4_long(pull_end=1.425, tail_flat=0)
    tfs = {"d1": _d1_long(), "h4": h4, "h1": _h1_long_trigger(entry_close=1.425)}
    r = evaluate(tfs)
    assert r["state"] == "none"
    assert r["blocked_at"] == "C"
    assert 38.2 <= r["fib_pct"] <= 61.8
    assert r["ema50_dist_atr"] > 1.0


# ── 6. Gate D fail: no reversal candle, and no swing break ───────────────────
def test_gate_d_fail_no_reversal_candle():
    h1 = _h1_long_trigger(no_candle=True)
    tfs = {"d1": _d1_long(), "h4": _h4_long(), "h1": h1}
    r = evaluate(tfs)
    assert r["state"] == "none"
    assert r["blocked_at"] == "D"


def test_gate_d_fail_no_swing_break():
    # engulfing shape intact, but the swing high (~1.4305) sits above the close (1.4250)
    h1 = _h1_long_trigger(entry_close=1.4250, swing_high=1.4300)
    tfs = {"d1": _d1_long(), "h4": _h4_long(), "h1": h1}
    r = evaluate(tfs)
    assert r["state"] == "none"
    assert r["blocked_at"] == "D"


# ── 7. RR too low -> armed, no fire ───────────────────────────────────────────
def test_rr_too_low_is_armed_not_fired():
    tfs = {"d1": _d1_long(), "h4": _h4_small_leg(), "h1": _h1_small()}
    r = evaluate(tfs)
    assert r["state"] == "armed"
    assert r["blocked_at"] == "rr"
    assert r["rr"] < 2.0
    assert r["direction"] == "long"
    # risk numbers are still computed/exposed even though it doesn't fire
    assert r["entry"] is not None and r["stop"] is not None and r["target"] is not None


# ── 8. Determinism ─────────────────────────────────────────────────────────────
def test_determinism_same_input_same_output():
    tfs = {"d1": _d1_long(), "h4": _h4_long(), "h1": _h1_long_trigger()}
    r1 = evaluate(tfs)
    r2 = evaluate(tfs)
    assert r1 == r2


if __name__ == "__main__":
    import traceback
    fns = [v for k, v in sorted(globals().items()) if k.startswith("test_") and callable(v)]
    passed = failed = 0
    for fn in fns:
        try:
            fn()
            print(f"  PASS  {fn.__name__}")
            passed += 1
        except Exception:
            print(f"  FAIL  {fn.__name__}")
            traceback.print_exc()
            failed += 1
    print(f"\n{passed} passed, {failed} failed")
    raise SystemExit(1 if failed else 0)
