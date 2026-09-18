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
| **[ATOM_FX_LIBRARY_STYLE.md](ATOM_FX_LIBRARY_STYLE.md)** | Prose rules for every Library and Playbook entry — no developer narration, lead with the answer, lists for list-shaped things. | Writing or editing any `LibraryContent.kt`/`*PlaybookContent.kt` entry. |

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
- 2026-09-10 — ATOM_FX_DESIGN.md bumped to v1.1 (§17/§19/§20 resynced to the shipped Wheel v2 + Simplification Rework: Setup Bands vocabulary, the retired Tradeable Now card, the fixed D1/H4 wing consensus). ATOM_FX_BUILD_STATUS.md fully refreshed against current source (was last audited 2026-09-02) — first pass missed the Bollinger Band D1 touch alert, Watchlist screen, and Regime/Alert Playbook, corrected same day. `ATOM_FX_WHEEL_V2_SPEC.md` gained a §12 addendum: the dial is pairs-only now (no Currencies mode, no on-dial ticker), the 4 corner buttons are wing selectors not a mode/timeframe toggle, and there's no rim-glow treatment — §11's own bullets on those points are marked superseded rather than rewritten, to preserve the historical reasoning. The Functional Spec's pair-sheet tab list, and `ATOM_FX_DESIGN.md` §14.2 (still describes a
Currency Flow sheet deleted outright 2026-09-06) and §6/§6A generally (the pre-Wheel-v2 wheel
body — already understood to be superseded by `ATOM_FX_WHEEL_V2_SPEC.md` per this file's own
table, not audited line-by-line) are still known-stale — flagged for a follow-up pass, not
resynced today. Also found and fixed in this pass: two places (`CLAUDE.md` §3, this doc-sync's own
earlier same-day `ATOM_FX_DESIGN.md` edit) had repeated §11's stale on-dial-ticker/Currencies-toggle
claims without checking the code first — a reminder that a doc citing another doc's addendum still
needs its own verification, not just a citation.
- 2026-09-10 — added ATOM_FX_LIBRARY_STYLE.md v1.0 (Pieter's ask, after noticing Library/Playbook
  prose had drifted into commit-message-style developer narration — "Pieter's own catch," change
  dates, bug-fix history — instead of plain trading explanation). Full Library and Playbook
  rewrite to match, same day.
