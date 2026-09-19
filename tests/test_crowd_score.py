"""
ATOM FX — Crowd score tests (scanner/extend/crowd_data.py + crowd_score.py, Signals Roadmap §5c).

Synthetic fixtures only — no network. The important ones:
  * golden parity with the research branch's tested port (tests/golden/crowd_score_golden.json);
  * closed-market bars never enter a bar (the filter `bb_touch` does not have — outstanding item 18);
  * completed bars only, and exactly when a bar counts as complete (the "fires on time" rule);
  * the COT no-look-ahead alignment (a bar reads the report dated the Tuesday of the PREVIOUS week);
  * attaching the series leaves every other key byte-identical.

Run:  python -m tests.test_crowd_score      (or: pytest tests/test_crowd_score.py)
"""
import io
import json
import zipfile
from pathlib import Path

import numpy as np
import pandas as pd

from scanner.extend import crowd_data as cd
from scanner.extend import crowd_score as cs

GOLDEN = Path(__file__).parent / "golden" / "crowd_score_golden.json"
FAR_FUTURE = pd.Timestamp("2099-01-01", tz="UTC")


# ── synthetic H1 ─────────────────────────────────────────────────────────────────
def _h1(start_utc, end_utc, seed=0, with_closed=False):
    """Hourly UTC H1 bars random walk. Only bars inside the real FX week [Sun 17:00 ET, Fri 17:00 ET) are kept —
    unless with_closed, which keeps EVERY hour (the flat market-closed bars Twelvedata emits)."""
    idx = pd.date_range(start_utc, end_utc, freq="h", tz="UTC", inclusive="left")
    ny = idx.tz_convert("America/New_York")
    wd, hr = ny.weekday, ny.hour
    open_market = ~((wd == 5) | ((wd == 6) & (hr < 17)) | ((wd == 4) & (hr >= 17)))
    rng = np.random.default_rng(seed)
    close = 1.10 + np.cumsum(rng.normal(0, 0.0004, len(idx)))
    open_ = np.concatenate(([1.10], close[:-1]))
    high = np.maximum(open_, close) + rng.uniform(0, 0.0003, len(idx))
    low = np.minimum(open_, close) - rng.uniform(0, 0.0003, len(idx))
    df = pd.DataFrame({"datetime": idx.strftime("%Y-%m-%d %H:%M:%S"), "open": open_, "high": high, "low": low, "close": close})
    if with_closed:
        flat = ~open_market                                   # closed-market bars: flat at the previous close
        for col in ("open", "high", "low"):
            df.loc[flat, col] = df.loc[flat, "close"]
        return df
    return df[open_market].reset_index(drop=True)


# ── golden parity ────────────────────────────────────────────────────────────────
def test_scores_reproduce_the_research_ports_golden_output_exactly():
    g = json.loads(GOLDEN.read_text())
    df = pd.DataFrame({k: g[k] for k in ("open", "high", "low", "close")})
    cot = np.array([np.nan if v is None else v for v in g["cot"]])
    out = cs.compute_scores(df, cot)
    assert np.allclose(out["top_score"], g["top_score"], atol=1e-9, rtol=0)
    assert np.allclose(out["bot_score"], g["bot_score"], atol=1e-9, rtol=0)
    for k in g["factors"]:
        assert list(out[f"top_{k}"].astype(int)) == g["factors"][k], k
        assert list(out[f"bot_{k}"].astype(int)) == g["factors_bot"][k], k
    # the fixture must actually exercise the scoring, or the parity above proves nothing
    assert out["bot_score"].max() > 50 and out["top_score"].max() > 30


def test_weights_sum_to_100_and_scores_are_bounded():
    assert sum(cs.WEIGHTS.values()) == 100.0
    g = json.loads(GOLDEN.read_text())
    df = pd.DataFrame({k: g[k] for k in ("open", "high", "low", "close")})
    out = cs.compute_scores(df, None)
    assert out["top_score"].between(0, 100).all() and out["bot_score"].between(0, 100).all()


