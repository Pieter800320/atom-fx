# ATOM FX — Architecture Document

**Version:** 1.0 · **Status:** Specification (hand to Claude Code as an anti-drift contract)
**Author of record:** Pieter · **Reference system:** `fx-signal-board` (FX Signal Board)
**Target:** Native Android APK, built in Android Studio with Claude Code.

---

## 0. How to read this document

This is the **binding technical contract** for ATOM FX. Claude Code must treat it as the source of truth and must not "improve", refactor, or re-derive anything marked **FROZEN**. When this document and a code suggestion disagree, this document wins. When this document is silent, ask before inventing.

Three tiers of change authority are used throughout:

| Tier | Meaning | Who may change it |
|---|---|---|
| **FROZEN** | Exact port of the reference engine. Formulas, thresholds, weights, field names — byte-for-byte behaviour. | Nobody, without Pieter's explicit sign-off. This is Rule #1. |
| **EXTEND** | New backend outputs that sit *on top of* the frozen engine. They read frozen values; they never modify them. | Additive only. Never edit a frozen module to make an EXTEND feature easier. |
| **NEW** | The Android app, the push transport, the presentation layer. Green field. | Free to design within this spec. |

> **RULE #1 (non-negotiable).** ATOM FX calculates the *same things* as FX Signal Board and fires signals on the *same criteria*. Every number a trader sees must be reproducible from the frozen engine. The redesign is a **visualisation and interaction layer**, plus a small set of **additive** analytical outputs. It is never a re-implementation of the trading logic.

---

## 1. System overview

ATOM FX is a three-part system. Only Part C is genuinely new software; Parts A and B are the reference backend, forked verbatim and then *extended additively*.

```
┌──────────────────────────────────────────────────────────────────────┐
│  A. FROZEN CORE  (Python scanner, forked verbatim from fx-signal-board) │
│     fetch → aggregate → pills/score → mom1212 → csm → regime →         │
│     cont_score → rank → correlate → structure                          │
│     ── produces every existing number, unchanged ──                    │
└───────────────┬──────────────────────────────────────────────────────┘
                │ (in-process, same scan run)
┌───────────────▼──────────────────────────────────────────────────────┐
│  B. EXTEND LAYER  (new Python modules, additive)                       │
│     csm_delta → currency_flow → breadth → structure-expose →           │
│     potential-engine (six-factor) → recommendation (AI synthesis)      │
│     ── writes NEW keys into signals.json; reads frozen values only ──  │
└───────────────┬──────────────────────────────────────────────────────┘
                │ commit signals.json to GitHub (unchanged transport)
                │ + FCM push on the SAME firing criteria
┌───────────────▼──────────────────────────────────────────────────────┐
│  C. ATOM FX ANDROID APP  (Kotlin + Jetpack Compose, NEW)               │
│     signals.json repository → Energy Wheel (Canvas) → bottom sheets →  │
│     edge panels → scrolling pills → price chart (WebView island)       │
│     ── pure consumer of signals.json; never computes trading logic ──  │
└──────────────────────────────────────────────────────────────────────┘
```

**Data contract between B and C:** `signals.json`, fetched from GitHub over HTTPS. The app is a *pure consumer*. It never re-derives pills, CSM, regime, momentum, continuation, rank, structure, or the six-factor potential state. If the app needs a value, the backend exposes it (spec §42 of the Energy Wheel brief: "expose, don't recompute in the frontend").

**Cost posture (unchanged from reference):** GitHub Actions (free, public repo) + Twelvedata free tier + Yahoo Finance (unofficial) + Anthropic API (~a few dollars/month) + FCM (free). No paid hosting.

---

## 2. The Frozen Core (Part A) — Rule #1 boundary

These modules are **forked verbatim** into the new `atom-fx` repo under `scanner/`. Claude Code must copy them without edits. The table is the canonical inventory; the sub-sections restate each formula so drift can be detected by reading this document alone.

| Module | Produces | Tier |
|---|---|---|
| `config.py` | 12 tradable pairs, 8 currencies, risk-on/off sets, correlates, TF config | FROZEN |
| `fetch.py` | Twelvedata H1 OHLCV, dynamic rate limiter (6 calls/min, 429 handling) | FROZEN |
| `aggregator.py` | H1 → H4 (UTC 4h blocks) and H1 → D1 (UTC calendar day) | FROZEN |
| `score.py` | EMA200/EMA50/RSI/MACD/DMI+ADX/ATR scoring; `compute_reset_score`; `atr_percentile` | FROZEN |
| `structure.py` | BOS / CHoCH event, direction, strength, multiplier | FROZEN |
| `pills.py` | label → pill string mapping (`bull_strong`…`bear_strong`) | FROZEN |
| `mom1212.py` | MOM1212 oscillator D1/H4/H1 + deltas + CMP + CMP deltas | FROZEN |
| `csm.py` | Currency Strength Model, 16-pair universe, D1/H4/H1 | FROZEN |
| `regime.py` | 4-vote regime per TF (Risk-On/Off/Mixed/Ranging) | FROZEN |
| `cont_score.py` | Continuation score 0–100 (computeQAI port) | FROZEN |
| `rank.py` | Setup rank 0–10, cross-asset impact table | FROZEN |
| `correlate.py` | 12×12 Pearson correlation on H4 returns | FROZEN |
| `scan_h1.py` | Hourly orchestrator; assembles frozen `signals.json`; gold signal; alert triggers | **FROZEN logic, EXTEND call sites** (see §5.2) |
| `scan_news.py` | Macro (Yahoo), W1 regime, headlines, calendar, catalyst, ranked narrative, daily brief, week ahead | **FROZEN logic, EXTEND with `recommendation`** (§6) |