- 2026-09-17 — the deferred follow-up flagged above (§6/§6A, §14.2, and the Functional Spec's
  pair-sheet tab list) resynced, found via a full 4-area audit, not another targeted grep — the
  same failure mode this doc already warned about. `GLOSSARY.md`'s wheel/nav sections, and
  `ATOM_FX_DESIGN.md` §6/§6A/§11/§12/§13.1/§14/§14.7/§17 corrected with inline "superseded"
  notices (old content kept, not deleted, per this file's own established convention). Also fixed:
  `CLAUDE.md` §3 and `ATOM_FX_DESIGN.md` §17 both still claimed the wheel is sized
  `min(width, height-chrome)` with zero scroll — wrong since 2026-09-03 (`WheelScreen.kt`'s own
  flagged exception, itself never synced here either). `ATOM_FX_FUNCTIONAL_SPEC.md` got a
  whole-document staleness banner rather than a full rewrite — §1's table, §2's nav map, and §5
  are still not individually corrected; treat `ATOM_FX_BUILD_STATUS.md` §B and
  `ATOM_FX_DESIGN.md` §19 as current truth over that document's specifics until they are.
- 2026-09-17 (same day, continued) — the doc-drift audit above was one slice of a full
  four-area system audit (backend/scanner, Android data layer, Android UI, docs); the rest of
  its findings landed as code fixes across the session, each with its own same-commit doc
  update rather than deferred: `ATOM_FX_ARCHITECTURE.md` (stale `potential_state`/"Setup" push
  type, missing `bb_touch`), `ATOM_FX_SIGNALS_ROADMAP.md` §7 (stale toggle list) and its own §2
  banner (§2.1's retired alert), and `ATOM_FX_BUILD_STATUS.md` picked up several more corrections
  in the same pass: its own "Audited 2026-09-10" banner understating a dozen+ edits since, outstanding
  item 11 (`BreadthSheet.kt` cleanup — already done, never checked off), item 12
  (`versionCode`/`versionName` — already bumped to 2/"0.2", the row was stale), a new item 13
  logging the EXTEND-layer failure observability gap as an accepted limitation, and a new
  Structure-multiplier row (Pair sheet Breakdown tab) with a matching `LibraryContent.kt`
  update pointing at where that number now actually lives. Full list of code-side fixes (git
  rebase conflict-resolution flag, recommendation bias-flip trigger, notification-history race,
  signals-cache poisoning, literal-color tokens) is in `main`'s own commit history from this date
  — not duplicated here since none of them changed a spec doc's own content.

- 2026-09-18 — the 4-indicator ChartSheet glance panel completed (%B-20 + BandWidth joining
  RSI/MACD), new EXTEND module `scanner/extend/bollinger_series.py`, schema v10. Docs resynced in
  the same session per this repo's own rule: `ATOM_FX_DESIGN.md` §19.4a (superseded banner — the
  12-period pair %B card is deferred, not deleted, and Currency %B moved to `CurrencyDetailSheet`)
  and §19.4b (rewritten opening + the two new indicators' spec + `ChartCommon.kt`),
  `ATOM_FX_ARCHITECTURE.md` §4.2 (the `bollinger_series` contract, and why it is deliberately NOT a
  re-parameterisation of `bb_touch.py`), `GLOSSARY.md` (%B split into its two real reads, new
  BandWidth and Squeeze entries, Currency %B's new home), `ATOM_FX_BUILD_STATUS.md` (new row, three
  amended rows, new outstanding item 16 logging the BB-touch-alert rework Pieter flagged to
  discuss), and `LibraryContent.kt` (rewritten %B entry, new BandWidth entry, corrected placements
  for Currency %B and RSI/MACD). One stale comment found and fixed in passing:
  `PercentBOscillator.kt` still cited `ui/insights/PercentBBoardChart.kt` as a caller — a file
  deleted with the Insights %B picker and absent from the repo, checked rather than assumed.

- 2026-09-18 (2nd) — M15 added to the ChartSheet glance panel (Pieter's ask, "just as a
  chart"), fed by a genuinely separate, faster-cadence (~45 min) job — `scanner/scan_m15.py`,
  schema v11 — rather than folding into scan_h1.py's own 2h fetch (explicitly evaluated and
  rejected: it would either leave M15 just as stale as everything else, or require speeding up
  the whole scoring/alert pipeline, a materially bigger decision Pieter didn't ask for tonight).
  Docs resynced same session: `ATOM_FX_ARCHITECTURE.md` §4.2 (the new contract, the credit-
  budget math, the `m15_updated` field and why it's separate from `updated`), `ATOM_FX_DESIGN.md`
  §19.4b (the TF row + a new M15 subsection), `GLOSSARY.md` (%B/BandWidth TF lists), 
  `ATOM_FX_BUILD_STATUS.md` (new row, flagged **not yet live** — new outstanding item 17: the
  external Apps Script trigger for `scan_m15.yml` still needs to be added by Pieter, weekday-only,
  ~45-min cadence — this repo cannot configure it), and `LibraryContent.kt` (%B/BandWidth/RSI-MACD
  entries; also caught and fixed the %B entry's own summary line, stale since the 20-period
  rework — it still said "D1 Bollinger Bands" while its own howItWorks correctly described the
  D1/H4/H1 20-period read).

- 2026-09-18 (3rd) — the M15 Apps Script trigger went live and immediately surfaced a real
  production bug: `scan_h1.py` rebuilds `pairs_out` fresh every run, and its
  `attach_momentum_series`/`attach_bollinger_series` calls REPLACE each pair's whole series
  dict (d1/h4/h1 only) — silently erasing the "m15" key `scan_m15.py` had just written on the
  very next `scan_h1.py` run. Confirmed in production via GitHub's own commit history (a real
  `chore: m15 scan` commit's data was gone by the following `h1 scan` commit) before being
  fixed same-session. Fix: both `attach_*` functions take an optional `prev_pairs` argument
  and carry the "m15" key forward; `m15_updated` (missed the first time) added to
  `PRESERVED_KEYS`. `ATOM_FX_ARCHITECTURE.md` §4.2 and `ATOM_FX_BUILD_STATUS.md` (M15 row +
  item 17, now resolved) both resynced same session with the full incident + fix writeup.
  5 new regression tests, 97/97 extend suite green, Rule #1 holds. Lesson for future
  cross-cadence keys (this file's own PRESERVED_KEYS comment already said this, and it still
  got missed): a new key written outside `scan_h1.py`'s own run — at ANY nesting level, not
  just top-level — needs its own explicit carry-forward path the same session it starts being
  written, verified against a live production run, not just an isolated unit test of the
  writer alone.
