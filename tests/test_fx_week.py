"""
ATOM FX — fx_week.py tests (the fix for BUILD_STATUS outstanding item 18).

Synthetic H1 only — no network. The property that matters most: the frozen aggregator, fed the prepared frame, has the D1 depth
its scorer needs (>= 210 bars) — which it does NOT have from the fetch window alone once closed-market rows are dropped.

Run:  python -m tests.test_fx_week      (or: pytest tests/test_fx_week.py)
"""
import numpy as np
import pandas as pd

from scanner.aggregator import build_tfs                  # frozen, read-only
from scanner.extend import fx_week as fw

MIN_D1 = 210                                              # score_pair's own minimum


def _prep(*a, **k):
    """prepare_h1 on a frame whose labels are ALREADY UTC (the synthetic frames below)."""
    return fw.prepare_h1(*a, source_tz=None, **k)


def _h1(start_utc, end_utc, seed=0):
    """Hourly UTC H1 for EVERY hour (as Twelvedata now returns them): open-market hours random-walk, closed-market hours
    flat at the previous close. Datetimes are the strings the fetch returns."""
    idx = pd.date_range(start_utc, end_utc, freq="h", tz="UTC", inclusive="left")
    ny = idx.tz_convert("America/New_York")
    wd, hr = ny.weekday, ny.hour
    closed = (wd == 5) | ((wd == 6) & (hr < 17)) | ((wd == 4) & (hr >= 17))
    rng = np.random.default_rng(seed)
    step = np.where(closed, 0.0, rng.normal(0, 0.0004, len(idx)))
    close = 1.10 + np.cumsum(step)
    open_ = np.concatenate(([1.10], close[:-1]))
    spread = np.where(closed, 0.0, rng.uniform(0, 0.0003, len(idx)))
    return pd.DataFrame({"datetime": idx.strftime("%Y-%m-%d %H:%M:%S"), "open": open_,
                         "high": np.maximum(open_, close) + spread, "low": np.minimum(open_, close) - spread, "close": close})


# ── the rule ─────────────────────────────────────────────────────────────────────
def test_closed_market_mask_boundaries_in_winter_and_summer():
    # winter (EST): Fri 17:00 ET = 22:00 UTC, Sun 17:00 ET = 22:00 UTC. summer (EDT): 21:00 UTC.
    rows = ["2026-01-09 21:00", "2026-01-09 22:00", "2026-01-10 12:00", "2026-01-11 21:00", "2026-01-11 22:00",
            "2026-07-10 20:00", "2026-07-10 21:00", "2026-07-12 20:00", "2026-07-12 21:00"]
    df = pd.DataFrame({"datetime": [r + ":00" for r in rows]})
    assert list(fw.closed_market_mask(df)) == [False, True, True, True, False, False, True, True, False]


def test_drop_closed_keeps_only_the_real_week_and_resets_the_index():
    h1 = _h1("2026-03-01 00:00", "2026-03-15 00:00")
    d = fw.drop_closed(h1)
    assert 0 < len(d) < len(h1) and list(d.index) == list(range(len(d)))
    assert not fw.closed_market_mask(d).any()
    assert fw.drop_closed(None) is None


def test_migration_detection():
    assert fw.is_migration_scan({"updated": "x"}) is True                        # old-convention signals.json
    assert fw.is_migration_scan({"bars_convention": "fx_week_v0"}) is True
    assert fw.is_migration_scan({"bars_convention": fw.BARS_CONVENTION}) is False
    assert fw.is_migration_scan({}) is False and fw.is_migration_scan(None) is False   # first-ever run: nothing to compare


# ── the depth trap, and the store that solves it ─────────────────────────────────
def _window(end="2026-09-19 10:00", rows=5000):
    """The last `rows` H1 rows before `end` from a long phantom-laden stream — what one scan's fetch returns."""
    full = _h1("2025-06-01 00:00", end, seed=7)
    return full.tail(rows).reset_index(drop=True), full


def test_the_fetch_window_alone_is_too_shallow_for_the_frozen_d1_scorer_once_closed_rows_are_dropped():
    fresh, _ = _window()
    live_d1 = len(build_tfs(fresh)["d1"])
    filtered_d1 = len(build_tfs(fw.drop_closed(fresh))["d1"])
    assert live_d1 >= MIN_D1 - 2 and filtered_d1 < MIN_D1, (live_d1, filtered_d1)   # the reason the store exists


def test_prepare_with_a_store_gives_the_frozen_d1_scorer_its_depth(tmp_path):
    fresh, full = _window()
    older = fw.merge_history(pd.DataFrame(columns=fw._COLS), fw._clean(full.head(len(full) - 1500)))   # history that has aged out of the fetch
    fw._write_history("EURUSD", older, tmp_path)
    out = _prep("EURUSD", fresh, use_store=True, store_dir=tmp_path)
    tfs = build_tfs(out)
    assert len(out) == fw.PIPELINE_ROWS and len(tfs["d1"]) >= MIN_D1, len(tfs["d1"])
    assert not fw.closed_market_mask(out).any()


