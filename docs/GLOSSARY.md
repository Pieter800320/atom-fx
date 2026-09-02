# ATOM FX — Glossary

Use these terms **verbatim** in code, comments, UI copy, and commit messages. One
name per concept; do not introduce synonyms. (Claude Code: match these exactly.)

## Scores & measurements (do not confuse these)

- **Potential** — the wheel's primary number, 0–100, derived from how many of the six
  confluence factors a pair has passed. Radius on the pair wheel encodes it. *Not* the
  same as Setup Rank.
- **Setup Rank** — the frozen deterministic score, 0–10, from `rank.py`. Shown in the
  pair sheet. An independent measurement from Potential.
- **Continuation score** — frozen, 0–100, multi-timeframe technical alignment (`cont_score.py`).
- **CMP** — Composite Momentum Position, 0–100, the primary single momentum reading.
- **MOM1212** — the momentum oscillator (D1/H4/H1), 0–100, with per-TF deltas.
- **CSM** — Currency Strength Model, 0–100 per currency, over 8 currencies / 16 pairs, D1/H4/H1.
- **CSM Delta** — change in CSM over a defined lookback: "getting stronger/weaker" (the flow signal).
- **Breadth** — how many of a currency's relationships agree with its move (broad vs narrow).

## The wheels

- **Energy Wheel / pair wheel** — the landing wheel: 12 pairs at **fixed angles** (angle = identity),
  **radius = Potential**, six confluence rings, factor-marker dots, nucleus, halo.
- **Currency Strength Wheel / currency wheel** — the companion: 8 currencies at fixed angles,
  **radius = CSM strength**, risk bloc on top, defensive bloc on bottom.
- **Nucleus** — the pair wheel's centre: the market state (regime). Tappable → Regime/Macro sheet.
- **Ring** — one of the six confluence levels: **1 Regime · 2 Currency Flow · 3 Breadth · 4 Momentum · 5 Structure · 6 Entry Setup** (this order, these names).
- **Factor markers** — the six dots along a pair's radial path (R·F·B·M·S·E); bright = passed.
- **Level** — 0–6, the count of consecutive factors a pair has passed (0 = nucleus).

## Potential states

- **LOW** (levels 0–2) · **WATCH** (levels 3–5) · **TRADEABLE** (level 6) · **A+** (level 6 and Setup Rank ≥ 8.5).

## Currency flow

- **Flow leader** — the currency with the strongest positive CSM Delta (getting stronger fastest).
  This is what the app shows as "leading" (header flow line, Status Strip, Currency Flow sheet,
  the wheel's Currency Flow ticker).
- **Laggard** — the negative equivalent (weakening fastest).
- **Absolute leader / absolute laggard** — the currency with the highest/lowest absolute CSM
  right now, distinct from flow leader/laggard. Still computed by the backend and present in
  `signals.json` (`currency_flow.absolute_leader/absolute_laggard`), but Pieter dropped it from
  the UI (2026-09-02) to keep the Currency Flow sheet and ticker to one clear number per
  currency — flow leader/laggard only. Don't resurface it without checking with him first.

## Macro

- **Macro archetype / named regime** — one of the ten handbook regimes A–J (e.g. *Growth-positive
  risk-on*, *Recession shock*, *Liquidity shock*). Produced by `macro_regime.py`.
- **Evidence axis** — a group of correlated indicators counted as ONE piece of evidence
  (Risk · Rates · USD · Commodity · Safe-haven). Confidence = how many distinct axes agree.
- **Gold overlay** — *defensive gold* vs *diversification gold*.
- **USD regime** — rate dominance · growth dominance · global risk-off · confidence shock.

## Data & change tiers

- **`signals.json`** — the one data document the app consumes. Frozen keys + additive extend keys.
- **FROZEN / EXTEND / NEW** — the three change tiers (see INDEX.md). Rule #1 = FROZEN never changes.
- **Spark** — the compact recent-closes array per pair per TF, for the simple line chart.
- **Regime pill** — a pair's D1/H4/H1 directional read: `bull` · `bull_strong` · `neutral` · `bear` · `bear_strong`.

## Navigation

- **Bottom nav** — the four top-level tabs: **Wheel · Currency · Macro · Insights** (swipeable).
- **Bottom sheet** — a detail surface that rises above a tab (pair sheet, factor sheets, currency detail).
- **Tradeable Now / Watch** — the ranked pill band under the pair wheel.
