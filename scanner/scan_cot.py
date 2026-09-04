"""
ATOM FX — weekly COT/Conviction scan  (Signals Roadmap §4)

100% NEW orchestrator — unlike `scan_h1.py`/`scan_news.py` (frozen logic, extend
call-sites), this file has no frozen ancestor; it's a green-field entry point that reads
the already-hourly-fresh `signals.json`, adds a positioning overlay, and writes it back.

Flow:
  1. Load the existing signals.json (already fresh from the last scan_h1.py run —
     pills, csm, breadth, reset_score are all there; no re-fetch, no recompute).
  2. Fetch + parse CFTC's public TFF report (scanner/extend/cot.py).
  3. Compute the 6-input Conviction score (scanner/extend/conviction.py), EWMA-smoothed
     against last week's conviction key.
  4. Write the new `conviction` key, save.
  5. Detect conviction_extreme transitions and push them (same send_push_alert drop-in
     Phase 1's state_alerts.py and the frozen Gold Signal both use).

Runs on its own weekly cadence (CFTC publishes Fridays, covering the prior Tuesday) —
see .github/workflows/scan_cot.yml. Never invoked from scan_h1.py.
"""
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).parent.parent
sys.path.insert(0, str(ROOT))

from scanner.extend import cot as _cot
from scanner.extend import conviction as _conviction
from push.alert_helpers import send_push_alert


def load_signals():
    path = ROOT / "data" / "signals.json"
    if path.exists():
        try:
            with open(path) as f:
                return json.load(f)
        except Exception:
            pass
    return {}


def save_signals(data: dict):
    path = ROOT / "data" / "signals.json"
    path.parent.mkdir(exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)


def main():
    print("=== ATOM FX — Weekly COT/Conviction Scan ===")
    now = datetime.now(timezone.utc)

    signals = load_signals()
    if not signals.get("pairs"):
        print("✗ No pairs data in signals.json yet (scan_h1 hasn't run) — aborting.")
        return

    prev_conviction = signals.get("conviction")

    print("\n[1/3] Fetching CFTC TFF (COT) data…")
    cot_data = _cot.fetch_cot_data()
    print(f"  COT date: {cot_data.get('cot_date')} · stale: {cot_data.get('cot_stale')}")

    print("\n[2/3] Computing Conviction scores…")
    conviction = _conviction.compute_conviction(
        cot_data=cot_data,
        pairs_block=signals.get("pairs", {}),
        csm_d1=signals.get("csm", {}).get("d1", {}),
        breadth=signals.get("breadth", {}),
        prev_conviction=prev_conviction,
    )
    conviction["updated"] = now.isoformat()
    for ccy, entry in conviction["currencies"].items():
        print(f"  {ccy}: conviction={entry['conviction']:+d} (cot_available={entry['cot_available']})")

    signals["conviction"] = conviction
    save_signals(signals)
    print("\n✓ signals.json saved")

    print("\n[3/3] Checking conviction_extreme transitions…")
    alerts = _conviction.compute_conviction_alerts(conviction, prev_conviction)
    for alert in alerts:
        print(f"  🔔 {alert['type']} — {alert['deeplink']}")
        send_push_alert(alert["msg"], alert["type"], alert["deeplink"], direction=alert.get("direction"))
    if not alerts:
        print("  No new extreme crossings this week.")

    print("=== Weekly COT/Conviction Scan complete ===")


if __name__ == "__main__":
    main()
