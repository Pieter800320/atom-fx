"""
ATOM FX research — crowded_reversal.py tests (Python port of the Crowded Market Pine indicator).

Synthetic, hand-checkable fixtures only — no network. Asserts EXTEND behaviour only; nothing
frozen is imported. The COT alignment test is the important one: it is the no-look-ahead proof.

Run:  python -m tests.test_crowded_reversal      (or: pytest tests/test_crowded_reversal.py)
"""
import numpy as np
import pandas as pd

from scanner.extend import crowded_reversal as cr


# ── Pine primitives, hand-computed ────────────────────────────────────────────────
def test_rma_seeds_with_sma_then_wilder_recursion():
    out = cr.rma([1, 2, 3, 4, 5], 3)
    assert np.isnan(out[:2]).all()
    assert out[2] == 2.0                                   # SMA(1,2,3)
    assert abs(out[3] - (2.0 * 2 + 4) / 3) < 1e-12         # (prev*(n-1)+x)/n
    assert abs(out[4] - (out[3] * 2 + 5) / 3) < 1e-12


def test_rma_waits_for_n_consecutive_valid_values():
    out = cr.rma([np.nan, 1, 2, 3, 4], 3)
    assert np.isnan(out[:3]).all() and out[3] == 2.0


def test_pstdev_is_population_not_sample():
    assert abs(cr.pstdev([1, 2, 3, 4], 4)[3] - np.sqrt(1.25)) < 1e-12      # not sqrt(5/3)


def test_percentrank_excludes_current_bar_and_counts_ties_as_le():
    out = cr.percentrank([1, 2, 3, 2, 5], 3)
    assert np.isnan(out[:3]).all()
    assert abs(out[3] - 100.0 * 2 / 3) < 1e-12             # prev [1,2,3], cur 2 -> {1,2} <= 2
    assert out[4] == 100.0                                 # prev [2,3,2], cur 5 -> all


def test_percentrank_nan_in_window_gives_nan():
    out = cr.percentrank([np.nan, 1, 2, 3], 3)
    assert np.isnan(out[3])


def test_rsi_extremes():
    up = cr.rsi(np.arange(1.0, 40.0), 14)
    dn = cr.rsi(np.arange(40.0, 1.0, -1.0), 14)
    assert up[-1] == 100.0 and dn[-1] == 0.0
    assert np.isnan(up[:14]).all() and not np.isnan(up[14])


def test_atr_first_bar_uses_high_minus_low():
    h, l, c = np.array([10., 11, 12]), np.array([9., 9.5, 10]), np.array([9.5, 10.5, 11.5])
    tr = cr.true_range(h, l, c, first_bar_hl=True)
    assert tr[0] == 1.0 and tr[1] == max(1.5, abs(11 - 9.5), abs(9.5 - 9.5))
    assert np.isnan(cr.true_range(h, l, c, first_bar_hl=False)[0])


# ── divergence: confirmed pivots, gap gate, latch ────────────────────────────────
_P = {**cr.PINE_DEFAULTS, "div_left": 2, "div_right": 2, "div_min_gap": 2, "div_max_gap": 20, "div_hold": 3}


def _div_fixture():
    rsi_v = np.array([50, 50, 52, 55, 70, 55, 52, 50, 50, 52, 60, 52, 50, 50, 50, 50], float)
    high = np.full(16, 90.0)
    high[4], high[10] = 100.0, 105.0        # price makes a HIGHER high ...
    low = np.full(16, 80.0)                 # ... while RSI makes a LOWER high (70 -> 60)
    return high, low, rsi_v


def test_bearish_divergence_confirms_at_pivot_plus_right_and_holds():
    high, low, rsi_v = _div_fixture()
    bear, bull = cr.divergence_active(high, low, rsi_v, _P)
    # second RSI pivot high is at bar 10, only confirmable at 10 + right(2) = 12; held 3 bars
    assert list(np.flatnonzero(bear)) == [12, 13, 14]
    assert not bear[:12].any(), "a divergence must never be flagged before it is confirmable"


def test_divergence_ignored_when_pivots_too_close_or_too_far():
    high, low, rsi_v = _div_fixture()
    bear, _ = cr.divergence_active(high, low, rsi_v, {**_P, "div_min_gap": 7})     # gap is 6
    assert not bear.any()
    bear, _ = cr.divergence_active(high, low, rsi_v, {**_P, "div_max_gap": 5})
    assert not bear.any()


def test_no_divergence_when_rsi_confirms_price():
    high, low, rsi_v = _div_fixture()
    rsi_v[10] = 75.0                        # RSI now makes a HIGHER high too -> not divergent
    bear, _ = cr.divergence_active(high, low, rsi_v, _P)
    assert not bear.any()


# ── regime debounce (slow to enter, instant to exit) ─────────────────────────────
def test_debounced_latch_enters_after_persist_bars_and_exits_immediately():
    raw = [1, 1, 0, 1, 1, 1, 1, 0]
    assert list(cr._debounced_latch(np.array(raw, bool), 3)) == [False, False, False, False, False, True, True, False]


