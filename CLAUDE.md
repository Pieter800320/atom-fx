# CLAUDE.md — ATOM FX

Claude Code reads this file automatically at the start of every session in this repo. It is
**binding**. Read it fully before doing anything else.

ATOM FX is a native Android FX-trading app (Kotlin + Jetpack Compose) built around a radial
"Energy Wheel", on top of a frozen Python scanner backend. Full context: `docs/INDEX.md`.

---

## 1. Read the source before you build (mandatory)

The specification documents are the source of truth. A pasted brief, task prompt, or chat
summary **paraphrases** them and can be wrong or incomplete — it **never overrides** them.

Before you change anything, open the relevant document(s) yourself — do not rely on a summary:

- **Always first:** `docs/INDEX.md` (the map) and `docs/GLOSSARY.md` (use terms verbatim).
- **Before any UI / layout / visual change:** `docs/ATOM_FX_DESIGN.md` **and** the relevant
  mockup in `docs/mockups/`. Do not design a screen without reading its Design section first.
- **Before any data-shape / backend / signals.json change:** `docs/ATOM_FX_ARCHITECTURE.md`
  and `docs/ATOM_FX_FUNCTIONAL_SPEC.md`.
- **When starting a build phase:** `docs/ATOM_FX_SETUP_AND_KICKOFF.md`.

Conflict rules:
- If a brief and a spec doc disagree, **the spec doc wins.**
- If the docs are **silent** on something, **ask before inventing** — do not make a silent
  judgment call on geometry, layout, thresholds, colours, or data shapes.
- If you believe a spec itself is wrong, say so and propose the change — do not just deviate.

---

## 2. The Golden Rule — Rule #1 (never break)

The trading calculations never change. Three tiers of authority:

- **FROZEN — `scanner/*.py`.** Verbatim fork of FX Signal Board. **Never edit.** These produce
  every existing number. The `tests/` guard (`python -m tests.test_rule1_frozen`) must stay
  green; if a change would touch a frozen file, **stop and ask**.
  - Nuance: `scanner/scan_h1.py` / `scan_news.py` are "frozen logic, extend call-sites" — you
    may add a new delivery channel at a notification call-site, never a calculation or a firing
    condition.
- **EXTEND — `scanner/extend/*.py`.** New additive analytics that read frozen values and write
  new `signals.json` keys. Never modify a frozen file to make an EXTEND feature easier.
- **NEW — `app/`, `push/`, workflows.** Green field, but still bound by the design/architecture
  docs above.

The Android app is a **pure consumer** of `signals.json` (Architecture §8.3): it never
recomputes a trading number in Kotlin. If the app needs a value, expose it from the backend.

---

## 3. Non-negotiable design invariants (check on every UI change)

These are stated in `docs/ATOM_FX_DESIGN.md` — read them there, but the ones most often
violated:

- **§17 — the landing screen never scrolls.** The wheel is a centred square sized to
  `min(width, height − chrome)`. Header, status strip, the wheel (Currencies/Pairs toggle and
  Currency Flow ticker live *on*/*under* the dial itself, not separate rows — see
  `ATOM_FX_WHEEL_V2_SPEC.md` §11), and Tradeable Now are **all visible at once, no vertical or
  horizontal scroll.** Never use `requiredSize()` / `verticalScroll()` to force the wheel bigger
  than fits — the wheel shrinks to fit, the layout does not scroll.
- **§20 — the acceptance test.** The finished landing view must answer, with no sheet open:
  current regime, strong/weak currencies, leading/weakening currency, highest-potential pairs,
  developing pairs, ignorable pairs. Verify against this before declaring a UI task done.
- **§2 — theming.** One `AtomColors` token set; **no literal hex** anywhere. Everything works in
  light and dark. Derive ramps from tokens.
- **§16 — haptics on every interactive control.** Every tappable element gives haptic feedback,
  not just the wheel. Check this on any new button/pill/toggle/tab.
- **Wheel identity.** Angle = identity (fixed per pair/currency); only radius/level changes.
  Never re-rank angular positions.

---

## 4. Before you say a task is done

1. It **compiles** (`./gradlew assembleDebug` or a green build in Android Studio).
2. Any backend change: **`python -m tests.test_rule1_frozen` is green** (Rule #1) and
   `python -m tests.test_extend` passes.
3. UI change: it satisfies the **§20 acceptance test** and the **§17 no-scroll** rule on a real
   device/emulator.
4. In your summary, **state which doc sections you read** and **flag every independent judgment
   call** you made, so it can be checked against the specs.

---

## 5. Repo conventions

- `git pull --rebase` before `git push` (the scan bot commits `data/signals.json` automatically).
- Never commit `.venv/` or build output.
- Keep reference mockups in `docs/mockups/` and commit them, so they are always readable here.
- Feature work on `feat/*` branches; `main` stays buildable.
- **The in-app Library (`LibraryContent.kt`) is updated with every pertinent change** — a new
  signal, a new sheet element, a changed calculation, a renamed term. It claims to explain "every
  single element, calculation and item in the app," so it goes stale the moment a shipped change
  isn't reflected there. Update it in the same session the change ships, not deferred to later.
