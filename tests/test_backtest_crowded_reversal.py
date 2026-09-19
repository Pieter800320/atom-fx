"""
ATOM FX research — tools/backtest_crowded_reversal.py tests (Experiment 3 additions).

Synthetic only. Covers the H4 bar loader and the flagged-event gate logic, so a gate cannot silently
pass or fail for the wrong reason.

Run:  python -m tests.test_backtest_crowded_reversal      (or: pytest tests/test_backtest_crowded_reversal.py)
"""
import numpy as np
import pandas as pd

from scanner.extend import barrier_race as br
from tools import backtest_crowded_reversal as bt


def test_h4_loader_uses_trading_day_for_cot_and_datetime_for_the_bar(tmp_path):
    (tmp_path / "EURUSD.csv").write_text(
        "datetime,open,high,low,close,n_h1,trading_day\n"
        "2026-01-11 20:00:00,1.1,1.2,1.0,1.15,2,2026-01-12\n"      # Sunday reopen stub -> Monday's COT week
        "2026-01-12 00:00:00,1.15,1.2,1.1,1.16,4,2026-01-12\n")
    df = bt.load_bars("EURUSD", "h4utc", tmp_path)
    assert list(df.columns[:5]) == ["date", "open", "high", "low", "close"]
    assert df["date"].iloc[0] == pd.Timestamp("2026-01-11 20:00")
    assert df["cot_day"].iloc[0] == pd.Timestamp("2026-01-12")           # Sunday stub picks Monday's week


def test_native_daily_loader_drops_weekend_fragments_and_uses_the_date_for_cot(tmp_path):
    (tmp_path / "EURUSD.csv").write_text(
        "date,open,high,low,close\n"
        "2026-09-17,1.10,1.11,1.09,1.105\n"      # Thu
        "2026-09-18,1.105,1.12,1.10,1.11\n"      # Fri
        "2026-09-19,1.11,1.11,1.11,1.11\n"       # Sat fragment
        "2026-09-20,1.11,1.11,1.11,1.11\n"       # Sun fragment
        "2026-09-21,1.11,1.13,1.10,1.12\n")      # Mon
    df = bt.load_bars("EURUSD", "d1utc", tmp_path)
    assert list(df["date"].dt.dayofweek) == [3, 4, 0]
    assert (df["cot_day"] == df["date"]).all()


def _flag_study(n_pairs, wins, n_events_per_pair, base_up=0.5):
    """Study with planted results. Each pair: 240 monthly-ish bars; flagged events all 'bottom'.
    Baseline: up-first on `base_up` of bars; flagged events succeed on `wins` fraction."""
    rng = np.random.default_rng(1)
    s = br.Study("t", horizon=1)
    for i in range(n_pairs):
        n = 600
        dates = pd.date_range("2020-01-31", periods=n, freq="7D")
        result = np.where(rng.random(n) < base_up, br.UP, br.DOWN)
        k = np.ones(n, int)
        flag_idx = np.arange(0, n, n // n_events_per_pair)[:n_events_per_pair]
        # make the flagged bars win with probability `wins`
        for j, ix in enumerate(flag_idx):
            result[ix] = br.UP if rng.random() < wins else br.DOWN
        flag = np.zeros(n, bool)
        flag[flag_idx] = True
        s.add_pair(f"PAIR{i}" if i else "EURUSD", dates, result, k, np.zeros(n, bool), flag, np.ones(n, bool))
    return s


def _studies(main_wins, n_events_per_pair):
    studies = {"score off": _flag_study(6, main_wins, n_events_per_pair)}
    for f in bt.SINGLE_FACTORS:
        studies[f"single {f}"] = _flag_study(6, 0.5, n_events_per_pair)     # single factors: no edge
    return studies


def test_flag_gates_pass_when_the_composite_clearly_beats_baseline_and_singles():
    res = bt.evaluate_flags(_studies(main_wins=0.85, n_events_per_pair=40), ["off"], n_boot=300)
    r = res["arms"]["off"]
    assert r["overall"]["n_events"] >= 100
    assert bt.verdict_flags(r, "full") == "PASS", r["checks"]


def test_flag_verdict_is_inconclusive_below_the_event_floor_whatever_the_lift():
    res = bt.evaluate_flags(_studies(main_wins=0.95, n_events_per_pair=5), ["off"], n_boot=100)
    r = res["arms"]["off"]
    assert r["overall"]["n_events"] < 100
    assert bt.verdict_flags(r, "full").startswith("INCONCLUSIVE")


def test_flag_verdict_fails_when_there_is_no_edge():
    res = bt.evaluate_flags(_studies(main_wins=0.5, n_events_per_pair=40), ["off"], n_boot=300)
    v = bt.verdict_flags(res["arms"]["off"], "full")
    assert v.startswith("FAIL")


def test_smoke_mode_never_issues_a_verdict():
    res = bt.evaluate_flags(_studies(main_wins=0.9, n_events_per_pair=40), ["off"], n_boot=100)
    assert bt.verdict_flags(res["arms"]["off"], "smoke").startswith("PLUMBING ONLY")


if __name__ == "__main__":
    import sys
    import pytest
    sys.exit(pytest.main([__file__, "-q"]))
