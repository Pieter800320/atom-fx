"""
ATOM FX research — deepest-available NY-close D1 history, for the crowded-reversal study.

Pages Twelvedata H1 history BACKWARDS per pair (end_date cursor) until the provider runs out
(HTTP 404 / empty page / short page), then aggregates to the 17:00-America/New_York-close D1
convention with the repo's own scanner.extend.agg_nyclose (the same code the live scanner and
tools/bootstrap_d1_nyclose.py use — no second aggregation implementation, no convention seam).

Differs from tools/bootstrap_d1_nyclose.py in exactly two ways: (1) it does not stop at 550 D1
bars — it goes to the provider's limit; (2) the API key travels in an Authorization header, never
in the URL, so no error message or log line can leak it. It writes to a SEPARATE store
(data/d1_nyclose_long/) so the live 2-year store is never overwritten.

Measured 2026-09-19: Twelvedata H1 for EUR/USD begins between 2020-01 and 2020-05 (404 before);
its daily bars go back to 2008 but are UTC-based with weekend fragments and disagree with the NY
close (median 13-20 pips/bar), which changes which days the indicator's factors fire (27-72%
agreement) — so daily bars are deliberately NOT used.

    set TWELVEDATA_KEY=...            (never paste the key into chat or commit it)
    py -m tools.fetch_d1_deep [PAIR ...]          # default: the 12 study pairs; resumable

Outputs: data/d1_nyclose_long/<PAIR>.json (+ _meta.json); raw H1 -> data/h1_cache/ (gitignored).
"""
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

import pandas as pd

ROOT = Path(__file__).parent.parent
sys.path.insert(0, str(ROOT))

from scanner.extend import agg_nyclose, d1_store

PAIRS = ["EURUSD", "GBPUSD", "USDJPY", "USDCAD", "NZDUSD", "AUDUSD", "USDCHF",
         "EURJPY", "GBPJPY", "CADJPY", "AUDJPY", "NZDJPY"]
OUT_DIR = ROOT / "data" / "d1_nyclose_long"
RAW_DIR = ROOT / "data" / "h1_cache"
PAGE_SIZE = 5000
MAX_PAGES = 30
CALL_DELAY_S = 9          # free tier: 8 credits/min


def _get(params, key, retries=3):
    """Returns (status, json_or_None). status: 'ok' | 'nodata' (404) | 'error'."""
    url = "https://api.twelvedata.com/time_series?" + urllib.parse.urlencode(params, quote_via=urllib.parse.quote)
    req = urllib.request.Request(url, headers={"Authorization": f"apikey {key}", "User-Agent": "Mozilla/5.0"})
    for attempt in range(1, retries + 1):
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                data = json.loads(r.read().decode())
            if data.get("code") == 429:
                wait = 60 * attempt
                print(f"  429 rate limit - waiting {wait}s")
                time.sleep(wait)
                continue
            return ("ok", data) if "values" in data else ("error", data)
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return "nodata", None
            print(f"  HTTP {e.code} (attempt {attempt}/{retries})")
        except Exception as e:
            print(f"  {type(e).__name__} (attempt {attempt}/{retries})")
        time.sleep(15)
    return "error", None


def backfill_h1(pair, key):
    symbol = f"{pair[:3]}/{pair[3:]}"
    pages, end_date = [], None
    for _ in range(MAX_PAGES):
        params = {"symbol": symbol, "interval": "1h", "outputsize": PAGE_SIZE, "order": "ASC", "type": "price"}
        if end_date:
            params["end_date"] = end_date
        status, data = _get(params, key)
        time.sleep(CALL_DELAY_S)
        if status != "ok":
            break
        df = pd.DataFrame(data["values"])
        for col in ("open", "high", "low", "close"):
            df[col] = pd.to_numeric(df[col], errors="coerce")
        df = df.sort_values("datetime").reset_index(drop=True)
        if df.empty:
            break
        pages.append(df)
        oldest = str(df["datetime"].iloc[0])
        print(f"  {pair}: page {len(pages)}: {len(df)} bars, oldest {oldest}")
        if len(df) < PAGE_SIZE or oldest == end_date:
            break
        end_date = oldest
    if not pages:
        return pd.DataFrame(columns=["datetime", "open", "high", "low", "close"])
    return (pd.concat(pages, ignore_index=True).drop_duplicates(subset="datetime")
            .sort_values("datetime").reset_index(drop=True))


def main(pairs):
    key = os.environ.get("TWELVEDATA_KEY")
    if not key:
        sys.exit("Set TWELVEDATA_KEY in your environment first.")
    RAW_DIR.mkdir(parents=True, exist_ok=True)
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    meta_path = OUT_DIR / "_meta.json"
    meta = json.loads(meta_path.read_text()) if meta_path.exists() else {"pairs": {}}
    meta.update({"source": "Twelvedata time_series interval=1h type=price, paged backwards via end_date",
                 "aggregation": "scanner.extend.agg_nyclose.aggregate_d1_nyclose_dated (17:00 America/New_York close, FX-week filtered)",
                 "fetched_utc": datetime.now(timezone.utc).isoformat(timespec="seconds")})
    for pair in pairs:
        raw_path = RAW_DIR / f"{pair}_deep_h1.csv"
        if raw_path.exists():
            h1 = pd.read_csv(raw_path)
            print(f"{pair}: using cached raw H1 ({len(h1)} bars)")
        else:
            print(f"{pair}: fetching")
            h1 = backfill_h1(pair, key)
            if h1.empty:
                print(f"{pair}: NO DATA - skipped")
                continue
            h1.to_csv(raw_path, index=False)
        d1 = agg_nyclose.aggregate_d1_nyclose_dated(h1)
        d1_store.save_store(pair, d1, str(OUT_DIR))
        dates = pd.to_datetime(d1["date"])
        weekend = int((dates.dt.dayofweek >= 5).sum())
        meta["pairs"][pair] = {"h1_bars": int(len(h1)), "h1_first": str(h1["datetime"].iloc[0]),
                               "h1_last": str(h1["datetime"].iloc[-1]), "d1_bars": int(len(d1)),
                               "d1_first": str(dates.min().date()), "d1_last": str(dates.max().date()),
                               "phantom_weekend_bars": weekend}
        print(f"{pair}: {len(h1)} H1 -> {len(d1)} D1, {dates.min().date()} .. {dates.max().date()}, phantom weekend={weekend}")
        meta_path.write_text(json.dumps(meta, indent=2))


if __name__ == "__main__":
    main(sys.argv[1:] or PAIRS)
