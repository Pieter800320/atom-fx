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
| **AI recommendation — Sonnet narration + cadence (Phase 7 full)** | ✅ | Wired into `scan_news.py`, `use_model=True`. Cadence is a union of three triggers (Pieter's call, 2026-09-03): a qualifying gold signal newer than the last narration, the regime `bias` flipping, or 12h+ since the last narration — whichever comes first. `recommendation.py` now preserves the AI-narrated text across `scan_h1.py`'s hourly seed-only refresh (was previously clobbered every hour regardless of scan_news's cadence — a real bug, fixed alongside this) |
| **`deep_analysis` daily brief 404 fix** | ✅ | `scan_news.py` `SONNET_MODEL` updated from the retired `claude-sonnet-4-20250514` to `claude-sonnet-5`. Also fixed a separate, previously-silent bug: `recommendation.py`'s `_call_sonnet` was calling `scan_news._sonnet(prompt)` with only one of its two required positional args, so every `use_model=True` call was silently raising and falling back to template text |
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
| **Settings screen (§9, header gear)** | ✅ | `ui/settings/SettingsScreen.kt` — live `system/dark/light` override (`UserPreferences` + `SharedPreferences`, resolved once in `MainActivity` so wheel/theme agree), notification master + per-type toggles (client-side filter, wired below), send-test (posts a local test notification), data-source URL (`SignalsRepository` now reads it live), a foreground-only refresh-cadence loop in `WheelViewModel`, freshness/diagnostics (last updated, status, schema version, force refresh), About (sourced from `GLOSSARY.md`). Price-level alerts is present but disabled — see next item, still its own build step |
| **Header gear + Insights icon (§3.1)** | ✅ | A persistent gear strip sits above the `HorizontalPager` (true on all 3 tabs, not just Wheel) → opens `SettingsScreen` full-screen. The old spec's separate "Insights icon" is superseded by Insights being its own nav tab now (same supersession as the Currency-tab decision) — not duplicated as a second entry point |
| **Notification per-type filtering** | ✅ | `AtomFxMessagingService` now checks `UserPreferences` before showing a gold-signal/level-alert notification — the only place this can be enforced, since the backend still sends both to one shared topic |
| **Price-level alerts UI (optional, §9/§12)** | 🟡 | Server *fires* level alerts already (`send_push_level_alert`). Settings has a disabled placeholder row now; the on-device "set alert" row on the pair sheet + PAT sync to `data/level_alerts.json` is still not built |
| Firebase Android registration end-to-end | 🟡 | `app/google-services.json` present; confirm a real push actually arrives on the device (subscribe to topic `atomfx-signals`) |
| **Journal / trade-thesis worksheet (§12)** | 🅿️ | Post-v1 (Phase 10+). Pre/post-trade forms from handbook Appendices C/D |

---

## C. Outstanding items — prioritized

**To reach a coherent v1 (the app matches the spec's navigation and surfaces):**

1. ~~**3-tab bottom nav + swipe**~~ — ✅ done. `HorizontalPager` nav: `Wheel · Macro · Insights` (Decision 1 resolved). Currency stays in the wheel toggle.
2. ~~**Insights screen**~~ — ✅ done. Aggregates recommendation + breaking headlines + catalyst check + calendar + daily brief + week ahead (Functional Spec §7). Week ahead turned out to have its own `week_ahead` backend key after all (generated Sunday evenings, ~24h persistence) — now mapped and shown as its own section when present, rather than folded into Daily Brief as first built.
3. ~~**Settings screen + header gear**~~ — ✅ done. Theme override, notification toggles + send-test, data/refresh, freshness (Functional Spec §9). Price-level alerts section present but disabled (item 6).
4. ~~**Turn on the AI narration (Phase 7 full)**~~ — ✅ done. `recommendation.build_recommendation(use_model=True)` wired into `scan_news.py`, firing on gold signal / regime-bias flip / 12h backstop (Pieter's call, 2026-09-03), plus the `deep_analysis` model-id fix.
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
2. **AI cadence & spend. — RESOLVED (Pieter, 2026-09-03): gold signal, regime-bias flip, or
   12h — whichever comes first**, not a flat timer. Reasoning discussed in-session: gold signal
   and regime flip are the two moments the narrative actually changes; a flat 12h-only cadence
   would occasionally sit through a full session cycle re-explaining a stale regime. Cost is
   still bounded — both event triggers are rare, so this is close to or cheaper than flat 12h in
   a typical week.
3. **Settings theme override — RESOLVED**: built as specced, `system · dark · light` (stored).

---

## E. Recommended next build order (hand to Claude Code, one at a time)

Each step must read `CLAUDE.md` first, then the cited spec section, and ship building before the next.

1. ~~**Nav shell**~~ — ✅ done. 3-tab bottom nav + `HorizontalPager` (`Wheel · Macro · Insights`); Wheel + Macro moved into it, empty Insights tab added.
2. ~~**Insights screen**~~ — ✅ done (Functional Spec §7).
3. ~~**Settings screen + header gear + theme override**~~ — ✅ done (Functional Spec §9, Design §2).
4. ~~**AI narration on**~~ — ✅ done (Architecture §6) + **`deep_analysis` model fix** — ✅ done (both in `scan_news.py`).
5. **Push end-to-end test**, then price-level alerts UI (§9) if wanted.
6. Journal when you're ready for post-v1.

> Keep this file current: when Claude Code finishes an item, it should flip the Status here in the
> same commit. That one habit prevents the "what's left?" confusion from recurring.
