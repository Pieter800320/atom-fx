#!/usr/bin/env python3
"""
tools/bootstrap_d1_nyclose.py
=========================================================================
ATOM FX — one-time NY-close D1 deep-history bootstrap (DECISION-001, spec §3
"EMA200 warm-up"; weekend bars filtered per DECISION-007).

PURPOSE
    scanner/extend/d1_store.py's update_d1() carries the D1 series forward one scan at a
    time, but it needs a real multi-year history to seed EMA200 with (210 bars is too few
    for a faithful warm-up). This script pages Twelvedata H1 history backwards per pair,
    aggregates it to the 17:00-America/New_York-close D1 convention
    (scanner/extend/agg_nyclose.py, Task 1a), and writes the persisted store
    (scanner/extend/d1_store.py, Task 1b) that update_d1() will read from on every future
    hourly scan.

    Manual, one-time (run again only to re-seed from scratch — it's idempotent, each run
    overwrites the store with a fresh full backfill). NOT called by scan_h1.py (Task 4 wires
    the ongoing carry-forward; this script never runs automatically).

HOW TO RUN
    export TWELVEDATA_KEY=your_key_here        # (Windows: set TWELVEDATA_KEY=...)
    python -m tools.bootstrap_d1_nyclose               # all scanner.config.PAIRS
    python -m tools.bootstrap_d1_nyclose EUR/USD GBP/USD   # or just specific pairs

    Twelvedata free tier: 5000 bars/call, so each pair needs a handful of calls (~3, to
    cover ~2+ years of H1 -> 550+ NY-close D1 trading days), each call's 5000-bar window
    ending where the previous one's oldest bar started (`end_date` paging). ~8s between
    calls to stay well inside the per-minute rate limit; on a pair's error, prints and
    moves on to the next pair rather than aborting the whole run.

Rule #1: EXTEND. Reuses the frozen scanner.fetch module read-only for auth/rate-limiting
(fetch.API_KEY, fetch.BASE, fetch._rate_wait) — never edits scanner/fetch.py. The actual HTTP
request is this tool's own _get()/_build_url() (see their docstrings for why: fetch._get's
own query-string building doesn't survive an `end_date` value, which fetch.fetch_ohlcv()
doesn't even expose as a param in the first place). Reuses scanner.extend.agg_nyclose
(Task 1a) for the boundary math and scanner.extend.d1_store (Task 1b) for persistence — no
duplicated aggregation logic here.
=========================================================================
"""
import json
import sys
import time
import urllib.parse
import urllib.request

import pandas as pd

from scanner import fetch
from scanner.config import PAIRS
from scanner.extend import agg_nyclose
from scanner.extend import d1_store

PAGE_SIZE = 5000
TARGET_D1 = 550        # spec §3 acceptance: >= 500 NY-close D1 bars per pair after bootstrap
MAX_PAGES = 12          # safety cap; ~2+ years of H1 history needs ~3 pages in practice
CALL_DELAY_S = 8        # spec: "spaced for the rate limit"

_consecutive_429 = 0    # local to this tool; mirrors fetch._get's own daily-limit abort


def _build_url(endpoint: str, params: dict) -> str:
    """Pure URL builder, no I/O — properly percent-encodes every param via
    urllib.parse.urlencode(quote_via=urllib.parse.quote), unlike the frozen scanner.fetch._get
    (which this tool can't edit), whose query string is built by plain f"{k}={v}"
    concatenation. That concatenation works for every OTHER caller in this app because none of
    them pass a value containing a space or colon — but this tool's `end_date` paging cursor
    is Twelvedata's own "YYYY-MM-DD HH:MM:SS" format, which DOES contain both, and
    urllib.request.urlopen rejects a URL with a raw, unescaped space ("URL can't contain
    control characters"). quote() (not urlencode's default quote_plus) encodes a space as
    %20, not '+' — some datetime parsers mishandle '+' as a literal plus sign. This also
    percent-encodes a pair symbol's "/" (e.g. "EUR/USD" -> "EUR%2FUSD") — a cosmetic wire
    difference from the frozen fetch._get's raw, unescaped "/", but %2F decodes back to "/"
    correctly on any standards-compliant server, so Twelvedata reads the same symbol either way.
    """
    query = {**params, "apikey": fetch.API_KEY}
    return f"{fetch.BASE}/{endpoint}?" + urllib.parse.urlencode(query, quote_via=urllib.parse.quote)