def test_without_cot_the_ceiling_is_75_and_the_cot_factor_never_fires():
    g = json.loads(GOLDEN.read_text())
    df = pd.DataFrame({k: g[k] for k in ("open", "high", "low", "close")})
    out = cs.compute_scores(df, None)
    assert out["top_score"].max() <= 75.0 and out["bot_score"].max() <= 75.0
    assert not out["top_cot"].any() and not out["bot_cot"].any()


# ── primitives ───────────────────────────────────────────────────────────────────
def test_rma_seeds_with_sma_and_pstdev_is_population():
    out = cs.rma([1, 2, 3, 4, 5], 3)
    assert np.isnan(out[:2]).all() and out[2] == 2.0 and abs(out[3] - (2.0 * 2 + 4) / 3) < 1e-12
    assert abs(cs.pstdev([1, 2, 3, 4], 4)[3] - np.sqrt(1.25)) < 1e-12


def test_percentrank_excludes_the_current_bar():
    out = cd.percentrank([1, 2, 3, 2, 5], 3)
    assert np.isnan(out[:3]).all() and abs(out[3] - 100.0 * 2 / 3) < 1e-12 and out[4] == 100.0


def test_bearish_divergence_confirms_after_the_pivot_and_is_held():
    p = {**cs.P, "div_left": 2, "div_right": 2, "div_min_gap": 2, "div_max_gap": 20, "div_hold": 3}
    rsi_v = np.array([50, 50, 52, 55, 70, 55, 52, 50, 50, 52, 60, 52, 50, 50, 50, 50], float)
    high = np.full(16, 90.0)
    high[4], high[10] = 100.0, 105.0                         # higher price high, lower RSI high
    bear, _ = cs.divergence_active(high, np.full(16, 80.0), rsi_v, p)
    assert list(np.flatnonzero(bear)) == [12, 13, 14]        # pivot 10 + right 2, held 3 bars, never earlier


# ── bars: closed-market filter, labels, alignment ────────────────────────────────
def test_within_fx_week_boundaries():
    ny = pd.DatetimeIndex(["2026-01-09 16:00", "2026-01-09 17:00",      # Fri 16:00 open / Fri 17:00 closed
                           "2026-01-10 12:00",                           # Sat closed
                           "2026-01-11 16:00", "2026-01-11 17:00"])      # Sun 16:00 closed / Sun 17:00 open
    assert list(cd.within_fx_week(ny)) == [True, False, False, False, True]


def test_market_closed_bars_do_not_change_any_bar():
    clean = _h1("2025-11-09 22:00", "2025-12-14 22:00", seed=3)
    dirty = _h1("2025-11-09 22:00", "2025-12-14 22:00", seed=3, with_closed=True)
    for kind in ("d1", "h4"):
        a = cd.completed_bars(clean, kind, FAR_FUTURE)
        b = cd.completed_bars(dirty, kind, FAR_FUTURE)
        assert len(a) == len(b) and np.allclose(a[["open", "high", "low", "close"]], b[["open", "high", "low", "close"]])


def test_d1_is_one_bar_per_trading_day_labelled_by_close_date_monday_to_friday():
    h1 = _h1("2025-11-09 22:00", "2025-11-30 22:00", seed=1)             # Sun 17:00 ET (EST) ... 3 weeks
    d1 = cd.completed_bars(h1, "d1", FAR_FUTURE)
    assert len(d1) == 15 and set(d1["trading_day"].dt.dayofweek) == {0, 1, 2, 3, 4}
    assert d1["trading_day"].iloc[0] == pd.Timestamp("2025-11-10")       # the Sunday reopen rolls into Monday


def test_h4_blocks_are_new_york_session_aligned_full_and_six_per_trading_day():
    h1 = _h1("2025-11-09 22:00", "2025-11-16 22:00", seed=2)             # one full week
    h4 = cd.completed_bars(h1, "h4", FAR_FUTURE)
    assert len(h4) == 30                                                 # 6 blocks x 5 trading days, no stubs
    assert (h4.groupby("trading_day").size() == 6).all()


