# ATOM FX — Research Log

The single source of truth for backtesting methodology and results. Work happens across
many chat sessions; chats are disposable, this file is permanent. **A new session reads this
file first to get current — then we plan the next experiment and hand Claude Code a spec.**

## How to resume in a new chat
1. Point Claude at the repo, on the `research` branch.
2. "Read docs/RESEARCH_LOG.md." That's the whole state.
3. Plan the next experiment in chat; Claude Code implements from a spec.

## Invariants (these do not change)
- **Rule #1 — frozen scanner.** Everything in `scanner/*.py` is a frozen verbatim fork. New
  code lives only in `scanner/extend/` and imports frozen functions read-only. The golden test
  `tests/test_rule1_frozen.py` enforces this and must stay green.
- **Candle fidelity.** Fetch H1 only (Twelvedata, up to 5000 bars); aggregate D1/H4 locally.
  D1 closes at 17:00 New York, not UTC midnight (measured ~3-4% EMA disagreement justified it).
  FX-week filter: drop Saturday bars, Sunday before 17:00 ET, and Friday from 17:00 ET.
  Wilder smoothing for RSI/ATR/ADX; EMA via ewm(adjust=False).
- **Backtester rigor.** No look-ahead (point-in-time / rolling window only). Out-of-sample and
  walk-forward required before any strategy is trusted. Costs modelled honestly within OHLC
  bounds. Every run records entry/exit timestamps so time-splits are always possible.
- **No tuning to a sample.** Parameters are NEVER adjusted to make one historical run look
  better — that is overfitting, not evidence. Any parameter change is its own pre-registered,
  out-of-sample experiment.
- **Pre-registration.** Before a run: write the hypothesis, the exact params, and the pass/fail
  criteria into the registry below. Then run. This is what prevents moving the goalposts.
- **One change at a time, measured.**

## Standard workflow
1. Pre-register the experiment (row in the registry, status = active).
2. Run: `set TWELVEDATA_KEY=...` then `py -m tools.backtest_trend_pullback` (or the strategy's
   runner). Never paste the key into chat or commit it.
3. Diagnostics: `py -m tools.analyze_backtest_trades`.
4. Save results to `data/backtest/<strategy>_<YYYY-MM>/` (params.json, trades.csv,
   diagnostics.txt, summary.md). Commit.
5. Record the verdict in the registry.

## Experiment registry
| # | Strategy | Hypothesis | Status | Result | Verdict | Pointers |
|---|----------|-----------|--------|--------|---------|----------|
| 1 | Trend-pullback (H1 exec, H4/D1 confirm) | H1 pullback-continuation entries in the D1-trend direction have positive expectancy across majors | CUT | 95 trades, +0.22R avg, PF 1.28; +0.05R excl. top trade, −0.21R excl. top 3; edge entirely JPY (+1.41R vs −0.35R non-JPY); 79% full-stop rate | Not a robust edge — single-trade artifact + JPY-regime concentration; un-executable by hand | tag `archive/trend-pullback-research`; `data/backtest/trend_pullback_2026-09/` |
| 2 | Crowded Market reversal indicator (Pine port, `pine/crowded_reversal.pine` on `main`) | The indicator's 0-100 confluence score predicts reversals: the barrier-race reversal rate rises with the score, across all bars | RUN 2026-09-19 (once, no changes after pre-registration) | Gradient slope per +20 score points: filter ON (Pine default) +0.026, CI -0.002..+0.059; filter OFF +0.041, CI +0.018..+0.070. Within-stratum rho 0.023 / 0.041. ADX filter effect (on minus off) -0.015, CI -0.026..-0.004. Flagged events (secondary): 25 / 32, inconclusive | Pine default (ADX suppress): **FAIL**, marginally (CI gate, best-single-factor gate). Filter OFF: **PASS** all 7 gates, but a small effect and not clearly better than the best single factor. Evidence AGAINST the ADX filter. | see "Experiment 2" below and "Result"; `scanner/extend/{crowded_reversal,barrier_race,score_gradient}.py`; `tools/backtest_crowded_reversal.py` |
| 3 | Crowded Market flags (score >= 60) on H4 — Pieter's chart observation | On H4 (app's UTC blocks, filter off), flagged bars reverse more often than like-for-like baseline bars | PRE-REGISTERED 2026-09-19, not yet run | — | — | see "Experiment 3" below; `scanner/extend/agg_h4.py`; `tools/build_h4_store.py` |

