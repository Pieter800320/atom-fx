"""
ATOM FX — swing high/low primitive (Task 2, spec §4 Gate C/D, §7 Task 2).

scanner/structure.py's own strict-left/tolerant-right pivot rule, replicated here (NOT
imported — structure.py is FROZEN, and its pivot detection lives inline inside
detect_structure(), never exposed as a reusable function) so the pullback methodology's
Fib/stop levels (Task 3) key off the SAME swing points structure.py's own BOS/CHoCH
classification already uses. Any drift between the two pivot rules would let a "swing high"
here disagree with the one structure.py itself found, undermining both — hence "replicate
exactly", not "approximate".

This module ONLY detects and exposes pivots (index, price) — no Fibonacci, no pullback
depth, no trade logic. That's scanner/extend/trend_pullback.py (Task 3).

Rule #1: EXTEND. Reads OHLC only, never modifies it, never touches a frozen file.
"""
import numpy as np


def find_swings(df, swing_n: int = 5) -> dict:
    """
    df      : OHLC DataFrame (needs 'high'/'low'), integer-indexed.
    swing_n : pivot lookback/lookahead on each side — same default and meaning as
              scanner.structure.detect_structure's own swing_n.

    Returns {"highs": [(index, price), ...], "lows": [(index, price), ...]}, both
    chronological (oldest first). `index` is df's own row index; `price` is the high/low
    value at that pivot. Empty lists if len(df) < swing_n*2 + 1.
    """
    high = df["high"].values.astype(float)
    low = df["low"].values.astype(float)

    if len(high) < swing_n * 2 + 1:
        return {"highs": [], "lows": []}

    highs, lows = [], []
    for i in range(swing_n, len(high) - swing_n):
        left_h = high[i - swing_n:i]
        right_h = high[i + 1:i + swing_n + 1]
        left_l = low[i - swing_n:i]
        right_l = low[i + 1:i + swing_n + 1]

        if len(left_h) == 0 or len(right_h) == 0:
            continue

        # Swing high: strictly above all left bars, >= all right bars (tie-tolerant right —
        # see structure.py's own header comment on why: eliminates a false SECOND pivot on a
        # duplicate-high plateau, since the first of the tied pair already claims it).
        if high[i] > np.max(left_h) and high[i] >= np.max(right_h):
            highs.append((df.index[i], float(high[i])))
        # Swing low: mirror image.
        if low[i] < np.min(left_l) and low[i] <= np.min(right_l):
            lows.append((df.index[i], float(low[i])))

    return {"highs": highs, "lows": lows}


def last_swing_high(df, swing_n: int = 5):
    """Most recent (index, price) swing high, or None if none exist."""
    highs = find_swings(df, swing_n)["highs"]
    return highs[-1] if highs else None


def last_swing_low(df, swing_n: int = 5):
    """Most recent (index, price) swing low, or None if none exist."""
    lows = find_swings(df, swing_n)["lows"]
    return lows[-1] if lows else None