def test_prepare_extends_the_store_and_is_idempotent(tmp_path):
    fresh, _ = _window(end="2026-09-16 10:00")                                # a Wednesday: the market is open
    _prep("EURUSD", fresh, store_dir=tmp_path)
    first = (tmp_path / "EURUSD.csv").read_bytes()
    _prep("EURUSD", fresh, store_dir=tmp_path)                        # same input again -> file untouched
    assert (tmp_path / "EURUSD.csv").read_bytes() == first
    later = _h1("2025-06-01 00:00", "2026-09-16 14:00", seed=7).tail(5000).reset_index(drop=True)
    _prep("EURUSD", later, store_dir=tmp_path)                        # four newer hours -> appended, nothing lost
    stored = fw.load_history("EURUSD", tmp_path)
    assert stored["datetime"].is_monotonic_increasing and stored["datetime"].is_unique
    assert stored["datetime"].iloc[-1] >= "2026-09-16 13:00:00"
    assert len(stored) > 3500


def test_a_refetched_bar_replaces_the_stored_one():
    stored = pd.DataFrame({"datetime": ["2026-09-18 10:00:00", "2026-09-18 11:00:00"], "open": [1.0, 1.0],
                           "high": [1.1, 1.1], "low": [0.9, 0.9], "close": [1.05, 1.06]})
    fresh = pd.DataFrame({"datetime": ["2026-09-18 11:00:00", "2026-09-18 12:00:00"], "open": [1.0, 1.0],
                          "high": [1.2, 1.1], "low": [0.9, 0.9], "close": [1.09, 1.07]})
    m = fw.merge_history(stored, fresh)
    assert list(m["close"]) == [1.05, 1.09, 1.07]                            # the 11:00 bar took the fresh value


def test_the_store_is_trimmed_to_store_rows(tmp_path):
    fresh, _ = _window()
    _prep("EURUSD", fresh, store_dir=tmp_path, store_rows=1200, pipeline_rows=1000)
    assert len(fw.load_history("EURUSD", tmp_path)) == 1200


def test_no_store_still_filters_and_a_pair_without_a_store_is_fine(tmp_path):
    fresh, _ = _window()
    out = _prep("EURGBP", fresh, use_store=False, store_dir=tmp_path)
    assert not fw.closed_market_mask(out).any() and not (tmp_path / "EURGBP.csv").exists()


# ── never break a scan ───────────────────────────────────────────────────────────
def test_a_corrupt_store_degrades_to_the_filtered_fetch_and_never_raises(tmp_path):
    (tmp_path / "EURUSD.csv").write_text("this,is,not\nvalid,h1,data\n")
    fresh, _ = _window()
    out = _prep("EURUSD", fresh, store_dir=tmp_path)
    assert len(out) > 1000 and not fw.closed_market_mask(out).any()


def test_a_broken_frame_falls_back_to_the_untouched_input():
    junk = pd.DataFrame({"unrelated": [1, 2, 3]})
    assert _prep("EURUSD", junk, use_store=False).equals(junk)


def test_a_broken_frame_that_cannot_even_be_filtered_falls_back_too():
    assert _prep("EURUSD", None, use_store=False) is None


# ── the frozen numbers change in exactly the intended way ────────────────────────
def test_prepared_bars_contain_no_flat_closed_market_days():
    fresh, _ = _window()
    live = build_tfs(fresh)["d1"]
    real = build_tfs(_prep("EURUSD", fresh, use_store=False))["d1"]
    flat = lambda d: int(((d["high"] - d["low"]) <= 1e-9).sum())
    assert flat(live) >= 20 and flat(real) == 0                               # phantom days out, none left flat


# ── the wiring ───────────────────────────────────────────────────────────────────
# ── the timestamps: Twelvedata's hourly labels are Sydney local time ────────────────────────────
def test_to_utc_labels_reproduces_the_tradingview_candles_that_exposed_the_offset():
    """Each pair is (our raw Twelvedata label, the true UTC open of the same candle on TradingView/OANDA)."""
    raw = ["2026-06-11 03:00:00",    # AEST +10: AUDUSD 4H bar TradingView calls 'Wed 10 Jun 2026 17:00' UTC (0.8 pips)
           "2026-09-19 03:00:00",    # AEST +10: GBPUSD last 4H bar of the week, 'Fri 18 Sep 2026 17:00' UTC (2.1 pips)
           "2026-05-18 07:00:00",    # AEST +10: AUDUSD first 4H bar of the week, 'Sun 17 May 2026 21:00' UTC
           "2020-03-11 08:00:00"]    # AEDT +11: AUDUSD D1 bar 'Wed 11 Mar 2020' opens Tue 10 Mar 21:00 UTC (3.1 pips)
    utc = ["2026-06-10 17:00:00", "2026-09-18 17:00:00", "2026-05-17 21:00:00", "2020-03-10 21:00:00"]
    assert list(fw.to_utc_labels(raw)) == utc


