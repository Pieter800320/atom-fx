"""
ATOM FX — persisted NY-close D1 store (DECISION-001, spec §3 "EMA200 warm-up").

The one-time bootstrap (tools/bootstrap_d1_nyclose.py) writes each pair's deep-history
NY-close D1 series to data/d1_nyclose/<PAIR>.json. Every hourly scan (Task 4) then calls
update_d1() with that scan's own fresh H1 fetch: it loads the store, folds in the newly-
closed trading day(s), recomputes EMA50/EMA200 on the FULL accumulated series, and saves —
so EMA200 gets a real multi-year warm-up while hourly API cost stays at today's 1 call/pair.

Rule #1: EXTEND. Reuses scanner.extend.agg_nyclose (Task 1a) for the boundary/grouping math
and the frozen scanner.score._ema (via agg_nyclose.with_emas) for EMAs — never re-implemented
here. update_d1() does NOT fetch anything itself; the caller (Task 4) supplies fresh_h1_df.
"""
import json
import os

import pandas as pd

from scanner.extend.agg_nyclose import aggregate_d1_nyclose_dated, with_emas

DEFAULT_DATA_DIR = "data/d1_nyclose"
_COLUMNS = ["date", "open", "high", "low", "close"]


def _path(pair: str, data_dir: str) -> str:
    return os.path.join(data_dir, f"{pair.replace('/', '')}.json")


def load_store(pair: str, data_dir: str = DEFAULT_DATA_DIR) -> pd.DataFrame:
    """The persisted D1 history for `pair` (date, open, high, low, close; oldest first).
    Returns an empty, correctly-columned DataFrame if no store file exists yet."""
    path = _path(pair, data_dir)
    if not os.path.exists(path):
        return pd.DataFrame(columns=_COLUMNS)

    with open(path) as f:
        rows = json.load(f)
    if not rows:
        return pd.DataFrame(columns=_COLUMNS)

    df = pd.DataFrame(rows)[_COLUMNS]
    df["date"] = pd.to_datetime(df["date"])
    for col in ("open", "high", "low", "close"):
        df[col] = df[col].astype(float)
    return df


def save_store(pair: str, d1_dated_df: pd.DataFrame, data_dir: str = DEFAULT_DATA_DIR) -> None:
    """Write pair's D1 history as one JSON file: a list of {date, open, high, low, close}
    records, date as an ISO 'YYYY-MM-DD' string."""
    os.makedirs(data_dir, exist_ok=True)
    out = d1_dated_df.copy()
    out["date"] = pd.to_datetime(out["date"]).dt.strftime("%Y-%m-%d")
    rows = out[_COLUMNS].to_dict(orient="records")
    with open(_path(pair, data_dir), "w") as f:
        json.dump(rows, f, indent=2)


def merge(store_df: pd.DataFrame, fresh_dated_df: pd.DataFrame) -> pd.DataFrame:
    """
    Union store_df and fresh_dated_df by 'date'. Where a date is present in both, the FRESH
    row wins (this is how the last, still-forming trading day gets updated as more of its
    H1 bars close through the day); dates only in one side are kept as-is; dates only in
    fresh are appended. Result: sorted ascending by date, no duplicate dates.
    """
    store_norm = store_df.copy()
    if not store_norm.empty:
        store_norm["date"] = pd.to_datetime(store_norm["date"])
    fresh_norm = fresh_dated_df.copy()
    if not fresh_norm.empty:
        fresh_norm["date"] = pd.to_datetime(fresh_norm["date"])

    combined = pd.concat([store_norm, fresh_norm], ignore_index=True)
    if combined.empty:
        return pd.DataFrame(columns=_COLUMNS)

    # fresh rows are appended AFTER store rows above, so keep="last" on an overlapping date
    # keeps the fresh value without depending on sort_values() being stable for ties.
    combined = combined.drop_duplicates(subset="date", keep="last")
    combined = combined.sort_values("date").reset_index(drop=True)
    for col in ("open", "high", "low", "close"):
        combined[col] = combined[col].astype(float)
    return combined[_COLUMNS]


def update_d1(pair: str, fresh_h1_df: pd.DataFrame, data_dir: str = DEFAULT_DATA_DIR) -> pd.DataFrame:
    """
    Load pair's persisted D1 store, fold in fresh_h1_df's own NY-close aggregation, save the
    merged store, and return it WITH ema50/ema200 attached (frozen scanner.score._ema, via
    with_emas). This is the function Task 4's hourly scan calls per pair — it never fetches
    anything itself; the caller supplies that scan's own already-fetched H1 bars.
    """
    store = load_store(pair, data_dir)
    fresh_dated = aggregate_d1_nyclose_dated(fresh_h1_df)
    merged = merge(store, fresh_dated)
    save_store(pair, merged, data_dir)
    return with_emas(merged)
