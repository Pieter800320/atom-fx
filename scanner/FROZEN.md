FROZEN - verbatim fork of fx-signal-board. Never edit these files. New code goes in scanner\extend.

## Exceptions log (every deliberate touch of a frozen-tier file leaves a paper trail here)

- **2026-09-19 — real-FX-week bars (`scan_h1.py`, input only). Pieter's explicit sign-off** ("I think you can do all of these ...
  including the full B fix that changes frozen numbers"). Twelvedata has returned market-closed weekend bars since 2026-01-11;
  every frozen calculation was being run with them in its window. **What changed:** `scan_h1.py`'s fetch loop now hands the pipeline
  `scanner/extend/fx_week.prepare_h1(...)` — the fetched rows with closed-market bars dropped and, for the 12 wheel pairs, merged
  with a persistent H1 history (`data/h1_history/`) so the frozen D1 scorer keeps its >= 210 bars without a second API call — plus
  a `bars_convention` marker and a one-scan suppression of the edge-triggered pushes (the Gold Signal gate is on the PUSH only,
  the 2026-09-17 precedent). **What did not change:** no frozen calculation file (`aggregator.py`, `score.py`, `pills.py`,
  `regime.py`, `csm.py`, ...) was edited; `python -m tests.test_rule1_frozen` is green. **Expected effect (measured on real data,
  same window, live vs fixed):** H1 pill differs on 7/12 pairs, H4 pill 5/12, H4 score 11/12, H4 ADX mean 3.7 (max 12.7), BB touch
  state 4/12; regimes can shift. See `docs/ATOM_FX_BUILD_STATUS.md` outstanding item 18.

