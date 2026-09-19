"""
ATOM FX research — level_race.py tests (Experiment 4: "back to the previous support/resistance").

Synthetic, hand-checkable fixtures only — no network. The two properties that matter most are pinned
down explicitly: (1) levels never use a swing that was not yet confirmed at that bar (no look-ahead),
(2) the baseline is matched on distance, so a flagged bar is not penalised for sitting far from its level.

Run:  python -m tests.test_level_race      (or: pytest tests/test_level_race.py)
"""
import numpy as np
import pandas as pd

from scanner.extend import level_race as lr
from tools import backtest_crowded_reversal as bt


# ── levels ───────────────────────────────────────────────────────────────────────
def _series(n=40, base_high=100.1, base_low=99.0):
    high = np.full(n, base_high)
    low = np.full(n, base_low)
    close = np.full(n, 100.0)
    atr = np.full(n, 2.0)                       # band for resistance: 101 .. 110
    return high, low, close, atr


def test_level_is_unavailable_until_the_swing_is_confirmed():
    high, low, close, atr = _series()
    high[10] = 103.0                            # a clean swing high at bar 10 (swing_n=2 -> confirmed at bar 12)
    up, _ = lr.level_targets(high, low, close, atr, swing_n=2)
    assert np.isnan(up[11]), "bar 11 must not see a swing that only bars 12+ can confirm"
    assert up[12] == 103.0 and up[30] == 103.0


def test_most_recent_qualifying_swing_wins():
    high, low, close, atr = _series()
    high[8], high[16] = 103.0, 105.0
    up, _ = lr.level_targets(high, low, close, atr, swing_n=2)
    assert up[20] == 105.0                      # the newer swing, not the older one
    assert up[15] == 103.0                      # before 105 is confirmed (bar 18), the older one still stands


def test_swings_closer_than_min_distance_or_farther_than_max_are_skipped():
    high, low, close, atr = _series()
    high[8] = 103.0                             # in band
    high[16] = 100.5                            # 0.25 ATR above -> too close (< 0.5 ATR)
    high[24] = 115.0                            # 7.5 ATR above -> too far (> 5 ATR)
    up, _ = lr.level_targets(high, low, close, atr, swing_n=2)
    assert up[30] == 103.0


def test_lookback_limits_how_old_a_level_may_be():
    high, low, close, atr = _series(n=60)
    high[8] = 103.0
    up, _ = lr.level_targets(high, low, close, atr, swing_n=2, lookback=20)
    assert up[25] == 103.0 and np.isnan(up[40])


def test_support_below_is_the_mirror_image():
    high, low, close, atr = _series()
    low[10] = 97.0                              # swing low 1.5 ATR below the close
    _, down = lr.level_targets(high, low, close, atr, swing_n=2)
    assert np.isnan(down[11]) and down[12] == 97.0


# ── the 1:1 race ─────────────────────────────────────────────────────────────────
def _race(rows, up_level=102.0, down_level=98.0, horizon=3):
    high = np.array([r[0] for r in rows], float)
    low = np.array([r[1] for r in rows], float)
    close = np.full(len(rows), 100.0)
    atr = np.full(len(rows), 2.0)
    up = np.full(len(rows), up_level)
    dn = np.full(len(rows), down_level)
    return lr.level_race_all(high, low, close, atr, up, dn, horizon=horizon)


def test_bottom_side_success_stop_both_timeout():
    ok = _race([(100, 100), (101, 99), (102.5, 99), (100, 100), (100, 100)])
    assert ok["code_b"][0] == lr.SUCCESS and ok["k_b"][0] == 2 and abs(ok["d_b"][0] - 1.0) < 1e-12
    stopped = _race([(100, 100), (101, 99), (101.5, 97.9), (100, 100), (100, 100)])
    assert stopped["code_b"][0] == lr.STOP
    both = _race([(100, 100), (102.0, 98.0), (100, 100), (100, 100), (100, 100)])
    assert both["code_b"][0] == lr.BOTH and both["code_t"][0] == lr.BOTH      # same-bar both -> failure
    timeout = _race([(100, 100), (101, 99), (101, 99), (101, 99), (100, 100), (100, 100)])
    assert timeout["code_b"][0] == lr.TIMEOUT and timeout["k_b"][0] == 3


def test_top_side_target_is_below_and_stop_above():
    r = _race([(100, 100), (101, 99), (101.5, 97.5), (100, 100), (100, 100)])
    assert r["code_t"][0] == lr.SUCCESS and r["k_t"][0] == 2
    r = _race([(100, 100), (101, 99), (102.5, 99), (100, 100), (100, 100)])
    assert r["code_t"][0] == lr.STOP


def test_invalid_when_no_level_or_no_forward_bars():
    r = _race([(100, 100)] * 6, up_level=np.nan, down_level=np.nan)
    assert (r["code_b"] == lr.INVALID).all() and (r["code_t"] == lr.INVALID).all()
    r = _race([(100, 100), (101, 99), (101, 99), (101, 99), (100, 100)], horizon=3)
    assert r["code_b"][-1] == lr.INVALID and r["code_b"][-3] == lr.INVALID


