"""
ATOM FX — real-FX-week bars  (EXTEND; the fix for BUILD_STATUS outstanding item 18)

TIMESTAMPS FIRST (2026-09-19). Twelvedata's hourly forex `datetime` labels are Australia/Sydney LOCAL time (UTC+10 in AEST, UTC+11 in AEDT), NOT UTC —
matched against five TradingView candles (errors 0.8-6.8 pips at that offset vs 25-129 unshifted). Everything below (the week rule, the frozen aggregator's
D1/H4 boundaries, the Crowd score's New York bars) is written for UTC, so `to_utc_labels` converts the labels FIRST and every frame leaving this module is
in true UTC. See BUILD_STATUS item 18 and scanner/FROZEN.md.

THE PROBLEM. Since 2026-01-11 Twelvedata returns market-closed bars every weekend — a flat-ish Sunday pre-open block
and a recurring Saturday block. Every downstream number (the frozen aggregator's D1/H4, pills, ADX, regime, CSM, the
EXTEND chart series and the BB touch alert) was being computed with them in the window. Measured 2026-09-19 on the same
5,000-row H1 window, live vs closed-market rows dropped: the H1 pill differed on 7 of 12 pairs, the H4 pill on 5, the H4
score on 11, H4 ADX by a mean 3.7 points (max 12.7); 28% of D1 rows were closed-market-only bars.

THE FIX (Pieter's explicit sign-off on the frozen-number change, 2026-09-19 — see scanner/FROZEN.md). Closed-market rows
are dropped from every raw H1 frame BEFORE anything is built from it, restoring the semantics every frozen calculation was
written against. Nothing in the frozen calculation files changes: only the input `scan_h1.py` hands them.

THE DEPTH TRAP. The frozen D1 scorer needs >= 210 UTC-day bars, and the 5,000-row fetch clears that only because the
phantom weekend days count toward it (5,000 rows ~ 30 weeks ~ 179 real D1 bars once they are dropped). Twelvedata caps a
call at 5,000 rows, and a second call per pair per scan would cost ~144 extra credits a day against an 800/day budget that
is already ~75% used. So instead the newest fetch is MERGED INTO a persistent, append-only H1 history per wheel pair
(`data/h1_history/<PAIR>.csv`, seeded once from the deep cache, extended by every scan): the store supplies the older bars
that have aged out of the fetch window, and the pipeline always receives the last PIPELINE_ROWS *real* H1 rows (~41 weeks,
~250 D1 bars). No extra API call, ever.

SAFETY. `prepare_h1` never raises: any failure falls back to the freshly fetched rows with closed-market bars dropped (or,
failing even that, the untouched frame), so a bad store can degrade the depth but never break a scan.

MIGRATION. The numbers step-change once. Edge-triggered alerts compare against the previous scan, so the first scan under the
new convention would fire a burst of spurious "transitions". `is_migration_scan(prev)` lets callers suppress state-transition
pushes for that one scan; `BARS_CONVENTION` is written into signals.json every scan as the marker.
"""
from pathlib import Path

import numpy as np
import pandas as pd

ROOT = Path(__file__).parent.parent.parent
HISTORY_DIR = ROOT / "data" / "h1_history"

NY = "America/New_York"
NY_CLOSE_HOUR = 17
PIPELINE_ROWS = 5000          # real H1 rows handed to the pipeline (the frozen code was written against 5,000)
STORE_ROWS = 6000             # real H1 rows kept per pair in the history store
SOURCE_TZ = "Australia/Sydney"    # the timezone Twelvedata's raw hourly labels are in (DST-aware: +10 AEST / +11 AEDT)
BARS_CONVENTION = "fx_week_v3"    # v2 = labels converted to true UTC + closed-market rows dropped; v3 = + D1/H4 aggregated on the New York trading clock
_COLS = ["datetime", "open", "high", "low", "close"]


