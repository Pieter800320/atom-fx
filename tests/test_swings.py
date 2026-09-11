"""
ATOM FX — swings.py tests (Task 2, spec §7).

Synthetic fixtures only — no network. Style follows tests/test_extend.py.

This file asserts EXTEND behaviour only; it never touches a frozen key. tests/test_rule1_frozen
stays the proof that no frozen file changed. The one exception: scanner.structure.detect_structure
is imported READ-ONLY here (never called elsewhere in this file's own module, only in the
consistency-check tests below) to prove swings.py's pivot rule agrees with structure.py's own.

Run:  python -m tests.test_swings      (or: pytest tests/test_swings.py)
"""
import numpy as np
import pandas as pd

from scanner.extend.swings import find_swings, last_swing_high, last_swing_low
from scanner.structure import detect_structure


def _df(high, low):
    return pd.DataFrame({"high": [float(h) for h in high], "low": [float(l) for l in low]})


# ── 1. Hand-built series with known pivots ────────────────────────────────────────
# swing_n=2. Peaks in `high` at index 3 (value 5) and index 8 (value 4); troughs in `low`
# at index 3 (value 1) and index 8 (value 2) -- verified by hand against the strict-left/
# tolerant-right rule (each candidate i checked against high[i-2:i] / high[i+1:i+3]).
_KNOWN_HIGH = [1, 2, 3, 5, 3, 2, 1, 2, 4, 2, 1]
_KNOWN_LOW = [5, 4, 3, 1, 3, 4, 5, 4, 2, 4, 5]


def test_known_pivots_returned_exactly():
    r = find_swings(_df(_KNOWN_HIGH, _KNOWN_LOW), swing_n=2)
    assert r["highs"] == [(3, 5.0), (8, 4.0)]
    assert r["lows"] == [(3, 1.0), (8, 2.0)]


def test_too_few_bars_returns_empty():
    # swing_n=5 needs len >= 11; 3 bars is far short.
    r = find_swings(_df([1, 2, 3], [1, 2, 3]), swing_n=5)
    assert r == {"highs": [], "lows": []}


# ── 2. Tie handling: a duplicate-high plateau must not create a false SECOND pivot ─
def test_duplicate_high_plateau_no_false_pivot():
    # swing_n=1. high[2]==high[3]==4 (a plateau). Index 2: strictly above its left
    # neighbour (2) AND >= its right neighbour (4, tie-tolerant) -> a real pivot. Index 3:
    # its LEFT neighbour is the tied 4 -> fails the STRICT left comparison (4 > 4 is false),
    # even though its right side (3) would pass -- exactly the false-second-pivot structure.py's
    # own header comment says this rule exists to eliminate.
    high = [1, 2, 4, 4, 3, 1]
    low = [0, 0, 0, 0, 0, 0]   # flat -- no low pivots possible, not the point of this test
    r = find_swings(_df(high, low), swing_n=1)
    assert r["highs"] == [(2, 4.0)]
    assert r["lows"] == []


# ── 3. last_swing_high / last_swing_low ───────────────────────────────────────────
def test_last_swing_returns_most_recent():
    df = _df(_KNOWN_HIGH, _KNOWN_LOW)
    assert last_swing_high(df, swing_n=2) == (8, 4.0)
    assert last_swing_low(df, swing_n=2) == (8, 2.0)


def test_last_swing_none_on_too_few_bars():
    df = _df([1, 2, 3], [1, 2, 3])
    assert last_swing_high(df, swing_n=5) is None
    assert last_swing_low(df, swing_n=5) is None


# ── 4. Consistency: swings-derived trend must match scanner.structure.detect_structure ─
def _oscillating_trend_df(rising: bool, n: int = 40, cycles: float = 2.0):
    """A rising or falling baseline with a superimposed oscillation, so swing_n=5 finds
    multiple alternating pivots that still climb (rising) or fall (falling) overall --
    same shape structure.py's own trend-from-two-swings logic is built to classify."""
    osc = 0.05 * np.sin(np.linspace(0, cycles * 2 * np.pi, n))
    base = np.linspace(1.00, 1.40, n) if rising else np.linspace(1.40, 1.00, n)
    close = base + osc
    return pd.DataFrame({"open": close, "high": close + 0.01, "low": close - 0.01, "close": close})


def _swings_trend(df, swing_n=5):
    r = find_swings(df, swing_n)
    highs, lows = r["highs"], r["lows"]
    assert len(highs) >= 2 and len(lows) >= 2, "fixture must yield >=2 swings each side"
    higher_highs = highs[-1][1] > highs[-2][1]
    higher_lows = lows[-1][1] > lows[-2][1]
    lower_highs = highs[-1][1] < highs[-2][1]
    lower_lows = lows[-1][1] < lows[-2][1]
    if higher_highs and higher_lows:
        return "bull"
    if lower_highs and lower_lows:
        return "bear"
    return "neutral"


def test_swings_trend_matches_detect_structure_bull():
    df = _oscillating_trend_df(rising=True, n=40, cycles=2.0)
    assert _swings_trend(df) == "bull"
    assert detect_structure(df, atr=0.01)["direction"] == "bull"


def test_swings_trend_matches_detect_structure_bear():
    df = _oscillating_trend_df(rising=False, n=50, cycles=3.0)
    assert _swings_trend(df) == "bear"
    assert detect_structure(df, atr=0.01)["direction"] == "bear"


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
