# ATOM FX — Documentation Index

The single entry point for the project. Read in this order; every other doc
cross-references by section number. **Claude Code should read this file first.**

## The contract (read these before writing code)

| Doc | What it governs | Read when |
|---|---|---|
| **[SETUP_RUNBOOK.md](SETUP_RUNBOOK.md)** | Step-by-step: create the repo, place files, run the Rule #1 test, first commit. | Setting the project up (do this first). |
| **[ATOM_FX_ARCHITECTURE.md](ATOM_FX_ARCHITECTURE.md)** | The technical contract: frozen vs extend vs new tiers, the `signals.json` data contract, the app architecture, build phases, Rule #1. | Any backend or app-structure work. |
| **[ATOM_FX_DESIGN.md](ATOM_FX_DESIGN.md)** | The visual/interaction contract: tokens, both themes, the two wheels, bottom nav, sheets, motion, accessibility. | Any UI work. |
| **[ATOM_FX_FUNCTIONAL_SPEC.md](ATOM_FX_FUNCTIONAL_SPEC.md)** | What displays where: the function-and-placement inventory, the macro archetype engine, navigation & flow. | Deciding what goes on which screen. |
| **[ATOM_FX_SETUP_AND_KICKOFF.md](ATOM_FX_SETUP_AND_KICKOFF.md)** | GitHub/FCM/secrets detail + the exact phase-by-phase prompts to give Claude Code. | Driving CC through the build. |
| **[GLOSSARY.md](GLOSSARY.md)** | Exact terms. Use these names verbatim — do not invent synonyms. | Whenever naming anything. |
| **[mockups/atom-fx-screen-kit.html](mockups/atom-fx-screen-kit.html)** | Visual mockup of every surface + the screen-flow map. Open in a browser. | Building any screen. |
| **[ATOM_FX_WHEEL_V2_SPEC.md](ATOM_FX_WHEEL_V2_SPEC.md)** | The radial-dial wheel redesign — supersedes `ATOM_FX_DESIGN.md` §6/§6A's wheel specifics where they conflict. §11 is a running addendum of what actually shipped vs the original brief. | Any wheel/dial work. |
| **[mockups/atom-fx-wheel-preview.html](mockups/atom-fx-wheel-preview.html)** | Interactive reference render of the Wheel v2 dial (dark/light × currencies/pairs). Open in a browser. | Any wheel/dial work. |
| **[ATOM_FX_SIGNALS_ROADMAP.md](ATOM_FX_SIGNALS_ROADMAP.md)** | Phased plan for new signals/notifications (state-transition alerts, COT conviction, Bollinger reversals, rate differential), from a full audit of the frozen scanner against both upstream zips. | Implementing any new signal or push notification. |

## The safety net & test data

| Path | What it is |
|---|---|
| **`tests/`** | The Rule #1 regression guard. Bootstrap once (`python -m tests.make_golden`), then it fails any change to a frozen calculation. See `tests/README.md`. |
| **`fixtures/`** | Full `signals.json` documents for each market state (risk-on, risk-off, no-setups, liquidity-shock) — build and test the app offline. See `fixtures/README.md`. |
| **`tools/make_ui_fixtures.py`** | Regenerates the fixtures. |

## The three change tiers (the one rule that matters most)

- **FROZEN** — `scanner/*.py`, forked verbatim from `fx-signal-board`. Never edit. Rule #1.
- **EXTEND** — `scanner/extend/*.py`, new additive analytics that read frozen values (csm_delta, currency_flow, breadth, potential, macro_regime, spark, recommendation).
- **NEW** — the Android app, push transport, everything in `app/`.

If a change would edit a frozen file, **stop and ask Pieter.**

## Versioning

Each spec has a version at the top. When a spec changes, bump its version and note
what changed in a short line here:

- v1.0 — initial architecture, design, functional spec, setup, runbook, fixtures, Rule #1 test.
- 2026-09-04 — added ATOM_FX_SIGNALS_ROADMAP.md v1.0 (phased plan for new signals/notifications, from a full audit of the frozen scanner against both upstream zips).
