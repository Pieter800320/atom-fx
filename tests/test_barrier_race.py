"""
ATOM FX research — barrier_race.py tests.

Synthetic, hand-checkable fixtures only — no network. Pins down the pre-registered rules: the
tie rule (same-bar-both-barriers -> continuation), timeout handling, de-clustering, and that the
baseline uses the identical race function as the flagged events.

Run:  python -m tests.test_barrier_race      (or: pytest tests/test_barrier_race.py)
"""
import numpy as np
import pandas as pd

from scanner.extend import barrier_race as br


def _ohlc(rows):
    """rows: [(high, low)] per bar; close is 100 on bar 0 and irrelevant after (only bar-0 close
    is used as the race reference). ATR is passed separately."""
    high = np.array([r[0] for r in rows], float)
    low = np.array([r[1] for r in rows], float)
    close = np.full(len(rows), 100.0)
    return high, low, close


def _race(rows, horizon=3):
    h, l, c = _ohlc(rows)
    atr = np.full(len(rows), 2.0)              # barriers at 102 / 98
    return br.race_all(h, l, c, atr, horizon=horizon, mult=1.0)


# ── the race itself ──────────────────────────────────────────────────────────────
def test_up_first():
    r, k = _race([(100, 100), (101, 99), (102.5, 99), (100, 100), (100, 100)])
    assert r[0] == br.UP and k[0] == 2


def test_down_first():
    r, k = _race([(100, 100), (101, 99), (101, 97.5), (100, 100), (100, 100)])
    assert r[0] == br.DOWN and k[0] == 2


def test_same_bar_both_barriers_is_both_and_counts_as_continuation_either_side():
    r, k = _race([(100, 100), (103, 97), (100, 100), (100, 100), (100, 100)])
    assert r[0] == br.BOTH and k[0] == 1
    assert not br.is_reversal(r[0], "bottom") and not br.is_reversal(r[0], "top")


def test_timeout_when_neither_barrier_touched():
    r, k = _race([(100, 100), (101, 99), (101, 99), (101, 99), (100, 100), (100, 100)])
    assert r[0] == br.TIMEOUT and k[0] == 3
    assert not br.is_reversal(r[0], "bottom") and not br.is_reversal(r[0], "top")


def test_bars_without_enough_forward_data_or_atr_are_censored():
    r, _ = _race([(100, 100), (101, 99), (101, 99), (101, 99), (100, 100)], horizon=3)
    assert r[-1] == br.CENSORED and r[-2] == br.CENSORED and r[-3] == br.CENSORED
    h, l, c = _ohlc([(100, 100)] * 6)
    r, _ = br.race_all(h, l, c, np.full(6, np.nan), horizon=3)
    assert (r == br.CENSORED).all()


def test_barrier_touched_exactly_counts_as_a_hit():
    r, _ = _race([(100, 100), (102.0, 99), (100, 100), (100, 100), (100, 100)])
    assert r[0] == br.UP


def test_reversal_direction_mapping():
    assert br.is_reversal(br.UP, "bottom") and not br.is_reversal(br.UP, "top")
    assert br.is_reversal(br.DOWN, "top") and not br.is_reversal(br.DOWN, "bottom")


# ── de-clustering ────────────────────────────────────────────────────────────────
def test_new_flag_inside_an_open_window_is_not_a_new_event():
    n = 12
    result = np.full(n, br.TIMEOUT)
    k = np.full(n, 3)                                   # each event's window is 3 bars
    top = np.zeros(n, bool)
    bot = np.zeros(n, bool)
    bot[[0, 1, 2, 3, 4]] = True
    ev, conflicts = br.declustered_events(top, bot, result, k, np.ones(n, bool))
    # event at 0 is busy through bar 3; flags at 1,2,3 skipped; bar 4 opens a new event
    assert [i for i, _ in ev] == [0, 4] and conflicts == 0


