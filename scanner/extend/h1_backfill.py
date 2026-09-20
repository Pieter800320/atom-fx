"""
ATOM FX — cold-start backfill of the persistent H1 history (EXTEND; 2026-09-20)

WHY. Twelve Data's terms (twelvedata.com/terms, section 2.2/2.3, last updated 2026-01-01) do not allow redistributing its raw data without a redistribution add-on: derived data
that cannot recreate the original is fine, raw candles in a PUBLIC repository are not. So `data/h1_history/` (the persistent hourly store `fx_week.py` merges each scan into) is no
longer committed; it lives in the workflow's private Actions cache instead (.github/workflows/scan_h1.yml). A cache can be evicted, so when a wheel pair's store is short
(`fx_week.store_rows(key) < fx_week.MIN_STORE_ROWS`) `scan_h1.py` calls `fetch_older` ONCE to pull the older 5,000-row window that ends just before the freshly fetched one — 12 extra
API calls, only after a cold start — and the merge then rebuilds the store.

Read-only use of the frozen `scanner.fetch._get`; nothing frozen is edited. Never raises for anything except the daily-credit RuntimeError the caller already handles.
"""
import pandas as pd


def fetch_older(pair: str, first_raw_label: str, outputsize: int = 5000):
    """Twelve Data hourly rows that END one hour before `first_raw_label` (a raw Twelve Data label, i.e. Sydney time, exactly as fetched). Returns a raw-shaped
    DataFrame (oldest first) or None when the API returns nothing usable."""
    from scanner.fetch import _get                                  # frozen, read-only
    end = (pd.to_datetime(first_raw_label) - pd.Timedelta(hours=1)).strftime("%Y-%m-%d %H:%M:%S").replace(" ", "%20")
    raw = _get("time_series", {"symbol": pair, "interval": "1h", "outputsize": outputsize, "order": "ASC", "type": "price", "end_date": end})
    if raw.get("status") == "error" or "values" not in raw:
        print(f"  \u26a0 backfill {pair}: {raw.get('message', 'no values')}")
        return None
    df = pd.DataFrame(raw["values"])
    for col in ("open", "high", "low", "close"):
        df[col] = pd.to_numeric(df[col], errors="coerce")
    return df.sort_values("datetime").reset_index(drop=True)