def closed_market_mask(h1_df: pd.DataFrame) -> np.ndarray:
    """True for rows OUTSIDE the real FX week [Sunday 17:00 ET, Friday 17:00 ET): all of Saturday, Sunday before 17:00,
    Friday from 17:00. `datetime` is UTC (Twelvedata's own timestamps); DST-correct via the tz conversion."""
    ny = pd.to_datetime(h1_df["datetime"], utc=True).dt.tz_convert(NY)
    wd, hr = ny.dt.weekday, ny.dt.hour
    return ((wd == 5) | ((wd == 6) & (hr < NY_CLOSE_HOUR)) | ((wd == 4) & (hr >= NY_CLOSE_HOUR))).to_numpy()


def within_fx_week(ny_naive: pd.DatetimeIndex) -> np.ndarray:
    """Same rule on tz-dropped America/New_York wall-clock times (the form crowd_data builds bars from)."""
    wd, hr = ny_naive.weekday, ny_naive.hour
    closed = (wd == 5) | ((wd == 6) & (hr < NY_CLOSE_HOUR)) | ((wd == 4) & (hr >= NY_CLOSE_HOUR))
    return ~np.asarray(closed)


def drop_closed(h1_df: pd.DataFrame) -> pd.DataFrame:
    """H1 rows inside the real FX week only; index reset; nothing else about the frame changes."""
    if h1_df is None or len(h1_df) == 0:
        return h1_df
    return h1_df[~closed_market_mask(h1_df)].reset_index(drop=True)


def is_migration_scan(prev) -> bool:
    """True on the ONE scan that first runs under this convention: there is a previous signals.json, but it was produced
    before the fix (no marker, or a different one). A first-ever run (no prev) is not a migration — nothing to compare."""
    return bool(prev) and prev.get("bars_convention") != BARS_CONVENTION


# ----------------------------------------------------------------------------------------
# the persistent H1 history
# ----------------------------------------------------------------------------------------
def to_utc_labels(labels, source_tz: str = SOURCE_TZ) -> pd.Series:
    """Naive local-time labels in `source_tz` -> true-UTC 'YYYY-MM-DD HH:MM:SS' strings. DST-aware. The one ambiguous local hour a year
    (and the skipped one) falls on a weekend when the market is closed; ambiguous is read as standard time, skipped shifts forward."""
    ts = pd.to_datetime(pd.Series(labels))
    loc = ts.dt.tz_localize(source_tz, ambiguous=False, nonexistent="shift_forward")
    return loc.dt.tz_convert("UTC").dt.strftime("%Y-%m-%d %H:%M:%S")


def to_trading_clock(h1_df: pd.DataFrame) -> pd.DataFrame:
    """True-UTC H1 rows -> the same rows with `datetime` re-labelled as NEW YORK wall time + 7 hours.

    The frozen aggregator groups H1 bars by the calendar date of the label (D1) and by 4-hour label blocks (H4). On this clock those groups are exactly
    the broker/TradingView conventions, with NO frozen file edited: label midnight = 17:00 New York (so D1 is the 17:00 NY close and the Sunday session
    rolls into Monday — no Sunday stub candle), and H4 blocks start at 17, 21, 01, 05, 09, 13 New York (TradingView's alignment; no 1-hour Friday or
    3-hour Sunday stub blocks). DST-aware. Used ONLY to build the D1 and H4 frames; H1 and everything that needs real UTC keeps the UTC labels."""
    out = h1_df.copy()
    ny = pd.to_datetime(out["datetime"], utc=True).dt.tz_convert(NY).dt.tz_localize(None) + pd.Timedelta(hours=7)
    out["datetime"] = ny.dt.strftime("%Y-%m-%d %H:%M:%S").to_numpy()
    return out


def normalize_frame(df: pd.DataFrame, source_tz: str = SOURCE_TZ) -> pd.DataFrame:
    """A raw fetched frame (any extra columns kept) -> labels in true UTC, sorted, de-duplicated, closed-market rows dropped."""
    if df is None or len(df) == 0:
        return df
    out = df.copy()
    if source_tz:
        out["datetime"] = to_utc_labels(out["datetime"], source_tz).to_numpy()
    out = out.sort_values("datetime").drop_duplicates(subset="datetime", keep="last")
    return drop_closed(out)


