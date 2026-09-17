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
- **%B** — 0–100, where price sits inside its own 12-period D1 Bollinger Bands (50 = middle band,
  0/100 = the outer bands; can read past 0 or 100, a real "walk along the band"). A 12-period SMA
  of %B itself is its signal line. This D1 closes 17:00 New York (retail-platform convention),
  **not** the UTC-midnight D1 every other signal in the app uses — `bb_touch.py`'s own
  `_d1_ny_close()`, added 2026-09-10 after a live mismatch against LiteFinance's own %B reading.
  `pairs.<PAIR>.bb_d1.pctb`/`.pctb_sma`, long-press a wheel node (ChartSheet) — moved off the pair
  sheet's Breakdown tab 2026-09-10.
- **Board %B** — the market-wide average of every pair's own %B/signal line. `signals.json` key
  `percent_b_board`, computed every scan — **no UI surface any more** (lived on the Insights tab's
  Market Indicators card until that card was retired 2026-09-10, see "Whole Market Indicators"
  below). **Not currency-direction aware** — it averages every pair's raw %B with no regard for
  which side is base vs. quote, so a falling reading can mean opposite things pair to pair
  (EUR/USD falling = USD strong; USD/CAD falling = USD weak). See Currency %B below.
- **Currency %B** — the currency-direction-corrected sibling to Board %B (2026-09-10, Pieter's own
  catch), one line+signal per currency (same 8 as CSM). Built the same way CSM's own
  `compute_csm_d1` corrects currency strength from a mixed pair set, mirrored around 100 instead
  of negated (since %B is a 0-100 position, not a signed return). `signals.json` key
  `percent_b_currency`. Shown long-press a wheel node (ChartSheet) — the pair's own two currencies
  (base + quote), right alongside that pair's own %B, so a currency-level "USD looks fragile" read
  can be checked against whether the pair's OTHER leg agrees before treating it as a trade
  candidate. (It briefly also had an Insights-tab surface, same day — retired with the rest of
  "Whole Market Indicators" below, once this pair-level one proved to be the useful one.)

## The wheel

**(Updated 2026-09-17 — this section previously described the pre-Simplification-Rework wheel:
a six-confluence-ring design with a separate Currency Strength Wheel. That design was retired
2026-09-05/06/09; see `ATOM_FX_BUILD_STATUS.md` §B for the history if you're reading an old
mockup or chat log that still uses the terms below this note.)**

- **Energy Wheel / pair wheel** — the one landing wheel (there is no second, currency-only wheel
  any more): 12 pairs at **fixed angles** (angle = identity, never re-ranked), wedge **radius =
  the selected wing's value** for that pair. Four selectable wings, chosen via the 4 corner
  buttons: **Setup** (Continuation Score, all TFs), **Trend** (ADX, fixed H4), **Momentum**
  (H4 momentum oscillator, fixed H4), **Volatility** (ATR percentile, fixed D1).
- **Currency strength** — has no wheel of its own and no on-dial mode on the pair wheel (both
  retired 2026-09-04). Shown instead in the always-on `CsmBarStrip` below the wheel, with its
  own Strength/Flow toggle.
- **Nucleus / hub** — the wheel's centre: shows the **D1 Regime** (moved here from H4, 2026-09-09).
- ~~Ring / Factor markers / Level~~ — the old six-confluence-factor system (R·F·B·M·S·E rings,
  a 0–6 "level" count). Retired from the visible wheel in the Simplification Rework; the
  underlying `Factor` enum and `PairNode.level/state/factorsPassed/blockedAt` fields still exist
  in `WheelUiState.kt` as legacy/mostly-dead code (`factorsPassed` is still read for one glyph
  dot color; the rest has no UI reader left) — don't build new behavior on these, use `cont`.

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

## Whole Market Indicators — retired 2026-09-10

Was a card on the Insights tab: four concept reads (Relative Rotation, Confidence Index, Breadth
Thrust, %B/Currency %B) picked one at a time via a "+ &lt;current&gt;" fan-out button — "tried
live so Pieter could compare them before any one earns a permanent home." Once Currency %B proved
useful, the whole card was removed rather than trimmed (Currency %B already had its permanent
home on ChartSheet by then; the other three had nowhere else to go, so they left the app with it).
Terms kept here for anyone chasing old references:

