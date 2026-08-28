"""
Rule #1 regression test — the automated guardian of the frozen calculations.

It recomputes every frozen calculation on the fixed synthetic fixture and asserts
the result is byte-identical (after 6dp rounding) to the committed golden snapshot.

    pytest tests/test_rule1_frozen.py        # or: python -m tests.test_rule1_frozen

If this FAILS, a frozen scanner file changed in a way that alters a number.
That is exactly what Rule #1 forbids. Do NOT "fix" the test by regenerating the
golden — investigate what changed first.
"""
import json
from pathlib import Path
from tests.frozen_probe import compute_frozen

GOLDEN = Path(__file__).parent / "golden" / "frozen_golden.json"


def _load_golden():
    assert GOLDEN.exists(), (
        "No golden file. Run `python -m tests.make_golden` once, right after "
        "forking the scanner verbatim, to create it."
    )
    with open(GOLDEN) as f:
        return json.load(f)


def _diff(a, b, path=""):
    """Return the first human-readable difference, or None if equal."""
    if isinstance(a, dict) and isinstance(b, dict):
        if a.keys() != b.keys():
            return f"{path}: keys differ (golden {sorted(a)} vs now {sorted(b)})"
        for k in a:
            d = _diff(a[k], b[k], f"{path}.{k}")
            if d:
                return d
        return None
    if isinstance(a, list) and isinstance(b, list):
        if len(a) != len(b):
            return f"{path}: length {len(a)} → {len(b)}"
        for i, (x, y) in enumerate(zip(a, b)):
            d = _diff(x, y, f"{path}[{i}]")
            if d:
                return d
        return None
    if a != b:
        return f"{path}: {a!r} → {b!r}"
    return None


def test_frozen_calculations_unchanged():
    golden = _load_golden()
    current = json.loads(json.dumps(compute_frozen()))  # normalise tuples→lists
    diff = _diff(golden, current)
    assert diff is None, (
        "RULE #1 VIOLATION — a frozen calculation changed:\n  " + diff +
        "\n\nA frozen scanner file was modified in a way that alters a number. "
        "Revert it, or get Pieter's explicit sign-off and rerun make_golden."
    )


if __name__ == "__main__":
    test_frozen_calculations_unchanged()
    print("✓ Rule #1 holds — frozen calculations unchanged.")