def _clean(df: pd.DataFrame, source_tz=None) -> pd.DataFrame:
    """`source_tz` None = labels are already UTC (the persistent store); a zone name = convert from it first."""
    out = df[_COLS].copy()
    if source_tz:
        out["datetime"] = to_utc_labels(out["datetime"], source_tz).to_numpy()
    else:
        out["datetime"] = pd.to_datetime(out["datetime"], utc=True).dt.strftime("%Y-%m-%d %H:%M:%S")
    for c in ("open", "high", "low", "close"):
        out[c] = pd.to_numeric(out[c], errors="coerce")
    return out.dropna(subset=["open", "close"])


def load_history(key: str, store_dir=None) -> pd.DataFrame:
    path = Path(store_dir or HISTORY_DIR) / f"{key}.csv"
    if not path.exists():
        return pd.DataFrame(columns=_COLS)
    return _clean(pd.read_csv(path))


def _write_history(key: str, df: pd.DataFrame, store_dir=None) -> None:
    d = Path(store_dir or HISTORY_DIR)
    d.mkdir(parents=True, exist_ok=True)
    df.to_csv(d / f"{key}.csv", index=False, float_format="%.6f")


def merge_history(history: pd.DataFrame, fresh: pd.DataFrame, store_rows: int = STORE_ROWS) -> pd.DataFrame:
    """Union of stored and freshly fetched real-FX-week rows; a re-fetched row REPLACES the stored one (the newest H1 bar
    is revised while it forms); sorted, de-duplicated by timestamp, closed-market rows dropped, newest `store_rows` kept."""
    both = pd.concat([history, fresh], ignore_index=True) if len(history) else fresh.copy()
    both = both.drop_duplicates(subset="datetime", keep="last").sort_values("datetime")
    both = drop_closed(both)
    return both.tail(store_rows).reset_index(drop=True)


def prepare_h1(key: str, fresh_df: pd.DataFrame, use_store: bool = True, store_dir=None, persist: bool = True,
               pipeline_rows: int = PIPELINE_ROWS, store_rows: int = STORE_ROWS, source_tz=SOURCE_TZ) -> pd.DataFrame:
    """The frame every downstream calculation should be built from for this pair: real FX week only, newest
    `pipeline_rows` rows. With `use_store` the older rows come from (and the fresh rows are appended to) the pair's
    persistent history. `source_tz` is the zone the fetched labels are in (Sydney); the returned frame is in true UTC. NEVER raises."""
    try:
        fresh = _clean(fresh_df, source_tz)
        if use_store:
            merged = merge_history(load_history(key, store_dir), drop_closed(fresh), store_rows)
            if persist:
                _write_if_changed(key, merged, store_dir)
        else:
            merged = drop_closed(fresh)
        out = merged.tail(pipeline_rows).reset_index(drop=True)
        # the pipeline expects the fetch's own frame shape: extra columns (volume) are not used by anything downstream
        return out
    except Exception as e:                                # noqa: BLE001 — degrade, never break a scan
        print(f"  ⚠ fx_week {key}: {type(e).__name__}: {e} — using the fetched rows with closed-market bars dropped")
        try:
            return normalize_frame(fresh_df, source_tz)
        except Exception:                                 # noqa: BLE001
            return fresh_df


def _write_if_changed(key: str, merged: pd.DataFrame, store_dir=None) -> None:
    path = Path(store_dir or HISTORY_DIR) / f"{key}.csv"
    if path.exists():
        try:
            old = load_history(key, store_dir)
            if len(old) == len(merged) and old["datetime"].iloc[-1] == merged["datetime"].iloc[-1] \
                    and np.allclose(old["close"].to_numpy(), merged["close"].to_numpy(), atol=1e-9):
                return                                    # nothing new — do not touch the file (no needless git churn)
        except Exception:                                 # noqa: BLE001 — an unreadable store is simply rewritten
            pass
    _write_history(key, merged, store_dir)