def test_h4_block_times_in_new_york_and_utc():
    # winter: 17:00 ET = 22:00 UTC. First block of the week starts Sunday 22:00 UTC.
    h1 = _h1("2025-11-09 22:00", "2025-11-10 22:00", seed=4)
    h4 = cd.completed_bars(h1, "h4", FAR_FUTURE)
    assert len(h4) == 6 and h4["trading_day"].nunique() == 1
    first_h1_utc = pd.Timestamp(h1["datetime"].iloc[0], tz="UTC")
    assert first_h1_utc.tz_convert("America/New_York").hour == 17


def test_h4_and_d1_stay_whole_across_the_march_dst_change():
    # 2026-03-08 is the US spring-forward: the week of Mar 1 opens Sun 22:00 UTC (EST), the week of Mar 8 opens Sun 21:00 UTC (EDT).
    h1 = _h1("2026-03-01 22:00", "2026-03-13 21:00", seed=6)
    h4 = cd.completed_bars(h1, "h4", FAR_FUTURE)
    d1 = cd.completed_bars(h1, "d1", FAR_FUTURE)
    assert len(d1) == 10 and set(d1["trading_day"].dt.dayofweek) == {0, 1, 2, 3, 4}
    assert len(h4) == 60 and (h4.groupby("trading_day").size() == 6).all()


# ── completeness ("fires on time") ───────────────────────────────────────────────
def _week():
    return _h1("2025-11-09 22:00", "2025-11-14 22:00", seed=5)           # Sun 17:00 ET .. Fri 17:00 ET (EST)


def test_last_bar_is_dropped_until_its_close_has_passed():
    h1 = _week()
    full = cd.completed_bars(h1, "d1", FAR_FUTURE)
    # Friday's session closes 2025-11-14 22:00 UTC. One minute before -> the last D1 bar is incomplete.
    early = cd.completed_bars(h1, "d1", pd.Timestamp("2025-11-14 21:59", tz="UTC"))
    assert len(early) == len(full) - 1
    on_close = cd.completed_bars(h1, "d1", pd.Timestamp("2025-11-14 22:00", tz="UTC"))
    assert len(on_close) == len(full)


def test_last_bar_needs_its_final_hour_present_even_after_the_close():
    h1 = _week()
    missing_last_hour = h1.iloc[:-1]                                     # provider has not published the last H1 bar yet
    d1 = cd.completed_bars(missing_last_hour, "d1", FAR_FUTURE)
    assert d1["trading_day"].iloc[-1] == pd.Timestamp("2025-11-13")      # Friday's bar is held back


def test_an_earlier_bar_is_complete_because_a_later_one_exists():
    h1 = _week()
    h4 = cd.completed_bars(h1, "h4", pd.Timestamp("2025-11-11 01:30", tz="UTC"))     # mid-week, many bars exist
    assert len(h4) > 5                                                   # only the LAST block can ever be withheld


# ── COT: alignment, refresh ──────────────────────────────────────────────────────
def _reports():
    return pd.DataFrame({"date": pd.to_datetime(["2026-09-01", "2026-09-08", "2026-09-15"]), "net": [50.0, 100.0, 200.0]})


def test_a_bar_reads_the_report_dated_the_tuesday_of_the_previous_week_never_later():
    cal = pd.bdate_range("2026-09-07", "2026-09-25")
    net, asof = cd._net_daily(_reports(), cal, 1)
    wk = lambda start: net[start: pd.Timestamp(start) + pd.Timedelta(days=4)]
    assert (wk("2026-09-07") == 50.0).all()
    assert (wk("2026-09-14") == 100.0).all()          # the Tuesday-09-08 report, NOT the 09-15 one
    assert (wk("2026-09-21") == 200.0).all()
    assert (net["2026-09-14":"2026-09-18"] != 200.0).all()
    assert pd.Timestamp(asof["2026-09-16"]) == pd.Timestamp("2026-09-08")