def _get(endpoint: str, params: dict, retries: int = 3) -> dict:
    """Same request/retry/rate-limit/daily-abort behaviour as the frozen scanner.fetch._get,
    reusing its rate limiter (fetch._rate_wait) and credentials (fetch.API_KEY/fetch.BASE)
    read-only — only the URL construction differs (_build_url() above), which is the actual
    bug fix. Kept local to this tool rather than editing scanner/fetch.py."""
    global _consecutive_429
    url = _build_url(endpoint, params)
    for attempt in range(1, retries + 1):
        fetch._rate_wait()
        try:
            with urllib.request.urlopen(url, timeout=60) as r:
                data = json.loads(r.read().decode())
            if data.get("code") == 429:
                _consecutive_429 += 1
                if _consecutive_429 >= 3:
                    raise RuntimeError(
                        f"Daily API credit limit reached (3 consecutive 429s). "
                        f"Message: {data.get('message', '-')}"
                    )
                wait = 15 * attempt
                print(f"  ⚠ 429 rate limit — waiting {wait}s (attempt {attempt}/{retries})")
                time.sleep(wait)
                continue
            _consecutive_429 = 0
            return data
        except RuntimeError:
            raise
        except Exception as e:
            print(f"  ⚠ _get attempt {attempt}/{retries} failed: {e}")
            if attempt < retries:
                time.sleep(15)
    raise TimeoutError(f"All {retries} attempts failed for {url[:80]}")


def _fetch_h1_page(symbol: str, end_date: str | None, outputsize: int = PAGE_SIZE):
    """One page of raw H1 bars. Adds the `end_date` backward-paging cursor, which
    scanner.fetch.fetch_ohlcv() doesn't expose as a param. Returns None on a provider error."""
    params = {"symbol": symbol, "interval": "1h", "outputsize": outputsize,
              "order": "ASC", "type": "price"}
    if end_date:
        params["end_date"] = end_date
    raw = _get("time_series", params)
    if raw.get("status") == "error" or "values" not in raw:
        print(f"  ⚠ {symbol}: fetch error at end_date={end_date}: {raw.get('message', '?')}")
        return None
    df = pd.DataFrame(raw["values"])
    for col in ("open", "high", "low", "close"):
        df[col] = pd.to_numeric(df[col], errors="coerce")
    return df.sort_values("datetime").reset_index(drop=True)


def _backfill_h1(pair: str) -> pd.DataFrame:
    """Page H1 backwards until >= TARGET_D1 NY-close trading days are covered, or the
    provider runs out of history (a page shorter than PAGE_SIZE), or MAX_PAGES is hit."""
    pages = []
    end_date = None
    for _ in range(MAX_PAGES):
        page = _fetch_h1_page(pair, end_date)
        time.sleep(CALL_DELAY_S)
        if page is None or page.empty:
            break
        pages.append(page)

        combined = (pd.concat(pages, ignore_index=True)
                    .drop_duplicates(subset="datetime")
                    .sort_values("datetime")
                    .reset_index(drop=True))
        if len(agg_nyclose.aggregate_d1_nyclose_dated(combined)) >= TARGET_D1:
            break
        if len(page) < PAGE_SIZE:
            break  # provider is exhausted -- no more history available for this pair

        end_date = str(page["datetime"].iloc[0])  # next page: everything older than this

    if not pages:
        return pd.DataFrame(columns=["datetime", "open", "high", "low", "close"])
    return (pd.concat(pages, ignore_index=True)
            .drop_duplicates(subset="datetime")
            .sort_values("datetime")
            .reset_index(drop=True))


def bootstrap_pair(pair: str) -> None:
    h1 = _backfill_h1(pair)
    if h1.empty:
        print(f"{pair}: no data fetched, skipped")
        return

    d1 = agg_nyclose.aggregate_d1_nyclose_dated(h1)
    d1_store.save_store(pair, d1)

    dates = pd.to_datetime(d1["date"])
    phantom_sundays = int((dates.dt.dayofweek == 6).sum())
    phantom_saturdays = int((dates.dt.dayofweek == 5).sum())
    print(f"{pair}: {len(d1)} D1 bars, {dates.min().date()} -> {dates.max().date()}, "
          f"phantom-Sunday count = {phantom_sundays}, phantom-Saturday count = {phantom_saturdays}")
    if phantom_sundays or phantom_saturdays:
        print(f"  ⚠ {pair}: expected 0 phantom weekend days — investigate before trusting this store.")


def main(pairs=None) -> None:
    if not fetch.API_KEY:
        sys.exit("Set TWELVEDATA_KEY in your environment first.")
    for pair in (pairs or PAIRS):
        try:
            bootstrap_pair(pair)
        except Exception as e:
            print(f"{pair}: ERROR - {e}")
            continue


if __name__ == "__main__":
    main(sys.argv[1:] or None)
