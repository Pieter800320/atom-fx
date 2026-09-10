# ATOM FX — Wheel v2 (Radial Dial) — Implementation & Wiring Brief

**For:** Claude Code, building inside `atom-fx`. **Status:** binding brief for the wheel redesign only.
**Visual reference:** `REGIME_FX_radial_dial_curved_text_centered_v4.html` (Pieter's mockup — the *look* to reproduce; ignore its mock scaffolding: scenario A–J buttons, clock, and the price ticker footer are demo-only and are NOT part of the app).

> **Golden Rule still applies.** This is a NEW/presentation-tier change only. Do not touch anything under `scanner/` (FROZEN) or the EXTEND backend. The wheel is a pure consumer of `signals.json` (Architecture §8.3): every value it shows is a straight copy of a field the backend already produces. No trading number is recomputed in Kotlin.

---

## 0. What this change is

Replace the current Energy-Wheel Canvas with the radial dial from the mockup. The core spec invariant is preserved: **angle = identity, radius = potential/strength** (Architecture §8.2). The old "dots orbiting at a radius" become "wedges filled outward"; the metaphor is the same.

The dial has **three concentric zones**:

1. **Hub (centre)** — the regime nucleus (was: nucleus).
2. **Middle ring** — toggles between **Currencies** (8 wedges, radius = CSM strength 0–100) and **Pairs** (12 wedges, radius = 6 potential step-bands). This is the merge of the old pair wheel + the currency wheel.
3. **Outer ring** — 10 cross-asset wedges (confirm/dim, up/down), from `macro_assets`. This is new to the wheel (was only on the Macro screen).

### Keep from the current app (do NOT lose these)
- The **six factors keep their identity.** The step-bands are the six confluence factors in order (R·F·B·M·S·E). Keep the tappable **factor-pill row** below the wheel (current `RingKeyRow` in `WheelScreen.kt`) and the six-factor checklist in `PairSheet`. On a pair wedge, mark the **blocking** band (see §4).
- **A+/tradeable emphasis** — rim/glow on level-6 wedges, brighter for A+.
- **Dual theming** — everything reads `AtomColors`; NO literal hex (Design §2). Derive the 6-step green/red ramps from tokens (see §6).
- **Long-press a pair → line chart** (existing `ChartSheet`).

### Drop (from the mockup)
- Price ticker footer, scenario A–J buttons, the standalone clock. The app's existing `HeaderBar` (freshness/updated) stays.

### Terminology
- Currencies have **strength** (CSM 0–100). Pairs have **Potential** (6-factor level 0–6). The toggle is **Currencies · Pairs**; never call the pair metric "strength."

---

## 1. Files to change (and what stays)

| File | Change |
|---|---|
| `ui/wheel/WheelUiState.kt` | ADD `WheelMode` enum, `CurrencySeg`, `CrossAssetSeg`; add `currencies`, `crossAssets`, `archetypeLine` to `WheelUiState`. Keep `nodes`, `rings`, `nucleus`. |
| `ui/wheel/WheelGeometry.kt` | ADD dial radii (hub, middle ring R0/R1, outer ring R0/R1), `polar()`, `wedgePath()`, per-mode slice angles. Keep `PAIR_ORDER`; add `CCY_ORDER`, `XASSET_ORDER`. |
| `ui/wheel/WheelCanvas.kt` | REWRITE as the radial dial: outer cross-asset ring + toggled middle ring + hub, curved labels, hit-testing, animations (§5). |
| `ui/wheel/WheelScreen.kt` | ADD the Currencies/Pairs toggle control; route new tap targets; keep factor-pill row + Tradeable Now. |
| `domain/WheelMapper.kt` | ADD `mapCurrencies()`, `mapCrossAssets()` (+ confirm rule §3), archetype line on nucleus. Keep node/ring/nucleus mapping. |
| `ui/sheets/BottomSheetHost.kt` | ADD `SheetTarget.CrossAsset(id)` → new sheet; `SheetTarget.Currency` already exists. |
| `ui/sheets/CrossAssetSheet.kt` | NEW — the sorted cross-asset list (§7). Reuse row rendering from `MacroScreen.kt`. |
| `ui/theme/Color.kt` | ADD a `stepColor(step, direction, colors)` helper deriving the 6-step ramp from tokens (§6). |

Nothing in `data/`, `scanner/`, or the other sheets changes. The data model (`Signals.kt`) already parses every field needed.

---

## 2. State shape (`WheelUiState.kt`)

```kotlin
enum class WheelMode { PAIRS, CURRENCIES }

data class CurrencySeg(
    val code: String,          // CCY_ORDER position = angle
    val index: Int,
    val strength: Int,         // csm["h4"][code], 0..100  -> radius fill
    val delta: Double,         // csm_delta["h4"][code]     -> Δ chip / arrow
    val breadthBand: String,   // breadth["h4"][code].band  -> optional tick
    val tint: Tint,            // strength>=50 bull-ramp else bear-ramp
)

data class CrossAssetSeg(
    val id: String,            // "VIX","SPX",... XASSET_ORDER position = angle
    val index: Int,
    val label: String,         // macro_assets[id].label ?: id
    val up: Boolean,           // direction == "up" (else down; "flat" -> muted)
    val flat: Boolean,
    val confirm: Boolean,      // supports current regime (§3 rule)
    val valueText: String,     // formatted value
    val deltaText: String,     // "+4.4%" / "+8.6bp"
)

data class WheelUiState(
    val nucleus: NucleusState,   // + archetypeLine (see below)
    val nodes: List<PairNode>,   // 12, unchanged (pair mode)
    val rings: List<RingDescriptor>,
    val currencies: List<CurrencySeg>,   // 8, currency mode
    val crossAssets: List<CrossAssetSeg>, // 10, outer ring
)
```

Add `val archetypeLine: String` to `NucleusState` (e.g. `"A · GROWTH RISK-ON · HIGH"` from `macro_regime.primary`; empty string if `macro_regime` absent).

`CCY_ORDER = ["USD","JPY","CHF","EUR","GBP","CAD","AUD","NZD"]` (mockup order; risk-off bloc first so a "bloom" reads risk-on/off — matches Design §6A intent).
`XASSET_ORDER = ["VIX","SPX","US10Y","US3M","CURVE","DXY","WTI","COPPER","GOLD","BTC"]`. `CURVE` maps to `macro_assets["curve"]`.

---

## 3. Mapper additions (`WheelMapper.kt`)

**Currencies** — for each `code` in `CCY_ORDER`:
- `strength = signals.csm["h4"]?.get(code)?.toInt() ?: 0`
- `delta = signals.csmDelta["h4"]?.get(code) ?: 0.0`
- `breadthBand = signals.breadth["h4"]?.get(code)?.band ?: "weak"`
- `tint = if (strength >= 50) Tint.BULL else Tint.BEAR`

**Cross-assets** — for each `id` in `XASSET_ORDER` (mapping `CURVE`→`"curve"`, else lowercase id):
- `entry = signals.macroAssets[key]`; `up = entry?.direction == "up"`, `flat = entry?.direction == "flat"`.
- `valueText` from `entry.value`; `deltaText` from `deltaPct` (`"%+.1f%%"`) or `deltaBp` (`"%+.1fbp"`).
- **confirm rule (reuses Phase-1 `macro_regime.evidence`):** map each asset → axis, then `confirm = evidence.firstOrNull{it.axis==axis}?.supports == true`.
  - risk: VIX, SPX, COPPER, BTC · rates: US10Y, US3M, CURVE · usd: DXY · commodity: WTI, COPPER, GOLD · safe_haven: GOLD
  - (COPPER/GOLD touch two axes — confirm if *either* supports.) If `macro_regime` is absent, `confirm = false` for all (ring renders all-dim; that's a valid state).

**Archetype line** on nucleus: `macro_regime.primary` → `"${code} · ${name.uppercase()} · ${confidence.uppercase()}"`, else `""`.

Everything above is a straight read/format — no new numbers.

---

## 4. The pair ring — preserve the six-factor meaning

Each pair wedge fills outward in up to 6 bands = `PotentialEntry.level`. Colour the bands with `stepColor(k, direction, colors)`. Critically:
- The **blocking factor** (`blockedAt`) is the *next* band that did NOT fill. Draw a thin bright hairline / factor glyph (R/F/B/M/S/E) at the top edge of the filled stack so the wedge answers "blocked at Structure" at a glance.
- Level-6 wedge: full rim glow (tradeable). If `state == APLUS`: brighter rim (repurpose the mockup's `confirmGlow`).
- Direction sets the ramp: BULL → green ramp, BEAR → red ramp, NEUTRAL/`none` → muted grey (nucleus-style, barely filled). A pair with no directional D1 pill sits near the hub (level 0), exactly as now.

The six-factor **legend pill row** (existing `RingKeyRow`) stays directly under the wheel in BOTH modes — it's the reliable tap route to the six factor sheets and the only place the factor *names* live now.

---

## 5. Animations (Compose)

- **Tap select:** animate a per-wedge `scale` to ~1.02 and draw a selection glow on the selected wedge (`animateFloatAsState`, keyed on selected id). Deselect on second tap / dismiss.
- **Mode switch (Pairs↔Currencies):** one `Animatable modeProgress 0→1`. Cross-fade: outgoing ring alpha 1→0 + scale 1→0.88; incoming 0→1 + 0.88→1. Outer ring + hub do NOT animate on mode switch (they're regime/macro context, mode-independent).
- **Data update (new `signals.json`):** animate each wedge's fill to its new value — currency radius (0–100) and pair band count — with `animateFloatAsState` keyed on the value. This is the "living dial." Cross-asset ring: fade confirm glow/colour to the new state. Hub regime word cross-fades.
- **Pulsating dot:** `rememberInfiniteTransition`, opacity 0.35↔1 over ~2.5s; colour = regime tint from tokens.
- Durations/easing per **Design §7** (radial advance/retreat 400–700 ms, standard easing).

---

## 6. Theming (`Color.kt`) — no literal hex

Add a helper that derives the ramps from tokens so light+dark both work:

```kotlin
// step 1..6, direction picks the ramp; interpolate token soft->full, capped.
fun stepColor(step: Int, direction: Direction, c: AtomColors): Color {
    val s = step.coerceIn(1, 6) / 6f
    val (lo, hi) = when (direction) {
        Direction.BULL -> c.bullSoft to c.bull
        Direction.BEAR -> c.bearSoft to c.bear
        else -> c.neutral.copy(alpha = 0.25f) to c.neutral
    }
    return lerp(lo, hi, 0.25f + 0.75f * s)   // androidx.compose.ui.graphics.lerp
}
```

Currency fill uses `stepColor(ceil(strength/16.67), tint-as-direction, colors)` (mirrors the mockup's `csColor`). Hub metal / plate / hairlines map to `surface`/`surfaceRaised`/`hairline`/`hairlineStrong`; label text to `textPrimary`/`textSecondary`/`textMuted`. Cross-asset confirm stroke uses `bull`/`bear`; dim uses `hairline`.

---

## 7. New `CrossAssetSheet.kt`

Trigger: tap an outer wedge → `SheetTarget.CrossAsset(id)`.
Content: the full 10-asset list (VIX, SPX, US10Y, US3M, 10Y-3M curve, DXY, Gold, S&P… per Functional Spec §6.6), **sorted with the tapped asset pinned to the top and visually highlighted** (card border in `bull`/`bear`, subtle raised surface). Each row: label · value · direction arrow · change · confirm/dim badge · currency-impact note. Reuse the row composable already in `MacroScreen.kt` (extract it to `SheetComponents.kt` so both use one renderer). Source: `macro_assets` + `macro_regime.evidence`.

---

## 8. Curved labels in Compose

The mockup uses SVG `<textPath>`. In Compose Canvas, draw curved text with `drawContext.canvas.nativeCanvas.drawTextOnPath(text, androidPath, 0f, 0f, paint)` where `androidPath` is the arc for that wedge's label radius; centre with `paint.textAlign = CENTER` and place at the wedge mid-angle. Lower-half wedges: build the arc reversed (as the mockup's `curvedLabel` does with the swept flag) so text stays upright/readable. Keep labels `pointer-events`-free (hit-test the wedge, not the text).

---

## 9. Acceptance (the landing dial answers, no sheet open)
Current regime + archetype (hub) · which currencies are strong/weak (Currencies mode) · which pairs have highest Potential and where each is blocked (Pairs mode) · whether the macro backdrop confirms (outer ring). One tap on any wedge / the hub opens the right sheet. Toggling Currencies↔Pairs animates cleanly with the outer ring + hub steady. Light and dark both read correctly (no hardcoded colour). The Rule #1 guard is irrelevant here (no backend touched) but must still be green.

---

## 10. Build order (ship each step running)
1. State + mapper additions (`WheelUiState`, `WheelMapper`) + unit-test the mapper against `fixtures/state_risk_on.json` (currencies/crossAssets populate, confirm rule fires).
2. `WheelGeometry` dial maths + `stepColor` helper.
3. `WheelCanvas` static render: hub + both middle rings + outer ring + curved labels (no animation yet). Verify on device in both themes.
4. Hit-testing + tap routing + the toggle control + `CrossAssetSheet`.
5. Animations (§5).
6. Blocking-band marker + A+/tradeable glow polish.

---

## 11. Addendum (2026-09-02) — superseded/added decisions

Everything above is the original brief. This section records what actually shipped where it
diverges, per Pieter's direct sign-off in session. Treat this section as **current truth**
over §0–10 where they conflict.

- **Curved labels are hand-placed, not `drawTextOnPath`.** `Canvas.drawTextOnPath` overlapped
  labels into neighbouring wedges on-device (an Android/Skia quirk, not a math error — the
  wedge geometry itself was already correct). `WheelCanvas.curvedLabel` now measures the label,
  shrinks it to fit the wedge's actual arc length, and places each glyph at its own trig-derived
  angle — overlap is structurally impossible regardless of label length or wedge width.
- **The wheel fills all the space §17 allows, no more.** An earlier attempt forced the dial
  30% bigger via `requiredSize()` + `verticalScroll()` — reverted; it violated §17 outright (see
  `CLAUDE.md` §3). The dial only grew for real once the actual chrome around it shrank (see below)
  — §17's own sizing formula, unchanged, just has more room to work with now.
- **The six-factor pill row (`RingKeyRow`) is gone.** Regime/Breadth duplicated the Status
  Strip's own tap targets (`StatusStrip.kt` already routes REGIME/BREADTH cells to the same
  sheets). Momentum/Structure/Entry never had global meaning — they were routing to
  `PairSheet(wheelState.topPair(), …)`, an arbitrary pick with no basis in the data model. They
  remain reachable properly as tabs inside each pair's own sheet (§14.7 — already wired).
- **`DATA STALE` moved into the header**, next to the freshness dot — this is what §8/§9 already
  specified; it had drifted into a separate row above the wheel.
- **Four corner buttons form a 4th ring — chrome, deliberately not shaped like a data cell.**
  Tapered wedges (`WheelGeometry.taperedCornerPath`/`cornerButtonAngles`): wide at the hub-facing
  edge (`TOGGLE_INNER_SPAN_DEG`), narrower at the tip (`TOGGLE_OUTER_SPAN_DEG`), curved top/bottom
  edges (arcs at r0/r1, following the dial's own curvature — not flat chords), only the two outer
  (narrow-end) corners rounded. Sits just outside the XA ring, ~75% thicker than it
  (`TOGGLE_R0/R1_FRAC`). Hit-tested and drawn inside `WheelCanvas` itself, not a Compose overlay —
  it costs the layout nothing, which is most of why the wheel could grow at all. This geometry is
  still exactly what's shipped — only *what the four corners mean* has changed since, twice; see
  §12 below for current truth (they're wing selectors now, not a mode/timeframe toggle) and don't
  trust the rest of this bullet's own content (labels, bottom/top corner assignment) as current.
- ~~On-wheel numeric currency values were added, then removed~~ — moot: see §12, there is no
  currency display on the dial at all any more (2026-09-04).
- ~~The Currency Flow ticker is always on, both modes~~ — retired 2026-09-04 ("get rid of the
  ticker entirely"), see §12.
- ~~Currency Flow sheet dropped "Absolute leader/laggard."~~ — moot: the whole sheet was deleted
  2026-09-06, see §12.
- ~~Depth/motion pass: currency wedges fill with a radial gradient… pair-ring bands… per band (6
  bands)… Tradeable/A+ pair rims get a real blurred glow… flashes and settles (`rimFlash`)~~ —
  **none of this reflects shipped code any more**, see §12: no currency wedges exist on the dial,
  pair wedges are a single continuous fill (not 6 discrete bands), and there is no rim-glow/flash
  treatment (`WheelCanvas.kt`'s own comment: "indicated by its fill/border alone — no separate rim
  glow treatment"). The hub's soft blurred ambient shadow is the one part of this bullet still true.
- **Haptics on every interactive control** — see `ATOM_FX_DESIGN.md` §16. Applies wheel-wide, not
  just to the dial. Still accurate, unchanged.

---

## 12. Second addendum (2026-09-10) — current truth over §11 where they conflict

§11 above was written 2026-09-02 and describes the wheel's *second* design, already superseded
twice since (the Simplification Rework, 2026-09-05/06/09, and the ticker/ring retirement,
2026-09-04, which actually landed first but wasn't folded into §11 at the time). Rather than
rewrite §11's own bullets in place and lose the historical reasoning, this section states what's
actually live, verified directly against `WheelCanvas.kt`/`WheelScreen.kt`/`WheelUiState.kt` on
2026-09-10 (part of the same doc-sync pass as `ATOM_FX_DESIGN.md` v1.1 and
`ATOM_FX_BUILD_STATUS.md` — see `CLAUDE.md` §5's doc-upkeep rule).

- **The middle ring is pairs-only now, always.** `WheelUiState.kt`'s own `WheelMode` doc comment:
  "The ring is exclusively pair-shaped, always — CSM/currency strength has its own permanent strip
  below." There is no Currencies-mode dial display any more, no Currencies/Pairs toggle anywhere
  on the wheel, and nothing currency-related drawn on the dial itself. (`WheelUiState`'s own
  top-of-file doc comment — "the middle ring toggles between PAIRS and CURRENCIES" — is itself now
  stale, contradicted by the more specific comment 20 lines below it; don't trust it either.)
- **The four corner buttons are the four wing selectors** — `SETUP` (labelled this way on-wheel
  since 2026-09-09, was `OVERALL`; internal `WheelMode.OVERALL` enum name unchanged), `TREND`,
  `MOMENTUM`, `VOLATILITY` (`WheelCanvas.drawCornerButtons`). Exactly one is always selected (the
  wheel is always in one of its 4 modes). Labels are **curved**, not straight (`curvedLabel`,
  2026-09-03 — this itself superseded this same file's original "reinforcing that it's chrome"
  straight-label call, before the wings even existed).
- **Currency strength moved off the dial entirely, into `CsmBarStrip`** — a permanent row below
  the wheel (not a ring, not a mode), 8 currencies in a fixed order (`WheelGeometry.CCY_ORDER`,
  never re-sorted by strength — leader/laggard is read by comparing bar heights, not position or
  a sorted list), with its own **Strength/Flow display toggle** (`CsmDisplayMode`): Strength shows
  a plain height=strength bar per currency; Flow shows a diverging chart off a zero-line
  (`csm_delta`-driven, up/down from the midline). Below that, a separate `TimeframeButtons` row
  (D1/H4/H1) drives the CSM strip only — it does **not** touch the wheel's own wings, which are a
  deliberately fixed timeframe consensus (D1 Regime/H4 Trend/H4 Momentum/D1 Volatility, see
  `ATOM_FX_BUILD_STATUS.md` Section B).
- **The hub draws only "REGIME" + the regime name.** `WheelCanvas.drawHub` — confirmed by reading
  the function directly. `WheelMapper.mapNucleus` still computes `strengthWord`, `confidence`,
  `flowLine` ("X leading · Y weakening"), and `archetypeLine` onto `NucleusState`, but **none of
  those four fields are rendered anywhere** — grepped the whole `ui/` tree for each, zero render
  sites outside `WheelMapper.kt`/`WheelUiState.kt` themselves. **Flag, not just a doc correction**:
  this means the landing screen currently has no on-screen sentence naming the regime's own
  strength/confidence, and no on-screen sentence naming the currency leader/laggard by name —
  leader/laggard is only inferable by comparing `CsmBarStrip` bar heights. Worth Pieter's own call
  on whether that's sufficient for the §20 acceptance test's "strong or weak" / "leading /
  weakening currency" questions, or whether `flowLine` (already computed, just unrendered) should
  actually be surfaced somewhere on the landing view. Not resolved as part of this doc-sync pass —
  a design decision, not a doc-accuracy one.
- **`StatusStrip` is the ranked-pairs recommendation glyph row**, not a mode/data toggle of any
  kind — see `ATOM_FX_DESIGN.md` §19 and `ATOM_FX_BUILD_STATUS.md` Section B for its own history
  (9-button cascade → single card → per-pair glyph row). Out of scope for this wheel-geometry
  section beyond noting it sits directly above the dial.
