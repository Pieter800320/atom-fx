# ATOM FX — Functional Specification

**Version:** 1.0 · Companion to `ATOM_FX_ARCHITECTURE.md` and `ATOM_FX_DESIGN.md`
**Purpose:** the complete inventory of *what the app shows, where it lives, and how you reach it.* Nothing hand-wavy — every data element is placed on a specific surface with a specific component and interaction. Hand this to Claude Code alongside the architecture and design docs.

---

## 0. How to read this document

This answers three questions for **every** piece of information in the system:

1. **What** is it? (the data element)
2. **Where** does it live? (which screen / sheet / panel / pill / chart)
3. **How** do you see and reach it? (component + interaction + animation)

Two rules from the architecture doc govern everything below:

- **Frozen calculations (Rule #1).** Every number shown is produced by the frozen scanner or the additive EXTEND layer. The app *displays*; it never *computes trading logic*.
- **The app is a pure consumer of `signals.json`.** If a value appears in the UI, it exists as a field in `signals.json`. Nothing is recomputed in Kotlin.

Direct answers to your questions are collected in **§1 (the quick-reference table)**; the rest of the document is the detail behind each row.

---

## 1. Quick reference — every function and where it lives

| Data / function | Shown? | Primary home | Component | How you reach it |
|---|---|---|---|---|
| **MOM1212 (D1/H4/H1) + deltas** | Yes | Pair sheet → Momentum tab; Momentum factor sheet | TF pill tabs + value rows | Tap node → Momentum tab; or tap Momentum ring |
| **CMP (composite momentum)** | Yes | Pair sheet Overview + Momentum tab; wheel factor 4 | Value + status line | Tap node; tap Momentum ring |
| **CSM (3 timeframes)** | Yes | CSM detail sheet; Currency Flow sheet (H4) | Ranked bars, TF pill toggle D1/H4/H1 | Status strip "Leader/Laggard" cell; Flow ring |
| **CSM Delta / Currency Flow** | Yes | Currency Flow sheet; nucleus sub-line | Ranked rows w/ Δ + flow arrows | Tap Flow ring; tap nucleus flow line |
| **Currency breadth** | Yes | Breadth sheet; status strip "Breadth" cell | Bar meters + support/total + band | Tap Breadth ring |
| **3-TF direction pills (D1/H4/H1 arrows)** | Yes | Pair sheet header + Overview; wheel factor markers | Arrow chips ↑/↓/– | On every pair node (markers) + pair sheet |
| **Continuation score (0–100)** | Yes | Pair sheet → Entry tab; Entry factor sheet | Value + gauge | Tap Entry ring; pair Entry tab |
| **Setup rank (0–10)** | Yes | Pair sheet header + Entry tab | "Rank #n / 12" + score | Pair sheet |
| **Potential (0–100) + level + state** | Yes | Wheel (radius + number); pair sheet header | Node + WHY checklist | The wheel itself |
| **Currency strength wheel (8 ccy)** | Yes (NEW) | Currency tab (top-level screen) | Radial: radius=strength, blocs, Δ chevrons | Bottom nav / swipe to Currency |
| **Currency detail (per ccy)** | Yes (NEW) | Currency detail sheet | CSM 3-TF, breadth, drivers, expressing pairs | Tap a currency node |
| **Structure (BOS/CHoCH, D1/H4)** | Yes | Structure factor sheet; pair sheet Structure tab | Direction + last event + strength | Tap Structure ring; pair Structure tab |
| **Correlations (12×12)** | Yes | Correlation view; pair sheet Correlation tab | Correlated-pairs list + matrix | Pair sheet Correlation tab; CSM/tools menu |
| **Cross-asset instruments (10)** | Yes | Macro screen (dashboard); pair sheet Macro tab | Appendix-A-style table + zone | Macro edge panel; pair Macro tab |
| **Macro archetype regime (A–J)** | Yes (NEW) | Macro screen headline; nucleus; recommendation | Named-regime banner + evidence axes | Tap nucleus → Regime/Macro; Macro panel |
| **Gold sub-regime + gold signal** | Yes | Macro screen; recommendation | Chip + line | Macro panel |
| **Regime (D1/H4/H1) + confidence** | Yes | Nucleus; Regime sheet; status strip | Nucleus state + 3-TF rows | Tap nucleus |
| **AI recommendation** | Yes | Recommendation edge panel; nucleus headline | Headline + action + rationale | Left edge swipe; tap nucleus headline |
| **News: breaking headlines (top 3)** | Yes | News/Insights panel; recommendation theme | Headline list | Insights panel |
| **News: catalyst (adversarial check)** | Yes | Insights panel; pair sheet Overview flag | One-line alert | Insights panel; pair sheet |
| **Economic calendar** | Yes | Calendar edge panel; pair "event risk" flag | Event rows + node rim | Right edge swipe; node rim badge |
| **Daily brief / Week ahead** | Yes | Insights panel | Collapsible text sections | Insights panel |
| **Simplified line chart (3 TF, no candles)** | Yes (NEW) | Pair sheet (top); optional mini on nodes | Native Compose sparkline ×3 | Pair sheet header |
| **Price level alerts** | Yes (optional) | Pair sheet → set-alert row; Settings PAT | Price input + above/below | Pair sheet; needs GitHub PAT |
| **Tradeable Now / Watch** | Yes | Landing bottom band | Scrolling pills | Always visible |
| **Settings (theme, notifications, data)** | Yes | Settings screen | Gear menu | Header gear icon |
| **Journal / trade thesis (worksheet)** | Opportunity | Journal screen (later phase) | Pre/post-trade forms | Nav (Phase 10+) |

> Everything marked **NEW** is added by the EXTEND layer; everything else already exists in the frozen backend and is being re-surfaced. **No candlestick charts anywhere** — the only price visual is the simplified line (§8).

---

## 2. Navigation & screen map

**Top-level navigation is a 4-tab bottom nav bar, and you can swipe between the tabs** (a Compose `HorizontalPager`). The four destinations are peers; detail rises above them in bottom sheets.

```
   ┌───────────┐   ┌───────────┐   ┌───────────┐   ┌───────────┐
   │   WHEEL   │◄─►│ CURRENCY  │◄─►│   MACRO   │◄─►│ INSIGHTS  │     ← swipe ↔ / tap nav
   │ pair wheel│   │ 8-ccy     │   │ archetype │   │ AI · news │
   │ +tradeable│   │ strength  │   │ +x-asset  │   │ +calendar │
   └─────┬─────┘   └─────┬─────┘   └───────────┘   └───────────┘
         │ tap node/ring/nucleus │ tap currency
         ▼                       ▼
   ┌──────────────┐        ┌────────────────┐        header gear ─► SETTINGS  (from any tab)
   │ PAIR SHEET   │        │ CURRENCY DETAIL│        long-press node ─► LINE CHART
   │ FACTOR SHEETS│        │ + highlights   │
   │ REGIME/MACRO │        │   pairs on Wheel│
   └──────────────┘        └────────────────┘
        (bottom sheets — rise above any tab, swipe-down / tap-scrim to dismiss)
```

- **Bottom nav** (4 tabs): `Wheel · Currency · Macro · Insights`. Icons + labels; active tab in the accent colour.
- **Swipe** left/right moves between tabs (the pager); the nav bar highlights in sync. This replaces the earlier edge-panel idea — Recommendation, News, and Calendar now live inside the **Insights** tab, which is cleaner and more standard.
- **Bottom sheets** are the detail layer and rise above whichever tab you're on; the wheel stays visible behind at half-height.
- **Settings** is reached from the header gear on any tab (not a nav tab). **Journal** (post-v1) would become a 5th tab or a Settings entry.

### 2.1 Screen flow (plain English)

1. The app opens on the **Wheel** tab.
2. **Swipe** (or tap the nav) to move `Wheel ↔ Currency ↔ Macro ↔ Insights`.
3. On **Wheel**: tap a **node** → Pair sheet (line charts + six-factor WHY + tabs); tap a **ring** → that Factor sheet; tap the **nucleus** → Regime/Macro sheet; **long-press a node** → line chart; tap a **Tradeable Now** pill → Pair sheet.
4. On **Currency**: tap a **currency node** → Currency detail sheet; it can highlight that currency's pairs when you swipe back to the Wheel.
5. On **Macro**: tap a cross-asset **row** → its zone context; the archetype banner explains the regime.
6. On **Insights**: read the recommendation, theme-tagged headlines, and upcoming events.
7. **Header gear** → Settings (any tab). **Freshness dot** → data status.
8. Dismiss any sheet by swiping down or tapping the dimmed area.

*(Visual reference: the `ATOM FX Screen Kit` mockup renders every surface above plus this flow as a diagram.)*

---

## 3. The Landing screen — element by element

Everything here is answerable at a glance (the 12-question acceptance test). Top → bottom:

**3.1 Header** — wordmark `ATOM FX`; regime word + arrow (e.g. `RISK ON ↑`); the currency-flow one-liner (`EUR leading · USD weakening`); `Updated HH:MM`; freshness dot; a **gear icon** (→ Settings) and an **Insights icon** (→ News/Brief). Source: `regime_h4`, `currency_flow`, `updated`.

**3.2 Status strip** — five non-scrolling micro-cells: `REGIME · LEADER · LAGGARD · BREADTH · TOP PAIR`. Each cell is tappable and opens the matching sheet. Source: `regime_h4`, `currency_flow`, `breadth`, top of `ranked`.

**3.3 The Energy Wheel** — the hero. Shows, and only shows: 6 rings (labelled 1 Regime → 6 Entry), 12 pair nodes at fixed angles with their **Potential number**, the six **factor-marker dots** per node (R·F·B·M·S·E, bright = passed), radial paths, the **nucleus** (market state), and the **top-pair halo**. Everything else is one tap away. Source: `potential`, `regime_h4`, `currency_flow`, `macro_regime`.

- **Nucleus contents:** regime word, strength, regime score, confidence, the flow line, and — if present — the **macro archetype name** (e.g. `Growth-positive risk-on`) as a quiet line. Tapping the nucleus opens the **Regime / Macro** sheet.

**3.4 Tradeable Now / Watch band** — scrolling pills. `Tradeable` = level-6 pairs ranked by setup rank (`EURUSD ↑ 86`); `A+` pills get a brighter rim; if none, `NO A+ SETUPS` + closest pair. A second `WATCH` row shows level 3–5 pairs with their blocking reason. Source: `potential`, `ranked`.

---

## 3A. The Currency Strength Wheel (NEW — the `Currency` tab)

A companion radial to the pair wheel that makes **currency-level patterns** legible at a glance. It is a **pure consumer** of existing data (`csm`, `csm_delta`, `breadth`) — no new backend.

**Why it exists.** A pair contains two currencies, so you cannot cleanly "group all the USDs" on the pair wheel, and its radius already means *potential*, not *strength*. The currency wheel solves that directly: one node per currency, radius = strength, so "USD weak / EUR strong" is an instant gestalt, and a whole **bloc blooming outward** reads as the market theme.

**Layout & encoding:**
- **8 currency nodes** (USD, EUR, GBP, JPY, CHF, AUD, CAD, NZD — all 8 the CSM already computes), at **fixed angular positions** (identity, like the pair wheel).
- **Two blocs:** the **risk bloc** (EUR, GBP, AUD, NZD, CAD) occupies the **top** arc; the **defensive bloc** (USD, JPY, CHF) occupies the **bottom** arc, with a faint divider. A **top-heavy bloom = risk-on**; bottom-heavy = risk-off. This is the clean, unambiguous version of the "group them and watch them move together" idea.
- **Radius = CSM strength** (H4 primary; centre = weakest, edge = strongest). Faint guide rings at 25/50/75/100.
- **Δ chevron** per node = CSM delta (who is *turning* stronger/weaker) — the flow signal.
- **Node colour = strength tier** (≥60 strong/green · 40–60 mid/amber · ≤40 weak/red).
- **Centre** = leader/laggard summary (flow leader + absolute leader).
- **Breadth-gated confidence:** a node's solidity/glow is tied to its breadth, so a currency that is "strong" on only one relationship does not look as convincing as a broad mover — the handbook's double-counting discipline, made visual.
- **TF toggle** (D1/H4/H1) via pills.

**Interaction:** tap a currency node → the **Currency detail sheet** (CSM across D1/H4/H1, breadth, the handbook's per-currency drivers, and the pairs that express it) and an option to **highlight that currency's pairs on the pair wheel** when you swipe back. This is the cross-link between the two wheels: a currency bloom on one and a pair-arc bloom on the other are the same theme, and the Macro tab names it.

**Fixed pair-wheel ordering (companion decision).** Because you have no muscle memory yet, freeze the 12 pair angular positions in a **currency-clustered order** (e.g. the `*/USD` pairs contiguous, the JPY-crosses contiguous) so a "USD theme" or "JPY-cross/carry theme" is partially visible on the pair wheel too — a free, one-time layout choice that never changes afterward.

---

## 4. The Pair sheet — the deepest surface

Opened by tapping a node. **Overview is default and already contains the "why".** Compact scrolling pill-tabs across the top: `Overview · Momentum · Structure · Flow · Entry · Macro · Correlation`.

**4.1 Header** — `EURUSD` / `EUR / USD` / state (`HIGH POTENTIAL`) / direction (`LONG`) / `Potential 86` / `Rank #1 / 12`. Directly below: the **three simplified line charts** (§8) for D1 / H4 / H1 so you see up/down at a glance before reading anything.

**4.2 Overview tab — the six-factor WHY** (the single most important surface). Six rows, each: status glyph (✓ pass / ✗ fail / – locked), factor name, one-line explanation, current value. The **blocking factor** is visually unmistakable. Example:

```
✓ REGIME     Risk-On supports EURUSD
✓ FLOW       EUR +8 / USD −10
✓ BREADTH    EUR 7/8 · USD 7/7
✓ MOMENTUM   CMP 70 · D1 delta +12
✓ STRUCTURE  Bullish BOS
✗ ENTRY      Extended · wait for reset
```
Source: `potential.<PAIR>.factors` + the underlying values.

**4.3 Momentum tab** — answers "does momentum support this?" Shows **MOM1212** for D1/H4/H1 (value + delta arrow), the **CMP** and its status (bull/neutral/bear), and CMP deltas. TF selected via pill sub-tabs. A simplified line chart reinforces. Source: `pairs.<PAIR>.mom`.

**4.4 Structure tab** — D1/H4/H1 directional read, **last structural event (BOS/CHoCH)**, structure strength, higher-highs/higher-lows summary. A CHoCH against the trade is flagged in red as a warning. Source: `pairs.<PAIR>.structure`.

**4.5 Flow tab** — this pair's currency-flow relationship: base strength + Δ, quote weakness + Δ, driver spread, and the base/quote breadth. Source: `csm`, `csm_delta`, `breadth`.

**4.6 Entry tab** — the "should I enter now?" surface: **Setup rank /10**, **continuation %**, ADX, reset score, ATR percentile, D1/H4/H1 trend alignment, EMA200 relationship, and an **Entry state** verdict (`GOOD LOCATION` / `EXTENDED` / `WAIT FOR RESET`). Also the **"set price alert"** row (optional, §9). Source: `pairs.<PAIR>` + `rank`.

**4.7 Macro tab** — the pair through the macro lens: which cross-assets support/oppose it (`DXY ↓ → USD offered`, `WTI ↑ → CAD bid`), and how the **active macro archetype** treats this pair's currencies (strong/weak basket membership). Source: `macro_assets`, `macro_regime`. (No seventh ring — macro is supporting evidence, per the wheel brief and the handbook's "cleanest pair" logic.)

**4.8 Correlation tab** — the pair's most-correlated pairs (`GBPUSD +0.85 · NZDUSD +0.78`) so duplicate exposure is visible, plus a note when a "different" idea is really the same trade (handbook: correlated positions are not independent risk). Source: `correlations`.

---

## 5. The six factor sheets (tap a ring)

Each teaches its analytical layer so you needn't inspect pairs one by one. Contents are canonical (full detail in Design §14). Quick map:

| Ring | Sheet answers | Key contents | Source |
|---|---|---|---|
| 1 Regime | "Does the environment support direction?" | D1/H4/H1 regime + confidence; the 3 votes; stability; **macro archetype link** | `regime_*`, `macro_regime` |
| 2 Currency Flow | "Who is getting stronger / weaker?" | 8 currencies ranked: strength, Δ, flow arrows; leader vs absolute leader | `csm`, `csm_delta`, `currency_flow` |
| 3 Breadth | "Is the move broad or narrow?" | per-currency bar meters, support/total, band | `breadth` |
| 4 Momentum | "Is momentum behind it?" | D1/H4/H1 MOM + delta; CMP + status; sparkline | `pairs.*.mom`, `spark` |
| 5 Structure | "Has structure confirmed?" | D1/H4 direction, BOS/CHoCH, strength; CHoCH warning | `pairs.*.structure` |
| 6 Entry Setup | "Is this an entrable location now?" | setup rank, continuation, ADX, reset, ATR%, alignment, EMA200 | `pairs.*`, `rank` |

---

## 6. The Macro screen & the Archetype Engine (your idea, built on the handbook)

This is the home for cross-assets and the new **named macro regime**. Reached by tapping the nucleus (→ Regime/Macro) or the Macro edge panel.

**6.1 What it shows (top → bottom):**

1. **Archetype banner** — the named regime, e.g. `A · GROWTH-POSITIVE RISK-ON`, with a confidence chip and a one-line AI narrative. If a secondary regime is close, it's shown smaller (`secondary: B · US rate dominance`).
2. **Currency bias** — two baskets: `STRONG: AUD · NZD · CAD` / `WEAK: JPY · CHF`, derived from the regime, colour-coded.
3. **Evidence axes** — the handbook's anti-double-counting logic made visible: instead of ten indicators, a handful of **distinct axes** (Risk · Rates · USD · Commodity/Inflation · Safe-haven), each showing its read and whether it supports the regime. Confidence = how many *distinct* axes agree, not how many indicators.
4. **Gold overlay** — `Defensive gold` vs `Diversification / inflation gold` chip (the handbook's two gold regimes), plus the existing gold signal.
5. **USD regime** — one of `rate dominance · growth dominance · global risk-off · confidence shock` (handbook §12), because "USD up" means different things.
6. **The cross-asset dashboard (Appendix A)** — the full 10-instrument table: `Factor · Variable · Value · Direction · Change · Zone/Interpretation · Currency impact`. Instruments: VIX, US10Y, US3M, 10Y-3M curve, DXY, Gold, S&P 500, Copper, WTI, BTC. Tap a row for its zone context (the existing `_ZONES` text). Source: `macro_assets`.
7. **Conflicts** — when evidence disagrees (e.g. *yields falling but VIX rising* → recession vs. disinflationary easing), the screen names the tension and how to resolve it, straight from the handbook's conflict examples.

**6.2 The engine (EXTEND — `scanner/extend/macro_regime.py`).** Deterministic classifier + AI narration. Reads only `macro_assets` (already fetched) and outputs a `macro_regime` object. It never changes the frozen macro calcs; it *interprets* them.

**Regime library (from Handbook Part IX §30 + §12 + §14):**

| Code | Name | Signature (cross-asset directions) | Currency bias |
|---|---|---|---|
| A | Growth-positive risk-on | SPX↑ VIX↓ Copper↑ | + AUD NZD CAD · − JPY CHF |
| B | US rate dominance | US3M/US10Y↑ (front-end↑), VIX stable | + USD · − EUR GBP AUD (vs USD); USDJPY↑ |
| C | Disinflationary easing | US3M↓ US10Y↓, VIX stable, SPX resilient | − USD · + AUD NZD EUR GBP |
| D | Recession shock | SPX↓ VIX↑ Copper↓ yields↓ | + JPY CHF (USD maybe) · − AUD NZD CAD |
| E | Liquidity shock | VIX spikes, correlations unstable, funding stress | + USD JPY CHF · − everything; **confidence capped low** |
| F | Inflation shock | US10Y↑ Commodities↑ WTI↑ | + USD; commodity ccys may initially benefit |
| G | Oil supply shock | WTI↑ VIX↑ SPX↓ | CAD ambiguous (terms-of-trade vs risk); + USD JPY CHF |
| H | China / industrial slowdown | Copper↓ (industrial) | − AUD NZD CAD · + JPY CHF |
| I | European energy shock | (EUR-specific; DXY↑, energy) | − EUR · + USD CHF; GBP may outperform EUR |
| J | Crowded carry unwind | VIX spike + JPY appreciation + leverage | + JPY CHF · − AUD NZD & carry crosses; positioning = multiplier |

**Confidence via distinct axes (the handbook's key discipline).** Each instrument maps to an axis; a regime is confirmed by *distinct axes*, not correlated indicators:

- **Risk axis:** SPX, VIX, Copper(demand), BTC, (AUDJPY as litmus)
- **Rates axis:** US3M, US10Y, curve
- **USD axis:** DXY
- **Commodity / inflation axis:** WTI, Copper(supply), Gold
- **Safe-haven axis:** Gold(defensive), JPY, CHF

`distinct_axes = number of axes whose net read supports the chosen regime`. Confidence: `High ≥ 3 · Medium = 2 · Low ≤ 1`. Liquidity-shock (E) is force-capped to Low because correlations are unstable (handbook §7, §38).

**Gold overlay & USD regime** are computed as sub-classifiers (defensive vs diversification gold from Gold+VIX+SPX+yields; USD regime from which axis is driving DXY).

**Output (`macro_regime` in `signals.json`):**
```json
{ "primary": {"code":"A","name":"Growth-positive risk-on","confidence":"High","distinct_axes":3},
  "secondary": {"code":"B","name":"US rate dominance","confidence":"Low"},
  "gold_overlay": "diversification",
  "usd_regime": "growth_dominance",
  "currency_bias": {"strong":["AUD","NZD","CAD"],"weak":["JPY","CHF"]},
  "evidence": [
    {"axis":"risk","read":"SPX↑ VIX↓ Copper↑","supports":true},
    {"axis":"rates","read":"US10Y↑","supports":true},
    {"axis":"usd","read":"DXY flat","supports":false}],
  "conflicts": ["Rates rising while risk-on — check whether growth or policy is leading"],
  "narrative": "Broad risk appetite with firm cyclical commodities favours the dollar-bloc growth currencies over funders.",
  "updated": "…" }
```

**How it feeds the app:** the nucleus shows `primary.name`; the recommendation (§7) builds on `currency_bias` + `primary`; the Macro screen renders the whole object; and the wheel's REGIME factor can optionally consult it (config `REGIME_SOURCE`). It stays consistent with the frozen `regime_*` (which classify risk-on/off from CSM+pills) — the archetype adds the *why* and the *named* pattern, exactly your "DEFENSIVE GOLD REGIME → X strong, Y weak" idea, grounded in the handbook.

**Rule #1 note:** the archetype engine does not alter the frozen regime, CSM, or gold-signal computations. It reads their inputs (cross-assets) and produces a new interpretive object. The AI narrates; the deterministic classifier decides the regime and bias.

---

## 7. The Recommendation engine (news + calendar + macro, synthesised)

The AI recommendation (architecture §6) is where **news, calendar, and macro archetype converge into one actionable read.** It answers your "does news support the narrative? does the calendar support it?"

**Inputs (all already in `signals.json`):** `macro_regime` (the named archetype + bias), `regime_*`, `currency_flow`, `ranked.top`, `gold_signal`, `catalyst` (news adversarial check), `breaking` (top headlines), `calendar` (next event), `macro_assets` zones.

**Output (`recommendation`):** headline, action (`TRADE / WATCH / STAND ASIDE`), primary pair + direction, confidence, rationale (40–60 words), **invalidation** (what flips it), and **next catalyst** (from the calendar). Shown in the left **Recommendation edge panel** and as the nucleus headline line.

**News does support the narrative, explicitly:**
- **Breaking headlines** (top 3, Haiku-curated) appear in the Insights panel.
- **Catalyst check** is adversarial (existing): "does any headline conflict with / accelerate / invalidate the top setups?" — surfaced as a one-line flag on the Insights panel and on affected pair sheets.
- **Theme tagging (NEW, EXTEND):** headlines are tagged to the active macro axis (a Fed headline → Rates/USD; a China PMI headline → Risk/Commodity), so the recommendation can say *"news theme confirms the rates-driven USD bid"* rather than just listing headlines. Handbook discipline: **news adds theme, not direction** — the deterministic engine sets direction, news colours the narrative.

**Calendar supports the narrative:**
- Events are tagged by currency; pairs whose currency has a high-impact event in the next 24h get an **"event risk" rim** on their node and a flag in the pair sheet.
- The recommendation names the **next high-impact catalyst and time**.
- After an event fires, the news/catalyst reflects the **surprise vs expectation** (handbook Part XI) — the framework the app leans on for "what changed."

---

## 8. The simplified line chart (no candles)

Your requirement: no candlesticks, but a simple line to see up/down at a glance on **all three timeframes**.

- **What:** a clean **close-price line** (area-filled, emphasized endpoint dot, coloured by net direction over the window) for **D1, H4, H1** — three small charts stacked or as a pill-tabbed single chart.
- **Where:** the top of the **Pair sheet** (below the header); a **long-press on any node** opens it directly; also used as the sparkline inside the Momentum sheet.
- **How it's drawn:** **natively in Compose (`Canvas`/a light chart lib)** — no WebView, no LightweightCharts, no candles. This removes the WebView island from the architecture entirely.
- **Data source (NEW, EXTEND — `spark`):** the backend exposes a compact recent-closes array per pair per TF (≈ 48–64 points each) in `signals.json`:
  ```json
  "spark": { "EURUSD": { "d1":[…], "h4":[…], "h1":[…] }, … }
  ```
  ≈ 12 pairs × 3 TF × ~56 points. Small, and it means **the app needs no market-data API key** — the sparkline renders from the same `signals.json` everything else uses. (A full zoomable chart, if ever wanted, could fetch on-device with an optional key — but that is explicitly out of scope; you asked for the simple line only.)
- **Direction read:** endpoint above window-start ⇒ `bull` colour; below ⇒ `bear`; plus the D1/H4/H1 % change already in `pairs.<PAIR>.d1_pct/d5_pct`. So "is the market up or down, on each timeframe" is answered instantly.

---

## 9. Settings (the gear) & how keys are handled

**Answer to "we probably need some API keys": the phone app needs none.** All market-data and AI keys live server-side as GitHub Actions secrets (`TWELVEDATA_KEY`, `ANTHROPIC_API_KEY`, `FCM_SERVICE_ACCOUNT`). The scanner uses them; the app only ever reads the finished `signals.json` and receives push. Keys never touch the device — a genuine security win over the old dashboard (which pasted the Twelvedata key into browser localStorage).

**Settings screen contents (header gear → full-screen or top sheet):**

| Setting | What it does | Needs a key? |
|---|---|---|
| **Theme** | System / Dark / Light override | No |
| **Notifications** | Toggle push; "send test"; per-type toggles (gold signal, level alerts; future: level-6 advance — off by default, see §12) | No |
| **Data source** | The `signals.json` URL (pre-filled to your repo raw/Pages); refresh cadence | No |
| **Price-level alerts (optional)** | Enable drawing alerts that sync to the repo | **GitHub PAT** (optional; only if you want cross-device level alerts written to `data/level_alerts.json`) |
| **Freshness / diagnostics** | Last update, next expected scan, schema version, "force refresh" | No |
| **About / legend** | The ⓘ guide: what every ring, score, and archetype means | No |

So the **only** optional key is a GitHub PAT, and only if you use synced price-level alerts. Everything else is keyless.

---

## 10. Component roles (your specific questions)

**Scrolling pills** — used wherever a small set of peers is chosen or scanned:
- Tradeable Now / Watch lists (ranked, horizontal).
- Pair-sheet tabs (`Overview … Correlation`) and Momentum TF sub-tabs (`D1 H4 H1`).
- CSM/Flow timeframe toggle (`D1 H4 H1`).
- Currency chips in the Flow/Breadth sheets.
They snap, scroll on overflow, and the active pill scrolls into view. They are the app's lightweight "switch context without leaving the surface" control.

**Bottom sheets** — the primary *detail* mechanism, three detents (collapsed / half / expanded), wheel visible behind at half. Homes for: the six factor sheets, the pair sheet, the Regime/Macro sheet, and the CSM detail. Tap-scrim or swipe-down to dismiss; the wheel keeps spatial context.

**Edge panels** — the primary *context* mechanism, summoned from screen edges so they never clutter the wheel:
- **Left → Recommendation** (the AI synthesis: what to do, why, what breaks it, next catalyst).
- **Right → Calendar / Events** (upcoming high-impact events + which pairs they touch).
- (Insights — breaking news / daily brief / week ahead — can be a top-sheet from the header Insights icon.)

**Special animations** (all communicate a state change; nothing decorative):
- **Radial advance / retreat** (400–700 ms, no bounce): a pair node glides outward when it passes a new factor, inward when it loses one; the changed factor-marker flips brightness in sync. This is *the* signature animation — market opportunity becoming visible.
- **Top-pair halo transfer:** old #1's halo fades, new #1's appears.
- **Breathing glow** on level 5–6 nodes only (subtle, opt-out with reduce-motion / battery saver).
- **Regime cross-fade:** nucleus accent + ring tints cross-fade when the regime/archetype changes.
- **Event-risk rim pulse:** a one-time gentle pulse when a pair enters the 24h-to-event window.
- **Staggered multi-node moves:** if several pairs move on a new scan, starts stagger 40–80 ms so the eye can follow.

---

## 11. Data contract additions (what the EXTEND layer must expose)

New `signals.json` keys this spec requires (all additive; frozen keys untouched):

```
macro_regime   { primary, secondary, gold_overlay, usd_regime, currency_bias, evidence[], conflicts[], narrative, updated }   ← §6
spark          { PAIR: { d1:[…], h4:[…], h1:[…] } }                                                                          ← §8
news_themes    { headlines:[{text, axis, sentiment}], updated }    (optional theme-tagging)                                   ← §7
potential, csm_delta, currency_flow, breadth, recommendation, per-pair structure, schema_version                             ← already specced (arch §4.2)
```

New EXTEND modules: `macro_regime.py`, `spark.py`, and a `news_themes` step folded into `scan_news.py` (all additive).

---

## 12. Not in v1 — deliberate scope line & opportunities

- **Journal / trade-thesis worksheet.** The handbook's Appendix C (pre-trade) and Appendix D (post-trade review) map perfectly to a Journal screen: capture regime, thesis, causal mechanism, chosen pair, invalidation, then review thesis quality vs. outcome. High value, but a distinct feature — **Phase 10+.** Flagged as the top post-v1 opportunity because it closes the handbook's "Review" loop and makes the app a discipline tool, not just a scanner.
- **New alert types** (pair advances to level 6; top-pair change). These change *when* notifications fire, which Rule #1 protects — so **opt-in, off by default**, clearly separate from the frozen gold/level alerts.
- **Positioning / COT data** (handbook's "positioning is a multiplier"). Not on the free data tier today; a later data source could add a positioning axis to the archetype engine and an asymmetry flag to the pair sheet.
- **Full zoomable price chart.** Explicitly out — you want the simple line only. If ever wanted, it's an on-device fetch with an optional key, isolated from the core keyless experience.
- **Bottom navigation bar.** If surfaces multiply (Wheel · Macro · Journal · Settings), a conventional bottom nav can replace some edge-panel summoning. Revisit after Phase 9.

---

## 13. Traceability — your questions, answered

- **MOM1212?** Yes — Momentum factor sheet + pair Momentum tab (D1/H4/H1 + deltas + CMP).
- **CSM on 3 timeframes?** Yes — CSM detail sheet + Flow sheet, D1/H4/H1 pill toggle.
- **Correlations?** Yes — pair Correlation tab + a correlation view.
- **3-timeframe direction arrows?** Yes — pills on the pair sheet and as factor-marker/direction glyphs on every node.
- **Macro archetype detection ("DEFENSIVE GOLD REGIME → X strong, Y weak")?** Yes — the Macro Archetype Engine (§6), 10 named regimes + gold/USD overlays, confidence by distinct evidence axes, currency-bias baskets. Built directly from your handbook.
- **News curation + pattern to support the narrative?** Yes — breaking headlines, adversarial catalyst check, and theme-tagging to the macro axis (§7). News adds theme, engine sets direction.
- **Calendar supports the narrative?** Yes — event tagging, node event-risk rim, next-catalyst in the recommendation, surprise-vs-expectation after the event (§7).
- **Cross assets displayed?** Yes — the full 10-instrument Appendix-A dashboard on the Macro screen, and per-pair in the Macro tab (§6).
- **Gear settings / API keys?** The app needs no keys (all server-side); Settings holds theme, notifications, data source, freshness, and an optional GitHub PAT only for synced price-level alerts (§9).
- **Bottom sheets / scrolling pills / edge panels / animations?** Fully specified in §10.
- **Simplified 3-TF line chart, no candles?** Yes — native Compose sparkline from a new `spark` field, on the pair sheet and long-press (§8); removes the WebView.

*End of Functional Specification.*