## Experiment 2 — Crowded Market reversal indicator (pre-registration, 2026-09-19)

**Written and committed BEFORE any score-gradient number has been computed on real data.**

### What the indicator is
`pine/crowded_reversal.pine` (methodology: `pine/CROWDED_REVERSAL_METHODOLOGY.md`): eight
boolean factors (%B, z-score, ATR stretch, RSI + RSI tier, regular divergence, volatility
climax, COT positioning) each add a fixed weight to a top score and a bottom score (0-100), and a
debounced ADX >= 30 + EMA200-slope regime filter can zero the counter-trend side. Ported factor-by-factor
to Python (`scanner/extend/crowded_reversal.py`, 36+ tests). All Pine defaults, unchanged — nothing here is fit.

### Hypothesis
H1: within a pair and a side, the probability that price REVERSES rises with that side's score.
H0: no relationship. Direction of the expected reversal: bottom score -> up, top score -> down.

### Definition of "reversal" (the barrier race — unchanged from the plan Pieter approved)
From a bar's close, with A = ATR(14) at that bar, barriers at close +/- 1.0 x A; walk the next
20 bars' high/low. Bottom side: up-barrier first = reversal. Top side: down-barrier first = reversal.
A bar touching BOTH barriers cannot be sequenced from D1 OHLC and resolves as CONTINUATION; no barrier
within 20 bars = timeout = NOT a reversal (kept in the denominator). The same function scores every bar.
Bars without 20 forward bars, or without ATR, are excluded everywhere.

### Primary test — the score gradient (this replaced the flagged-event test as primary; see "Why")
- Observations: every valid bar x {bottom, top}. x = that side's score (0-100), y = reversal (0/1).
- **Slope** = pooled within-(pair x side) OLS slope of y on x, reported per +20 score points.
  Fixed effects mean a pair's drift or up/down asymmetry cannot produce a fake gradient.
- **rho** = within-stratum correlation of y with x (scale-free; used to compare predictors).
- **Buckets** (fixed edges, left-closed): [0,10) [10,20) [20,30) [30,40) [40,50) [50,60) [60,100];
  reversal rate, like-for-like baseline (the stratum's own mean), lift.
- **Inference:** month-clustered bootstrap, 5000 resamples, seed 20260919. Calendar months are resampled
  with replacement and every statistic, including each stratum's baseline, is recomputed from the resample.
  The SAME resamples are used for every statistic, so all differences below are paired.
- **Arms:** regime filter `suppress` (Pine default) and `off`. Same thresholds, weights, data.

### Gates (per arm; ALL must hold for PASS, else FAIL; fixed now, never adjusted after seeing results)
1. Evaluation window >= 48 calendar months, otherwise INCONCLUSIVE.
2. Slope per +20 points **>= 0.02** (~ +6 points of reversal probability at a score of 60).
3. 95% CI lower bound of the slope **> 0**.
4. Slope **> 0 in both halves** of the evaluation window (split at the middle month).
5. Slope **> 0 excluding the JPY crosses**.
6. Slope **> 0 in every leave-one-pair-out** run (no single pair carries the result).
7. Composite rho **beats the best single factor's rho** (each factor's own weighted contribution on the
   same 0-100 scale), point estimate; the paired CI of the difference is reported.

### The ADX question (Pieter distrusts ADX as a reversal filter)
Decided by the data, by rule: slope(suppress) - slope(off), paired month-clustered 95% CI.
CI lower > 0 -> the filter improves the gradient (evidence for keeping it). CI upper < 0 -> it worsens it
(evidence against). CI includes 0 -> **no demonstrated value**: the data cannot justify the filter, and it
stays an untested assumption rather than a validated component. No arm is chosen post hoc.

