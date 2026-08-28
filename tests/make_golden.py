"""
One-time bootstrap: snapshot the CURRENT frozen calculations as the golden file.

Run this ONCE, right after you fork the scanner verbatim into atom-fx, while the
scanner is known-good. From then on, test_rule1_frozen.py guards against drift.

    python -m tests.make_golden

Re-run it ONLY when you have deliberately and knowingly changed frozen behaviour
with Pieter's sign-off (should be almost never).
"""
import json
from pathlib import Path
from tests.frozen_probe import compute_frozen

GOLDEN = Path(__file__).parent / "golden" / "frozen_golden.json"


def main():
    GOLDEN.parent.mkdir(parents=True, exist_ok=True)
    data = compute_frozen()
    with open(GOLDEN, "w") as f:
        json.dump(data, f, indent=2, sort_keys=True)
    print(f"✓ wrote {GOLDEN}")
    print(f"  csm currencies: {list(data['csm']['h4'].keys())}")
    print(f"  pairs snapshotted: {len(data['pairs'])}")
    print(f"  ranked setups: {len(data['rank'])}")


if __name__ == "__main__":
    main()
