# ATOM FX — Build Status & Outstanding Items

**Audited:** 2026-09-10, against the actual source in the repo at commit `5dcdc6a` (not a snapshot
this time — read `WheelUiState.kt`, `WheelMapper.kt`, `PairSheet.kt`, `StatusStrip.kt`,
`WheelScreen.kt`, `MainActivity.kt`, `NotificationHistoryScreen.kt`, `.gitignore`, `build.gradle.kts`
directly). Previous audit (2026-09-02) predated Wheel v2's wing rework, the Simplification Rework
(2026-09-05/06/09), the Notification History screen, the COT/Conviction overlay, the CSM dispersion
reliability gate, and the Rule #1 currency-strength/regime methodology fix — all folded in below.
This is the **living tracker** the project was missing — the specs describe the whole destination;
this maps how far it's built. Update the Status column as work lands.

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
| **`news_themes` tagging** (headlines → macro axis) | ✅ | `scan_news.py` `tag_theme()` — deterministic keyword match to the same five `macro_regime.py` evidence axes (risk/rates/usd/commodity/safe_haven), no AI call. `breaking.themes` (parallel array to `breaking.headlines`) is new; `recommendation.py`'s prompt now cites the axis per headline so the narration can say things like "rates-axis news confirms…". Never feeds back into `macro_regime`'s own axis scoring — colour only |
| **COT-based Conviction overlay** (Signals Roadmap Phase 3) | ✅ | `scanner/extend/conviction.py` — Conviction Extreme alert, adaptive threshold (80 normal / 40 when a currency's COT is stale) so it stays reachable under degraded evidence; `cot_confirmed`/"technical only" messaging flows into the real push |
| **CSM dispersion reliability gate** | ✅ | `scanner/extend/csm_dispersion.py` — caps CSM-derived votes/components to "unreliable" when a TF's dispersion ranks in the bottom 20% of its own rolling ~60-scan history. Needs ~2.5 days of live hourly scans to have accumulated enough history to ever actually trigger — worth checking back once it has |
| **EXTEND layer full audit** | ✅ | All six modules read line-by-line (`csm_delta`, `breadth`, `macro_regime`, `conviction`, `potential`, `recommendation`); one doc-comment fix, two new cross-validation regression tests added, one real threshold bug found and fixed (Conviction Extreme's unreachable-under-COT-staleness ceiling, see above) |
| **Rule #1 sign-off: currency-strength/regime FX methodology** | ✅ | `csm.py` `STRENGTH_PAIRS` 16→18 (added EURJPY/GBPJPY — pure data-completeness, zero new API cost); `regime.py` `classify_regime` Vote 3 rebuilt to measure the same narrow AUD+NZD+CAD-vs-havens concept Vote 1 already uses (was inconsistently mixing in GBP/EUR), Vote 2 gained CAD. Pieter's explicit sign-off after a full FX-methodology discussion, test/golden protocol followed exactly (`test_rule1_frozen` failed as expected, golden diffed and regenerated, `test_extend`'s appearance counts updated) — both suites green |

---

## B. Android app (Kotlin/Compose) — by surface

| Surface (spec ref) | Status | Where / what remains |
|---|---|---|
| Energy Wheel — pair mode (Design §6) | ✅ | `ui/wheel/*` (radial dial v2). 4 selectable wings, renamed 2026-09-05: **Overall** (Continuation Score, all TFs), **Trend** (ADX, fixed H4 — no D1/H1 ADX exists), **Momentum** (fixed H4, switched from D1 2026-09-09), **Volatility** (ATR percentile, fixed D1). Hub/nucleus moved H4→D1 Regime 2026-09-09 ("the H4 Regime changes too much") — Regime/Trend/Momentum/Volatility now form a deliberate fixed-timeframe consensus funnel, never a D1/H4/H1 toggle (tried twice, reverted both times, see `[[atom_fx_simplification_rework]]`) |
| Currency strength on the wheel (§3A) | ✅ | Merged into the wheel's Currencies/Pairs corner toggle (wheel v2 decision); always-on `CsmBarStrip` below the dial too, with a Strength/Flow display toggle |
| Cross-asset ring + sheet | ✅ | `WheelCanvas` outer ring + `ui/sheets/CrossAssetSheet.kt`, unchanged since last audit |
| Pair sheet (Phase 4) | ✅ | `ui/sheets/PairSheet.kt` — reworked 2026-09-05/09 from a 6-factor pass/fail WHY checklist to 3 tabs: **Overview** (5 informational rows — Regime D1/Trend H4/Momentum H4/Volatility D1/Structure H4, real bull/bear/watch tints, no gate glyphs), **Breakdown** (Momentum, Structure, Alignment), **Correlation**. `FlowSheet`/`BreadthSheet` (as a real tap target)/`EntrySheet`/`RecommendationSheet`/`CurrencyFlowSheet` are all gone — deleted outright or orphaned. `BreadthSheet.kt` still exists as a file but is unreachable dead code (`SheetTarget.Ring` is never produced by `WheelCanvas.hitTest`) — flagged, not yet cleaned up |
| Status strip / ranked-pairs glyph row (Phase 5) | ✅ | `ui/components/StatusStrip.kt` — was a 9-button cascade, then a single "Summary→Recommendation" card, now (2026-09-06) one small glyph per pair in `signals.ranked.top` (≤3, whichever clear the ranking gate) above the wheel; tap reflows open a full-width panel with that pair's Regime/Trend/Momentum/Volatility/Structure consensus + rank. There is **no separate "Tradeable Now" card any more** — that job is now this glyph row plus the wheel's own Overall wing (all 12 pairs, wedge size = Continuation Score) |
| Line chart, 3-TF, no candles (Phase 9) | ✅ | `ui/chart/LineChart.kt` + `ChartSheet`, unchanged |
| Macro screen (Phase 9) | ✅ | `ui/macro/MacroScreen.kt` (archetype, bias, evidence, cross-asset table). A reframe was raised and explicitly deferred 2026-09-05 ("the archetypes do not describe what one sees happening… evidence bars look nice but aren't evidence of anything") — open design question, not a defect in what's shipped |
| Push client (Phase 6) | ✅ | `push/AtomFxMessagingService.kt` + `DeepLink.kt`. Message format changed 2026-09-0x: `send_push.py` now sends **data-only** FCM (was combined notification+data, which the system tray auto-displayed when backgrounded/killed — bypassing the app's own `onMessageReceived` and the new Notification History hook entirely) |
| **Notification History screen** (new since last audit) | ✅ | `ui/settings/NotificationHistoryScreen.kt` (Settings → Notifications → "Notification history") — every push the app builds is recorded on-device, flat reverse-chronological list, per-alert-type label chip (GOLD SIGNAL/LEVEL ALERT/SETUP/STRUCTURE/REGIME/MACRO ARCHETYPE/VOLATILITY/ALIGNMENT/POSITIONING), each with a static "what to consider" line + "Learn more" deep-link into the matching Library entry, unread dot on the header gear. **Correction to an earlier memory**: this is a flat list, not grouped under REGIME/TREND/VOLATILITY/STRUCTURE/OTHER section headers — that description was of a screen this one superseded |
| Theme tokens, dual light/dark (Design §2) | ✅ | `ui/theme/*`, unchanged |
| Animations & polish (Phase 8) | 🟡 | Wheel has motion (mode cross-fade, rim flash on a pair earning A+, glyph-panel reflow), verified on-device per the Simplification Rework follow-ups. One acknowledged gap: the `CsmBarStrip` is still plain bars, not yet the "echoes the wheel" visual pass (`WheelScreen.kt`'s own doc comment) |
| **3-tab bottom nav + swipe (HorizontalPager)** | ✅ | `MainActivity.kt` — `Wheel · Macro · Insights` via `HorizontalPager` + Material 3 `NavigationBar`, synced both ways |
| **Insights tab/screen (§7, master table 46–50)** | ✅ | `ui/insights/InsightsScreen.kt` — recommendation card + breaking headlines + catalyst check + calendar + daily brief + week ahead, one scrolling screen. A rethink was raised and explicitly deferred 2026-09-05 ("ask ourselves how we can use it better, if at all") — open question, not a defect |
| **Settings screen (§9, header gear)** | ✅ | `ui/settings/SettingsScreen.kt` — live `system/dark/light` override, notification master + per-type toggles + send-test + **Notification History** (new), data-source URL, freshness/diagnostics, About. Price-level alerts present but disabled — see next item |
| **Header gear + Insights icon (§3.1)** | ✅ | Persistent gear strip above the `HorizontalPager` on all 3 tabs → opens `SettingsScreen` full-screen. Regime/flow line moved onto the wheel's own hub (no longer in the header); recommendation now lives in the status-strip glyph row, not the header either |
| **Notification per-type filtering** | ✅ | `AtomFxMessagingService` checks `UserPreferences` before showing a notification — the only enforcement point, since the backend still sends everything to one shared topic |
| **Price-level alerts UI (optional, §9/§12)** | 🟡 | Server *fires* level alerts already (`send_push_level_alert`). Settings has a disabled placeholder row; the on-device "set alert" row on the pair sheet + PAT sync to `data/level_alerts.json` is still not built |
| Firebase Android registration end-to-end | 🟡 | **Still not confirmed** — `app/google-services.json` present, but no evidence in git history of a real gold-signal/level-alert push actually landing on the device (only local `NotificationHistoryStore` records seeded directly, and CI-level FCM send tests). Worth re-checking now specifically, since the FCM message format itself changed (data-only) since the last time this was even informally exercised |
| **Journal / trade-thesis worksheet (§12)** | 🅿️ | Post-v1 (Phase 10+). Pre/post-trade forms from handbook Appendices C/D |

---

## C. Outstanding items — prioritized

**To reach a coherent v1 (the app matches the spec's navigation and surfaces):**

1. ~~**3-tab bottom nav + swipe**~~ — ✅ done. `HorizontalPager` nav: `Wheel · Macro · Insights` (Decision 1 resolved). Currency stays in the wheel toggle.
2. ~~**Insights screen**~~ — ✅ done. Aggregates recommendation + breaking headlines + catalyst check + calendar + daily brief + week ahead (Functional Spec §7).
3. ~~**Settings screen + header gear**~~ — ✅ done, since extended with a Notification History screen (2026-09-0x).
4. ~~**Turn on the AI narration (Phase 7 full)**~~ — ✅ done.
5. ~~**Wheel v2 wing rework + Simplification Rework**~~ — ✅ done (2026-09-05/06/09, not tracked in this file until now). 4 wings renamed to Overall/Trend/Momentum/Volatility, hub moved to D1 Regime, pair sheet reduced to 3 tabs, status strip became the ranked-pairs glyph row, the standalone Tradeable Now card retired. See Section B above and `ATOM_FX_DESIGN.md` §17/§19/§20 (synced today).
6. **Push end-to-end verification — still open.** No evidence yet of a real gold-signal/level-alert push landing on a physical device from the live backend; the FCM message format changed to data-only since this was last informally touched (see Section B), which makes it worth doing deliberately now rather than assuming the old informal confidence still applies.

**Polish / optional:**

7. Price-level alerts UI + PAT sync (Functional Spec §9) — still parked (Pieter, 2026-09-03), Settings row present but disabled.
8. ~~`news_themes` tagging~~ — ✅ done.
9. ~~Animation re-verify after wheel v2~~ — ✅ done, on-device (Simplification Rework follow-ups). One open item: `CsmBarStrip` visual polish pass (still plain bars).
10. Bollinger Band touch/reversal alerts (Signals Roadmap Phase 4) — not started.
11. `BreadthSheet.kt` / `SheetTarget.Ring` dead-code cleanup — `WheelCanvas.hitTest` never produces a `.Ring` target, so this sheet (and the `Factor.MOMENTUM/STRUCTURE/ENTRY` branches routing to it) is unreachable. Flagged in-code, not yet removed.
12. Minor hygiene: `versionCode`/`versionName` still stuck at `1`/`"0.1"`; no signed release build exists (every install has been a debug APK). **Correction**: `.idea/` *is* already in `.gitignore` — an earlier backlog note claiming otherwise was checked against the repo today and is wrong, don't re-flag it.

**Deferred (post-v1):**

13. Journal (Phase 10+) — parked (Pieter, 2026-09-03).
14. Macro archetype card reframe and Insights page rethink — both raised and explicitly deferred 2026-09-05 as open design questions, not defects.

---

## D. Open decisions (need your call before building)

1. **Currency tab vs. the wheel toggle. — RESOLVED (Pieter, 2026-09-02): 3-tab nav
   `Wheel · Macro · Insights`.** Currency strength lives inside the wheel's Currencies/Pairs
   toggle (wheel v2); there is no separate Currency tab.
2. **AI cadence & spend. — RESOLVED (Pieter, 2026-09-03): gold signal, regime-bias flip, or
   12h — whichever comes first**, not a flat timer.
3. **Settings theme override — RESOLVED**: built as specced, `system · dark · light` (stored).
4. **Wheel wing consensus timeframes — RESOLVED (Pieter, 2026-09-06, reaffirmed 2026-09-09):
   fixed, never a D1/H4/H1 toggle.** Tried wiring the wheel to the existing D1/H4/H1 toggle twice
   (2026-09-06 and implicitly reconsidered 2026-09-09) and reverted both times. Settled: D1 Regime
   → H4 Trend → H4 Momentum → D1 Volatility. If a future session is asked to make any wheel
   element "follow the toggle" again, check `[[atom_fx_simplification_rework]]` first.

---

## E. Recommended next build order (hand to Claude Code, one at a time)

Each step must read `CLAUDE.md` first, then the cited spec section, and ship building before the next.

1. ~~**Nav shell**~~ — ✅ done.
2. ~~**Insights screen**~~ — ✅ done.
3. ~~**Settings screen + header gear + theme override**~~ — ✅ done.
4. ~~**AI narration on**~~ — ✅ done.
5. ~~**Wheel v2 + Simplification Rework**~~ — ✅ done.
6. **Push end-to-end test** — next up, deliberately trigger a real backend push and confirm it arrives on the device.
7. Price-level alerts UI (§9), if wanted, then Bollinger Band alerts (Signals Roadmap Phase 4).
8. Journal when you're ready for post-v1.

> Keep this file current: when Claude Code finishes an item, it should flip the Status here in the
> same commit. That one habit prevents the "what's left?" confusion from recurring.
