# tests/ — the Rule #1 guard

This folder protects **Rule #1**: the frozen scanner calculations must never change.

## Files

| File | What it is |
|---|---|
| `frozen_probe.py` | Builds a deterministic synthetic OHLCV fixture (no network, no market data) and runs every frozen calculation on it. Test code — it *calls* the frozen modules but never edits them. |
| `make_golden.py` | **One-time bootstrap.** Snapshots the current frozen output to `golden/frozen_golden.json`. Run once, right after you fork the scanner. |
| `test_rule1_frozen.py` | The regression test. Recomputes and asserts the result still matches the golden snapshot. Fails (with the exact number that changed) if any frozen file alters a calculation. |
| `golden/` | Holds your `frozen_golden.json` after you bootstrap it. |

## Use

```bash
# 1. ONCE, right after forking the scanner verbatim:
python -m tests.make_golden
#    → writes golden/frozen_golden.json  (you should see "pairs snapshotted: 12")

# 2. Any time (and in CI, on every push):
pytest tests/test_rule1_frozen.py
#    or:  python -m tests.test_rule1_frozen
```

If the test **fails**, a frozen scanner file changed in a way that alters a number.
Do **not** "fix" it by re-running `make_golden`. Investigate what changed first —
that failure is the whole point. Only regenerate the golden if Pieter has
explicitly signed off on a deliberate change to the frozen engine.

## Notes

- The fixture is synthetic and seeded (`FIXTURE_SEED` in `frozen_probe.py`) — it is
  about *stability*, not realistic prices. Do not change the seed or `N_H1_BARS`;
  that would invalidate your golden.
- Floats are rounded to 6 dp before comparison to absorb tiny cross-machine
  differences. For maximum determinism, pin `numpy`/`pandas` versions in
  `requirements.txt` and bootstrap the golden in the same environment CI uses.