### 2.1 The 12 pairs and 8 currencies (FROZEN)

Pairs (permanent wheel identity, spec §4): `EUR/USD, GBP/USD, USD/JPY, USD/CHF, AUD/USD, USD/CAD, NZD/USD, EUR/JPY, GBP/JPY, AUD/JPY, NZD/JPY, CAD/JPY`.
Currencies: `GBP, EUR, AUD, NZD, CAD, JPY, CHF, USD`.
Risk-on set `{AUD, NZD, GBP, EUR, CAD}`; risk-off/defensive set `{JPY, CHF, USD}`.

### 2.2 CSM (FROZEN)

16-pair universe (`STRENGTH_PAIRS`): the 12 majors' constituents plus `EUR/GBP, EUR/CHF, GBP/CHF, AUD/NZD, AUD/CAD, GBP/AUD`. ATR-normalised returns, min-max normalised to 0–100.
- **D1:** 14-bar return, `D1×0.7 + H4×0.3`.
- **H4:** 5-bar H4 (`0.8`) + 8-bar H1 (`0.2`).
- **H1:** 6-bar H1, pure.
Do not change lookbacks, weights, the 16-pair set, or the normalisation. The new CSM Delta (EXTEND §5.1) is computed **from this exact function**, never a re-derivation.

### 2.3 MOM1212 + CMP (FROZEN)

`momentum = (SMA12_now − SMA12_past) / (12 × ATR14)`, normalised `50 + 50·tanh(2.8·m)`.
Delta lookbacks: D1 = 5 bars, H4 = 30 bars, H1 = 120 bars. CMP weights `D1×0.5 + H4×0.3 + H1×0.2`, computed as the sigmoid of the weighted *raw* momentum. CMP deltas at H1-offsets 4/8/12.

### 2.4 Regime (FROZEN)

Per timeframe, three votes (safe-haven divergence, USD proxy, risk-basket pill balance) plus a ranging override (<40% directional pills → Ranging). Output `{regime, confidence, score 0–10, stable}`. H4 is the principal trading regime (spec §5).

### 2.5 Continuation score & Setup rank (FROZEN)

- **Continuation (0–100):** TF alignment 35% · entry position 23% · CSM divergence 16% · regime fit 13% · rate 5% (neutral) · session 8%; ADX<20 caps at 45; counter-regime caps applied.
- **Setup rank (0–10):** cont 25% · cmp 20% · mom-delta 15% · csm 20% · regime 10% · cross 10%; **hard gate: D1 pill directional AND cont ≥ 45**.

These two remain **independent quantitative scores**, surfaced verbatim in the pair detail sheet. The Energy Wheel's "Potential" (EXTEND §5.4) is a *different* measurement and must not overwrite or replace them (spec §35).

### 2.6 Structure (FROZEN)

`detect_structure` → `{direction: bull|bear|neutral, event: BOS|CHoCH|none, strength 0–1, multiplier}`. Currently the multiplier folds into `score.py`; the raw dict is computed but **not yet exposed per-pair in signals.json**. EXTEND §5.3 exposes it. The detector itself is frozen — no second BOS detector anywhere, least of all in Kotlin (spec §42).

---

## 3. Repository topology

**Decision:** a single new repository, `atom-fx`, that forks the scanner verbatim and adds both the extend layer and the Android app. `fx-signal-board` is left **exactly as-is** as the reference.

```
atom-fx/                                  ← new GitHub repo (Pieter800320/atom-fx)
├── scanner/                              ← FROZEN, forked verbatim from fx-signal-board
│   ├── config.py  fetch.py  aggregator.py  score.py  structure.py
│   ├── pills.py  mom1212.py  csm.py  regime.py  cont_score.py
│   ├── rank.py  correlate.py  level_ema_alerts.py
│   ├── scan_h1.py                        ← frozen logic; EXTEND call-sites only (§5.2)
│   ├── scan_news.py                      ← frozen logic; + recommendation call (§6)
│   └── extend/                           ← EXTEND layer (all NEW, additive)
│       ├── csm_delta.py                  ← §5.1
│       ├── currency_flow.py             ← §5.1
│       ├── breadth.py                   ← §5.3
│       ├── structure_expose.py          ← §5.3
│       ├── potential.py                 ← §5.4 six-factor engine
│       ├── macro_regime.py              ← macro archetype engine (Functional Spec §6)
│       ├── spark.py                     ← compact recent-closes for native line chart (Functional Spec §8)
│       ├── recommendation.py            ← §6 AI synthesis (macro + news + calendar)
│       └── potential_config.py          ← all EXTEND thresholds, one place
├── push/
│   └── send_push.py                      ← §7 FCM transport (replaces Telegram delivery)
├── data/
│   └── signals.json                      ← the contract (frozen keys + new keys)
├── app/                                  ← NEW — Android app (Gradle module)
│   └── … (see §8)
├── docs/
│   ├── ATOM_FX_ARCHITECTURE.md           ← this file
│   └── ATOM_FX_DESIGN.md                 ← the design contract
├── .github/workflows/
│   ├── scan_h1.yml                       ← hourly scan + push (adapted from reference)
│   └── scan_news.yml                     ← 2h news + recommendation
└── requirements.txt
```