def test_normalize_frame_converts_to_utc_then_drops_closed_market_rows():
    utc_frame = _h1("2026-03-02 00:00", "2026-03-16 00:00")                        # Mar 2026: Sydney is on AEDT (+11)
    sydney = utc_frame.copy()
    sydney["datetime"] = (pd.to_datetime(sydney["datetime"]).dt.tz_localize("UTC").dt.tz_convert("Australia/Sydney")
                          .dt.strftime("%Y-%m-%d %H:%M:%S"))
    out = fw.normalize_frame(sydney)
    expected = fw.drop_closed(utc_frame)
    assert list(out["datetime"]) == list(expected["datetime"])
    assert not fw.closed_market_mask(out).any()
    # and it must NOT be the naive reading: treating the Sydney labels as UTC keeps a different set of rows
    naive = fw.drop_closed(sydney)
    assert list(naive["datetime"]) != list(expected["datetime"])


def test_prepare_h1_defaults_to_the_sydney_source():
    raw = pd.DataFrame({"datetime": ["2026-06-11 03:00:00", "2026-06-11 04:00:00"], "open": [1.0, 1.0], "high": [1.1, 1.1],
                        "low": [0.9, 0.9], "close": [1.0, 1.0]})
    out = fw.prepare_h1("EURUSD", raw, use_store=False)
    assert list(out["datetime"]) == ["2026-06-10 17:00:00", "2026-06-10 18:00:00"]


# ── the wiring ───────────────────────────────────────────────────────────────────
def test_scan_h1_and_m15_are_wired_to_fx_week_and_mark_their_output():
    import scanner.scan_h1 as h1
    import scanner.scan_m15 as m15
    assert h1._FX_WEEK_ENABLED is True and h1._fx_week is fw                          # switched back on, import succeeded
    assert m15._FX_WEEK_ENABLED is True and m15._fx_week is fw
    src = open(h1.__file__, encoding="utf-8").read()
    assert "_fx_week.prepare_h1(key, df, use_store=(pair in PAIRS))" in src            # fetch loop hands the pipeline UTC bars
    assert '"bars_convention"' in src and "_migration_scan" in src                     # marker + one-scan alert guard present
    assert fw.BARS_CONVENTION == "fx_week_v3"


# ── the New York trading clock (uniform D1/H4 with TradingView) ─────────────────────────────────
def test_to_trading_clock_labels_the_ny_close_as_midnight_and_handles_dst():
    utc = pd.DataFrame({"datetime": ["2026-06-14 21:00:00",     # Sun 17:00 EDT: the week's first hour -> Monday 00:00
                                     "2026-06-12 20:00:00",     # Fri 16:00 EDT: the week's last hour  -> Friday 23:00
                                     "2026-01-11 22:00:00",     # Sun 17:00 EST (winter)               -> Monday 00:00
                                     "2026-01-09 21:00:00"],    # Fri 16:00 EST                        -> Friday 23:00
                        "open": 1.0, "high": 1.0, "low": 1.0, "close": 1.0})
    assert list(fw.to_trading_clock(utc)["datetime"]) == ["2026-06-15 00:00:00", "2026-06-12 23:00:00", "2026-01-12 00:00:00", "2026-01-09 23:00:00"]


def test_the_frozen_aggregator_on_the_trading_clock_has_no_stub_candles():
    """Two full June weeks: on UTC labels the frozen aggregator makes Sunday stub D1 candles and short H4 blocks; on the trading clock it makes exactly
    5 D1 candles a week (Mon-Fri) and 30 complete 4-hour blocks a week."""
    week = fw.drop_closed(_h1("2026-06-07 00:00", "2026-06-21 00:00"))          # Sun 7 Jun .. Sun 21 Jun (UTC): two real FX weeks
    utc_d1 = build_tfs(week)["d1"]
    ny = build_tfs(fw.to_trading_clock(week))
    dates = pd.DatetimeIndex(pd.to_datetime(ny["d1"]["datetime"] if "datetime" in ny["d1"].columns else ny["d1"].index))
    assert len(ny["d1"]) == 10 and set(dates.dayofweek) <= {0, 1, 2, 3, 4}
    assert len(utc_d1) > len(ny["d1"])                                          # the UTC clock leaves Sunday stubs
    assert len(ny["h4"]) == 60


if __name__ == "__main__":
    import sys
    import pytest
    sys.exit(pytest.main([__file__, "-q"]))
