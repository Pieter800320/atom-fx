"""
ATOM FX — M15 glance-panel chart scan  (Design §19.4b, M15 added 2026-09-18)

100% NEW orchestrator — like `scan_cot.py` (not `scan_h1.py`/`scan_news.py`'s "frozen
logic, extend call-sites" shape), this file has no frozen ancestor. It exists for one
reason: Pieter wants the ChartSheet glance panel's M15 option meaningfully fresher
(~45 min) than the rest of the app's existing 2h `scan_h1.py` cadence, without moving
that cadence, its fetch, or its Rule #1 posture at all.

Flow:
  1. Load the existing signals.json (already written by the last scan_h1.py run).
  2. Fetch a GENUINELY SEPARATE M15 OHLCV series for the 12 wheel pairs — M15 cannot be
     derived from the existing H1 fetch (you cannot manufacture 15-minute resolution out
     of 60-minute bars), so this is a second, independent Twelvedata call per pair.
  3. Run the SAME frozen `_rsi`/`_macd` (via momentum_series._series_for) and the SAME
     EXTEND-tier band math (via bollinger_series._series_for) already used for d1/h4/h1,
     on these M15 closes.
  4. Merge the result into each pair's EXISTING `momentum_series`/`bollinger_series` maps
     under a new "m15" key — never replacing the whole dict, so d1/h4/h1 (scan_h1.py's
     own, on its own cadence) are left byte-for-byte untouched.
  5. Save. Bump `m15_updated` (a timestamp separate from the main `updated` field — see
     that field's own note below for why).

Deliberately NOT the 18-pair CSM/scoring universe — only the 12 wheel pairs (`PAIRS`),
since this is chart-only (ChartSheet, long-press a wheel node) and the 6 CSM_EXTRA pairs
have no ChartSheet surface to feed.

Runs on its own faster cadence — see .github/workflows/scan_m15.yml — independent of and
never invoked from scan_h1.py. **The actual trigger lives in the external Apps Script
scheduler, not in this repo** (same as every other workflow here) — whoever adds it MUST
scope it to the same weekday-only hours the existing scan_h1/scan_news triggers already
use, or it burns real Twelvedata credits fetching a closed weekend market for nothing.

Credit budget (Pieter's own call, 2026-09-18, after walking through the free-tier math):
12 pairs/run. At the chosen ~45 min cadence (32 runs/day) that's 384 credits/day, on top
of scan_h1.py's unchanged 144/day — 528/800, comfortable headroom. A per-pair fetch
failure here never aborts the whole run and never corrupts other pairs' data (see the
per-pair merge in the loop below) — a pair simply keeps whatever "m15" data it already
had (or none) until the next successful run for it.

Rule #1: reads raw OHLCV only, over a NEW interval scan_h1.py never fetches — never
touches d1/h4/h1, never calls the frozen aggregator, never feeds score.py, a score, an
alert, or the regime. If this job fails or is never triggered, every existing number in
the app is completely unaffected: the glance panel's M15 cards simply show "Not
available yet", the same fail-quiet convention every other missing-history case here
already uses.
"""
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

import pandas as pd

ROOT = Path(__file__).parent.parent
sys.path.insert(0, str(ROOT))

from scanner.config import PAIRS
from scanner.fetch import fetch_ohlcv
from scanner.extend import momentum_series as _momentum_series
from scanner.extend import bollinger_series as _bollinger_series

M15_INTERVAL = "15min"

# 1500 M15 bars ≈ 15.6 days — comfortably covers every warm-up need here (RSI/MACD's own
# ~30-bar minimum, Bollinger's 40-bar minimum, the squeeze detector's 125-bar lookback,
# plus the shared 90-point display tail) with real margin for weekends/holidays. Far
# below the 5000 H1 fetch uses — that number is sized for FROZEN D1 EMA200 warm-up
# (scanner/config.py's own comment), a requirement that does not apply here since M15
# never feeds scoring. A side effect worth knowing: at 1500 bars, every visible M15 bar
# in the 90-point tail has a FULL 125-bar squeeze lookback behind it — better squeeze
# coverage than D1 gets today (D1's own ~208-bar total history leaves only its most
# recent ~84 bars able to carry a verdict; see bollinger_series.py's own doc comment).
M15_OUTPUTSIZE = 1500