def test_pair_combination_inverts_the_quote_leg_and_averages():
    days = pd.bdate_range("2026-09-14", "2026-09-15")
    per = {"EUR": (pd.Series([15.0, 15.0], index=days), pd.Series(pd.to_datetime(["2026-09-08"] * 2), index=days)),
           "USD": (pd.Series([84.0, 84.0], index=days), pd.Series(pd.to_datetime(["2026-09-08"] * 2), index=days))}
    comb, asof = cd.combined_pctl_for_pair("EURUSD", per, days)
    assert np.allclose(comb, (15.0 + (100 - 84.0)) / 2)
    only_base, _ = cd.combined_pctl_for_pair("EURJPY", per, days)        # JPY missing -> base leg alone
    assert np.allclose(only_base, 15.0)
    none, _ = cd.combined_pctl_for_pair("AUDNZD", per, days)
    assert np.isnan(none).all()


def _zip_bytes(rows):
    hdr = ["Market and Exchange Names", cd._COL_DATE, cd._COL_CODE, cd._COL_LONG, cd._COL_SHORT]
    lines = [",".join(f'"{h}"' for h in hdr)] + [f'"X","{d}","{c}",{lg},{sh}' for c, d, lg, sh in rows]
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as z:
        z.writestr("annual.txt", "\n".join(lines))
    return buf.getvalue()


def test_refresh_appends_new_reports_dedupes_and_never_shrinks_the_store(tmp_path):
    store = tmp_path / "legacy_nc.csv"
    store.write_text("code,date,nc_long,nc_short\n099741,2026-09-08,198509,241125\n")
    raw = _zip_bytes([("099741", "2026-09-08", 198509, 241125), ("099741", "2026-09-15", 209000, 235993),
                      ("999999", "2026-09-15", 1, 1)])                    # an unmapped contract must be ignored
    r = cd.refresh_legacy_store(store, today=pd.Timestamp("2026-09-19").date(), fetch=lambda y: raw)
    got = pd.read_csv(store, dtype={"code": str})
    assert r["ok"] and r["added"] == 1 and len(got) == 2 and set(got["code"]) == {"099741"}
    assert got["date"].max() == "2026-09-15"


def test_refresh_failure_leaves_the_store_untouched(tmp_path):
    store = tmp_path / "legacy_nc.csv"
    store.write_text("code,date,nc_long,nc_short\n099741,2026-09-08,198509,241125\n")
    before = store.read_text()

    def boom(_):
        raise OSError("network down")

    r = cd.refresh_legacy_store(store, today=pd.Timestamp("2026-09-19").date(), fetch=boom)
    assert not r["ok"] and "network down" in r["reason"] and store.read_text() == before


def test_shipped_store_has_all_eight_contracts_back_to_2006():
    store = cd.load_legacy_store()
    assert set(store["code"]) == set(cd.CFTC_CODES.values())
    assert store["date"].min() <= pd.Timestamp("2006-01-10") and len(store) > 8000


# ── the series contract ──────────────────────────────────────────────────────────
def _bars(n=400, seed=0):
    rng = np.random.default_rng(seed)
    close = 1.10 + np.cumsum(rng.normal(0, 0.003, n))
    return pd.DataFrame({"trading_day": pd.bdate_range("2024-01-01", periods=n), "open": np.concatenate(([1.10], close[:-1])),
                         "high": close + rng.uniform(0.0005, 0.004, n), "low": close - rng.uniform(0.0005, 0.004, n),
                         "close": close})


def test_series_block_shape_tail_and_latest():
    bars = _bars()
    cot = np.full(len(bars), 55.0)
    asof = np.full(len(bars), np.datetime64("2026-09-08"))
    blk = cs.series_block(bars, cot, asof)
    assert len(blk["dates"]) == len(blk["top"]) == len(blk["bottom"]) == cs.TAIL
    assert blk["dates"][-1] == bars["trading_day"].iloc[-1].strftime("%Y-%m-%d")
    lt = blk["latest"]
    assert lt["bar_date"] == blk["dates"][-1] and lt["top"] == blk["top"][-1] and lt["bottom"] == blk["bottom"][-1]
    assert lt["cot_ok"] is True and lt["cot_pctl"] == 55.0 and lt["cot_asof"] == "2026-09-08"
    assert all(n in cs.FACTOR_NAMES.values() for n in lt["top_on"] + lt["bottom_on"])