def test_distance_bucket_edges():
    assert list(lr.d_bucket(np.array([0.5, 0.99, 1.0, 1.99, 2.0, 2.99, 3.0, 5.0]))) == [0, 0, 1, 1, 2, 2, 3, 3]


# ── the matched baseline ─────────────────────────────────────────────────────────
def _synthetic_lv(n=6000, seed=0):
    """Near levels (bucket 0) succeed 80%; far levels (bucket 3) succeed 10%. Half the bars each."""
    rng = np.random.default_rng(seed)
    near = np.arange(n) % 2 == 0
    d = np.where(near, 0.7, 4.0)
    p = np.where(near, 0.8, 0.1)
    code = np.where(rng.random(n) < p, lr.SUCCESS, lr.STOP)
    lv = {"code_b": code.copy(), "k_b": np.ones(n, int), "d_b": d,
          "code_t": np.full(n, lr.INVALID), "k_t": np.zeros(n, int), "d_t": np.full(n, np.nan)}
    return lv, near


def test_baseline_is_matched_on_distance_so_flagged_near_bars_are_not_credited_for_being_near():
    lv, near = _synthetic_lv()
    n = len(near)
    dates = pd.date_range("2020-01-31", periods=n, freq="D")
    flag = np.zeros(n, bool)
    flag[np.flatnonzero(near)[::15]] = True                # flag only NEAR bars — no genuine edge
    s = lr.LevelStudy("t", horizon=1)
    s.add_pair("EURUSD", dates, lv, np.zeros(n, bool), flag, np.ones(n, bool))
    r = s.lift(n_boot=0)
    assert r["n_events"] > 100
    # the matched baseline for near bars is ~80%, so the lift is ~0 — a POOLED baseline (~45%) would
    # have shown a spurious +35 points
    assert abs(r["baseline_rate"] - 0.8) < 0.03
    assert abs(r["lift"]) < 0.06


def test_a_genuine_edge_inside_a_bucket_is_still_detected():
    lv, near = _synthetic_lv()
    n = len(near)
    flag = np.zeros(n, bool)
    winners = np.flatnonzero(near & (lv["code_b"] == lr.SUCCESS))[::10]
    flag[winners] = True                                   # flag only near bars that WILL succeed
    dates = pd.date_range("2020-01-31", periods=n, freq="D")
    s = lr.LevelStudy("t", horizon=1)
    s.add_pair("EURUSD", dates, lv, np.zeros(n, bool), flag, np.ones(n, bool))
    r = s.lift(n_boot=200)
    assert r["flagged_rate"] == 1.0 and r["lift"] > 0.15 and r["ci_lo"] > 0


def test_flags_without_a_valid_level_are_excluded_and_counted():
    lv, near = _synthetic_lv(n=200)
    lv["code_b"][10] = lr.INVALID
    flag = np.zeros(200, bool)
    flag[10] = flag[50] = True
    dates = pd.date_range("2020-01-31", periods=200, freq="D")
    s = lr.LevelStudy("t", horizon=1)
    s.add_pair("EURUSD", dates, lv, np.zeros(200, bool), flag, np.ones(200, bool))
    assert s.censored_flags == 1 and len(s.events) == 1 and s.events[0]["date"] == dates[50]


def test_declustering_and_same_bar_conflicts():
    n = 100
    lv = {"code_b": np.full(n, lr.TIMEOUT), "k_b": np.full(n, 5), "d_b": np.full(n, 1.5),
          "code_t": np.full(n, lr.TIMEOUT), "k_t": np.full(n, 5), "d_t": np.full(n, 1.5)}
    dates = pd.date_range("2020-01-31", periods=n, freq="D")
    top, bot = np.zeros(n, bool), np.zeros(n, bool)
    bot[[0, 1, 2, 6]] = True                               # event at 0 busy through 5 -> bars 1,2 skipped; 6 free
    top[[20]] = bot[[20]] = True                           # conflict
    s = lr.LevelStudy("t", horizon=5)
    s.add_pair("EURUSD", dates, lv, top, bot, np.ones(n, bool))
    assert [e["date"] for e in s.events] == [dates[0], dates[6]] and s.conflicts == 1


def test_interface_is_compatible_with_the_pre_registered_gate_logic():
    lv, near = _synthetic_lv(n=3000)
    n = len(near)
    dates = pd.date_range("2020-01-31", periods=n, freq="D")
    flag = np.zeros(n, bool)
    flag[np.flatnonzero(near)[::12]] = True
    studies = {}
    for name in ["score off"] + [f"single {f}" for f in bt.SINGLE_FACTORS]:
        st = lr.LevelStudy(name, horizon=1)
        for pair in ("EURUSD", "GBPJPY"):
            st.add_pair(pair, dates, lv, np.zeros(n, bool), flag, np.ones(n, bool))
        studies[name] = st
    res = bt.evaluate_flags(studies, ["off"], n_boot=50)
    assert "off" in res["arms"] and res["arms"]["off"]["overall"]["n_events"] > 0
    assert bt.verdict_flags(res["arms"]["off"], "full") in ("PASS",) or bt.verdict_flags(res["arms"]["off"], "full").startswith(("FAIL", "INCONCLUSIVE"))


if __name__ == "__main__":
    import sys
    import pytest
    sys.exit(pytest.main([__file__, "-q"]))
