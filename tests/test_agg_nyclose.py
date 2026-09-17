"""
ATOM FX — NY-close D1 aggregation tests (Task 1a/DECISION-001; weekend filter/DECISION-007).

Synthetic H1 fixtures only — no network. Style follows tests/test_extend.py: reaches into
the module's own leading-underscore helper (_trading_days) directly where the public
aggregate_d1_nyclose() contract (OHLC only, no date column) can't answer a date question by
itself, same way test_extend.py imports _slice_ohlcv / _contributions / _d1_ny_close.

Run:  python -m tests.test_agg_nyclose      (or: pytest tests/test_agg_nyclose.py)

This file asserts EXTEND behaviour only; it never touches a frozen key. tests/test_rule1_frozen
stays the proof that no frozen file changed.
"""
import pandas as pd

from scanner.extend.agg_nyclose import (
    aggregate_d1_nyclose, aggregate_d1_nyclose_dated, with_emas, _trading_days,
)
from scanner.score import _ema


def _mk_h1(rows):
    """rows: list of (datetime_str_utc, open, high, low, close)."""
    dts, o, h, l, c = zip(*rows)
    return pd.DataFrame({"datetime": list(dts), "open": list(o), "high": list(h),
                          "low": list(l), "close": list(c)})


_FRIDAY_WD = 4


# ── 1. OHLC correctness for one known trading day ─────────────────────────────────
def test_ohlc_correctness_single_day():
    # All four bars fall in [2026-06-01 21:00 UTC, 2026-06-02 21:00 UTC) -- one NY-close
    # trading day (June is EDT, 17:00 NY == 21:00 UTC, no DST transition nearby).
    rows = [
        ("2026-06-01 21:00:00", 1.1000, 1.1010, 1.0995, 1.1005),
        ("2026-06-02 05:00:00", 1.1005, 1.1030, 1.1000, 1.1020),
        ("2026-06-02 12:00:00", 1.1020, 1.1025, 1.0980, 1.1010),
        ("2026-06-02 20:00:00", 1.1010, 1.1015, 1.0990, 1.0999),
    ]
    d1 = aggregate_d1_nyclose(_mk_h1(rows))
    assert len(d1) == 1
    row = d1.iloc[0]
    assert row["open"] == 1.1000            # first bar's open
    assert row["high"] == 1.1030            # max high across all four
    assert row["low"] == 1.0980             # min low across all four
    assert row["close"] == 1.0999           # last bar's close
    assert list(d1.columns) == ["open", "high", "low", "close"]
    assert d1.index.tolist() == [0]


# ── 2. 16:00 NY vs 17:00 NY -> different, adjacent trading days ───────────────────
def test_16_and_17_ny_bars_fall_in_different_trading_days():
    # 2026-06-10 is EDT: 16:00 NY == 20:00 UTC, 17:00 NY == 21:00 UTC.
    rows = [
        ("2026-06-10 20:00:00", 1.10, 1.10, 1.10, 1.10),   # 16:00 NY -> trading day Jun-10
        ("2026-06-10 21:00:00", 1.20, 1.20, 1.20, 1.20),   # 17:00 NY -> trading day Jun-11 (next)
    ]
    df = _mk_h1(rows)
    tagged = _trading_days(df)
    days = tagged["trading_day"].tolist()
    assert days[0] != days[1]
    assert days[1] == days[0] + pd.Timedelta(days=1)
    assert days[0].date() == pd.Timestamp("2026-06-10").date()
    assert days[1].date() == pd.Timestamp("2026-06-11").date()

    d1 = aggregate_d1_nyclose(df)
    assert len(d1) == 2


# ── 3. DST-aware boundary: UTC hour of the 17:00-NY close shifts by 1 ─────────────
# DECISION-007 note: the actual US DST transition instant always falls on a Sunday (2am
# local), with the day before always a Saturday -- both now correctly filtered out of the
# FX week, so a fixture anchored on the transition day itself would have most of its bars
# dropped before they could prove anything about the boundary. These use the nearest
# non-weekend days that still straddle the same EST<->EDT offset change (Thursday-before /
# Monday-after) -- same UTC-hour-shift property, on days the new filter never touches.
def test_boundary_tracks_local_17ny_across_spring_forward():
    # DST start 2026-03-08 (Sun) 02:00 local, EST -> EDT. 2026-03-05 (Thu) is still EST:
    # 17:00 NY == 22:00 UTC. 2026-03-09 (Mon) is already EDT: 17:00 NY == 21:00 UTC.
    rows = [
        ("2026-03-05 21:59:00", 1.0, 1.0, 1.0, 1.0),   # Thu 16:59 EST -> Mar-05
        ("2026-03-05 22:00:00", 1.0, 1.0, 1.0, 1.0),   # Thu 17:00 EST exactly -> Mar-06 (next)
        ("2026-03-09 20:59:00", 1.0, 1.0, 1.0, 1.0),   # Mon 16:59 EDT -> Mar-09
        ("2026-03-09 21:00:00", 1.0, 1.0, 1.0, 1.0),   # Mon 17:00 EDT exactly -> Mar-10 (next)
    ]
    tagged = _trading_days(_mk_h1(rows))
    days = [d.date() for d in tagged["trading_day"]]
    assert days == [
        pd.Timestamp("2026-03-05").date(), pd.Timestamp("2026-03-06").date(),
        pd.Timestamp("2026-03-09").date(), pd.Timestamp("2026-03-10").date(),
    ]