- **Relative Rotation** (`signals.json` key `rotation`) — a quadrant map of every currency's CSM
  (h4) against its CSM Delta (h4): Leading/Weakening/Lagging/Improving. Named for the real
  methodology it's modelled on (Relative Rotation Graphs). Backend still computes it; unused by
  the app.
- **Confidence Index** (`signals.json` key `pulse`) — a 0–100 composite of Breadth unanimity,
  Regime evidence-axis clarity, CSM dispersion percentile, and BB width expansion. Genuinely
  bespoke, no real external pedigree. Backend still computes it; unused by the app.
- **Breadth Thrust** (`signals.json` key `breadth_thrust`) — count of currencies breadth-strong
  minus breadth-weak, -8..+8, the FX analogue of a stock-market advance/decline line (incl. the
  Zweig Breadth Thrust). Backend still computes it; unused by the app.
- **%B / "Board %B"** — see its own entry above (Scores & measurements); superseded in the app by
  Currency %B.

## Macro

- **Macro archetype / named regime** — one of the ten handbook regimes A–J (e.g. *Growth-positive
  risk-on*, *Growth-scare risk-off*, *Liquidity stress*). Produced by `macro_regime.py`. App
  names (2026-09-10) deliberately diverge from the handbook's own A-J vocabulary — see
  Functional Spec §6's regime table for the full name-for-name cross-reference and rationale.
- **Evidence axis** — a group of correlated indicators counted as ONE piece of evidence
  (Risk · Rates · USD · Commodity · Safe-haven). Confidence = how many distinct axes agree.
  2026-09-10: also margin-aware now — a tie with the runner-up archetype on distinct axes
  force-caps confidence to Low, regardless of the absolute count (a tie is the ambiguous case,
  not a weaker clear win). Surfaced on the Macro screen as a named callout, not just a pill.
- **News corroboration** — `macro_regime.news_corroboration` (2026-09-10), "confirmed" /
  "price_only" / "unknown". Whether `scan_news.py`'s own `tag_theme()` headline-axis tags touch
  any of the primary archetype's own supporting axes — closes the gap between "the price
  pattern is real" and "the price pattern is what the news is actually about."
- **Gold overlay** — *defensive gold* vs *diversification gold*.
- **USD regime** — rate dominance · growth dominance · global risk-off · confidence shock.

## Data & change tiers

- **`signals.json`** — the one data document the app consumes. Frozen keys + additive extend keys.
- **FROZEN / EXTEND / NEW** — the three change tiers (see INDEX.md). Rule #1 = FROZEN never changes.
- **Spark** — the compact recent-closes array per pair per TF, for the simple line chart.
- **Regime pill** — a pair's D1/H4/H1 directional read: `bull` · `bull_strong` · `neutral` · `bear` · `bear_strong`.

## Navigation

- **Bottom nav** — the three top-level tabs: **Wheel (Home) · Macro · Insights** (swipeable via
  `HorizontalPager`). There is no separate Currency tab — currency strength lives in the
  `CsmBarStrip` on the Wheel tab (see "The wheel", above). Corrected 2026-09-17; this previously
  said four tabs including Currency, which hasn't existed since the 3-tab nav shipped.
- **Bottom sheet** — a detail surface that rises above a tab (pair sheet, currency/cross-asset
  sheet, calendar, etc.).
- ~~Tradeable Now / Watch~~ — retired 2026-09-04. That job is now the status-strip glyph row
  (one small glyph per pair in `signals.ranked.top`) plus the wheel's own Setup wing — there is
  no standalone pill band any more.
- **Sessions** (added 2026-09-17) — the header clock icon (`SessionsSheet.kt`), a side panel
  showing the four FX trading sessions (Sydney/Tokyo/London/New York): a 24h rolling timeline,
  each session's exact open/close in the device's own local time, and a live countdown. Its dot
  lights on a session **overlap** (London-NY or Tokyo-London) specifically, not just "any session
  open." Pure clock feature — no `signals.json` field backs it.