# ── COT alignment: the no-look-ahead proof ───────────────────────────────────────
def _reports():
    return pd.DataFrame({"date": pd.to_datetime(["2026-09-01", "2026-09-08", "2026-09-15"]),
                         "net": [50.0, 100.0, 200.0]})


def test_cot_week_k_uses_tuesday_of_week_k_minus_1_never_later():
    cal = pd.bdate_range("2026-09-07", "2026-09-25")
    net = cr.cot_net_daily(_reports(), cal, week_lag=1)
    week = lambda start: net[start: pd.Timestamp(start) + pd.Timedelta(days=4)]
    assert (week("2026-09-07") == 50.0).all()       # Tue 09-01 report, published Fri 09-04
    assert (week("2026-09-14") == 100.0).all()      # Tue 09-08 report — NOT the 09-15 one
    assert (week("2026-09-21") == 200.0).all()      # Tue 09-15 report, published Fri 09-18
    # explicitly: the report dated Tue 09-15 must be invisible for every bar of 09-14..09-18
    assert (net["2026-09-14":"2026-09-18"] != 200.0).all()


def test_cot_week_lag_2_reproduces_the_old_double_lag():
    cal = pd.bdate_range("2026-09-07", "2026-09-25")
    net = cr.cot_net_daily(_reports(), cal, week_lag=2)
    assert (net["2026-09-14":"2026-09-18"] == 50.0).all()       # one report OLDER than lag=1
    assert (net["2026-09-21":"2026-09-25"] == 100.0).all()


def test_cot_before_first_report_is_nan():
    cal = pd.bdate_range("2026-08-31", "2026-09-04")
    assert cr.cot_net_daily(_reports(), cal, 1).isna().all()


def test_combine_cot_pctl_inverts_quote_leg_and_averages():
    out = cr.combine_cot_pctl([15.0, 15.0, np.nan, np.nan], [84.1, np.nan, 30.0, np.nan])
    assert abs(out[0] - (15.0 + (100 - 84.1)) / 2) < 1e-9      # both legs
    assert out[1] == 15.0                                       # base only
    assert out[2] == 70.0                                       # quote only, inverted
    assert np.isnan(out[3])


def test_pair_legs():
    assert cr.pair_legs("EURJPY") == ("EUR", "JPY") and cr.pair_legs("USD/CAD") == ("USD", "CAD")


# ── the whole indicator on a synthetic series ────────────────────────────────────
def _walk(n=700, seed=7):
    rng = np.random.default_rng(seed)
    close = 100 + np.cumsum(rng.normal(0, 0.6, n))
    high = close + rng.uniform(0.1, 0.8, n)
    low = close - rng.uniform(0.1, 0.8, n)
    return pd.DataFrame({"open": close, "high": high, "low": low, "close": close})


def test_compute_flags_shape_bounds_and_determinism():
    df = _walk()
    a = cr.compute_flags(df)
    b = cr.compute_flags(df)
    pd.testing.assert_frame_equal(a, b)
    assert len(a) == len(df)
    assert a["top_score"].between(0, 100).all() and a["bot_score"].between(0, 100).all()


def test_without_cot_the_maximum_possible_score_is_75():
    # COT's 25/100 weight stays in the denominator but can never fire when the leg is unavailable
    a = cr.compute_flags(_walk(), cot_combined_pctl=None)
    assert a["top_score"].max() <= 75.0 + 1e-9 and a["bot_score"].max() <= 75.0 + 1e-9
    assert not a["top_cot"].any() and not a["bot_cot"].any()


def test_cot_factor_fires_at_extremes_with_pine_thresholds():
    df = _walk()
    pctl = np.full(len(df), 50.0)
    pctl[300], pctl[301] = 90.0, 10.0
    a = cr.compute_flags(df, cot_combined_pctl=pctl)
    assert a["top_cot"].iloc[300] and not a["top_cot"].iloc[301]
    assert a["bot_cot"].iloc[301] and not a["bot_cot"].iloc[300]


def test_apply_regime_suppress_zeroes_only_the_counter_trend_side():
    flags = pd.DataFrame({"top_score": [80.0, 80.0], "bot_score": [80.0, 80.0],
                          "strong_up": [True, False], "strong_dn": [False, True]})
    top, bot = cr.apply_regime(flags, "suppress")
    assert list(top) == [0.0, 80.0] and list(bot) == [80.0, 0.0]
    top, bot = cr.apply_regime(flags, "off")
    assert list(top) == [80.0, 80.0]
    top, bot = cr.apply_regime(flags, "penalize", 0.5)
    assert list(top) == [40.0, 80.0]


def test_rising_edge():
    assert list(cr.rising_edge([1, 1, 0, 1, 0, 0, 1])) == [True, False, False, True, False, False, True]


if __name__ == "__main__":
    import sys
    import pytest
    sys.exit(pytest.main([__file__, "-q"]))