**Why one repo:** the wheel needs new `signals.json` fields, so the backend must be *extended somewhere*. Forking the scanner into `atom-fx` keeps the reference pristine, gives one source of truth for the app's data, and means Claude Code never has to reach across repos. The app and its data pipeline version together.

**Branch discipline:** `main` is deployable. Feature work on `feat/*` branches, one per build phase (§9). The frozen `scanner/*.py` files (excluding `extend/` and the two scan orchestrators' new call-sites) should ideally be protected by a CODEOWNERS note so a drift edit is caught in review.

---

## 4. Data contract — `signals.json`

The app reads exactly one document. It is fetched from GitHub raw (or the Pages mirror) at a sensible interval (§8.4). Below, **FROZEN keys** are produced today by the reference; **NEW keys** are added by the EXTEND layer.

### 4.1 Frozen keys (unchanged shape)

```
updated            ISO timestamp
regime_d1/h4/h1    {regime, confidence, score, stable}
csm                {d1, h4, h1} → {CCY: 0-100}
correlations       {pairs:[12], matrix:[12][12]}
pairs              {PAIR: {pills, mom, adx, d1_pct, d5_pct, prev_close, prev5_close, cont}}
regime_w1          {regime, confidence, score, signals, total, stable}
macro              {label, confidence, stable, signals, total}
macro_assets       {vix, us10y, us3m, wti, gold, spx, copper, dxy, btc, curve}
catalyst           {text, updated}
ranked             {text, top:[{pair, direction, score}], updated}
calendar           {events:[{day,time,iso,currency,name,forecast,previous,note}], updated}
week_ahead         {text, generated_at}   (Sun–Mon only)
deep_analysis      {text, generated_at}   (daily brief)
breaking           {headlines:[…], updated}
gold_signal        {direction, gold_pct, h4_confirmed, h4_confidence, h1_confirmed, updated}
last_alert         ISO timestamp
```

Per-pair frozen block (unchanged):
```json
"EURUSD": {
  "pills": {"d1":"bull","h4":"neutral","h1":"neutral"},
  "mom": {"d1":76,"dd1":9,"h4":40,"dh4":-46,"h1":32,"dh1":-17,"cmp":57,"dcmp4":-1,"dcmp8":-2,"dcmp12":-4},
  "adx": 52.1, "d1_pct": -0.02, "d5_pct": -0.26,
  "prev_close": 1.16535, "prev5_close": 1.1682, "cont": 42
}
```

### 4.2 New keys (EXTEND layer — additive, never overwrite frozen keys)

```
csm_delta          {d1,h4,h1} → {CCY: signed delta}          (§5.1)
currency_flow      {leader, leader_delta, laggard, laggard_delta,
                    absolute_leader, absolute_laggard, driver_spread, tf}  (§5.1)
breadth            {h4:{CCY:{support,total,pct,band}}, d1:{…}}  (§5.3)
potential          {PAIR: {direction, level, state, score, factors{6}, setup_rank,
                          blocked_at, quality}}                 (§5.4)
recommendation     {headline, bias, action, primary_pair, direction,
                    confidence, rationale, invalidation, next_catalyst, generated_at}  (§6)
macro_regime       {primary, secondary, gold_overlay, usd_regime, currency_bias,
                    evidence[], conflicts[], narrative, updated}  (Functional Spec §6 — the archetype engine)
spark              {PAIR: {d1:[…closes], h4:[…], h1:[…]}}  — compact recent closes for the native line chart (Functional Spec §8)
news_themes        {headlines:[{text, axis, sentiment}], updated}  — optional theme-tagging (Functional Spec §7)
schema_version     integer — bump on any contract change; app checks it (§8.4)
```

Per-pair structure is added **inside the existing `pairs.<PAIR>` block** as a new sub-key, so it travels with the pair (§5.3):
```json
"structure": { "h4": {"direction":"bull","event":"BOS","strength":0.78,"multiplier":1.23},
               "d1": {"direction":"bull","event":"none","strength":0.0,"multiplier":1.0} }
```

> **Contract invariants Claude Code must honour:**
> 1. New keys are **added**; no frozen key is renamed, removed, or repurposed.
> 2. Any pair may be absent from `potential`/`ranked` (gated out) — the app must render "no thesis / nucleus", never crash.
> 3. `schema_version` starts at `1`. Bump it whenever a key is added or a shape changes; the app shows a soft "update app" state on a higher-than-known version rather than mis-parsing.
> 4. Missing AI keys (`recommendation`, `deep_analysis`, …) are normal (API down, cadence) — the app degrades gracefully.

---

## 5. The EXTEND layer (Part B) — additive analytics

All of §5 lives under `scanner/extend/`. Every function takes frozen outputs as input and returns new data. **None of them import-and-modify a frozen module.** All thresholds live in `potential_config.py` so the wheel can be tuned without code archaeology.

### 5.1 CSM Delta & Currency Flow (`csm_delta.py`, `currency_flow.py`)

**Goal (spec §6, §39–40):** distinguish "strong" from "getting stronger". Add a *flow* reading on top of the frozen CSM.

**Method — deterministic offset recompute (no state file).** Mirror how `mom1212.py` computes deltas: compute CSM now, and CSM at a defined bar-offset in the past, using the **same frozen `csm.py` functions** on a sliced price history. This is fully reproducible from the current fetch and requires no persistence.

```
For tf in {d1, h4, h1}:
    csm_now[tf]  = frozen csm[tf]                       # already computed this scan
    csm_past[tf] = csm.compute_csm_<tf>( ohlcv sliced back by OFFSET[tf] )
    csm_delta[tf][CCY] = round(csm_now[tf][CCY] − csm_past[tf][CCY], 1)
```

Default offsets (in bars of that TF, configurable in `potential_config.py`):
`OFFSET = {"d1": 1, "h4": 6, "h1": 6}` — i.e. "since yesterday" on D1, "since ~1 day ago" on H4, "since 6h ago" on H1. **H4 is the primary flow timeframe for the wheel** (spec §6).

Slicing helper (illustrative — the real one lives beside `csm.py`'s expectations): for each pair df in `ohlcv[key][tf]`, pass `df.iloc[:len(df) − OFFSET[tf]]` into the frozen compute function. Because min-max normalisation is relative, the delta captures *rotation of strength*, which is exactly the intended "flow".

**Currency Flow object** (derived from `csm_delta[h4]` + frozen `csm[h4]`):
```
leader           = argmax_CCY csm_delta_h4       (strongest positive driver)
leader_delta     = that value
laggard          = argmin_CCY csm_delta_h4       (strongest negative driver)
laggard_delta    = that value
absolute_leader  = argmax_CCY csm_h4             (highest absolute strength)
absolute_laggard = argmin_CCY csm_h4
driver_spread    = leader_delta − laggard_delta
tf               = "h4"
```
The leader/absolute_leader distinction is intentional and must be preserved in the UI (spec §7, §20).

### 5.2 Alert trigger call-sites in `scan_h1.py` (FROZEN logic)

`scan_h1.py`'s *gating conditions stay identical* (Rule #1: same firing criteria). The only change is the **transport**: where the reference calls `send_telegram(msg)`, ATOM FX calls `send_push(title, body, data)` (§7) with the same conditions and the same human-readable content. Concretely, the two existing triggers are preserved exactly:

1. **Gold signal** — fires when `gs_direction != "neutral" AND h4_confirmed AND h1_confirmed AND h4_conf in {Medium, High}`. (Computed by the frozen gold-signal block; do not touch that computation.)
2. **Price level alerts** — `level_ema_alerts.check_levels(...)` against the latest pair close (the reference passes the most-recent H4 bar close), fires on touch and deactivates the level. (Frozen.)

`check_ema_touches` remains present but disabled exactly as in the reference (gold signal is the sole proactive alert). Extending which conditions fire is out of scope for v1 — Rule #1 forbids silently changing when a signal fires.

### 5.3 Breadth & Structure exposure (`breadth.py`, `structure_expose.py`)

**Breadth (spec §8, §21).** "Is the currency move broad, or produced by one or two pairs?" Reuse the frozen CSM internals: in `csm.py`, each pair contributes `+combined` to its base and `−combined` to its quote. Breadth for a currency = the count of its contributions that agree with its net direction, over the total number of relationships it has in the 16-pair universe.

```
For each CCY:
    contribs = signed ATR-normalised contributions of CCY across the 16 STRENGTH_PAIRS  (h4)
    net_sign = sign(mean(contribs))
    support  = count(c for c in contribs if sign(c) == net_sign)
    total    = len(contribs)                # = that currency's appearances in the 16-pair set
    pct      = support / total
    band     = "strong" if pct >= 0.70 else "moderate" if pct >= 0.50 else "weak"
breadth["h4"][CCY] = {support, total, pct, band}
```

> **Denominator note (accuracy — do not "fix" to a flat /8).** In the frozen 16-pair `STRENGTH_PAIRS` universe each currency appears a *different* number of times: `USD=7, AUD=5, GBP=4, JPY=4, EUR=3, CHF=3, CAD=3, NZD=3`. So `total` varies by currency (3–7), and the Energy Wheel brief's illustrative "7/8" is aspirational, not literal. **Always compare by `pct`/`band`, never by raw `support` count**, so a 3-relationship currency and a 7-relationship currency are judged on the same scale. The UI may show `support/total` (e.g. `EUR 3/3`) but the *pass logic* (§5.4 factor 3) and colour band use `pct`. Do not invent extra pairs to pad the denominator (spec §8 forbids a separate market model).
`structure_expose.py` must add a thin hook so `csm.py`'s per-currency contribution lists are available to `breadth.py` **without changing `csm.py`'s outputs** — the cleanest way is a small frozen-friendly wrapper that recomputes the contribution lists by calling the same helpers (`_adj_return`), never by editing `csm.py`. Compute breadth for `h4` (primary) and optionally `d1`.

**Structure exposure.** `structure.py` is already run inside `score.py` for H4 and D1. Expose the raw dict per pair into `pairs.<PAIR>.structure.{h4,d1}` (§4.2) by capturing it in `scan_h1.py` where the H4/D1 scores are computed (they already hold `result["structure"]`). This is a **read-and-copy**, not a recompute.

### 5.4 The six-factor Potential Engine (`potential.py`)

This is the analytical heart of the wheel and the most important EXTEND module. It produces, **deterministically in Python**, the level/state/factor map the app visualises (spec §41: "Do not calculate the six-factor state independently in JavaScript/Kotlin"). The engine reads only frozen values + §5.1/§5.3 outputs.

**Direction basis.** A pair's directional thesis = its **D1 pill** (`bull` if `d1 ∈ {bull, bull_strong}`, `bear` if `d1 ∈ {bear, bear_strong}`, else **none**). This matches the frozen `rank.py` gate. A pair with no directional D1 pill sits at the **nucleus (level 0)**, state `low`, all factors false. (Consistent with §53's "isolated technical signal against the market" staying central.)

**Sequential six factors (spec §5–§12).** A pair advances outward **only by passing each factor in order**. `level` = the count of consecutive passes starting at factor 1; the first failure sets `blocked_at` and stops advancement regardless of later factors.

| Lvl | Factor | Pass condition (reads frozen/EXTEND values) | Config keys |
|---|---|---|---|
| 1 | **REGIME** | `regime_h4.regime ∈ {Risk-On, Risk-Off}` **and** direction aligns with that regime by the frozen risk sets (base or quote fits, i.e. `rank._regime_sc`-equivalent ≥ `REGIME_FIT_MIN`). Ranging/Mixed ⇒ **no advancement**. | `REGIME_SOURCE="h4"`, `REGIME_FIT_MIN=7` |
| 2 | **CURRENCY FLOW** | directional flow spread favourable: bull ⇒ `csm_delta_h4[base] − csm_delta_h4[quote] ≥ FLOW_MIN`; bear ⇒ inverse. | `FLOW_MIN=4.0` |
| 3 | **BREADTH** | move is broad: `breadth_h4[base].pct ≥ BREADTH_MIN` (bull base strength) **or** `breadth_h4[quote].pct ≥ BREADTH_MIN` (quote weakness), oriented to direction. | `BREADTH_MIN=0.50` |
| 4 | **MOMENTUM** | CMP supportive: bull ⇒ `cmp ≥ CMP_BULL` (60); bear ⇒ `cmp ≤ CMP_BEAR` (40). 40–60 = neutral = fail. | `CMP_BULL=60`, `CMP_BEAR=40` |
| 5 | **STRUCTURE** | `structure.h4.direction == direction` **and** `structure.h4.event != CHoCH` (a counter-CHoCH blocks; `BOS` or `none` permitted). | `STRUCTURE_REQUIRE_BOS=false` |
| 6 | **ENTRY SETUP** | thesis is entrable: `cont ≥ ENTRY_CONT_MIN` (70) **and** entry location reasonable (`reset_score ≤ RESET_MAX` (55) if present) **and** `ATR_pct` within `ATR_LO..ATR_HI` (20..70) if present. | `ENTRY_CONT_MIN=70`, `RESET_MAX=55`, `ATR_LO=20`, `ATR_HI=70` |

> These pass-conditions are **presentation config**, not trading logic. They re-express frozen outputs into a hierarchy. They may be tuned in `potential_config.py`; tuning them changes only *where nodes sit on the wheel*, never any frozen number. This distinction (spec §42, §56) is the entire reason the metaphor is honest rather than decorative.

**State (spec §34):**
```
low        level 0–2
watch      level 3–5
tradeable  level 6
aplus      level 6 AND setup_rank ≥ APLUS_RANK (8.5)   # rare by design
```

**Potential score 0–100 (spec §13).** Base by level, plus a bounded quality modifier so two pairs on the same ring differ:
```
BASE   = {0:10, 1:25, 2:40, 3:55, 4:70, 5:85, 6:100}
quality = clamp((setup_rank − 5)/5 * QUALITY_SPAN, −QUALITY_SPAN, +QUALITY_SPAN)   # QUALITY_SPAN=7
score  = clamp(round(BASE[level] + quality), 0, 100)
```
`setup_rank` is the frozen `rank.py` score if the pair qualified, else a neutral 5.0 for the modifier only (never shown as a rank). Keep the mapping in config; **do not alter the frozen rank** to feed it (spec §13, §35).

**Output per pair** (into `potential.<PAIR>`):
```json
{ "direction":"bull", "level":5, "state":"watch", "score":84,
  "factors":{"regime":true,"flow":true,"breadth":true,"momentum":true,"structure":true,"entry":false},
  "setup_rank":8.6, "blocked_at":"entry", "quality":6 }
```

`potential.py` runs inside the hourly scan **after** all frozen values and §5.1/§5.3 outputs exist, and before `signals.json` is written.

---

## 6. AI Recommendation Engine (`recommendation.py`)

**Decision (v1):** the full engine is *designed* here and in the design doc; v1 *ships* a single new `recommendation` object synthesised from outputs the frozen `scan_news.py` already produces. The AI **narrates the deterministic engine; it never overrides it** — the action/bias/primary pair are seeded from frozen outputs, and the model fills the human-readable framing. This keeps Rule #1's spirit: the machine decides *what*, the AI explains *why* and *what would break it*.

**Inputs (all already in `signals.json`):** `regime_d1/h4/h1`, `gold_signal`, `currency_flow` (§5.1), `ranked.top`, `catalyst`, `calendar.events` (next event), `macro_assets` (+ zone context already built in `scan_news.build_deep_context`), `deep_analysis`.

**Deterministic seed (computed before the model call):**
```
bias          = regime_h4.regime mapped to {risk_on, risk_off, mixed}
action        = "trade"       if any potential.state == "tradeable"/"aplus"
                "watch"        elif any potential.state == "watch"
                "stand_aside"  else
primary_pair  = ranked.top[0].pair   (or the highest-level potential pair)
direction     = that pair's direction
confidence    = gold_signal.h4_confidence blended with regime confidence
next_catalyst = earliest future calendar event
```

**Model call (v1):** one Sonnet call (reuse `scan_news._sonnet`, same model constants), fed the seed + context, returning strictly:
```json
{ "headline": "≤10 words, imperative, names the theme",
  "rationale": "40–60 words, plain English, cites the specific drivers",
  "invalidation": "one sentence: what flips this" }
```
`recommendation.py` merges the seed + model text into the `recommendation` object (§4.2) and writes `generated_at`. Cadence: regenerate on the daily-brief schedule (12h) or when `bias`/`primary_pair` changes; otherwise reuse (mirrors the reference's brief-age gating so API spend stays ~unchanged).

**Guardrails Claude Code must enforce:**
- The model **cannot** change `bias`, `action`, `primary_pair`, `direction` — those come from the deterministic seed. If the model text contradicts the seed, the seed wins and the text is regenerated once.
- On API failure, write no `recommendation` key (app falls back to showing the deterministic nucleus state + existing `deep_analysis`).
- No new trading numbers are invented. The recommendation references only values already in `signals.json`.

**Full-engine roadmap (design-doc detail, later phases):** per-pair recommendation reasoning, event-aware position sizing hints, a confidence model blending regime stability + breadth + calendar proximity, and a "what changed since last scan" diff. All additive; none alters the frozen engine.

---

## 7. Notifications — same criteria, native transport (`push/send_push.py`)

**Decision:** identical firing criteria (Rule #1), delivered as **native Android push via FCM** instead of Telegram.

**Server side (GitHub Actions).** `send_push(title, body, data)` posts to **FCM HTTP v1** using a **service-account JSON stored as a GitHub secret** (`FCM_SERVICE_ACCOUNT`). Delivery uses **topic messaging** — the backend sends to topic `atomfx-signals`; no per-device token registry is needed for a single user, and it scales to more devices free.

```
POST https://fcm.googleapis.com/v1/projects/<PROJECT_ID>/messages:send
Authorization: Bearer <OAuth2 access token minted from the service account>
{ "message": {
    "topic": "atomfx-signals",
    "notification": { "title": <title>, "body": <body> },
    "data": { "type":"gold_signal|level_alert", "pair":"…", "direction":"…", "deeplink":"atomfx://pair/EURUSD" },
    "android": { "priority":"high", "notification": { "channel_id":"atomfx_signals" } } } }
```

- `send_push` is a **drop-in for `send_telegram`** at the frozen call-sites in `scan_h1.py` (§5.2). Same message text; the gold-signal and level-alert conditions are unchanged.
- Telegram may remain wired as an **optional fallback** (guarded by whether its secrets exist) but push is the primary and only required transport. If Pieter later wants both, it's a one-line `or`.
- OAuth token minting: use Google's token endpoint with the service account (a ~30-line pure-`urllib` helper, no heavyweight SDK, consistent with the reference's dependency-light style).

**Client side (Android).** Firebase Messaging SDK; `google-services.json` in `app/`; a `FirebaseMessagingService` that subscribes to `atomfx-signals` on first launch; a high-importance notification channel `atomfx_signals`; deep links (`atomfx://pair/<PAIR>`, `atomfx://regime`) that open the relevant bottom sheet. Notification content mirrors the existing alert wording so a returning user sees the same information they get today.

**Secrets summary (GitHub → Settings → Secrets → Actions):**
`TWELVEDATA_KEY`, `ANTHROPIC_API_KEY`, `FCM_SERVICE_ACCOUNT` (JSON), `FCM_PROJECT_ID`. (`TELEGRAM_*` optional/legacy.)

---

## 8. The Android app (Part C)

### 8.1 Stack & principles

- **Language/UI:** Kotlin + Jetpack Compose (Material 3). Min SDK 26, target current stable.
- **Theming:** dark **and** light (design doc §2), driven by `isSystemInDarkTheme()` with a stored user override (`system | dark | light`). One `AtomColors` token set exposed via `CompositionLocal`; the wheel `Canvas` takes the resolved token set as a parameter so it repaints on theme change.
- **Architecture:** MVVM + unidirectional data flow. One `SignalsRepository` → `WheelViewModel` exposes an immutable `WheelUiState` → Compose renders. No business/trading logic in the app — it maps `signals.json` to pixels.
- **Async:** Kotlin Coroutines + Flow. **DI:** Hilt (or a hand-rolled container if Pieter prefers minimal deps — flag as a choice, default Hilt).
- **Serialization:** `kotlinx.serialization` with a schema mirroring §4, tolerant of missing keys (`@Serializable`, nullable + defaults).
- **The wheel is a `Canvas`**, not a view hierarchy of shapes and not an embedded SVG. Rationale: the radial animations, generous ring hit-targets, and factor-marker geometry (design doc) are cleanest as drawn geometry with `pointerInput` hit-testing.
- **No WebView, no candlestick charts.** The only price visual is a **simplified close-price line chart** (D1/H4/H1) drawn natively in Compose from the new `spark` field (Functional Spec §8). This removes the LightweightCharts WebView island from the earlier plan entirely.
- **The app needs no market-data or AI keys.** Twelvedata and Anthropic keys live server-side as GitHub secrets; the app only reads `signals.json` and receives FCM push. The one optional on-device credential is a GitHub PAT, used *only* if the user enables synced price-level alerts (Functional Spec §9).

### 8.2 Module / package layout

```
app/src/main/java/…/atomfx/
├── data/
│   ├── remote/SignalsApi.kt            # GitHub raw fetch (Retrofit/OkHttp or Ktor)
│   ├── model/Signals.kt … Potential.kt # @Serializable mirror of §4
│   ├── SignalsRepository.kt            # fetch + cache + freshness/staleness
│   └── FcmService.kt                   # topic subscribe + deep-link routing
├── domain/
│   └── WheelMapper.kt                  # signals.json → WheelUiState (pure, no trading logic)
├── ui/
│   ├── nav/    AppScaffold.kt        # bottom nav (4 tabs) + HorizontalPager (swipe)
│   ├── wheel/  WheelScreen.kt WheelCanvas.kt WheelGeometry.kt WheelAnim.kt
│   ├── currency/ CurrencyScreen.kt CurrencyWheel.kt CurrencyDetailSheet.kt   # pure consumer of csm/csm_delta/breadth — no new backend
│   ├── macro/  MacroScreen.kt        # archetype banner + bias + evidence axes + cross-asset dashboard
│   ├── insights/ InsightsScreen.kt   # recommendation + theme-tagged news + calendar + brief
│   ├── sheets/ RegimeSheet.kt FlowSheet.kt BreadthSheet.kt MomentumSheet.kt
│   │           StructureSheet.kt EntrySheet.kt PairSheet.kt
│   ├── components/ Pills.kt StatusStrip.kt TradeableNow.kt BottomSheetScaffold.kt
│   ├── chart/  LineChart.kt (native Compose sparkline, D1/H4/H1 — no candles, no WebView)
│   ├── settings/ SettingsScreen.kt
│   └── theme/  Color.kt Type.kt Motion.kt   # tokens from the design doc
├── notif/ NotificationChannels.kt
└── MainActivity.kt
```

`WheelGeometry.kt` implements the deterministic geometry from the design doc (§ wheel geometry): fixed angle per pair `angle = index·30° − 90°`, radii per level, polar→cartesian. **Angles are constant; only radius (level) changes** (spec §4, §63). The app must never re-rank angular positions.

### 8.3 State → pixels mapping (no recomputation)

`WheelMapper` consumes `potential`, `currency_flow`, `regime_*`, `pairs`, `recommendation` and yields:
```
WheelUiState(
  nucleus = NucleusState(regimeLabel, strength, score, confidence, flowLine, recommendationHeadline),
  nodes   = List<PairNode>(pair, angleDeg, level, state, direction, potential, factors, blockedAt, haloTop),
  rings   = 6 ring descriptors (label, active colour derived from aggregate state),
  strip   = StatusStrip(regime, leader, laggard, breadthBand, topPair),
  tradeable = List<TradeablePair> (only level-6),
  freshness = Fresh|Stale|Unavailable
)
```
Every field above is a copy or trivial format of a backend value. If the app ever needs a number that isn't in `signals.json`, the fix is an EXTEND field (§5), not a Kotlin calculation.

### 8.4 Data fetching, freshness, offline

- Fetch `signals.json` from GitHub raw on: cold start, resume, manual pull-to-refresh, and a **light interval aligned to the hourly scan** (do not poll aggressively — spec §59). Suggested: refresh on resume + every ~10 min while foregrounded, plus on push receipt.
- Cache last-good JSON locally (DataStore/file). On fetch failure, render cached data flagged **stale** (spec §58) — never blank, never invented (spec §57).
- `updated` older than the expected scan interval ⇒ show `DATA STALE`. Absent file ⇒ `DATA UNAVAILABLE`. `schema_version` newer than the app knows ⇒ soft "update recommended".

### 8.5 Preserved reference functionality (spec §60)

The wheel is the landing view, but nothing analytical from FX Signal Board is lost — it moves into sheets/panels/secondary screens: the simplified 3-TF line chart (replacing the candlestick overlay — no candles per Pieter), price-level alerts, calendar, catalyst, the full macro-asset dashboard, the macro archetype engine, week ahead, correlation, CSM detail, daily brief. The Functional Spec places every element on a specific surface; the Design doc styles each one.

---

## 9. Build phases (align Claude Code to this order)

The reference brief's Phase plan (spec §67) is adapted to the native app. **Ship each phase working before starting the next; the app must run at every phase.**

- **Phase 0 — Repo & pipeline.** Fork scanner into `atom-fx`; port `scan_h1.yml`/`scan_news.yml`; confirm `signals.json` still produced identically to the reference (byte-diff the frozen keys). No app yet.
- **Phase 1 — EXTEND backend.** Add `csm_delta`, `currency_flow`, `breadth`, structure exposure, `potential`, `spark` (line-chart closes), and `schema_version`. Verify frozen keys unchanged (diff test §10). No AI yet.
- **Phase 1b — Macro archetype engine.** Add `macro_regime.py` (the ten-regime classifier + gold/USD overlays + distinct-axis confidence, Functional Spec §6) and its `macro_regime` output. Deterministic; reads `macro_assets` only.
- **Phase 2 — Static wheel.** Compose `Canvas` wheel from **mock** `WheelUiState`: geometry, six rings, 12 nodes at fixed angles, radial paths, factor markers, nucleus. Verify layout/responsiveness/touch on an Android device. (spec §67 Phase 1.)
- **Phase 3 — Wire real data.** `SignalsRepository` + `WheelMapper` from live `signals.json`; freshness/stale/offline states.
- **Phase 4 — Bottom sheets.** Six factor sheets + pair sheet with the six-factor "WHY?" checklist (design doc). Draggable sheet (collapsed/half/expanded) with the wheel visible behind at half.
- **Phase 5 — Panels, pills, tradeable-now, status strip.** Edge panels (recommendation, calendar), scrolling pills, "Tradeable Now" / "Watch" / "No A+ setups".
- **Phase 6 — Push.** `send_push.py` + FCM client; same firing criteria; deep links open the right sheet.
- **Phase 7 — Recommendation engine v1.** `recommendation.py` + recommendation panel/nucleus line.
- **Phase 8 — Animations & polish.** Radial advance/retreat easing (400–700 ms), top-pair halo, subtle high-potential breathing, staggered multi-node moves (spec §28–§29, §56). Android safe-areas, `100dvh`-equivalent insets.
- **Phase 9 — Line chart, macro screen & preserved features.** Native 3-TF line chart from `spark` (no candles); the Macro screen (archetype banner + cross-asset dashboard + gold/USD overlays); calendar panel, correlation view, CSM detail, price-level alerts (optional PAT).
- **Phase 10+ (post-v1) — Journal.** Pre-trade thesis + post-trade review, mapping the handbook's Appendices C/D (Functional Spec §12).

---

## 10. Verification & acceptance

**Rule #1 regression test (must pass at every backend change).** A CI step runs the scanner on a fixed OHLCV fixture and asserts that every **frozen key** in `signals.json` is byte-identical to a checked-in golden file. EXTEND keys are excluded from the equality check (they're allowed to appear). If a frozen key changes, the build fails — this is the automated guardian of Rule #1.

**Potential-engine unit tests.** Given crafted `signals.json` inputs, assert the sequential level logic (spec §51–§53): the EURUSD-all-pass ⇒ level 6; the AUDJPY structure-fail ⇒ level 4 `watch`; the USDCHF regime-fail ⇒ level 0 `low` even with good momentum/structure.

**Consistency test.** The six factor names, order, colours, and state bands are identical between this document, `potential_config.py`, and the design doc. (Verified in task 4 of this build.)

**UX acceptance (spec §69).** The finished landing view answers, with no panel open: current regime; strong/weak; leading currency; weakening currency; highest-potential pairs; developing pairs; ignorable pairs. One tap on a pair answers: why attractive; which factors support; which factor blocks advancement; is entry location good. One tap on a ring answers: what is happening at that analytical layer. If all twelve are answerable elegantly, the redesign is successful.

---

## 11. Open questions / deferred (not v1)

- Multi-device push token strategy if Pieter adds devices (topic already scales; no change needed unless per-device targeting is wanted).
- Whether D1 regime should ever satisfy the REGIME factor when H4 is Ranging (currently strict H4; `REGIME_SOURCE` config exists to revisit).
- Historical sparklines for momentum/CSM (needs a small history file; additive; spec §22 "if historical values available").
- Full per-pair AI reasoning in the recommendation engine (roadmap §6).

*End of Architecture Document.*
