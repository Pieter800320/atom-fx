# ATOM FX — Build Status & Outstanding Items

**Audited:** 2026-09-02, against the source in the repo (`atomfxmain 1.zip` snapshot) and the five
spec docs. This is the **living tracker** the project was missing — the specs describe the whole
destination; this maps how far it's built. Update the Status column as work lands.

> Nothing in the vision was lost. Every surface below is specified in
> `docs/ATOM_FX_FUNCTIONAL_SPEC.md`, `docs/ATOM_FX_ARCHITECTURE.md`, `docs/ATOM_FX_DESIGN.md`,
> and shown in `docs/mockups/atom-fx-screen-kit.html`. The "thin documentation" feeling was the
> absence of *this* file, not missing specs.

Legend: ✅ done · 🟡 partial · ⬜ not started · 🅿️ post-v1 (deferred on purpose)

---

## A. Backend (Python scanner) — essentially complete

| Item | Status | Where / notes |
|---|---|---|
| Frozen engine + Rule #1 guard (Phase 0) | ✅ | `scanner/*.py`, `tests/`, `rule1.yml` green |
| EXTEND Phase 1 (csm_delta, currency_flow, breadth, structure, potential, spark, schema_version) | ✅ | `scanner/extend/`, wired in `scan_h1.py`, **live in `signals.json`** |
| Macro archetype engine (Phase 1b) | ✅ | `scanner/extend/macro_regime.py`, `macro_regime` live |
| Push transport + firing hook (Phase 6 backend) | ✅ | `push/send_push.py` + `scan_h1.py` `send_push_alert` / `send_push_level_alert` wired; `test_push.yml` green |
| AI recommendation — deterministic seed | ✅ | `recommendation.py` (use_model=False in `scan_h1`); `recommendation` live |
| **AI recommendation — Sonnet narration + 12h cadence (Phase 7 full)** | ⬜ | Module ready; NOT wired into `scan_news.py`. This is the "AI implementation" — switch on the model call + cadence (Architecture §6, Functional Spec §7) |
| **`deep_analysis` daily brief 404 fix** | ⬜ | `scan_news.py:22` `SONNET_MODEL="claude-sonnet-4-20250514"` is retired → update to a current model id. Config fix, not a calc (Rule #1 safe) |
| **`news_themes` tagging** (headlines → macro axis) | ⬜ | Optional EXTEND (Functional Spec §7). "News adds theme, engine sets direction" |

---

## B. Android app (Kotlin/Compose) — by surface

| Surface (spec ref) | Status | Where / what remains |
|---|---|---|
| Energy Wheel — pair mode (Design §6) | ✅ | `ui/wheel/*` (radial dial v2). Verify §17 no-scroll on device |
| Currency strength on the wheel (§3A) | ✅ | Merged into the wheel's Currencies/Pairs toggle (wheel v2 decision) |
| Cross-asset ring + sheet | ✅ | `WheelCanvas` outer ring + `ui/sheets/CrossAssetSheet.kt` |
| Six factor sheets + Pair sheet (Phase 4) | ✅ | `ui/sheets/` (Regime, Flow, Breadth, Momentum, Structure, Entry, Pair) |
| Status strip, scrolling pills, Tradeable Now (Phase 5) | ✅ | `ui/components/` |
| Line chart, 3-TF, no candles (Phase 9) | ✅ | `ui/chart/LineChart.kt` + `ChartSheet` |
| Macro screen (Phase 9) | ✅ | `ui/macro/MacroScreen.kt` (archetype, bias, evidence, cross-asset table) |
| Push client (Phase 6) | ✅ | `push/AtomFxMessagingService.kt` + `DeepLink.kt` |
| Theme tokens, dual light/dark (Design §2) | ✅ | `ui/theme/*` |
| Animations & polish (Phase 8) | 🟡 | Wheel has motion; re-verify after wheel v2 (mode cross-fade, living-dial, §17 no-scroll) |
| **3-tab bottom nav + swipe (HorizontalPager)** | ✅ | `MainActivity.kt` — `Wheel · Macro · Insights` via `HorizontalPager` + Material 3 `NavigationBar`, synced both ways |
| **Insights tab/screen (§7, master table 46–50)** | ✅ | `ui/insights/InsightsScreen.kt` — recommendation card + breaking headlines + catalyst check + calendar + daily brief, one scrolling screen. `Signals.kt` gained `breaking`/`catalyst` mappings it was missing. No theme chips on headlines yet (`news_themes` still ⬜, unrelated item below) and Daily Brief shows whatever `deep_analysis.text` holds today, including its literal `"Unavailable (HTTP Error 404...)"` string when the backend fetch is failing — same known gap as the `deep_analysis` 404 fix item below, not patched over with an invented empty-state here |
| **Settings screen (§9, header gear)** | ⬜ | Theme override (system/dark/light), notification toggles (push on/off, send-test, per-type), data/refresh, optional GitHub PAT. **None built.** App currently follows system theme only — no user override |
| **Header gear + Insights icon (§3.1)** | ⬜ | `HeaderBar` has calendar/recommendation chips; add the gear→Settings and Insights entry points |
| **Price-level alerts UI (optional, §9/§12)** | 🟡 | Server *fires* level alerts already (`send_push_level_alert`). The on-device "set alert" row on the pair sheet + PAT sync to `data/level_alerts.json` is not built |
| Firebase Android registration end-to-end | 🟡 | `app/google-services.json` present; confirm a real push actually arrives on the device (subscribe to topic `atomfx-signals`) |
| **Journal / trade-thesis worksheet (§12)** | 🅿️ | Post-v1 (Phase 10+). Pre/post-trade forms from handbook Appendices C/D |

---

## C. Outstanding items — prioritized

**To reach a coherent v1 (the app matches the spec's navigation and surfaces):**

1. ~~**3-tab bottom nav + swipe**~~ — ✅ done. `HorizontalPager` nav: `Wheel · Macro · Insights` (Decision 1 resolved). Currency stays in the wheel toggle.
2. ~~**Insights screen**~~ — ✅ done. Aggregates recommendation + breaking headlines + catalyst check + calendar + daily brief (Functional Spec §7). "Week ahead" has no distinct backend field yet — folded into the one Daily Brief text block rather than inventing a second one.
3. **Settings screen + header gear** — theme override, notification toggles + send-test, data/refresh, freshness; optional GitHub PAT section (Functional Spec §9). Add the user theme override into `AtomFxTheme`.
4. **Turn on the AI narration (Phase 7 full)** — wire `recommendation.build_recommendation(use_model=True)` into `scan_news.py` on the 12h cadence (Architecture §6). This is the "AI implementation." Do the `deep_analysis` model-id fix at the same time.
5. **Push end-to-end verification** — confirm a gold-signal push actually lands on your phone.

**Polish / optional:**

6. Price-level alerts UI + PAT sync (Functional Spec §9).
7. `news_themes` tagging (Functional Spec §7).
8. Animation re-verify after wheel v2; §17 no-scroll check on device.

**Deferred (post-v1):**

9. Journal (Phase 10+).

---

## D. Open decisions (need your call before building)

1. **Currency tab vs. the wheel toggle. — RESOLVED (Pieter, 2026-09-02): 3-tab nav
   `Wheel · Macro · Insights`.** Currency strength lives inside the wheel's Currencies/Pairs
   toggle (wheel v2); there is no separate Currency tab. This supersedes the Functional Spec §2
   four-tab nav — build the 3-tab `HorizontalPager` accordingly.
2. **AI cadence & spend.** Sonnet narration every 12h is the spec default. Confirm that cadence
   (cost is a few $/month) or pick another (e.g. only when bias/primary pair changes).
3. **Settings theme override** — confirm you want `system · dark · light` (stored) as specced.

---

## E. Recommended next build order (hand to Claude Code, one at a time)

Each step must read `CLAUDE.md` first, then the cited spec section, and ship building before the next.

1. ~~**Nav shell**~~ — ✅ done. 3-tab bottom nav + `HorizontalPager` (`Wheel · Macro · Insights`); Wheel + Macro moved into it, empty Insights tab added.
2. ~~**Insights screen**~~ — ✅ done (Functional Spec §7).
3. **Settings screen + header gear + theme override** (Functional Spec §9, Design §2).
4. **AI narration on** (Architecture §6) + **`deep_analysis` model fix** (both in `scan_news.py`).
5. **Push end-to-end test**, then price-level alerts UI (§9) if wanted.
6. Journal when you're ready for post-v1.

> Keep this file current: when Claude Code finishes an item, it should flip the Status here in the
> same commit. That one habit prevents the "what's left?" confusion from recurring.