def test_boundary_tracks_local_17ny_across_fall_back():
    # DST end 2026-11-01 (Sun) 02:00 local, EDT -> EST. 2026-10-29 (Thu) is still EDT:
    # 17:00 NY == 21:00 UTC. 2026-11-02 (Mon) is already EST: 17:00 NY == 22:00 UTC.
    rows = [
        ("2026-10-29 20:59:00", 1.0, 1.0, 1.0, 1.0),   # Thu 16:59 EDT -> Oct-29
        ("2026-10-29 21:00:00", 1.0, 1.0, 1.0, 1.0),   # Thu 17:00 EDT exactly -> Oct-30 (next)
        ("2026-11-02 21:59:00", 1.0, 1.0, 1.0, 1.0),   # Mon 16:59 EST -> Nov-02
        ("2026-11-02 22:00:00", 1.0, 1.0, 1.0, 1.0),   # Mon 17:00 EST exactly -> Nov-03 (next)
    ]
    tagged = _trading_days(_mk_h1(rows))
    days = [d.date() for d in tagged["trading_day"]]
    assert days == [
        pd.Timestamp("2026-10-29").date(), pd.Timestamp("2026-10-30").date(),
        pd.Timestamp("2026-11-02").date(), pd.Timestamp("2026-11-03").date(),
    ]


# ── 4. Sunday reopen rolls into Monday; zero phantom-Sunday days over weeks ───────
def _weekly_fixture(start="2026-06-01", end="2026-06-22"):
    """Hourly bars covering a realistic FX week: closed Fri 17:00 NY -> Sun 17:00 NY."""
    ny = pd.date_range(start, end, freq="1h", tz="America/New_York")
    closed = (
        (ny.dayofweek == 5) |                              # Saturday: closed all day
        ((ny.dayofweek == 4) & (ny.hour >= 17)) |           # Friday from 17:00 NY: closed
        ((ny.dayofweek == 6) & (ny.hour < 17))              # Sunday before 17:00 NY: closed
    )
    ny_open = ny[~closed]
    utc = ny_open.tz_convert("UTC")
    n = len(utc)
    closes = [1.1000 + 0.0001 * i for i in range(n)]
    return pd.DataFrame({
        "datetime": utc.strftime("%Y-%m-%d %H:%M:%S"),
        "open": closes, "high": [c + 0.0002 for c in closes],
        "low": [c - 0.0002 for c in closes], "close": closes,
    }), ny_open


def test_no_phantom_sunday_trading_day_over_multiple_weeks():
    h1_df, _ = _weekly_fixture()
    tagged = _trading_days(h1_df)
    unique_days = tagged["trading_day"].unique()
    sunday_count = sum(1 for d in unique_days if pd.Timestamp(d).dayofweek == 6)
    assert sunday_count == 0


def test_sunday_evening_bars_group_into_monday():
    h1_df, ny_open = _weekly_fixture()
    tagged = _trading_days(h1_df)

    # find the first Sunday-evening (>= 17:00 NY, i.e. market-open) bar in the fixture
    sunday_mask = (ny_open.dayofweek == 6) & (ny_open.hour == 17)
    assert sunday_mask.any(), "fixture must contain at least one Sunday 17:00 NY bar"
    sunday_ts = ny_open[sunday_mask][0]
    expected_monday = (sunday_ts.tz_localize(None).normalize() + pd.Timedelta(days=1))

    row = tagged.loc[tagged.index[ny_open.get_indexer([sunday_ts])[0]]]
    assert row["trading_day"] == expected_monday
    assert row["trading_day"].dayofweek == 0  # Monday