def load_signals():
    path = ROOT / "data" / "signals.json"
    if path.exists():
        try:
            with open(path) as f:
                return json.load(f)
        except Exception as e:
            print(f"  [load_signals] ERROR: {path} exists but failed to parse ({e}).")
    return None


def save_signals(data: dict):
    path = ROOT / "data" / "signals.json"
    path.parent.mkdir(exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)


def _m15_dates(df: pd.DataFrame) -> pd.Series:
    """Oldest-first ISO datetime strings, straight from this fetch's own `datetime`
    column — M15 is a genuinely separate fetch, not H1-derived, so unlike
    tf_dates.py's H4/H1 recovery there is nothing to reconstruct here. `fetch_ohlcv`
    already sorts ascending with a clean 0..n index (fetch.py's own sort_values +
    reset_index), so this shares that same index with `df["close"]` by construction —
    exactly what momentum_series._series_for's `dates_full.tail(n)` call needs to stay
    aligned with the values it labels. Returned as a Series (not a plain list): that
    same call requires a pandas object with `.tail()`, the same contract
    tf_dates.h1_dates()/h4_dates() already honour for their own callers."""
    return pd.to_datetime(df["datetime"], utc=True).dt.strftime("%Y-%m-%dT%H:%M:%S")


def main():
    print("=== ATOM FX — M15 Glance-Panel Chart Scan ===")
    now = datetime.now(timezone.utc)

    signals = load_signals()
    if not signals or not signals.get("pairs"):
        print("✗ No pairs data in signals.json yet (scan_h1 hasn't run) — aborting.")
        return

    print(f"\n[1/2] Fetching M15 OHLCV for {len(PAIRS)} wheel pairs ({M15_OUTPUTSIZE} bars each)…")
    fetched = 0
    for i, pair in enumerate(PAIRS):
        key = pair.replace("/", "")
        print(f"  [{i+1}/{len(PAIRS)}] {key} M15")
        try:
            df = fetch_ohlcv(pair, M15_INTERVAL, M15_OUTPUTSIZE)
        except RuntimeError as e:
            # Daily credit limit — abort the fetch loop immediately, same convention
            # scan_h1.py's own fetch loop uses. Pairs already updated this run keep
            # their fresh data; pairs not yet reached keep whatever they had before.
            print(f"  ✗ {e}")
            print(f"  Aborting fetch — {fetched}/{len(PAIRS)} pairs updated this run.")
            break
        except Exception as e:
            print(f"  ⚠ {key} skipped: {e}")
            continue
        if df is None or key not in signals["pairs"]:
            continue

        close = df["close"].astype(float)
        dates = _m15_dates(df)

        block = signals["pairs"][key]
        # Merge into the EXISTING maps — never replace the whole dict, so d1/h4/h1
        # (written by scan_h1.py, on its own 2h cadence) are left completely untouched.
        momentum = dict(block.get("momentum_series") or {})
        momentum["m15"] = _momentum_series._series_for(close, dates)
        block["momentum_series"] = momentum

        bollinger = dict(block.get("bollinger_series") or {})
        bollinger["m15"] = _bollinger_series._series_for(close, dates)
        block["bollinger_series"] = bollinger

        fetched += 1

    if fetched == 0:
        print("\n✗ No pairs updated this run — leaving signals.json untouched.")
        return

    # A separate freshness marker from the main `updated` field — deliberately NOT
    # touching `updated` itself, since that field drives the app's own staleness check
    # (Architecture §8.4) against the WHOLE dataset (alerts, regime, CSM, everything
    # scan_h1.py writes). Bumping it here would tell the app the entire scan is fresher
    # than it actually is, when only the M15 chart data changed this run. Reflects "the
    # last run that updated at least one pair" — a run where every pair failed leaves it
    # unchanged, rather than claiming a refresh that didn't actually happen. Not yet
    # surfaced anywhere in the app UI (out of scope for the chart itself); kept in the
    # data so a future staleness indicator doesn't need a schema change to add one.
    signals["m15_updated"] = now.isoformat()

    save_signals(signals)
    print(f"\n[2/2] Saved — {fetched}/{len(PAIRS)} pairs updated.")
    print("=== M15 Glance-Panel Chart Scan complete ===")


if __name__ == "__main__":
    main()
