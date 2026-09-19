"""
ATOM FX research — agg_h4.py tests.

Synthetic H1 only — no network. Proves (1) the 'utc' alignment reproduces the APP's frozen
scanner.aggregator.aggregate_h4 exactly on clean data, (2) closed-market bars are dropped,
(3) the Sunday reopen stub and its trading-day label, (4) the 'ny' alignment's 17:00-NY blocks.

Run:  python -m tests.test_agg_h4      (or: pytest tests/test_agg_h4.py)
"""
import numpy as np
import pandas as pd

from scanner.aggregator import aggregate_h4 as app_aggregate_h4          # frozen, read-only
from scanner.extend.agg_h4 import aggregate_h4_ts


def _h1(start, hours, seed=0):
    """UTC-hourly synthetic H1, random-walk prices. `datetime` as the strings Twelvedata returns."""
    rng = np.random.default_rng(seed)
    idx = pd.date_range(start, periods=hours, freq="h")
    close = 1.10 + np.cumsum(rng.normal(0, 0.0005, hours))
    open_ = np.concatenate(([1.10], close[:-1]))
    high = np.maximum(open_, close) + rng.uniform(0, 0.0003, hours)
    low = np.minimum(open_, close) - rng.uniform(0, 0.0003, hours)
    return pd.DataFrame({"datetime": idx.strftime("%Y-%m-%d %H:%M:%S"), "open": open_,
                         "high": high, "low": low, "close": close})


def test_utc_alignment_reproduces_the_apps_frozen_aggregator_on_open_market_hours():
    # Tue 2026-03-03 00:00 UTC for 48h = Tue+Wed: all inside the FX week, no closed hours.
    h1 = _h1("2026-03-03 00:00", 48)
    mine = aggregate_h4_ts(h1, "utc")
    app = app_aggregate_h4(h1)
    assert len(mine) == len(app) == 12
    for col in ("open", "high", "low", "close"):
        assert np.allclose(mine[col].to_numpy(), app[col].to_numpy(), atol=0, rtol=0)
    assert mine["datetime"].iloc[0] == pd.Timestamp("2026-03-03 00:00")
    assert (mine["n_h1"] == 4).all()


def test_closed_market_bars_are_dropped():
    # Fri 2026-03-06 15:00 UTC (10:00 ET, open) .. Mon 2026-03-09 03:00 UTC covers the weekend close.
    h1 = _h1("2026-03-06 15:00", 60)
    ts = aggregate_h4_ts(h1, "utc")
    starts = pd.DatetimeIndex(ts["datetime"])
    # nothing may start on Saturday, and nothing on Sunday before the 17:00 ET (22:00 UTC in March, EST->EDT
    # switch is 2026-03-08, so Sunday 17:00 ET = 21:00 UTC) reopen
    assert not (starts.dayofweek == 5).any()
    sunday = starts[starts.dayofweek == 6]
    assert (sunday >= pd.Timestamp("2026-03-08 20:00")).all()


def test_sunday_reopen_stub_is_kept_and_rolls_into_mondays_trading_day():
    # Winter (EST): Sunday 17:00 ET = 22:00 UTC. H1 bars 22:00 and 23:00 UTC form a 2-bar stub in the
    # 20:00 UTC block. Data: Sun 2026-01-11 20:00 UTC through Mon 02:00 UTC.
    h1 = _h1("2026-01-11 20:00", 8)
    ts = aggregate_h4_ts(h1, "utc")
    stub = ts.iloc[0]
    assert stub["datetime"] == pd.Timestamp("2026-01-11 20:00") and stub["n_h1"] == 2
    assert stub["trading_day"] == pd.Timestamp("2026-01-12")            # Monday, not Sunday


def test_friday_close_stub():
    # Winter: Friday 17:00 ET = 22:00 UTC. The 20:00 UTC block keeps H1 20:00 and 21:00 only.
    h1 = _h1("2026-01-09 16:00", 8)                                     # Fri 16:00 .. Sat 00:00 UTC
    ts = aggregate_h4_ts(h1, "utc")
    last = ts.iloc[-1]
    assert last["datetime"] == pd.Timestamp("2026-01-09 20:00") and last["n_h1"] == 2


def test_ny_alignment_blocks_start_at_17_00_new_york_and_are_full():
    # A full clean trading day in winter: Mon 2026-01-12 17:00 ET = 22:00 UTC, through Tue 17:00 ET.
    h1 = _h1("2026-01-12 22:00", 24)
    ts = aggregate_h4_ts(h1, "ny")
    assert len(ts) == 6 and (ts["n_h1"] == 4).all()
    hours = [t.hour for t in pd.DatetimeIndex(ts["datetime"])]
    assert hours == [17, 21, 1, 5, 9, 13]                              # NY wall-clock block starts
    assert (ts["trading_day"] == pd.Timestamp("2026-01-13")).all()      # all belong to Tuesday's session


def test_ny_alignment_has_no_stubs_over_a_whole_week():
    # Sunday 22:00 UTC (17:00 ET winter) .. Friday 22:00 UTC (17:00 ET)
    h1 = _h1("2026-01-11 22:00", 5 * 24)
    ts = aggregate_h4_ts(h1, "ny")
    assert len(ts) == 30 and (ts["n_h1"] == 4).all()


def test_bad_alignment_raises():
    try:
        aggregate_h4_ts(_h1("2026-03-03 00:00", 8), "bogus")
    except ValueError:
        return
    raise AssertionError("expected ValueError")


if __name__ == "__main__":
    import sys
    import pytest
    sys.exit(pytest.main([__file__, "-q"]))