### Secondary (informational, no gates): flagged events at score >= 60
De-clustered (a new flag opens no event until the previous event's race has resolved), per pair; like-for-like
measured baseline (same pair, same direction, same window, same tie/timeout rules); lift and month-clustered CI.
Read as INCONCLUSIVE below 100 events. Pre-registered expectation: it will be below 100 (see below).

### Data
- Price: Twelvedata H1, paged backwards, aggregated to the 17:00 America/New_York-close D1 with
  `scanner.extend.agg_nyclose` (same code as the live scanner) -> `data/d1_nyclose_long/`,
  `tools/fetch_d1_deep.py`. Depth is the provider's: EUR/USD H1 begins 2020-01-30 (404 before), ~6.6 years.
  Evaluation starts 260 bars after each pair's first bar (EMA200/ADX/z warm-up) and only where the COT
  percentile exists.
- COT: CFTC Legacy futures-only, Non-Commercial long/short, 8 contracts, 2006-2026
  (`tools/fetch_cot_legacy.py` -> `data/cot_legacy/legacy_nc.csv`, sha256 recorded in params.json).
  Alignment: bar in calendar week k reads the report with as-of date = Tuesday of week k-1 (published
  Friday k-1) — never a report dated in week k or later (unit-tested). COT percentile: 156 weeks, computed on a
  Mon-Fri calendar as the Pine does; reproduced TradingView's diagnostic label to ~0.3 percentile points on
  2026-09-19 (net positions matched exactly).
- 12 pairs: EURUSD GBPUSD USDJPY USDCAD NZDUSD AUDUSD USDCHF EURJPY GBPJPY CADJPY AUDJPY NZDJPY. D1 only.
  H4 is out of scope for this experiment (no deeper intraday history than ~3 years is available).

### Why the primary test changed from the flagged-event test (disclosed, before any gradient run)
1. Count-only estimate (no outcomes) on 19 years of UTC daily data: ~41 (filter on) / ~91 (filter off)
   de-clustered composite events over 211 pair-years — under the 100-event floor even in the best case,
   and a 5-point lift is undetectable at n = 40-90 (interval about +/-10 points).
2. Twelvedata's daily bars are UTC-based and disagree with the 17:00 NY close (median 13-20 pips per bar;
   `timezone` has no effect on daily bars). On the 2-year overlap the indicator's factors agree on which days
   are "on" only 27-72% of the time, composite score correlation 0.69, and only 1 of 4 flags coincided. UTC
   daily bars would test a different indicator than the one on TradingView — so they are NOT used.
3. Hence: NY-close history only (~6.6 yr), and a test that uses every bar rather than a rare threshold.
Approved by Pieter 2026-09-19 (option C).

### What has already been seen (full disclosure)
- The plumbing smoke run on the ~2-year cache showed 4 flagged events (2 reversal, 2 continuation) — a
  plumbing check by design, recorded as such in `data/backtest/crowded_reversal_smoke_2026-09/`.
  That window overlaps the last 2 years of this experiment's window.
- The count-only power estimate and the flag-agreement measurement described above (no outcomes).
- NO score-gradient statistic, bucket table, or slope has been computed on any real data before this entry.
- A plumbing smoke of the gradient code on the ~2-year cache WILL be run after this commit, labelled
  PLUMBING ONLY, and will not change any parameter or gate above.

### Known limits (stated in advance)
One macro-regime era (~2020-2026, incl. the 2022 USD surge) — a pass says nothing about earlier regimes.
Bars overlap, so observations are serially dependent; the month-clustered bootstrap absorbs same-month
dependence but not every cross-pair dependency (shared USD/JPY legs, shared COT legs). D1 OHLC cannot
sequence intrabar (tie -> continuation, applied to every bar alike).

### Result (run once on 2026-09-19; code + data at commit `bac23ac`; outputs in `data/backtest/crowded_reversal_exp2_2026-09/`)
Window: 68 months (2021-01 .. 2026-09), 12 pairs, ~35,000 observations per arm. Nothing was changed between
the pre-registration commit (`34a4d73`) and this run; the 2-year smoke run did not alter any parameter or gate.