def test_series_block_without_cot_reports_cot_not_ok_and_caps_at_75():
    blk = cs.series_block(_bars(seed=1), None, None)
    assert blk["latest"]["cot_ok"] is False and blk["latest"]["cot_pctl"] is None and blk["latest"]["cot_asof"] is None
    assert max(blk["top"] + blk["bottom"]) <= 75.0


def test_too_little_history_fails_quiet_and_no_bar_before_the_zscore_window_is_scored():
    assert cs.series_block(_bars(n=100), None, None) == cs._EMPTY
    assert cs.series_block(None, None, None) == cs._EMPTY
    blk = cs.series_block(_bars(n=130), None, None)                       # only bars 100..129 are scoreable
    assert len(blk["dates"]) == 30


def test_last_90_from_a_short_window_agree_with_full_history_on_nearly_every_bar():
    """Production has ~208 D1 bars (5000 H1), not the years the study used. Warm-up must not change the answer."""
    full = _bars(n=900, seed=7)
    a = cs.series_block(full, None, None)
    b = cs.series_block(full.iloc[-208:].reset_index(drop=True), None, None)
    same = np.mean(np.isclose(a["top"], b["top"]) & np.isclose(a["bottom"], b["bottom"]))
    assert same >= 0.9, f"only {same:.0%} of the last 90 bars agree between a 208-bar and a full-history run"


# ── the scan hook ────────────────────────────────────────────────────────────────
def test_attach_adds_only_crowd_series_and_leaves_every_other_key_byte_identical():
    import copy
    h1 = _h1("2025-11-09 22:00", "2026-08-30 22:00", seed=9)              # 42 weeks ~ production's 5000 H1, crosses the March DST change
    pairs = {"EURUSD": {"bb_d1": {"touching": "upper", "pctb": [1.5]}, "bollinger_series": {"d1": {"pctb": [1.0]}},
                        "momentum_series": {"d1": {"rsi": [50.0]}}, "adx": 25.1}}
    snapshot = copy.deepcopy(pairs)
    cs.attach_crowd_series(pairs, {"EURUSD": h1}, now_utc=FAR_FUTURE, store=cd.load_legacy_store())
    assert set(pairs["EURUSD"]["crowd_series"]) == {"d1", "h4"}
    without = {k: v for k, v in pairs["EURUSD"].items() if k != "crowd_series"}
    assert json.dumps(without, sort_keys=True) == json.dumps(snapshot["EURUSD"], sort_keys=True)
    d1 = pairs["EURUSD"]["crowd_series"]["d1"]
    assert d1["latest"] is not None and len(d1["dates"]) == cs.TAIL      # ~208 D1 bars leave ~108 scoreable >= the 90-point tail


def test_a_failing_pair_leaves_its_key_absent_and_never_breaks_the_scan():
    pairs = {"EURUSD": {}, "GBPUSD": {}}
    good = _h1("2025-11-09 22:00", "2026-08-30 22:00", seed=9)
    bad = pd.DataFrame({"datetime": ["not a date"], "open": [1], "high": [1], "low": [1], "close": [1]})
    cs.attach_crowd_series(pairs, {"EURUSD": good, "GBPUSD": bad}, now_utc=FAR_FUTURE, store=cd.load_legacy_store())
    assert "crowd_series" in pairs["EURUSD"] and "crowd_series" not in pairs["GBPUSD"]


def test_attach_is_a_no_op_without_h1_data():
    pairs = {"EURUSD": {}}
    cs.attach_crowd_series(pairs, None)
    cs.attach_crowd_series(pairs, {})
    assert pairs == {"EURUSD": {}}


if __name__ == "__main__":
    import sys
    import pytest
    sys.exit(pytest.main([__file__, "-q"]))
