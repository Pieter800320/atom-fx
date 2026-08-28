# ATOM FX — GitHub Setup & Claude Code Kickoff

**Version:** 1.0 · Companion to `ATOM_FX_ARCHITECTURE.md` and `ATOM_FX_DESIGN.md`
**Goal:** get from "empty idea" to "Claude Code building ATOM FX in Android Studio, against the specs, without drifting."

This is the practical runbook. Do the parts in order. Where a step is a one-time account thing (FCM, secrets), it's marked **[once]**. Where you paste a prompt into Claude Code, it's in a fenced block you can copy verbatim.

---

## 0. The mental model (read once)

You are building **one repo, three layers** (architecture §1):

- **Frozen scanner** — your existing FX Signal Board Python, copied in unchanged. It keeps producing the same numbers and the same signal firing criteria (Rule #1).
- **Extend layer** — a few new Python files that *add* fields to `signals.json` (the wheel's data) without touching the frozen files.
- **Android app** — a brand-new Kotlin/Compose app that reads `signals.json` and draws the Energy Wheel.

Claude Code will do almost all of the typing. Your job is to set up the repo + accounts, hand CC the three spec documents, and drive it phase by phase (architecture §9). The single most important habit: **CC edits `scanner/extend/` and `app/`, and never edits the frozen `scanner/*.py` files.**

---

## 1. Create the GitHub repo **[once]**

You already have `Pieter800320/fx-signal-board`. Leave it untouched — it's your reference.

1. GitHub → **New repository** → name `atom-fx` → **Private** (you can make it public later for free Actions minutes) → **Add a README** → Create.
2. Clone it locally to where you keep projects:
   ```bash
   git clone https://github.com/Pieter800320/atom-fx
   cd atom-fx
   ```
3. Create the top-level structure (empty for now — CC fills it):
   ```bash
   mkdir -p scanner/extend push data app docs .github/workflows
   ```
4. Copy the three spec docs into `docs/`:
   ```
   docs/ATOM_FX_ARCHITECTURE.md
   docs/ATOM_FX_DESIGN.md
   docs/ATOM_FX_SETUP_AND_KICKOFF.md   ← this file
   ```
5. Commit:
   ```bash
   git add . && git commit -m "chore: scaffold atom-fx + specs" && git push
   ```

---

## 2. Fork the scanner in (the frozen core)

Copy your existing scanner **verbatim** so Rule #1 holds by construction.

```bash
# from inside atom-fx/
cp -R ../fx-signal-board/scanner/*.py           scanner/
cp -R ../fx-signal-board/scanner/level_ema_alerts.py scanner/   # if not already copied
cp    ../fx-signal-board/requirements.txt        .
cp -R ../fx-signal-board/data/signals.json       data/          # seed data
git add . && git commit -m "chore: fork frozen scanner verbatim" && git push
```

> Do **not** copy `dashboard/` (that's the old web UI) or the old workflows yet. We adapt workflows in step 5. Do **not** copy `scanner/__pycache__/`.

**Lock the frozen files (recommended):** add a `CODEOWNERS` and a short note so any accidental edit is obvious in a diff.
```
# .github/CODEOWNERS
scanner/*.py            @Pieter800320   # FROZEN — Rule #1, do not edit
```
Add a `scanner/FROZEN.md` containing one line: *"These files are a verbatim fork of fx-signal-board. Rule #1: never edit. New analytics go in scanner/extend/."*

---

## 3. Firebase / FCM for push **[once]**

This replaces Telegram delivery with native push, on the **same firing criteria** (architecture §7).

1. [console.firebase.google.com](https://console.firebase.google.com) → **Add project** → name it `atom-fx` → you can disable Analytics.
2. **Add an Android app** to the project:
   - Package name: pick now and keep it, e.g. `com.pieter.atomfx` (must match the app's `applicationId` later).
   - Download **`google-services.json`** → you'll drop it into `app/` when the app module exists (step 6).
3. **Service account for the backend** (so GitHub Actions can send push):
   - Firebase → Project settings → **Service accounts** → **Generate new private key** → downloads a JSON file.
   - Note your **Project ID** (top of settings).
4. Keep both files safe; you'll paste them into GitHub secrets next. **Never commit them.**

---

## 4. GitHub Actions secrets **[once]**

atom-fx → Settings → **Secrets and variables → Actions** → New repository secret, for each:

| Secret | Value | Notes |
|---|---|---|
| `TWELVEDATA_KEY` | your Twelvedata key | same as reference |
| `ANTHROPIC_API_KEY` | your Anthropic key | powers scan_news + recommendation |
| `FCM_SERVICE_ACCOUNT` | **entire contents** of the service-account JSON | paste the whole file |
| `FCM_PROJECT_ID` | Firebase project ID | e.g. `atom-fx` |
| `TELEGRAM_BOT_TOKEN` | *(optional)* | only if you want Telegram as a fallback |
| `TELEGRAM_CHAT_ID` | *(optional)* | " |

---

## 5. Workflows (CI) — adapt from the reference

Copy the two workflow files from `fx-signal-board/.github/workflows/` (`scan_h1.yml`, `scan_news.yml`) into `atom-fx/.github/workflows/` and change three things (CC can do this — see the Phase 0 prompt):

1. **Remove the Pages deploy job** from `scan_h1.yml` (there's no web dashboard now — the app reads `signals.json` from GitHub raw). Keep the *scan* job and its `git commit data/`.
2. **Swap the alert transport:** the scan still runs identically; where it previously relied on Telegram secrets, ensure `send_push` (architecture §7) is what fires. Add `FCM_SERVICE_ACCOUNT` and `FCM_PROJECT_ID` to the job `env`.
3. **Run the extend layer** after the frozen scan: the hourly job calls the frozen scan, then the `scanner/extend/` steps write the new keys. (CC wires this in Phase 1.)

Keep the schedules as they are (hourly-ish scan, 2-hourly news, Sunday week-ahead). Do not poll harder (architecture §8.4).

---

## 6. Android Studio + Claude Code — the kickoff

Install **Android Studio** (latest stable), and the **Claude Code** plugin/terminal inside it. Open the `atom-fx` folder as the project root. Everything below is what you type to Claude Code.

> **Golden rule to give CC first, every session.** Paste this as the very first message whenever you start a CC session on this project:

```
You are building ATOM FX. The binding specs are in docs/:
- docs/ATOM_FX_ARCHITECTURE.md  (what the numbers are; the frozen/extend/new tiers)
- docs/ATOM_FX_DESIGN.md         (how it looks and behaves)

Hard rules:
1. RULE #1: never edit scanner/*.py (the frozen fork). They produce the same
   numbers and the same signal firing criteria as fx-signal-board. New analytics
   go ONLY in scanner/extend/. If you think a frozen file must change, STOP and ask me.
2. The Android app is a pure consumer of data/signals.json. It never recomputes
   pills, CSM, regime, momentum, continuation, rank, structure, or the six-factor
   potential. If the app needs a value, it must already be a field in signals.json;
   if it isn't, add it in scanner/extend/, not in Kotlin.
3. Build in the phases in ARCHITECTURE §9, one at a time. The app/scanner must
   run at the end of every phase. Do not scaffold future phases early.
4. When the spec is silent, ask me before inventing.
Acknowledge these rules and then wait for the phase instruction.
```

Then drive it **one phase per instruction**. Suggested prompts:

### Phase 0 — Pipeline parity
```
Phase 0. Do NOT touch scanner/*.py. Adapt .github/workflows/scan_h1.yml and
scan_news.yml from the reference: remove the GitHub Pages deploy job, keep the
scan + commit-data jobs, and add FCM_SERVICE_ACCOUNT and FCM_PROJECT_ID to env.
Then add a CI check that runs the scanner on a fixed OHLCV fixture and asserts
every FROZEN key in data/signals.json is byte-identical to a golden file
(EXTEND keys excluded). Show me the diff and the workflow files.
```

### Phase 1 — Extend backend
```
Phase 1. Implement the EXTEND layer exactly per ARCHITECTURE §5 and §6, in
scanner/extend/ only: csm_delta.py, currency_flow.py, breadth.py,
structure_expose.py, potential.py, potential_config.py (all thresholds here),
and recommendation.py (v1 seed+Sonnet per §6). Wire them into the hourly and
news scans AFTER the frozen steps, writing the new signals.json keys from §4.2,
plus schema_version=1 and per-pair structure. Add unit tests for the six-factor
sequential logic (the §51–§53 examples). Prove the frozen keys are unchanged.
```

### Phase 2 — Static wheel
```
Phase 2. Create the Android app module (Kotlin, Jetpack Compose, Material 3,
applicationId com.pieter.atomfx, minSdk 26). Build ONLY the Energy Wheel on a
Compose Canvas from MOCK WheelUiState — geometry, six rings, 12 nodes at fixed
angles (angle=index*30-90, EURUSD at 12 o'clock), radial paths, factor markers,
nucleus — following DESIGN §6 and the tokens in DESIGN §2-§4. No networking yet.
It must run on a device and be responsive with no horizontal scroll.
```

### Phases 3–9
Give one prompt per phase, each naming the architecture/design sections it implements (§9 lists them): wire real `signals.json` (Ph3), bottom sheets + WHY checklist (Ph4), panels/pills/tradeable-now/status-strip (Ph5), FCM push + client (Ph6), recommendation panel (Ph7), animations & polish (Ph8), chart WebView island + preserved features (Ph9). Always end a phase by running it before moving on.

---

## 7. Habits that prevent drift

- **One phase, one branch, one PR.** `feat/phase-1-extend`, etc. Review the diff before merge — the CODEOWNERS note makes any frozen-file edit jump out.
- **Re-paste the Golden Rule** at the start of every CC session; context resets between sessions.
- **If CC proposes editing a `scanner/*.py` frozen file, say no** and ask it to solve it in `scanner/extend/` or expose a field instead.
- **Keep thresholds in `potential_config.py`.** When the wheel "feels wrong," you tune config, you don't touch logic.
- **Byte-diff the frozen keys** after every backend change (the Phase 0 CI check does this automatically).
- **Handover note per session** (your existing convention): before ending, ask CC to write a short `docs/handover/<date>.md` of what changed and what's next.

---

## 8. Quick reference — what lives where

| You want to change… | Edit… | Never edit… |
|---|---|---|
| A trading number / when a signal fires | nothing — it's frozen (ask Pieter) | `scanner/*.py` |
| Where nodes sit on the wheel (factor thresholds) | `scanner/extend/potential_config.py` | frozen scoring |
| A new data field the app needs | `scanner/extend/*.py` (+ `schema_version`) | Kotlin (no recompute) |
| Wheel look / motion / colours | `app/…/ui/theme/*`, `WheelCanvas.kt` | the data contract |
| Sheet contents | `app/…/ui/sheets/*` | frozen fields' meaning |
| Push wording / channel | `push/send_push.py`, `app/…/notif/*` | the firing criteria |

---

## 9. First-day checklist

- [ ] `atom-fx` repo created, specs in `docs/`
- [ ] Frozen scanner forked in; `FROZEN.md` + `CODEOWNERS` added
- [ ] Firebase project + Android app + `google-services.json` downloaded; service-account key generated
- [ ] All GitHub secrets set
- [ ] Android Studio opens `atom-fx`; Claude Code responds to the Golden Rule
- [ ] Phase 0 green: workflows adapted, frozen-key parity check passing
- [ ] Phase 1 green: `signals.json` now carries `csm_delta`, `currency_flow`, `breadth`, `potential`, per-pair `structure`, `recommendation`, `schema_version` — with frozen keys unchanged

When those are ticked, you're building the wheel.

*End of Setup & Kickoff Guide.*
