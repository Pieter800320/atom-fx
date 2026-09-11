"""
ATOM FX — NY-close D1 aggregation tests (Task 1a, DECISION-001).

Synthetic H1 fixtures only — no network. Style follows tests/test_extend.py: reaches into
the module's own leading-underscore helper (_trading_days) directly where the public
aggregate_d1_nyclose() contract (OHLC only, no date column) can't answer a date question by
itself, same way test_extend.py imports _slice_ohlcv / _contributions / _d1_ny_close.

Run:  python -m tests.test_agg_nyclose      (or: pytest tests/test_agg_nyclose.py)

This file asserts EXTEND behaviour only; it never touches a frozen key. tests/test_rule1_frozen
stays the proof that no frozen file changed.
"""
import pandas as pd

from scanner.extend.agg_nyclose import aggregate_d1_nyclose, with_emas, _trading_days
from scanner.score import _ema


def _mk_h1(rows):
    """rows: list of (datetime_str_utc, open, high, low, close)."""
    dts, o, h, l, c = zip(*rows)
    return pd.DataFrame({"datetime": list(dts), "open": list(o), "high": list(h),
                          "low": list(l), "close": list(c)})


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
def test_boundary_tracks_local_17ny_across_spring_forward():
    # 2026-03-08 02:00 local is US DST start (EST -> EDT). 2026-03-07 is EST (UTC-5):
    # 17:00 NY == 22:00 UTC. 2026-03-08 afternoon is already EDT (UTC-4): 17:00 NY == 21:00 UTC.
    rows = [
        ("2026-03-07 21:59:00", 1.0, 1.0, 1.0, 1.0),   # 16:59 EST -> Mar-07
        ("2026-03-07 22:00:00", 1.0, 1.0, 1.0, 1.0),   # 17:00 EST exactly -> Mar-08 (next)
        ("2026-03-08 20:59:00", 1.0, 1.0, 1.0, 1.0),   # 16:59 EDT -> Mar-08
        ("2026-03-08 21:00:00", 1.0, 1.0, 1.0, 1.0),   # 17:00 EDT exactly -> Mar-09 (next)
    ]
    tagged = _trading_days(_mk_h1(rows))
    days = [d.date() for d in tagged["trading_day"]]
    assert days == [
        pd.Timestamp("2026-03-07").date(), pd.Timestamp("2026-03-08").date(),
        pd.Timestamp("2026-03-08").date(), pd.Timestamp("2026-03-09").date(),
    ]


def test_boundary_tracks_local_17ny_across_fall_back():
    # 2026-11-01 02:00 local is US DST end (EDT -> EST). 2026-10-31 is EDT (UTC-4):
    # 17:00 NY == 21:00 UTC. 2026-11-01 afternoon is already EST (UTC-5): 17:00 NY == 22:00 UTC.
    rows = [
        ("2026-10-31 20:59:00", 1.0, 1.0, 1.0, 1.0),   # 16:59 EDT -> Oct-31
        ("2026-10-31 21:00:00", 1.0, 1.0, 1.0, 1.0),   # 17:00 EDT exactly -> Nov-01 (next)
        ("2026-11-01 21:59:00", 1.0, 1.0, 1.0, 1.0),   # 16:59 EST -> Nov-01
        ("2026-11-01 22:00:00", 1.0, 1.0, 1.0, 1.0),   # 17:00 EST exactly -> Nov-02 (next)
    ]
    tagged = _trading_days(_mk_h1(rows))
    days = [d.date() for d in tagged["trading_day"]]
    assert days == [
        pd.Timestamp("2026-10-31").date(), pd.Timestamp("2026-11-01").date(),
        pd.Timestamp("2026-11-01").date(), pd.Timestamp("2026-11-02").date(),
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