| | filter ON (Pine default) | filter OFF |
|---|---|---|
| slope per +20 pts (95% CI) | +0.026 (-0.002 .. +0.059) | +0.041 (+0.018 .. +0.070) |
| rho | 0.023 | 0.041 |
| halves / ex-JPY / worst leave-one-out | +0.003, +0.052 / +0.037 / +0.023 | +0.032, +0.058 / +0.046 / +0.038 |
| rho vs best single factor (`stretch`), paired CI | -0.0086 (-0.032 .. +0.016) | +0.0092 (-0.010 .. +0.029) |
| **verdict (pre-registered gates)** | **FAIL** — CI lower bound, best-single-factor | **PASS** — all 7 gates |

**ADX filter, by the pre-registered rule:** slope(on) - slope(off) = -0.015, 95% CI -0.026 .. -0.004 -> the CI is
entirely below 0: the filter WORSENS the gradient. Zeroing the counter-trend side's score inside strong trends
made the score a worse predictor of reversal — consistent with those bars carrying real reversal information
(not directly tested: this run did not isolate the suppressed bars' own reversal rate).

**How to read this — what it does and does not show:**
- The effect is SMALL. Reversal probability rises about 4 points per +20 score points (filter off); the scores of
  10-30 sit ~4 points above baseline (rate ~53% vs ~49%), and the [0,10) bucket (almost all bars) is slightly
  below it (-0.9%).
  Symmetric barriers make ~50% the null, so this is ~50% -> ~54% for a typical scored bar. It is a statistical
  tendency, NOT a demonstrated profitable edge: no costs, sizing, stops, or entry rules were tested.
- The dose-response is not clean at the top: buckets 30-60 are noisy and non-monotonic, and the >=60 bucket
  (57 observations, ~32 de-clustered events) shows +12.6 points with a CI of -2.2 .. +33.8 — consistent with the
  gradient but not evidence on its own. The secondary flagged-event test (25 / 32 events) is inconclusive, as
  pre-registered.
- The composite is NOT clearly better than its best single factor: rho 0.041 vs 0.032 (`stretch`), paired CI
  straddling 0. It passes gate 7 by point estimate only. The extra machinery has not demonstrated it earns its keep.
- Two arms were pre-registered; the passing one is the non-default. One of two, on one macro era (2021-2026,
  including the 2022 USD surge), with overlapping bars — treat the pass as suggestive, not settled.
- Not tested: H4, any threshold other than the Pine defaults, other pairs, earlier regimes, live forward data.

### Reproduce
```
py -m tools.fetch_cot_legacy 2006
set TWELVEDATA_KEY=...   (never in chat or git)      py -m tools.fetch_d1_deep
py -m tools.backtest_crowded_reversal --tag exp2_2026-09 --mode full --data-dir data/d1_nyclose_long
```

## Experiment 3 — Crowded Market flags on H4 (pre-registration, 2026-09-19)

**Written and committed BEFORE any H4 outcome (reversal, lift, slope) has been computed on real data.**

### Origin
Pieter, 2026-09-19, from looking at TradingView charts: the red/green top/bottom FLAGS "seem to work well on both
D1 and H4, especially in ranging markets. If one trades back to the previous support/resistance, it often
succeeds (seemingly at a glance)." Only the flags (score >= 60) would go into the app. This is a hypothesis from
chart hindsight — exactly what the methodology doc says is not validation — so it is tested here. What is and is
not testable now:
- flags work on **D1**: already tested (Experiment 2, secondary) — 25-32 events, lift +15-16 points, CI includes 0,
  INCONCLUSIVE (below the 100-event floor). Not re-run.
- flags work on **H4**: TESTED HERE (primary).
- "especially in **ranging** markets": TESTED HERE, exploratory only (no gate).
- "trades back to the previous **support/resistance**": NOT tested — a different outcome (target = a prior level) that
  needs its own pre-registered definition; candidate Experiment 4.

### Hypothesis (primary)
H3: on H4, with the regime filter OFF (the Pine default since 2026-09-19), bars where the top or bottom score first
reaches >= 60 (rising edge) are followed by a reversal more often than like-for-like baseline bars.
H0: no difference. Direction: bottom flag -> up, top flag -> down.

### Reversal definition — identical to Experiment 2, in H4 bars
From a bar's close, A = ATR(14) of H4 bars, barriers at close +/- 1.0 x A, walk the next 20 H4 bars (~3.3 trading days).
Bottom flag: up-barrier first = reversal; top flag: down-barrier first. A bar touching BOTH barriers -> CONTINUATION;
no barrier in 20 bars -> timeout -> NOT a reversal (kept in the denominator). Same function for every bar.

### Primary test — flagged events, H4, UTC blocks (the app's convention), filter OFF
Events: rising edge of (top score >= 60) or (bottom score >= 60); a top and a bottom flag on the same bar are skipped
(conflict) and counted. De-clustered per pair: a new flag opens no event until the previous event's race resolved.
Baseline: the same race from EVERY valid bar of the same pair, same direction, same evaluation window, same tie/timeout
rules — measured, never assumed. Lift = flagged reversal rate minus that like-for-like baseline. Month-clustered
bootstrap (5000 resamples, seed 20260919; baseline recomputed inside every resample).

**Gates F1-F6 — ALL must hold for PASS, else FAIL; F1 failing makes the result INCONCLUSIVE, not FAIL:**
- F1: >= 100 de-clustered events.
- F2: lift >= 0.05 (5 points) AND the 95% CI lower bound > 0.
- F3: lift > 0 in both halves of the evaluation window (split at the middle month).
- F4: lift > 0 excluding the JPY crosses.
- F5: lift > 0 excluding the single best pair (the pair contributing most to the lift).
- F6: the composite's flagged lift is greater than the best SINGLE factor's flagged lift (each factor's own rising edge,
  same de-clustering, same baseline) — point estimate.

### Secondary (reported with their gates for information; they cannot rescue a failed primary)
- Gradient replication on H4: Experiment 2's gates G1-G7, unchanged, both arms.
- ADX filter on H4: slope(suppress) - slope(off), paired CI, same decision rule as Experiment 2.
- Flagged events with the filter ON (Suppress), for comparison.
- **Robustness — bar alignment:** the same primary test on NY-aligned H4 (blocks at 17:00, 21:00, 01:00 ... New York
  time — TradingView's FX H4 and consistent with the D1 store). If UTC PASSES and NY fails F1 or F2, the result is
  labelled ALIGNMENT-DEPENDENT and is not treated as a clean pass. NY cannot rescue a UTC failure.

### Exploratory — ranging vs trending (NO gates; cannot promote or rescue anything)
Filter-off score, split by the Pine's own debounced state at the bar: "trending" = ADX>=30 + EMA200-slope regime
(either direction), "ranging" = neither. Gradient slope and flagged lift with CIs, baselines computed inside each
stratum. This is the only test of "especially in ranging markets"; no conclusion is drawn from it beyond what its
CIs support.

### Parameters — all fixed now
Pine defaults, unchanged (weights, thresholds, lengths in bars). Horizon 20 H4 bars, barrier 1.0 x ATR(14). Warm-up
400 H4 bars per pair (~67 trading days: EMA200, ADX, z-score), and a bar counts only where the COT percentile exists.
12 pairs: EURUSD GBPUSD USDJPY USDCAD NZDUSD AUDUSD USDCHF EURJPY GBPJPY CADJPY AUDJPY NZDJPY.
COT: as Experiment 2 — a bar in calendar week k reads the report with as-of date Tuesday of week k-1, by NY-close
trading day (the Sunday reopen rolls into Monday); percentile window 156 weeks on a Mon-Fri calendar.

### Data
- Source: the same Twelvedata H1 history as Experiment 2 (EUR/USD begins 2020-01-30; ~43,100 bars per pair), cached
  raw in `data/h1_cache/` (gitignored; regenerate with `tools/fetch_d1_deep.py`).
- H4 built by `scanner/extend/agg_h4.py` (`tools/build_h4_store.py`): drops closed-market bars with the repo's own
  DECISION-007 FX-week rule, then groups into 4-hour blocks keeping timestamps. **UTC alignment** reproduces the app's
  frozen `scanner.aggregator.aggregate_h4` exactly on open-market hours (unit-tested) and keeps its Sunday-reopen and
  Friday-close STUB blocks (1-3 H1 bars; ~2 per week), as the app does. **NY alignment** has no stubs.
- Stores committed: `data/h4_utc_long/`, `data/h4_ny_long/` (~9,950 / ~9,700 bars per pair).
- Data quirk, disclosed: the provider omits about one H1 bar most Monday mornings (NY blocks starting 01:00 ET have 3
  H1 bars). OHLC of those blocks is otherwise valid; nothing is patched.

### Expected event counts (count-only, no outcomes, before this entry)
Conservative fixed-window de-clustering over ~73 pair-years: composite score >= 60 -> **~152 events with the filter off**,
~79 with it on (UTC); ~171 / ~103 (NY). The primary arm (filter off) is therefore expected to clear F1 (the real rule
resolves races early, so counts should be at least these). Single factors: 1,400-3,400 events each; COT alone ~77.

### What has already been seen (full disclosure)
- All of Experiment 2, including the D1 flagged-event result above and the finding that the ADX filter worsened the gradient.
- Pieter's chart observation (above).
- The count-only H4 event estimate above, and H4 store diagnostics (block counts, stub counts) — no outcomes.
- Regression check: after generalising `tools/backtest_crowded_reversal.py` to `--timeframe`, the D1 run reproduced
  Experiment 2 exactly (slopes 0.026 / 0.041, same CIs, same 25 / 32 flagged events).
- NO H4 reversal rate, lift, slope, bucket or stratum has been computed on any data before this entry. A plumbing
  smoke on ONE pair and a short window will follow this commit, labelled PLUMBING ONLY; it changes nothing above.

### Not tested here (stated in advance)
Costs, sizing, stops, entries; the support/resistance-return outcome; thresholds other than 60; other pairs; earlier
regimes; live forward data. One macro era (2020-2026). 12 pairs share USD/JPY legs and COT legs, so pairs are not
independent; bars overlap. D1 OHLC/H4 OHLC cannot sequence intrabar (tie -> continuation, applied identically).

### Reproduce
```
py -m tools.build_h4_store
py -m tools.backtest_crowded_reversal --timeframe h4utc --primary flagged --tag exp3_h4utc_2026-09 --mode full
py -m tools.backtest_crowded_reversal --timeframe h4ny  --primary flagged --tag exp3_h4ny_2026-09  --mode full
py -m tools.backtest_crowded_reversal --timeframe d1 --primary gradient --tag exp2_2026-09 --mode full   # Experiment 2
```

## Parked ideas
**Priority order (Pieter's call, 2026-09-17): CSM H1 flow extremes is Priority 1** — listed
first below regardless of when it was added. Everything else here is unordered.
- **[Priority 1] CSM H1 flow extremes, aligned with the recommendation/trend (Pieter's idea,
  2026-09-17).** Observation: when one currency's H1 flow (`csm_delta.h1`) is near its extreme
  high and the other leg's is near its extreme low, in the same direction the
  recommendation/trend/momentum already point, that fast H1 read might catch a pullback
  resuming into continuation before slower timeframes show it. **Two concrete risks to test
  for, not assume away, before trusting this:**
  (1) CSM is min-max normalised to 0-100 *every single scan* (`csm.py::_normalise`'s own doc
  comment: "min-max rescaling always stretches whatever gap exists to fill exactly 0-100 every
  scan") — some currency is ALWAYS near 100 and some ALWAYS near 0, even in a flat,
  undifferentiated market. "Maximal/minimal" as stated doesn't distinguish a real divergence
  from routine noise in a thin basket. The project already solved this exact problem for the
  strength reading (`scanner/extend/csm_dispersion.py`, gates on `raw_spread`
  percentile-ranked against its own rolling history, not a fixed threshold, since a raw
  threshold "looks the same whether there's real cross-currency signal or none") — the same
  dispersion-aware gating would need to apply to the delta/flow measure too, or the signal
  fires constantly and mostly means nothing.
  (2) A sharp flow spike on a fast timeframe (H1) is at least as consistent with short-term
  exhaustion about to mean-revert as it is with genuine continuation starting — "fast" cuts
  both ways, catches real shifts early AND catches noise early. This needs an actual empirical
  test, not intuition, and is the same ambiguity (momentum vs. mean-reversion at an extreme)
  that's historically hard to resolve without rigorous out-of-sample testing.
  Also worth checking before building: how much `csm_delta` already overlaps with what
  `rank_pairs()` itself scores (its `mom_dlt`/`csm` components) — if there's heavy overlap,
  this gate may be a stricter/later read of the same signal the recommendation already uses,
  not independent confirming evidence. Same cross-pair replay infrastructure cost as the
  recommendation-glyph idea below (CSM needs all 12 pairs at once). Entry timing (at the flow
  extreme itself, or once it starts reverting toward normal), and SL/TP, not yet specified.
  Not pre-registered.
- Trend-pullback exit-hypothesis retest: the target=leg_high exit produced lottery-ticket RRs
  (planned RR up to ~200:1). A different exit (fixed R multiple / trailing / partial profits)
  is a genuinely different hypothesis — test it with train/test separation, pre-registered,
  never as a reactive tune of Strategy 1.
- Strategy 2 and Strategy 3 from the original three-strategy set (not yet built).
- **Breakout-and-retest (Pieter's idea, 2026-09-17).** Observation: after price breaks through
  a level (bear case: breaks below the nearest swing low still above current price), it tends
  to retrace back to retest exactly that broken level before continuing. Looks clearest on H4.
  Proposed mechanism: track the most recent swing low price has closed below as an "unmitigated"
  level; fire when price returns to touch it. `scanner/extend/swings.py`'s `find_swings`/
  `last_swing_low`/`last_swing_high` (already on this branch, from Strategy 1) cover the level
  detection — this is mostly reusing existing infrastructure, not new engineering. Open design
  questions to pin down before pre-registering as an experiment: (1) **break definition** — close
  below the level, or is a wick-only break enough; (2) **retest definition** — wick-touch fires
  faster but noisier, a close back at/through the level is stricter; (3) **level lifecycle** — if
  price breaks a second, lower level before ever retesting the first, does the first level stay
  live or only the most recent one count; (4) **expiry** — does an unmitigated level go stale
  after N bars, or stay valid indefinitely. Not pre-registered yet — do that (hypothesis, exact
  params, pass/fail criteria) before running anything, per this file's own rule above.
- **Recommendation-glyph entries (Pieter's idea, 2026-09-17).** Observation: when a pair newly
  appears in `signals.ranked.top` (the same edge-trigger `scan_h1.py::_recommendation_alerts`
  already fires a push on), the market looks "ready to trade" — enter on appearance, structure
  the stop/target off H4 open/close levels, RR >= 1.5 (tweakable). **Materially harder to
  backtest than Strategy 1**: `rank_pairs()`'s score depends on CSM, which is a cross-pair
  construct (needs all 12 pairs' data simultaneously to derive any one currency's strength), plus
  D1 regime and cross-asset macro — there's no single-pair OHLCV rolling-window shortcut here.
  Testing this for real means replaying the ENTIRE frozen technical pipeline (regime, CSM,
  momentum, structure) across all 12 pairs in lockstep at every historical point, then feeding
  that into the same frozen `rank_pairs()` — all Rule #1-safe (read-only frozen-function calls,
  same pattern as the live scanner), but real infrastructure to build, not a quick add. Also
  needs precise SL/TP rules pinned down (which H4 candle, open vs. close, what distance) before
  it's testable, and any RR sweep must be pre-registered as a fixed set of values decided before
  running, not searched for after seeing results (this file's own "no tuning to a sample" rule).
  Not pre-registered.
