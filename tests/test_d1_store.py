"""
ATOM FX — d1_store tests (Task 1b, DECISION-001 EMA200 warm-up persistence).

Synthetic fixtures only, no network — style follows tests/test_extend.py. File-I/O tests use
a throwaway tempfile.TemporaryDirectory() instead of the real data/d1_nyclose/, and instead
of a pytest fixture, so this file also runs standalone via `python -m tests.test_d1_store`
like every other extend test.

This file asserts EXTEND behaviour only; it never touches a frozen key. tests/test_rule1_frozen
stays the proof that no frozen file changed.
"""
import tempfile

import pandas as pd

from scanner.extend import d1_store
from scanner.score import _ema


def _dated(rows):
    """rows: list of (date_str, open, high, low, close)."""
    dates, o, h, l, c = zip(*rows)
    return pd.DataFrame({"date": pd.to_datetime(list(dates)), "open": list(o), "high": list(h),
                          "low": list(l), "close": list(c)})


# ── merge ──────────────────────────────────────────────────────────────────────
def test_merge_appends_new_day():
    store = _dated([("2026-06-01", 1.10, 1.11, 1.09, 1.105)])
    fresh = _dated([("2026-06-02", 1.105, 1.12, 1.10, 1.115)])
    merged = d1_store.merge(store, fresh)
    assert merged["date"].tolist() == list(pd.to_datetime(["2026-06-01", "2026-06-02"]))
    assert len(merged) == 2


def test_merge_replaces_last_inprogress_day_with_fresh_values():
    store = _dated([
        ("2026-06-01", 1.10, 1.11, 1.09, 1.105),
        ("2026-06-02", 1.105, 1.108, 1.100, 1.106),   # still forming, now stale
    ])
    fresh = _dated([("2026-06-02", 1.105, 1.130, 1.098, 1.120)])  # same day, updated OHLC
    merged = d1_store.merge(store, fresh)
    assert len(merged) == 2
    row = merged[merged["date"] == pd.Timestamp("2026-06-02")].iloc[0]
    assert (row["open"], row["high"], row["low"], row["close"]) == (1.105, 1.130, 1.098, 1.120)


def test_merge_dedupes_overlapping_dates_fresh_wins():
    store = _dated([
        ("2026-06-01", 1.10, 1.11, 1.09, 1.105),
        ("2026-06-02", 1.11, 1.12, 1.10, 1.115),
    ])
    fresh = _dated([
        ("2026-06-02", 1.115, 1.130, 1.108, 1.120),
        ("2026-06-03", 1.120, 1.140, 1.115, 1.135),
    ])
    merged = d1_store.merge(store, fresh)
    assert merged["date"].duplicated().sum() == 0
    assert len(merged) == 3
    row = merged[merged["date"] == pd.Timestamp("2026-06-02")].iloc[0]
    assert row["close"] == 1.120   # fresh's value, not store's stale 1.115


def test_merge_result_is_chronological_ascending_no_dupes():
    store = _dated([("2026-06-03", 1.0, 1.0, 1.0, 1.0), ("2026-06-01", 1.0, 1.0, 1.0, 1.0)])
    fresh = _dated([("2026-06-02", 1.0, 1.0, 1.0, 1.0)])
    merged = d1_store.merge(store, fresh)
    assert merged["date"].tolist() == sorted(merged["date"].tolist())
    assert merged["date"].duplicated().sum() == 0


# ── save_store / load_store round-trip ────────────────────────────────────────
def test_save_and_load_round_trips_values_unchanged():
    df = _dated([
        ("2026-06-01", 1.10001, 1.11002, 1.09003, 1.10500),
        ("2026-06-02", 1.10500, 1.12000, 1.10000, 1.11500),
    ])
    with tempfile.TemporaryDirectory() as tmp:
        d1_store.save_store("EUR/USD", df, data_dir=tmp)
        loaded = d1_store.load_store("EUR/USD", data_dir=tmp)

    assert loaded["date"].tolist() == df["date"].tolist()
    for col in ("open", "high", "low", "close"):
        assert loaded[col].tolist() == df[col].tolist()


def test_load_store_returns_empty_frame_when_no_file_yet():
    with tempfile.TemporaryDirectory() as tmp:
        loaded = d1_store.load_store("EUR/USD", data_dir=tmp)
    assert loaded.empty
    assert list(loaded.columns) == ["date", "open", "high", "low", "close"]


# ── update_d1: delegates EMA math to the frozen scanner.score._ema ────────────
def test_update_d1_ema_columns_match_frozen_ema_on_merged_closes():
    store = _dated([(f"2026-01-{d:02d}", 1.10 + 0.001 * d, 1.11 + 0.001 * d,
                      1.09 + 0.001 * d, 1.10 + 0.001 * d) for d in range(1, 21)])  # 20 days

    # Fresh H1 fixture (January -> EST, 17:00 NY == 22:00 UTC): two NEW trading days,
    # 2026-01-22 and 2026-01-23, not already in `store` above.
    rows = [
        ("2026-01-21 22:00:00", 1.130, 1.140, 1.120, 1.135),  # opens trading day 2026-01-22
        ("2026-01-22 10:00:00", 1.135, 1.150, 1.130, 1.145),  # still 2026-01-22
        ("2026-01-22 22:00:00", 1.145, 1.160, 1.140, 1.155),  # opens trading day 2026-01-23
    ]
    dts, o, h, l, c = zip(*rows)
    fresh_h1 = pd.DataFrame({"datetime": list(dts), "open": list(o), "high": list(h),
                             "low": list(l), "close": list(c)})

    with tempfile.TemporaryDirectory() as tmp:
        d1_store.save_store("EUR/USD", store, data_dir=tmp)
        result = d1_store.update_d1("EUR/USD", fresh_h1, data_dir=tmp)

    assert len(result) == 22   # 20 stored days + 2 genuinely new trading days
    assert {"ema50", "ema200"} <= set(result.columns)
    assert list(result["ema50"]) == list(_ema(result["close"], 50))
    assert list(result["ema200"]) == list(_ema(result["close"], 200))


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
