"""
ATOM FX — backtest_trend_pullback.py tests (Task 5, DECISION-008).

Unit-tests resolve_trade() ONLY — a pure function of a synthetic forward price path plus a
trade's entry/stop/target, no network, no detector call. Style follows tests/test_extend.py.

This file asserts EXTEND behaviour only; it never touches a frozen key. tests/test_rule1_frozen
stays the proof that no frozen file changed.

Run:  python -m tests.test_backtest_trend_pullback
"""
import pandas as pd

from tools.backtest_trend_pullback import resolve_trade


def _bars(rows):
    """rows: list of (open, high, low, close) for bars AFTER the entry bar (index 0 here
    corresponds to entry_idx+1 in resolve_trade's own bar numbering, via the offset below)."""
    o, h, l, c = zip(*rows)
    return pd.DataFrame({"open": list(o), "high": list(h), "low": list(l), "close": list(c)})


def _with_entry_bar(entry_close, rows):
    """Prepend a dummy entry bar (index 0, the signal bar) ahead of the forward path (rows),
    so entry_idx=0 and the path under test starts at index 1, matching resolve_trade's own
    'walk forward from entry_idx+1' contract."""
    entry_bar = (entry_close, entry_close, entry_close, entry_close)
    return _bars([entry_bar] + list(rows))


# ── LONG ───────────────────────────────────────────────────────────────────────
def test_long_wins_when_target_hit_before_stop():
    entry, stop, target = 1.1000, 1.0950, 1.1100
    df = _with_entry_bar(entry, [
        (1.1000, 1.1020, 1.0990, 1.1010),   # neither hit
        (1.1010, 1.1105, 1.1005, 1.1090),   # high >= target -> win here
        (1.1090, 1.1120, 1.1080, 1.1100),   # would also hit -- must not be reached
    ])
    r = resolve_trade(df, entry_idx=0, direction="long", entry=entry, stop=stop, target=target)
    assert r["exit_reason"] == "target"
    assert r["exit_idx"] == 2
    assert r["exit_price"] == target
    assert r["bars_held"] == 2
    assert r["realized_R"] == (target - entry) / (entry - stop)


def test_long_loses_when_stop_hit_before_target():
    entry, stop, target = 1.1000, 1.0950, 1.1100
    df = _with_entry_bar(entry, [
        (1.1000, 1.1020, 1.0990, 1.1010),
        (1.1010, 1.1030, 1.0940, 1.0960),   # low <= stop -> loss here
        (1.0960, 1.1200, 1.0900, 1.1150),   # would also hit target -- must not be reached
    ])
    r = resolve_trade(df, entry_idx=0, direction="long", entry=entry, stop=stop, target=target)
    assert r["exit_reason"] == "stop"
    assert r["exit_idx"] == 2
    assert r["exit_price"] == stop
    assert r["realized_R"] == -1.0


def test_long_stop_first_on_same_bar_hitting_both():
    entry, stop, target = 1.1000, 1.0950, 1.1100
    df = _with_entry_bar(entry, [
        (1.1000, 1.1150, 1.0900, 1.1050),   # one bar hits BOTH stop and target
    ])
    r = resolve_trade(df, entry_idx=0, direction="long", entry=entry, stop=stop, target=target)
    assert r["exit_reason"] == "stop"          # conservative: stop wins the tie
    assert r["exit_price"] == stop
    assert r["realized_R"] == -1.0


def test_long_timeout_exits_at_close_with_formula_based_R():
    entry, stop, target = 1.1000, 1.0950, 1.1100
    # 3 quiet bars, neither stop nor target ever touched -- max_hold_bars=3 forces a timeout
    # exactly at the 3rd bar.
    rows = [(1.1000, 1.1020, 1.0990, 1.1010)] * 3
    df = _with_entry_bar(entry, rows)
    r = resolve_trade(df, entry_idx=0, direction="long", entry=entry, stop=stop,
                       target=target, max_hold_bars=3)
    assert r["exit_reason"] == "timeout"
    assert r["exit_idx"] == 3         # entry_idx(0) + max_hold_bars(3)
    assert r["bars_held"] == 3
    exit_close = 1.1010
    assert r["exit_price"] == exit_close
    assert r["realized_R"] == (exit_close - entry) / (entry - stop)


def test_long_timeout_when_data_runs_out_before_max_hold():
    # only 2 bars of forward data available, max_hold_bars=10 -- must exit at the LAST
    # available bar, not crash trying to read past the end of the frame.
    entry, stop, target = 1.1000, 1.0950, 1.1100
    df = _with_entry_bar(entry, [
        (1.1000, 1.1020, 1.0990, 1.1010),
        (1.1010, 1.1030, 1.0995, 1.1015),
    ])
    r = resolve_trade(df, entry_idx=0, direction="long", entry=entry, stop=stop,
                       target=target, max_hold_bars=10)
    assert r["exit_reason"] == "timeout"
    assert r["exit_idx"] == 2          # last row in the frame
    assert r["bars_held"] == 2
    assert r["exit_price"] == 1.1015


# ── SHORT (mirror) ─────────────────────────────────────────────────────────────
def test_short_wins_when_target_hit_before_stop():
    entry, stop, target = 1.1000, 1.1050, 1.0900
    df = _with_entry_bar(entry, [
        (1.1000, 1.1010, 1.0990, 1.0995),
        (1.0995, 1.1005, 1.0895, 1.0910),   # low <= target -> win here
    ])
    r = resolve_trade(df, entry_idx=0, direction="short", entry=entry, stop=stop, target=target)
    assert r["exit_reason"] == "target"
    assert r["exit_price"] == target
    assert r["realized_R"] == (entry - target) / (stop - entry)


def test_short_stop_first_on_same_bar_hitting_both():
    entry, stop, target = 1.1000, 1.1050, 1.0900
    df = _with_entry_bar(entry, [
        (1.1000, 1.1100, 1.0850, 1.0950),   # one bar hits BOTH stop and target
    ])
    r = resolve_trade(df, entry_idx=0, direction="short", entry=entry, stop=stop, target=target)
    assert r["exit_reason"] == "stop"
    assert r["exit_price"] == stop
    assert r["realized_R"] == -1.0


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