# ── 5. Incomplete current trading day is included, as the last row ──────────────
def test_incomplete_current_day_is_last_row():
    rows = [
        # Day A: [Jun-14 21:00 UTC, Jun-15 21:00 UTC)
        ("2026-06-14 21:00:00", 1.1000, 1.1005, 1.0995, 1.1002),
        ("2026-06-15 10:00:00", 1.1002, 1.1010, 1.0998, 1.1004),
        # Day B: [Jun-15 21:00 UTC, Jun-16 21:00 UTC)
        ("2026-06-15 21:00:00", 1.1004, 1.1008, 1.0996, 1.1006),
        ("2026-06-16 10:00:00", 1.1006, 1.1012, 1.1000, 1.1009),
        # Day C (incomplete -- only 3 hours in, far short of the next 17:00-NY close):
        ("2026-06-16 21:00:00", 1.1009, 1.1020, 1.1005, 1.1015),
        ("2026-06-16 22:00:00", 1.1015, 1.1030, 1.1010, 1.1025),
        ("2026-06-16 23:00:00", 1.1025, 1.1028, 1.1018, 1.1022),
    ]
    d1 = aggregate_d1_nyclose(_mk_h1(rows))
    assert len(d1) == 3

    last = d1.iloc[-1]
    assert last["open"] == 1.1009
    assert last["high"] == 1.1030
    assert last["low"] == 1.1005
    assert last["close"] == 1.1022

    day_a, day_b = d1.iloc[0], d1.iloc[1]
    assert (day_a["open"], day_a["high"], day_a["low"], day_a["close"]) == (1.1000, 1.1010, 1.0995, 1.1004)
    assert (day_b["open"], day_b["high"], day_b["low"], day_b["close"]) == (1.1004, 1.1012, 1.0996, 1.1009)


# ── 6. DECISION-007: drop market-closed weekend bars before grouping ─────────────
def test_sunday_preopen_block_dropped_monday_ohlc_excludes_it():
    # 2026-06-14 is a Sunday, 2026-06-15 a Monday (June is EDT, 17:00 NY == 21:00 UTC).
    # Pre-open Sunday bars (00:00-16:00 ET, i.e. UTC 04:00-20:00) get a sentinel 9.9 OHLC --
    # if they leaked into any resulting candle it would show up unmistakably.
    preopen = [
        ("2026-06-14 04:00:00", 9.9, 9.9, 9.9, 9.9),   # 00:00 ET Sun -- dropped
        ("2026-06-14 08:00:00", 9.9, 9.9, 9.9, 9.9),   # 04:00 ET Sun -- dropped
        ("2026-06-14 12:00:00", 9.9, 9.9, 9.9, 9.9),   # 08:00 ET Sun -- dropped
        ("2026-06-14 16:00:00", 9.9, 9.9, 9.9, 9.9),   # 12:00 ET Sun -- dropped
        ("2026-06-14 20:00:00", 9.9, 9.9, 9.9, 9.9),   # 16:00 ET Sun -- dropped
    ]
    reopen_and_monday = [
        ("2026-06-14 21:00:00", 1.1000, 1.1010, 1.0995, 1.1005),  # 17:00 ET Sun -- KEPT (reopen)
        ("2026-06-15 00:00:00", 1.1005, 1.1015, 1.1000, 1.1010),  # 20:00 ET Sun -- kept
        ("2026-06-15 03:00:00", 1.1010, 1.1020, 1.1005, 1.1012),  # 23:00 ET Sun -- kept
        ("2026-06-15 04:00:00", 1.1012, 1.1030, 1.1008, 1.1020),  # 00:00 ET Mon -- kept
        ("2026-06-15 14:00:00", 1.1020, 1.1040, 1.1015, 1.1025),  # 10:00 ET Mon -- kept
        ("2026-06-15 20:00:00", 1.1025, 1.1035, 1.1018, 1.1022),  # 16:00 ET Mon -- kept
    ]
    h1_df = _mk_h1(preopen + reopen_and_monday)

    dated = aggregate_d1_nyclose_dated(h1_df)
    assert (dated["date"].dt.dayofweek == 6).sum() == 0    # zero Sunday-labeled D1 bars
    assert len(dated) == 1                                  # everything survives into ONE day: Monday
    assert dated["date"].iloc[0].dayofweek == 0             # Monday

    row = dated.iloc[0]
    assert (row["open"], row["high"], row["low"], row["close"]) == (1.1000, 1.1040, 1.0995, 1.1022)
    assert 9.9 not in (row["open"], row["high"], row["low"], row["close"])


