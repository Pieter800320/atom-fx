# ATOM FX

A native Android FX trading app — a redesign of FX Signal Board around the **Energy Wheel**.

**New here? Start with [`docs/SETUP_RUNBOOK.md`](docs/SETUP_RUNBOOK.md)** — it walks you
through setting the project up step by step. Then read [`docs/INDEX.md`](docs/INDEX.md),
which links every specification document.

## The one rule

**Rule #1:** the frozen scanner calculations (`scanner/*.py`) never change. ATOM FX
calculates the same things as FX Signal Board and fires signals on the same criteria.
New analytics are additive, in `scanner/extend/`. The `tests/` folder enforces this
automatically.

## Layout

- `docs/` — the specifications and the visual mockup (open `docs/mockups/atom-fx-screen-kit.html`).
- `scanner/` — the frozen calculation engine (forked verbatim; do not edit).
- `tests/` — the Rule #1 regression guard.
- `fixtures/` — full `signals.json` states for building and testing the app offline.
- `tools/` — helper scripts.
- `app/` — the Android app (added during the build).

*Personal research tool. Nothing here is financial advice.*
