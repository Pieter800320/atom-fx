#!/usr/bin/env python3
"""
verify_candle_fidelity.py
=========================================================================
ATOM FX — D1 candle-boundary fidelity check.

PURPOSE
    Quantify how much ATOM FX's current Daily candle (built by grouping
    H1 bars on the UTC calendar date, i.e. a 00:00-UTC close) differs from
    a broker-standard Daily candle (17:00 New York close, DST-aware, with
    the Sunday session rolled into Monday) — for the calculations the
    trend-following methodology depends on: the D1 50/200 EMA bias.

    This is the evidence for the "canonical D1 boundary" Decision Log entry.
    It changes nothing in your repo. It only measures and reports.

WHAT IT REPORTS, per pair:
    - D1 bars available (EMA200 warm-up check; 200 is the bare minimum)
    - Phantom "Sunday" daily candles produced by the UTC-date method
    - % of recent days the two methods DISAGREE on the price-vs-EMA50 bias
    - % of recent days they disagree on the price-vs-EMA200 bias
    - The current (latest) bias under each method, side by side

HOW TO RUN
    export TWELVEDATA_KEY=your_key_here        # (Windows: set TWELVEDATA_KEY=...)
    python verify_candle_fidelity.py

    Uses 12 API calls (one 5000-bar H1 fetch per pair), spaced ~10s apart
    to stay inside the free-tier limits — about 2 minutes total. No data is
    written anywhere; results print to the console.

READING THE RESULT
    If the disagreement percentages are near zero, the UTC-midnight D1 is
    fine as-is and you can build the methodology on it. If they are
    materially non-zero (a few % or more), the D1 boundary must be fixed to
    17:00 NY before the app can be called execution-grade — because a
    disagreement day is a day the app's D1 bias would differ from what you
    see on your own broker/TradingView chart.
=========================================================================
"""

import os
import sys
import time
import json
import urllib.request
import urllib.parse
from datetime import timedelta

import numpy as np
import pandas as pd

# --- config (mirrors scanner/config.py) ----------------------------------
PAIRS = [
    "EUR/USD", "GBP/USD", "USD/JPY", "USD/CHF",
    "AUD/USD", "USD/CAD", "NZD/USD",
    "EUR/JPY", "GBP/JPY", "AUD/JPY", "NZD/JPY", "CAD/JPY",
]
API_KEY   = os.environ.get("TWELVEDATA_KEY", "")
BASE      = "https://api.twelvedata.com"
OUTPUTSIZE = 5000          # same as TF_BARS["h1"] in the app
MIN_DELAY  = 10.0          # seconds between calls (6/min, matches fetch.py)
LOOKBACK_DAYS = 200        # window over which to measure disagreement
NY_TZ = "America/New_York"

_last_call = 0.0


def _rate_wait():
    global _last_call
    wait = MIN_DELAY - (time.monotonic() - _last_call)
    if wait > 0:
        time.sleep(wait)
    _last_call = time.monotonic()


def fetch_h1(symbol: str) -> pd.DataFrame | None:
    """Fetch 5000 H1 bars for one pair (same endpoint/params as the app)."""
    params = {
        "symbol": symbol, "interval": "1h", "outputsize": OUTPUTSIZE,
        "order": "ASC", "type": "price", "apikey": API_KEY,
    }
    url = f"{BASE}/time_series?" + urllib.parse.urlencode(params)
    _rate_wait()
    try:
        with urllib.request.urlopen(url, timeout=60) as r:
            data = json.loads(r.read().decode())
    except Exception as e:
        print(f"  ! fetch failed {symbol}: {e}")
        return None
    if data.get("status") == "error" or "values" not in data:
        print(f"  ! api error {symbol}: {data.get('message', '?')}")
        return None
    df = pd.DataFrame(data["values"])
    for c in ("open", "high", "low", "close"):
        df[c] = pd.to_numeric(df[c], errors="coerce")
    df["dt"] = pd.to_datetime(df["datetime"], utc=True)
    return df.sort_values("dt").reset_index(drop=True)


# --- the two aggregations ------------------------------------------------

def agg_utc_midnight(h1: pd.DataFrame) -> pd.DataFrame:
    """CURRENT app method: group H1 by UTC calendar date (00:00 UTC close)."""
    g = h1.set_index("dt")
    d1 = g.resample("D", closed="left", label="left").agg(
        open=("open", "first"), high=("high", "max"),
        low=("low", "min"), close=("close", "last"),
    ).dropna(subset=["open", "close"])
    d1.index = d1.index.date          # key by UTC date
    return d1


