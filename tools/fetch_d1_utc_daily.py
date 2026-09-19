"""
ATOM FX research — Twelvedata NATIVE daily bars (UTC-based), for the out-of-sample era check.

Twelvedata's own `1day` FX bars reach back to ~2008 (5,000 rows per call) but are UTC-based and carry
weekend fragments; they are NOT the 17:00-New-York-close convention the rest of this research uses
(measured 2026-09-19: median 13-20 pips/bar close disagreement; the `timezone` parameter has no effect on
daily bars; the indicator's factors agree on which days are "on" only 27-72% of the time). They are therefore
used ONLY where nothing else reaches: an EARLIER-ERA replication (Experiment 5), reported separately and
never mixed with the NY-close stores.

    set TWELVEDATA_KEY=...            (never paste the key into chat or commit it; sent as a header, not in the URL)
    py -m tools.fetch_d1_utc_daily [PAIR ...]

Output: data/d1_utc_daily/<PAIR>.csv (date, open, high, low, close — raw, weekend rows included; the runner drops
Saturday/Sunday rows at load time) and _meta.json.
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
PAIRS = ["EURUSD", "GBPUSD", "USDJPY", "USDCAD", "NZDUSD", "AUDUSD", "USDCHF",
         "EURJPY", "GBPJPY", "CADJPY", "AUDJPY", "NZDJPY"]
OUT_DIR = ROOT / "data" / "d1_utc_daily"
CALL_DELAY_S = 9          # free tier: 8 credits/min


def fetch_pair(pair, key):
    params = {"symbol": f"{pair[:3]}/{pair[3:]}", "interval": "1day", "outputsize": 5000,
              "order": "ASC", "timezone": "UTC"}
    url = "https://api.twelvedata.com/time_series?" + urllib.parse.urlencode(params, quote_via=urllib.parse.quote)
    req = urllib.request.Request(url, headers={"Authorization": f"apikey {key}", "User-Agent": "Mozilla/5.0"})
    for attempt in range(1, 4):
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                data = json.loads(r.read().decode())
            if data.get("code") == 429:
                time.sleep(60 * attempt)
                continue
            if "values" not in data:
                print(f"  {pair}: {data.get('message', 'no values')}")
                return None
            df = pd.DataFrame(data["values"]).rename(columns={"datetime": "date"})
            for col in ("open", "high", "low", "close"):
                df[col] = pd.to_numeric(df[col], errors="coerce")
            df["date"] = pd.to_datetime(df["date"])
            return df.sort_values("date").reset_index(drop=True)[["date", "open", "high", "low", "close"]]
        except urllib.error.HTTPError as e:
            print(f"  {pair}: HTTP {e.code} (attempt {attempt})")
        except Exception as e:
            print(f"  {pair}: {type(e).__name__} (attempt {attempt})")
        time.sleep(15)
    return None


def main(pairs):
    key = os.environ.get("TWELVEDATA_KEY")
    if not key:
        sys.exit("Set TWELVEDATA_KEY in your environment first.")
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    meta_path = OUT_DIR / "_meta.json"
    meta = json.loads(meta_path.read_text()) if meta_path.exists() else {"pairs": {}}
    meta.update({"source": "Twelvedata time_series interval=1day outputsize=5000 order=ASC timezone=UTC",
                 "convention": "UTC-based native daily bars, weekend fragments included (NOT 17:00 NY close)",
                 "fetched_utc": datetime.now(timezone.utc).isoformat(timespec="seconds")})
    for pair in pairs:
        df = fetch_pair(pair, key)
        time.sleep(CALL_DELAY_S)
        if df is None or df.empty:
            print(f"{pair}: NO DATA - skipped")
            continue
        df.to_csv(OUT_DIR / f"{pair}.csv", index=False, float_format="%.6f")
        weekend = int((df["date"].dt.dayofweek >= 5).sum())
        meta["pairs"][pair] = {"rows": int(len(df)), "first": str(df["date"].min().date()),
                               "last": str(df["date"].max().date()), "weekend_rows": weekend}
        print(f"{pair}: {len(df)} rows {df['date'].min().date()} .. {df['date'].max().date()} (weekend rows {weekend})")
        meta_path.write_text(json.dumps(meta, indent=2))


if __name__ == "__main__":
    main(sys.argv[1:] or PAIRS)