def test_window_frees_early_when_a_barrier_is_hit_early():
    n = 12
    result = np.full(n, br.UP)
    k = np.array([1] * n)                               # resolves after 1 bar
    bot = np.zeros(n, bool)
    bot[[0, 1, 2]] = True
    ev, _ = br.declustered_events(np.zeros(n, bool), bot, result, k, np.ones(n, bool))
    assert [i for i, _ in ev] == [0, 2]                 # bar 1 is inside the window, bar 2 is free


def test_simultaneous_top_and_bottom_flags_are_skipped_and_counted():
    n = 6
    result, k = np.full(n, br.TIMEOUT), np.full(n, 2)
    top, bot = np.zeros(n, bool), np.zeros(n, bool)
    top[1] = bot[1] = True
    ev, conflicts = br.declustered_events(top, bot, result, k, np.ones(n, bool))
    assert ev == [] and conflicts == 1


def test_flags_outside_eval_window_or_censored_are_ignored():
    n = 6
    result = np.array([br.TIMEOUT, br.TIMEOUT, br.TIMEOUT, br.CENSORED, br.TIMEOUT, br.TIMEOUT])
    k = np.full(n, 1)
    bot = np.ones(n, bool)
    mask = np.array([False, True, True, True, True, True])
    ev, _ = br.declustered_events(np.zeros(n, bool), bot, result, k, mask)
    assert 0 not in [i for i, _ in ev] and 3 not in [i for i, _ in ev]


# ── the study: baseline like-for-like, lift, bootstrap ───────────────────────────
def _study(outcome_for_events):
    """One pair, 24 monthly 'bars'. Baseline: bottom reversals succeed on 50% of bars. Flagged
    events (one per month, bottoms) succeed according to outcome_for_events."""
    months = pd.date_range("2024-01-31", periods=24, freq="ME")
    n = len(months)
    result = np.where(np.arange(n) % 2 == 0, br.UP, br.DOWN)        # 50% up-first everywhere
    k = np.ones(n, int)
    bot = np.zeros(n, bool)
    bot[:] = True                                                    # a flag every bar...
    # ...but de-clustered with k=1 windows every OTHER bar becomes an event:
    s = br.Study("test", horizon=1)
    s.add_pair("EURUSD", months, result, k, np.zeros(n, bool), bot, np.ones(n, bool))
    return s


def test_baseline_is_measured_not_assumed():
    s = _study(None)
    r = s.lift(n_boot=0)
    assert abs(r["baseline_rate"] - 0.5) < 0.05        # up-first on alternating bars ~ 50%
    assert r["n_events"] > 0


def test_lift_is_flagged_minus_baseline_and_positive_when_flagged_always_wins():
    months = pd.date_range("2024-01-31", periods=24, freq="ME")
    n = len(months)
    result = np.full(n, br.DOWN)
    result[::2] = br.UP
    k = np.ones(n, int)
    s = br.Study("t", horizon=1)
    flag = np.zeros(n, bool)
    flag[::2] = True                                    # flag only the bars that turn out UP
    s.add_pair("EURUSD", months, result, k, np.zeros(n, bool), flag, np.ones(n, bool))
    r = s.lift(n_boot=200)
    assert r["flagged_rate"] == 1.0 and abs(r["baseline_rate"] - 0.5) < 1e-9
    assert abs(r["lift"] - 0.5) < 1e-9
    assert r["ci_lo"] > 0


def test_bootstrap_is_deterministic_for_a_fixed_seed():
    s = _study(None)
    a, b = s.lift(n_boot=300, seed=1), s.lift(n_boot=300, seed=1)
    assert a == b


def test_no_events_gives_nan_lift_not_a_crash():
    n = 30
    s = br.Study("t", horizon=1)
    dates = pd.date_range("2024-01-01", periods=n)
    s.add_pair("EURUSD", dates, np.full(n, br.UP), np.ones(n, int), np.zeros(n, bool),
               np.zeros(n, bool), np.ones(n, bool))
    r = s.lift(n_boot=10)
    assert r["n_events"] == 0 and np.isnan(r["lift"])


if __name__ == "__main__":
    import sys
    import pytest
    sys.exit(pytest.main([__file__, "-q"]))