def agg_ny_close(h1: pd.DataFrame) -> pd.DataFrame:
    """
    BROKER-STANDARD method: trading day runs 17:00 NY -> 17:00 NY, DST-aware.
    Implemented in NY *wall-clock* time so DST is handled automatically, then
    shifted +7h so 17:00 becomes the day boundary. The Sunday 17:00 session
    therefore rolls into Monday's candle (no phantom Sunday stub).
    """
    ny_local = h1["dt"].dt.tz_convert(NY_TZ).dt.tz_localize(None)  # naive NY wall time
    trading_day = (ny_local + timedelta(hours=7)).dt.floor("D").dt.date
    tmp = h1.assign(_key=trading_day)
    d1 = tmp.groupby("_key").agg(
        open=("open", "first"), high=("high", "max"),
        low=("low", "min"), close=("close", "last"),
    )
    return d1


def add_ema_bias(d1: pd.DataFrame) -> pd.DataFrame:
    c = d1["close"].astype(float)
    d1 = d1.copy()
    d1["ema50"]  = c.ewm(span=50,  adjust=False).mean()
    d1["ema200"] = c.ewm(span=200, adjust=False).mean()
    d1["bias50"]  = (c > d1["ema50"]).astype(int)
    d1["bias200"] = (c > d1["ema200"]).astype(int)
    return d1


def disagreement(a: pd.DataFrame, b: pd.DataFrame, col: str) -> float:
    """% of the last LOOKBACK_DAYS calendar days the two biases differ."""
    # index both by calendar date, forward-fill onto a common daily axis
    sa = pd.Series(a[col].values, index=pd.to_datetime(list(a.index)))
    sb = pd.Series(b[col].values, index=pd.to_datetime(list(b.index)))
    start = max(sa.index.min(), sb.index.min())
    end   = min(sa.index.max(), sb.index.max())
    if pd.isna(start) or pd.isna(end) or start >= end:
        return float("nan")
    cal = pd.date_range(start, end, freq="D")
    sa = sa.reindex(cal).ffill()
    sb = sb.reindex(cal).ffill()
    window = sa.tail(LOOKBACK_DAYS)
    other  = sb.tail(LOOKBACK_DAYS)
    mask = window.notna() & other.notna()
    if mask.sum() == 0:
        return float("nan")
    return round(100.0 * (window[mask] != other[mask]).mean(), 1)


def count_phantom_sundays(d1_utc: pd.DataFrame) -> int:
    """UTC-date daily bars that fall on a Sunday (the ~2h weekend-open stub)."""
    return sum(1 for d in d1_utc.index if pd.Timestamp(d).weekday() == 6)


def main():
    if not API_KEY:
        sys.exit("Set TWELVEDATA_KEY in your environment first.")
    print(f"ATOM FX candle-fidelity check  —  {len(PAIRS)} pairs, "
          f"last {LOOKBACK_DAYS} days\n")
    header = (f"{'PAIR':8} {'D1bars':>6} {'Sun':>4} "
              f"{'EMA50 diff':>11} {'EMA200 diff':>12}  {'now (UTC/NY)':>14}")
    print(header)
    print("-" * len(header))

    rows = []
    for sym in PAIRS:
        h1 = fetch_h1(sym)
        if h1 is None or len(h1) < 210:
            print(f"{sym.replace('/',''):8} — insufficient data")
            continue
        utc = add_ema_bias(agg_utc_midnight(h1))
        ny  = add_ema_bias(agg_ny_close(h1))
        d50  = disagreement(utc, ny, "bias50")
        d200 = disagreement(utc, ny, "bias200")
        phantom = count_phantom_sundays(utc)
        now_utc = "L" if utc["bias50"].iloc[-1] else "S"
        now_ny  = "L" if ny["bias50"].iloc[-1] else "S"
        name = sym.replace("/", "")
        print(f"{name:8} {len(ny):>6} {phantom:>4} "
              f"{d50:>10}% {d200:>11}%  {now_utc:>7}/{now_ny:<6}")
        rows.append((name, d50, d200, phantom))

    if rows:
        avg50  = np.nanmean([r[1] for r in rows])
        avg200 = np.nanmean([r[2] for r in rows])
        print("-" * len(header))
        print(f"{'AVG':8} {'':>6} {'':>4} {avg50:>10.1f}% {avg200:>11.1f}%")
        print("\nInterpretation:")
        print(f"  EMA50 bias differs on ~{avg50:.1f}% of days, "
              f"EMA200 on ~{avg200:.1f}%, on average.")
        print("  Near 0%  -> UTC-midnight D1 is fine; build on it as-is.")
        print("  A few %+ -> fix the D1 boundary to 17:00 NY before building.")
        print("  'now (UTC/NY)' shows today's live EMA50 bias under each method;")
        print("  any pair showing L/S (or S/L) is a live disagreement right now.")


if __name__ == "__main__":
    main()