def test_saturday_block_dropped_no_leak_into_friday_or_monday():
    # 2026-06-12 Fri, 06-13 Sat, 06-14 Sun, 06-15 Mon (EDT: 17:00 NY == 21:00 UTC).
    friday_kept = [
        ("2026-06-12 14:00:00", 1.2000, 1.2010, 1.1995, 1.2005),  # 10:00 ET Fri -- kept
        ("2026-06-12 20:00:00", 1.2005, 1.2015, 1.2000, 1.2008),  # 16:00 ET Fri -- kept (last before close)
    ]
    friday_close_and_saturday_dropped = [
        ("2026-06-12 21:00:00", 8.8, 8.8, 8.8, 8.8),   # 17:00 ET Fri -- dropped (market closed)
        ("2026-06-13 00:00:00", 8.8, 8.8, 8.8, 8.8),   # 20:00 ET Fri -- dropped
        ("2026-06-13 04:00:00", 8.8, 8.8, 8.8, 8.8),   # 00:00 ET Sat -- dropped
        ("2026-06-13 05:00:00", 8.8, 8.8, 8.8, 8.8),   # 01:00 ET Sat -- dropped
        ("2026-06-13 06:00:00", 8.8, 8.8, 8.8, 8.8),   # 02:00 ET Sat -- dropped
        ("2026-06-13 07:00:00", 8.8, 8.8, 8.8, 8.8),   # 03:00 ET Sat -- dropped
    ]
    sunday_reopen_and_monday = [
        ("2026-06-14 21:00:00", 1.3000, 1.3010, 1.2995, 1.3005),  # 17:00 ET Sun -- kept (reopen)
        ("2026-06-15 04:00:00", 1.3005, 1.3015, 1.3000, 1.3008),  # 00:00 ET Mon -- kept
    ]
    h1_df = _mk_h1(friday_kept + friday_close_and_saturday_dropped + sunday_reopen_and_monday)

    dated = aggregate_d1_nyclose_dated(h1_df)
    assert (dated["date"].dt.dayofweek == 5).sum() == 0    # zero Saturday-labeled D1 bars
    assert len(dated) == 2                                  # exactly Friday + Monday, nothing between
    assert dated["date"].iloc[0].dayofweek == _FRIDAY_WD and dated["date"].iloc[1].dayofweek == 0

    friday_row, monday_row = dated.iloc[0], dated.iloc[1]
    assert (friday_row["open"], friday_row["high"], friday_row["low"], friday_row["close"]) == \
        (1.2000, 1.2015, 1.1995, 1.2008)
    assert (monday_row["open"], monday_row["high"], monday_row["low"], monday_row["close"]) == \
        (1.3000, 1.3015, 1.2995, 1.3008)
    for row in (friday_row, monday_row):
        assert 8.8 not in (row["open"], row["high"], row["low"], row["close"])


def test_friday_boundary_1600_kept_1700_dropped():
    # 2026-06-12 is a Friday (EDT: 16:00 NY == 20:00 UTC, 17:00 NY == 21:00 UTC).
    rows = [
        ("2026-06-12 20:00:00", 1.5000, 1.5010, 1.4995, 1.5005),  # 16:00 ET Fri -- kept
        ("2026-06-12 21:00:00", 7.7, 7.7, 7.7, 7.7),               # 17:00 ET Fri -- dropped
    ]
    dated = aggregate_d1_nyclose_dated(_mk_h1(rows))
    assert len(dated) == 1
    row = dated.iloc[0]
    assert row["date"].dayofweek == _FRIDAY_WD
    assert (row["open"], row["high"], row["low"], row["close"]) == (1.5000, 1.5010, 1.4995, 1.5005)


def test_clean_weekday_only_week_unaffected_by_filter():
    # 2026-06-15 (Mon) .. 2026-06-19 (Fri), all bars well inside the FX week -- proves the
    # filter doesn't over-drop legitimate weekday bars.
    rows = []
    base = 1.1000
    for day_offset, day in enumerate(["15", "16", "17", "18", "19"]):
        for hour, delta in ((3, 0.0000), (10, 0.0005), (15, -0.0002)):
            o = base + day_offset * 0.001 + delta
            rows.append((f"2026-06-{day} {hour:02d}:00:00", o, o + 0.0004, o - 0.0004, o + 0.0001))

    dated = aggregate_d1_nyclose_dated(_mk_h1(rows))
    assert len(dated) == 5
    assert dated["date"].dt.dayofweek.tolist() == [0, 1, 2, 3, 4]   # Mon..Fri, in order

    for i, day in enumerate(["15", "16", "17", "18", "19"]):
        day_rows = [r for r in rows if r[0].startswith(f"2026-06-{day}")]
        expected_open = day_rows[0][1]
        expected_high = max(r[2] for r in day_rows)
        expected_low = min(r[3] for r in day_rows)
        expected_close = day_rows[-1][4]
        row = dated.iloc[i]
        assert (row["open"], row["high"], row["low"], row["close"]) == \
            (expected_open, expected_high, expected_low, expected_close)


# ── with_emas: delegates to the frozen scanner.score._ema, doesn't reimplement it ─
def test_with_emas_matches_frozen_ema_exactly():
    closes = [1.10 + 0.0003 * i for i in range(260)]
    d1 = pd.DataFrame({"open": closes, "high": closes, "low": closes, "close": closes})
    out = with_emas(d1)
    assert list(out["ema50"]) == list(_ema(d1["close"], 50))
    assert list(out["ema200"]) == list(_ema(d1["close"], 200))
    # original frame is untouched (a copy was returned)
    assert "ema50" not in d1.columns and "ema200" not in d1.columns


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
