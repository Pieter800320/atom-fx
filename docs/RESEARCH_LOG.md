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
| 3 | Crowded Market flags (score >= 60) on H4 — Pieter's chart observation | On H4 (app's UTC blocks, filter off), flagged bars reverse more often than like-for-like baseline bars | RUN 2026-09-19 (once, no changes after pre-registration) | H4 UTC blocks, filter off: 176 events, flagged 42.6% vs baseline 49.1%, lift -6.5% (CI -13.2..+0.6); gradient slope +0.002 (CI -0.007..+0.013). NY-aligned H4: 206 events, lift +2.8% (CI -4.8..+10.9); slope +0.004. Single factors flat. Ranging-vs-trending: no support | **FAIL** (primary, UTC): H3 not supported on H4. NY-aligned also fails. The score is flat across buckets on H4; the D1 gradient of Experiment 2 does not carry over | see "Experiment 3" below and its "Result"; `scanner/extend/agg_h4.py`; `tools/build_h4_store.py` |
| 4 | Crowded Market flags (score >= 60) vs the previous support/resistance — Pieter's chart observation | Flagged bars reach the most recent confirmed swing level before an equal adverse move more often than distance-matched baseline bars (primary: H4, app's UTC blocks, filter off) | RUN 2026-09-19 (once, no changes after pre-registration) | H4 UTC blocks, filter off: 117 events, flagged reached the level 18.8% vs distance-matched baseline 22.2%, lift -3.4% (CI -9.7..+4.1). NY-aligned H4: 126 events, lift -3.1% (CI -9.3..+3.4). D1: 56 / 22 events, inconclusive | **FAIL** (primary, H4 UTC; NY also fails). D1 INCONCLUSIVE (too few events, as pre-registered). No support for "flags trade back to the previous S/R more often"; 91% of flagged events had their previous level 3-5 ATR away | see "Experiment 4" below and its "Result"; `scanner/extend/level_race.py`; `tools/backtest_crowded_reversal_levels.py` |
| 5 | Single factor (ATR-stretch) vs the composite; earlier-era (2009-2020) replication of the D1 gradient | H5a: the composite's D1 gradient replicates in 2009-2020 on native daily bars. H5b (only if H5a replicates): ATR-stretch alone is non-inferior to the composite (margin 0.01 in rho) | RUN 2026-09-19 (once, no changes after pre-registration) | Era B (2009-2020, native daily, filter off): composite gradient slope -0.001 (CI -0.020 .. +0.020), buckets flat 46-54%. Era A' (2021-2026, same bars): +0.036 (CI +0.011 .. +0.063). Stretch vs composite: unresolved in both eras | **H5a CONTRADICTED** (no D1 gradient in 2009-2020; the 2021-2026 effect is not robust across eras). **H5b not interpretable** (no composite effect to match). Bar convention is not the cause of the era gap (UTC bars reproduce +0.036 in 2021-2026) | see "Experiment 5" below and its "Result"; `tools/fetch_d1_utc_daily.py`; runner `--timeframe d1utc` |
| 6 | Crowded Market score > 30 against the OPPOSITE background colour — Pieter's strategy idea | Top score > 30 while the strong-trend shading is green (or bottom score > 30 while it is red) reverses more often than other bars in the SAME regime, on both D1 (native daily 2009-2026) and H4 (app's UTC blocks) | RUN 2026-09-19 (once, no changes after pre-registration) | P1 D1 native 2009-2026: 380 events, flagged reversed 51.1% vs same-regime baseline 51.6%, lift -0.5% (CI -5.7..+5.2). P2 H4 UTC: 908 events, 47.9% vs 50.7%, lift -2.8% (CI -6.1..+1.1). Score>30 on ANY background: 48.2% (D1) / 47.9% (H4) | **NOT SUPPORTED** (both primaries FAIL F2-F5; H4 also fails F6, F7). The colour adds nothing measurable to the score, and the score alone is at or below a coin flip | see "Experiment 6" below; `tools/backtest_crowded_reversal_regime.py` |
| 7 | Shading turns ON right after a stretched score (score > 30 within the previous 10 bars) — Pieter's screenshot observation | The bar where the opposite strong-trend shading switches on, after a score > 30 in the previous 10 bars, reverses more often than OTHER onset bars of the same colour, on both D1 (native daily 2009-2026) and H4 (UTC) | RUN 2026-09-19 (once, no changes after pre-registration) | P1 D1 native 2009-2026: 320 events, reversed 48.8% vs other-onset baseline 50.3%, lift -1.6% (CI -5.2..+2.3). P2 H4 UTC: 800 events, 49.6% vs 48.9%, lift +0.7% (CI -2.0..+3.4). Every onset bar: 50.4% (D1) / 48.8% (H4) | **NOT SUPPORTED** (both primaries FAIL F2-F3 and F6). The shading turning on after a stretched score is a coin flip, and no better than any other onset | see "Experiment 7" below; `tools/backtest_crowded_reversal_onset.py` |
| 8 | D1 and H4 Crowd scores coincide (both > 30, same side) — Pieter's idea | When the H4 score AND the last completed D1 score are both above 30 on the same side, the H4 bar reverses more often than like-for-like baseline bars (and more than H4-alone flags) | ACTIVE 2026-09-19 (pre-registered, not yet run) | - | - | see "Experiment 8" below; `tools/backtest_crowded_reversal_confluence.py` |

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

### Result (run once on 2026-09-19; code + data at commit `f8b1624`; outputs in `data/backtest/crowded_reversal_exp3_h4utc_2026-09/` and `..._exp3_h4ny_2026-09/`)
Window: ~77 months (2020-05 .. 2026-09), 12 pairs, ~230,000 H4 observations per arm. Nothing was changed between the
pre-registration commit and these runs; the one-pair smoke did not alter any parameter or gate.

| filter OFF | H4 UTC blocks (PRIMARY) | H4 NY-aligned (robustness) |
|---|---|---|
| de-clustered flagged events | 176 (F1 passes) | 206 |
| flagged reversal rate vs like-for-like baseline | 42.6% vs 49.1% | 51.9% vs 49.2% |
| **lift (95% CI)** | **-6.5% (-13.2 .. +0.6)** | +2.8% (-4.8 .. +10.9) |
| halves / excl. JPY / excl. best pair | -8.9, -3.4 / -8.5 / -8.1 | +3.1, +2.9 / +8.1 / +1.1 |
| flagged-event gates | **FAIL** (F2, F3, F4, F5, F6) | FAIL (F2, F6) |
| gradient slope per +20 pts (95% CI) | +0.002 (-0.007 .. +0.013) | +0.004 (-0.005 .. +0.015) |
| gradient gates | FAIL | FAIL |

**Verdict (pre-registered): FAIL on the primary.** H3 — that flagged bars reverse more often than baseline on H4 — is
not supported. The NY-aligned check cannot rescue it and does not contradict it: it also fails.

**How to read this:**
- **The score does not predict 20-bar / 1-ATR reversals on H4 in this test.** Reversal rate is 47-51% in every score
  bucket (UTC, filter off: 49.1, 50.5, 49.6, 47.9, 49.2, 47.3, 48.0) — no gradient. Each single factor is flat as well
  (flagged lift within about +/-3 points; COT alone has only 77 events).
- **The two bar alignments disagree in sign on the flagged lift** (-6.5% vs +2.8%) while both intervals comfortably
  include ~0. That disagreement is itself informative: flag-level H4 results are noisy and convention-sensitive, so
  neither number should be read as a finding in either direction. What is NOT ambiguous is that neither passes.
- **"Especially in ranging markets" is not supported** (exploratory, no gates): UTC ranging flags lift -11.4% (CI
  -21.7 .. -0.4, n=87) vs trending -1.1% (CI -9.1 .. +7.7); NY ranging +3.5% (CI -6.4 .. +13.3) vs trending +1.9%.
  (The Suppress-arm flag set is essentially the ranging set, which is why those figures coincide.)
- **ADX filter on H4:** no demonstrated value (slope difference -0.001, CI -0.008 .. +0.005, UTC; 0.000 NY) — it
  neither helps nor hurts here, unlike D1 where it worsened the gradient.
- **Contrast with Experiment 2:** the filter-off D1 gradient was +0.041 (CI +0.018 .. +0.070); on H4 it is ~0. These
  data cannot say why. Candidates, not separated by any test: (a) the D1 result was partly chance (one of two arms, one
  macro era, overlapping bars, and the D1 flagged-event test was itself inconclusive); (b) a real effect that lives at
  the longer horizon (a 20-bar D1 race spans ~4 weeks; 20 H4 bars ~3.3 days at a much smaller ATR); (c) convention
  effects (bar alignment already changed the sign of the H4 flagged lift). Telling them apart needs new
  pre-registered work, not more looking at these numbers.
- **What this does not test:** the "trades back to the previous support/resistance" outcome (candidate Experiment 4),
  any discretionary context a chart reader applies, entries, exits, costs.

**Consequence recorded:** the observation "flags work well on D1 and H4" is NOT supported on H4, and only weakly and
inconclusively on D1. This is not a validated basis for an app flag.

### Reproduce
```
py -m tools.build_h4_store
py -m tools.backtest_crowded_reversal --timeframe h4utc --primary flagged --tag exp3_h4utc_2026-09 --mode full
py -m tools.backtest_crowded_reversal --timeframe h4ny  --primary flagged --tag exp3_h4ny_2026-09  --mode full
py -m tools.backtest_crowded_reversal --timeframe d1 --primary gradient --tag exp2_2026-09 --mode full   # Experiment 2
```

## Experiment 4 — do flagged bars trade back to the previous support/resistance? (pre-registration, 2026-09-19)

**Written and committed BEFORE any level-return outcome has been computed on any bar or flag.**

### Origin
Pieter, 2026-09-19: "If one trades back to the previous support/resistance, it often succeeds (seemingly at a glance)."
Experiments 2-3 measured a different outcome (a symmetric 1 x ATR reversal race) and found nothing on H4 and an
era-dependent tendency on D1. This experiment tests the part of the observation they did not: the S/R-return outcome.

### Definitions (all fixed now)
- **Flags:** identical to Experiments 2-3 — the Pine port, score >= 60, rising edge, filter OFF (the default) as the
  primary arm, de-clustered per pair; a top and a bottom flag on the same bar are skipped and counted.
- **Previous support/resistance (a judgment call, stated as such):** the most recent CONFIRMED swing, from the repo's own
  `scanner.extend.swings.find_swings` (swing_n = 5, strict-left / tolerant-right). A swing at bar i uses bars i-5..i+5, so it
  is usable only from bar i+5 on — no look-ahead. Bottom flag (expects UP): target = the most recent confirmed swing HIGH
  lying 0.5 to 5.0 ATR above the flag bar's close and no older than 120 bars ("previous resistance"). Top flag: the mirror,
  swing LOW ("previous support"). No qualifying swing -> the bar has no valid level on that side and is excluded, flagged and
  baseline alike (~82-87% of bars have one).
- **Race:** target = that level, distance D; stop = the same distance D on the other side (1:1). Walk the next 20 bars'
  high/low. Target first = SUCCESS. Stop first = failure. Both inside one bar = failure (cannot be sequenced from OHLC).
  Neither in 20 bars = timeout = failure. **Success means the level was reached before an equal adverse move — not profit;**
  no costs, entry rule, sizing or discretionary exit.
- **Baseline (the essential control):** a stretched bar sits farther from its previous level than an average bar, and a farther
  level is harder to reach in 20 bars — so a pooled baseline would be biased AGAINST flagged bars. The baseline is measured per
  (pair, side, level distance in ATR: [0.5,1) [1,2) [2,3) [3,5]) over every valid bar in the window; a flagged event's lift is its
  outcome minus the baseline rate of ITS bucket. Month-clustered bootstrap (5000, seed 20260919) recomputes every bucket baseline
  inside each resample.

### Primary test and gates — H4 on the app's UTC blocks, filter OFF
Gates F1-F6 exactly as Experiment 3, on this outcome: F1 >= 100 de-clustered events (else INCONCLUSIVE); F2 lift >= 5 points AND
95% CI lower bound > 0; F3 lift > 0 in both halves; F4 lift > 0 excluding JPY crosses; F5 lift > 0 excluding the best pair;
F6 beats the best single factor's flagged lift (each factor's own rising edge, same de-clustering, same matched baseline).
The gate logic is the same code (`tools.backtest_crowded_reversal.evaluate_flags`), not a re-implementation.

### Secondary (same gates reported, none can rescue a failed primary)
- **H4 NY-aligned blocks** (TradingView-style): robustness. UTC PASS + NY not clean = ALIGNMENT-DEPENDENT.
- **D1, native daily UTC bars 2009-2026** and **D1, NY-close store 2021-2026:** the D1 half of the question. **Pre-registered
  expectation: both INCONCLUSIVE on F1** — count-only estimates give ~56 and ~22 de-clustered events with a valid level, far
  below 100; flags at score >= 60 are simply rare on D1. They are run and reported anyway, so the D1 half is answered honestly
  as "too few events to tell", not silently dropped. Experiment 5 already showed the D1 gradient is era-dependent (absent
  2009-2020), so a pooled 2009-2026 D1 number would mix eras; F3 (both halves) is the gate that exposes that.
- Filter ON (Suppress) arm for comparison; single factors; the flagged-success-by-distance-bucket table (diagnostic).

### Parameters and data
Pine defaults; swing_n 5; lookback 120 bars; distance band 0.5-5.0 ATR; horizon 20 bars; 1:1; warm-up 400 (H4) / 260 (D1) bars; 12 pairs
(EURUSD GBPUSD USDJPY USDCAD NZDUSD AUDUSD USDCHF EURJPY GBPJPY CADJPY AUDJPY NZDJPY); COT as before. Data: the committed stores
`data/h4_utc_long`, `data/h4_ny_long`, `data/d1_utc_daily` (native daily, weekend rows dropped), `data/d1_nyclose_long`.

### Expected event counts (count-only, no outcomes, before this entry)
Conservative fixed-window de-clustering, flags with a valid level: H4 UTC ~115 (filter off) / ~64 (on); H4 NY ~125 / ~82;
D1 native daily ~56 / ~32; D1 NY-close ~22 / ~17. **The primary clears F1 only narrowly on this estimate**; the real rule frees
windows early, so counts should be at least these, but a shortfall would make the primary INCONCLUSIVE — accepted in advance.

### What has already been seen (full disclosure)
- Experiments 2, 3 and 5 in full. In particular the H4 flags tested here ARE the flags of Experiment 3, whose 1 x ATR outcome
  (lift -6.5% UTC / +2.8% NY, both failing) was already known; and the D1 tendency's era-dependence.
- The count-only estimates above (event counts and level availability). No level-return outcome for any bar or flag.
- A plumbing smoke on ONE pair and a short window will follow this commit, labelled PLUMBING ONLY; it changes nothing above.

### Not tested here
Other definitions of support/resistance (round numbers, multi-touch zones, discretionary lines); entries, exits, costs;
thresholds other than 60; other pairs; regimes. A different S/R definition is a different experiment, not a re-run.

### Result (run once on 2026-09-19; pre-registration commit `94f2466`; outputs `data/backtest/crowded_reversal_exp4_*_2026-09/`)
Nothing changed between the pre-registration commit and these runs; the one-pair smoke did not alter any parameter or gate.

| filter OFF | H4 UTC blocks (PRIMARY) | H4 NY-aligned | D1 native daily 2009-2026 | D1 NY-close 2021-2026 |
|---|---|---|---|---|
| de-clustered events (valid level) | **117** (F1 passes) | 126 | 56 | 22 |
| flagged success vs distance-matched baseline | 18.8% vs 22.2% | 19.0% vs 22.1% | 17.9% vs 18.6% | 27.3% vs 21.2% |
| **lift (95% CI)** | **-3.4% (-9.7 .. +4.1)** | -3.1% (-9.3 .. +3.4) | -0.8% (-9.4 .. +8.6) | +6.1% (-10.9 .. +27.7) |
| verdict | **FAIL** (F2-F6) | FAIL | INCONCLUSIVE (F1) | INCONCLUSIVE (F1) |

**Verdict (pre-registered): FAIL on the primary.** Flagged bars did NOT reach the previous support/resistance more often
than distance-matched baseline bars on H4 — the point estimate is slightly below baseline in both alignments, with intervals
that include zero. The D1 half of the question is **INCONCLUSIVE, as pre-registered**: 56 and 22 events cannot detect an
effect of any plausible size (the intervals span roughly -10 to +9 and -11 to +28 points). Halves on the primary: +1.3% / -7.4%.

**A structural finding the matching exposed (worth knowing regardless of the verdict):**
- **At a flag, the "previous support/resistance" is usually FAR away.** 106 of the 117 primary events (91%) had their level
  3-5 ATR from the close (only 1 within 1 ATR); a flag is by construction a stretched bar, and stretched bars sit far from
  their last swing level. In that bucket the baseline success rate over 20 H4 bars is only ~20%, and **55% of flagged
  events (64 of 117) simply timed out** — the level was not reached within ~3.3 trading days, and neither was the equal stop.
- So "trades back to the previous S/R" from a flag means a large move relative to ATR, which is slow. A pooled baseline would
  have compared those far-level flags against average bars with near levels and produced a large fake deficit; the matching
  removes that, and what remains is a small negative difference within noise.
- **Not tested and not excluded:** a longer horizon than 20 bars (a return to a level 3-5 ATR away plausibly needs more
  time on H4); other S/R definitions (round numbers, multi-touch zones, discretionary lines); a stop tighter than the target.
  Each is a different pre-registered experiment.
- Single factors are flat or slightly negative on this outcome as well (z: -3.1%, CI -5.4 .. -0.6 on UTC).
- ADX filter (Suppress arm): 64 UTC / 82 NY events, inconclusive.

**Consequence recorded:** the observation "flags often succeed when price trades back to the previous S/R" is NOT supported on
H4 by this test, and cannot be assessed on D1 (too few flags). Together with Experiments 3 and 5 this is not a validated basis
for an app flag.

### Reproduce
```
py -m tools.backtest_crowded_reversal_levels --timeframe h4utc --tag exp4_h4utc_2026-09 --mode full
py -m tools.backtest_crowded_reversal_levels --timeframe h4ny   --tag exp4_h4ny_2026-09  --mode full
py -m tools.backtest_crowded_reversal_levels --timeframe d1utc  --tag exp4_d1utc_2026-09 --mode full
py -m tools.backtest_crowded_reversal_levels --timeframe d1     --tag exp4_d1ny_2026-09  --mode full
```

## Experiment 5 — is a single factor as good as the composite? + earlier-era replication (pre-registration, 2026-09-19)

**Written and committed BEFORE any earlier-era (2009-2020) outcome has been computed.**
(Numbered 5 because the support/resistance-return study is Experiment 4; they were requested together and are independent.)

### Origin and why new data is required
Pieter, 2026-09-19: check whether a simpler indicator — e.g. ATR-stretch alone — does as well on D1 as the whole
composite. Experiment 2 already compared them on the NY-close D1 store (2021-2026): composite rho 0.041 vs
stretch 0.0318, paired CI of the difference -0.010 .. +0.029 — unresolved. Re-analysing the same bars cannot add
evidence, so this experiment uses an EARLIER ERA the D1 result has never seen.

### Data — and its caveat
Twelvedata NATIVE daily bars (`tools/fetch_d1_utc_daily.py` -> `data/d1_utc_daily/`, 2007-10 .. 2026-09, ~5,000 rows
per pair, Saturday/Sunday fragments dropped at load). These are UTC-based, NOT the 17:00-NY-close bars of Experiments 2-3
(measured: median 13-20 pips/bar close disagreement; the indicator's factors agree on which days are "on" only 27-72%
on the 2-year overlap). So this replicates the CONCEPT under a different bar convention AND a different era. A success is
therefore robust to both; a failure is ambiguous between "era" and "convention" and must be read that way.
COT: as before (Legacy futures-only, week k reads Tuesday-of-week-(k-1)); its percentile needs ~3 years, so evaluation
starts 2009-01-05. Warm-up 260 bars.

### Hypotheses
- **H5a (replication).** In the earlier era (2009-01-05 .. 2020-12-31, ~37,300 bars per side, 12 pairs), the composite score
  (filter OFF, the Pine default) shows the same gradient: within-(pair x side) slope of reversal probability on score.
  Reversal = the Experiment 2 barrier race, unchanged (1 x ATR(14), 20 bars, same-bar-both -> continuation, timeout -> no).
  Verdict: **REPLICATES** if slope per +20 points >= 0.02 AND the month-clustered 95% CI lower bound > 0;
  **CONTRADICTED** if the CI upper bound < 0.02; otherwise **INCONCLUSIVE**.
- **H5b (simplicity).** ATR-stretch alone — the factor exactly as the indicator defines it (close - EMA50 >= 3 ATR,
  weight 7/100, on the same 0-100 scale) — predicts reversals as well as the composite. Metric: within-stratum
  correlation rho of reversal with the predictor, paired month-clustered bootstrap (5000, seed 20260919) of
  rho(stretch) - rho(composite). Rule, fixed now: **stretch AS GOOD** if the CI lower bound > -0.01 (non-inferiority
  margin: a quarter of the composite's Experiment-2 rho of 0.041); **composite BETTER** if the CI upper bound < 0;
  otherwise **INCONCLUSIVE**.
- **Guardrail on H5b:** non-inferiority is trivially true when the composite itself does nothing. H5b is interpreted
  ONLY if H5a = REPLICATES; otherwise its result is recorded as "not interpretable — no composite effect to match".
- Only stretch carries a pre-registered claim. The other six single factors appear in the same table for information;
  no conclusion is drawn from them (multiple comparisons).

### Secondary (informational, no gate)
- **Era A' (2021-01-01 .. 2026-09) on the same UTC daily bars:** if the UTC convention reproduces Experiment 2's
  NY-close slope (+0.041) the bar convention is not what drives the result; if it does not, convention matters.
- Flagged events (score >= 60) counts and lift in era B — informational (~30-60 events expected; below the floor).
- ADX filter (suppress minus off) in era B, same rule as Experiment 2.

### Parameters — all fixed now
Pine defaults; 12 pairs (EURUSD GBPUSD USDJPY USDCAD NZDUSD AUDUSD USDCHF EURJPY GBPJPY CADJPY AUDJPY NZDJPY);
gradient buckets, fixed effects and bootstrap exactly as Experiment 2. `--eval-end 2020-12-31` for era B.

### What has already been seen (full disclosure)
- Experiment 2 in full, including every single-factor slope and rho on the NY-close D1 store (stretch rho 0.0318,
  rsi 0.0314, z 0.0277 vs composite 0.041) and the unresolved paired CI above.
- Count-only estimates on THIS UTC daily data (earlier today): ~41 (filter on) / ~91 (filter off) de-clustered
  composite events over 211 pair-years, and the factor-agreement measurement on the 2-year overlap. No outcomes.
- NO era-B reversal rate, slope, rho or bucket has been computed before this entry.

### Not tested here
Anything on H4; the support/resistance outcome (Experiment 4); costs/entries; other pairs. Era B adds a different
macro regime (2009-2020: post-crisis, low-rate, low-volatility, then 2020) but is still 12 correlated pairs.

### Result (run once on 2026-09-19; pre-registration commit `ebf0bee`; outputs `data/backtest/crowded_reversal_exp5_eraB_2026-09/` and `..._eraA_2026-09/`)
Nothing changed between the pre-registration commit and these runs.

| filter OFF, native daily UTC bars | Era B: 2009-01 .. 2020-12 (out-of-sample) | Era A': 2021-01 .. 2026-09 (informational) |
|---|---|---|
| months / pairs | 144 / 12 | 68 / 12 |
| composite gradient slope per +20 pts (95% CI) | **-0.001 (-0.020 .. +0.020)** | +0.036 (+0.011 .. +0.063) |
| reversal rate by score bucket [0,10) .. [60,100] | 49.8, 48.5, 48.8, 53.1, 47.8, 53.6, 46.0 | 48.8, 53.5, 52.5, 52.6, 46.1, 55.5, 65.1 (last: 43 obs) |
| **H5a replication verdict** | **CONTRADICTED** (CI upper bound sits at the 0.02 line) | REPLICATES |
| flagged events (score >= 60): n, lift (95% CI) | 92, -4.2% (-14.5 .. +7.8) | 27, +18.8% (-6.3 .. +42.8) — inconclusive |
| ADX filter, suppress minus off | -0.000 (CI -0.012 .. +0.012): no demonstrated value | -0.013 (CI -0.026 .. -0.001): worsens |

**H5a: CONTRADICTED.** In 2009-2020 the composite score shows no gradient (slope ~0, buckets flat with no monotone
pattern; flagged events lift -4.2%, CI including 0). The verdict is technically "contradicted" because the CI upper
bound lands on the 0.02 threshold; the plain reading is "no effect detected, and at most about the size seen in
2021-2026."

**H5b: NOT INTERPRETABLE**, by the pre-registered guardrail (H5a did not replicate). The table shows ATR-stretch
labelled "AS GOOD" in era B, but that is the trivial case — neither predictor does anything there. In era A', where
the composite does have an effect, stretch vs composite is INCONCLUSIVE (rho 0.039 vs 0.035; paired CI of the
difference stretch-minus-composite -0.015 .. +0.023), as in Experiment 2 (0.032 vs 0.041; stretch-minus-composite CI
-0.029 .. +0.010). So the direct answer
to "does ATR-stretch alone do as well?" is: **its point estimate is comparable (in era A' it is even slightly higher),
but the data cannot establish non-inferiority within the pre-registered margin — nor that the composite is better.**
(The composite is significantly better than %B alone, divergence alone and climax alone in era A', not better than
stretch, z, RSI or COT alone.)

**What this changes:**
- **The D1 tendency is era-dependent.** It appears in 2021-2026 under BOTH bar conventions (NY-close +0.041, UTC
  daily +0.036) and is absent in 2009-2020. That rules out the bar convention as the cause of the era gap and leaves
  either a real regime dependence or the 2021-2026 result being partly chance — these data cannot separate them.
- **Combined with Experiment 3 (H4: nothing), the evidence for the indicator's predictive value is now: a small
  tendency in one 5.7-year window on D1, absent in the 12 years before it and absent on H4.** That is weak.
- The ADX filter: worsens the gradient in 2021-2026 on both conventions; neutral in 2009-2020 and on H4.
- Not tested: whether some regime variable explains WHY 2021-2026 differs (a pre-registered follow-up could ask).

### Reproduce
```
set TWELVEDATA_KEY=...   (never in chat or git)      py -m tools.fetch_d1_utc_daily
py -m tools.backtest_crowded_reversal --timeframe d1utc --primary gradient --eval-end 2020-12-31 --tag exp5_eraB_2026-09 --mode full
py -m tools.backtest_crowded_reversal --timeframe d1utc --primary gradient --eval-start 2021-01-01 --tag exp5_eraA_2026-09 --mode full
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

## Experiment 6 — Crowd score > 30 against the OPPOSITE background colour (pre-registration, 2026-09-19)

**Written and committed BEFORE any outcome of this strategy has been computed on any bar.**

### Origin
Pieter, 2026-09-19: "test one strategy: the criteria are that top/bottom scores must be above 30, and they must coincide with a
background colour of the OPPOSITE colour." In the Pine the background is the strong-trend shading: **green** while a strong
uptrend is latched, **red** while a strong downtrend is. The Crowd TOP score is drawn red and the BOTTOM score green, so
"opposite colour" is read as: **top score > 30 while the background is GREEN; bottom score > 30 while it is RED.** (This reading is
recorded here so it can be corrected; the alternative — same colour — is the "with_trend" arm below and is reported too.)
This is a counter-trend fade inside a strong trend: precisely the flags the Pine's "Suppress" mode removes, and Experiment 2 found
Suppress made the score a worse predictor. It is therefore not implausible, and not the same claim as Experiment 3's observation
("especially in ranging markets").

### Definitions (all fixed now)
- **Score:** the Pine port's composite 0-100 top/bottom score with the regime adjustment OFF (raw). **"Above 30" = strictly greater than 30.**
- **Background:** the Pine's own debounced strong-trend latch at the SAME bar (ADX >= 30, EMA200 slope over 20 bars, price on the
  matching side of EMA200, 3 consecutive bars to enter, immediate exit) — `strong_up` = green, `strong_dn` = red.
- **Flag (primary arm `opposite`):** rising edge of (top score > 30 AND green) for a top; of (bottom score > 30 AND red) for a bottom.
  De-clustered per pair exactly as Experiments 2-4 (`barrier_race.declustered_events`).
- **Outcome:** Experiments 2-4's barrier race, unchanged: 1 x ATR(14) barriers from the flag bar's close, 20 bars, top expects DOWN
  first, bottom expects UP first, same-bar-both -> continuation, timeout -> not a reversal. **A reversal is not profit:** no
  costs, entry rule, sizing or exit are modelled.
- **Baseline (the one new element, and essential):** in a strong uptrend a downward 1 x ATR move is rarer than in an average bar, so
  comparing with all bars would flatter a counter-trend fade. The baseline is **regime-matched**: for top flags every valid bar of
  the same pair with the green shading (any score), for bottom flags every bar with the red shading, per pair, side and month.
  The lift = flagged reversal rate minus that rate, i.e. **does score > 30 add anything beyond the background colour itself?**
  Month-clustered bootstrap (5000, seed 20260919) recomputes the baselines inside each resample, as before.
- **Other arms (same bars, informational, none can rescue a failed primary):** `with_trend` (top & red / bottom & green, matched to
  same-regime bars); `no_shading` (score > 30 with no shading, matched to unshaded bars); `any` (score > 30 on any background,
  matched to all bars — the comparison for F7).

### Primary tests — TWO, independent, both required
- **P1: D1, Twelvedata native daily bars, 2009-2026** (`data/d1_utc_daily`, the longest history; NOT the app's 17:00-NY bars,
  which start in 2020 and hold too few events). Halves for F3 are the month-median split.
- **P2: H4, the app's UTC blocks** (`data/h4_utc_long`).
Each is judged on its own with the gates below. **Overall verdict:** SUPPORTED only if BOTH pass; exactly one passing = SUGGESTIVE
ONLY (single-timeframe, not supported — two looks were taken); neither = NOT SUPPORTED. INCONCLUSIVE on either (F1) = INCONCLUSIVE overall.

### Gates (per primary, ALL must hold; fixed now)
F1 >= 100 de-clustered events (else INCONCLUSIVE). F2 lift >= 5 points AND 95% CI lower bound > 0. F3 lift > 0 in both halves.
F4 lift > 0 excluding JPY crosses. F5 lift > 0 excluding the best pair. **F6** the flagged reversal rate itself > 50% (a 1:1 race
needs more than a coin flip before any cost). **F7** lift beats the `any` arm's lift (the colour must add something to the score alone).
F1-F5 are the same code as Experiment 3's `evaluate_flags` logic; F6 and F7 are new here.

### Secondary (informational, no gates)
D1 on the app's 17:00-NY store 2021-2026 (**pre-registered expectation: INCONCLUSIVE, ~67 events**); H4 NY-aligned blocks; D1 native
split by era (2009-2020 / 2021-2026); the four arms side by side; per-pair table. **No other threshold (20/40/60), no other
timeframe, no parameter sweep.** 30 is Pieter's number.

### Parameters and data
Pine defaults; warm-up 260 (D1) / 400 (H4) bars; 12 pairs (EURUSD GBPUSD USDJPY USDCAD NZDUSD AUDUSD USDCHF EURJPY GBPJPY CADJPY AUDJPY
NZDJPY); Legacy COT as before (a bar with no resolvable COT is excluded so the score ceiling is not silently 75); horizon 20; mult 1.0.

### Expected event counts (count-only, fixed 20-bar de-clustering, no outcome read, before this entry)
`opposite`: D1 native 2009-2020 ~188, 2021-2026 ~76 (so P1 ~264 — clears F1); H4 UTC ~642 (clears F1); D1 NY-close 2021-2026 ~67;
H4 NY ~617. `with_trend`: only 2-5 (D1) and ~29 (H4) — that arm will be too small to judge. Green/red bars are ~10-16% of bars.

### What has already been seen (full disclosure)
- Experiments 2-5 in full. Directly relevant: Experiment 2's exploratory strata (ranging vs trending) found "ranging not better"
  for flags at score >= 60; Experiment 2 found the Suppress (regime) filter made the gradient worse (on minus off -0.015); Experiment 3
  found no H4 gradient; Experiment 5 found the D1 gradient absent in 2009-2020. None of those measured THIS conjunction.
- The count-only estimates above (event counts by arm). No outcome of the strategy on any bar.
- The Pine port was already validated against the indicator (Experiment 2); the regime latch code is the one used by the Suppress arm.
- A plumbing smoke on ONE pair and a short window follows this commit, labelled PLUMBING ONLY; it changes nothing above.

### Not tested here
Other thresholds; the score's individual factors inside the regime; entries, exits, stops, costs and sizing (a PASS would only justify
building and testing an executable rule, not trading it); regimes defined differently (other ADX/EMA settings); other pairs.

### Result (run once on 2026-09-19; pre-registration commit `63e91d0`; outputs `data/backtest/crowded_reversal_exp6_*_2026-09/`)
Nothing changed between the pre-registration commit and these runs; the one-pair smoke (n=7, no conclusion drawn) altered no parameter or gate.

| `opposite` arm (score > 30 against the opposite colour) | **P1 D1 native 2009-2026** | **P2 H4 UTC** | D1 NY-close 2021-26 (info) | H4 NY (info) | D1 native 2009-20 (info) | D1 native 2021-26 (info) |
|---|---|---|---|---|---|---|
| de-clustered events | **380** | **908** | 95 | 862 | 277 | 103 |
| flagged reversal rate | 51.1% | 47.9% | 52.6% | 49.8% | 49.8% | 54.4% |
| same-regime baseline | 51.6% | 50.7% | 53.5% | 50.4% | 49.8% | 54.2% |
| **lift (95% CI)** | **-0.5% (-5.7 .. +5.2)** | **-2.8% (-6.1 .. +1.1)** | -0.8% (-10.2 .. +8.5) | -0.6% (-4.2 .. +3.4) | +0.1% (-5.7 .. +6.6) | +0.2% (-10.0 .. +9.8) |
| verdict | **FAIL** (F2-F5) | **FAIL** (F2-F7) | INCONCLUSIVE (F1) | FAIL | FAIL | FAIL |

Arms, P1 (D1 native): `any` 1,082 events, 48.2% vs 49.5%, lift -1.4%; `no_shading` 765, 48.0% vs 49.6%, -1.6%; `with_trend` only 7 events (too few to judge).
Arms, P2 (H4 UTC): `any` 2,670 events, 47.9% vs 49.1%, -1.1%; `no_shading` 1,874, 48.3% vs 49.0%, -0.7%; `with_trend` 31 events.
Halves: P1 -0.4% / +0.2%; P2 -6.0% / +1.1%. Ex-JPY: P1 -6.1%, P2 -0.0%. Ex-best-pair: P1 -2.0%, P2 -3.3%.

**Verdict (pre-registered): NOT SUPPORTED.** Neither primary clears F2 (lift >= 5 points), F3 (both halves), F4 or F5, so the
"one passes = suggestive" and "both pass = supported" branches never come into play. What the data say, in plain terms:
- Inside a strong trend, a score above 30 against the trend reverses 51% of the time on D1 and 48% on H4 — a coin flip, and
  indistinguishable from what ANY bar in that same strong trend does (51.6% / 50.7%). The score adds nothing beyond the background.
- The background colour adds nothing to the score either: score > 30 on any background reversed 48.2% (D1) and 47.9% (H4),
  so the counter-trend condition neither helps nor hurts on D1 and is nominally worse on H4 (-2.8% vs -1.1%, both inside noise).
- F6/F7 passing on D1 (51.1% > 50%; lift -0.5% beats -1.4%) is not evidence: the first is 1.1 points above a coin flip
  with a baseline that is itself 51.6%, and the second compares two lifts that are both statistically zero.
- Consistent with Experiments 2-5: no reversal edge shows up once the D1 2021-2026 window is left, and none on H4.
**Caveats (stated, not excuses):** the outcome is a 1 x ATR reversal race, not a trade with an entry, stop and target, so a
strategy with a different exit could still behave differently — this result says the SIGNAL does not raise reversal odds, it does not
test every way of trading it. Costs, entries and management were not modelled. `with_trend` had too few events to say anything.

## Experiment 7 — the opposite shading turns ON after a stretched score (pre-registration, 2026-09-19)

**Written and committed BEFORE any outcome of this strategy has been computed on any bar.**

### Origin and an honest statement of how it arose
Pieter, 2026-09-19, after Experiment 6 failed: the background "is often painted late ... it runs on a lagging indicator ... the move
changes direction when the indicator is at its strongest ... I see a lot of top and bottom scores happening as soon as the opposite
background appears" (six TradingView screenshots: CADJPY D+4H, GBPUSD D+4H, AUDUSD D+4H). **This is the second look at the same data
family (Experiment 6 was the first), prompted by a failure of the first.** A PASS here is therefore a hypothesis for a FORWARD
(live, unseen-data) test, never a validated edge; a FAIL closes this line.

### What the descriptive checks (NO outcomes) already showed, and why they do not settle it
Over 12 pairs, score >= 60 rising-edge flags: the opposite shading is ON at the flag bar for 59% (D1) / 46% (H4) against a chance rate of
8.7% / 11.1%; for flags with no shading yet, the opposite shading switches on within the next 10 bars for 45% (D1) / 63% (H4) against
7-8% for any unshaded bar; ADX at a flag inside the shading has a median of 43 / 42 against 38 across that shading, and the flag bar is
ADX's 20-bar high for 58% / 48% of flags. **This co-occurrence is largely mechanical**: a score is high when price is stretched, and a strong
move that stretches price is the same move that lifts ADX and paints the shading. Whether the shading's arrival marks a REVERSAL is an
outcome question, measured below. The shading appears AFTER the flag, so it can only be used as a confirmation: the causal, tradeable
moment is the bar where it switches on.

### Definitions (all fixed now)
- **Score / shading / race:** as Experiment 6 (raw Pine-port score, regime adjustment OFF; the Pine's debounced strong-trend latch; the 1 x ATR,
  20-bar barrier race from a bar's close, same-bar-both -> continuation, timeout -> not a reversal). Success is a reversal, not profit.
- **Event, top:** the GREEN shading turns on at bar i (latch False -> True) AND the raw top score was strictly greater than 30 on at least one bar
  of [i-N, i]. **Bottom:** the RED shading turns on at bar i AND the raw bottom score > 30 on at least one bar of [i-N, i]. The race starts at bar i's
  close. De-clustered per pair as before. **N = 10** for the strategy (`confirm10`); N = 5 (`confirm5`) is informational.
- **Baseline (the control):** every OTHER onset bar of the same colour, same pair, side and month, whatever the score before it. The lift = does a
  stretched score just before the shading turns on add anything beyond the shading turning on? Month-clustered bootstrap (5000, seed 20260919) as before.
- **`bare_onset` (informational):** every onset bar, matched to all same-regime bars (Experiment 6's baseline): does the onset moment itself carry
  any reversal information?

### Primary tests — TWO, independent, both required
P1: D1 native daily bars 2009-2026 (`d1utc`). P2: H4 on the app's UTC blocks (`h4utc`). SUPPORTED only if both pass; exactly one = SUGGESTIVE ONLY
(and, given the note above, a forward test is the only thing that could upgrade it); neither = NOT SUPPORTED; INCONCLUSIVE on either = INCONCLUSIVE.

### Gates (per primary, ALL must hold; fixed now)
F1 >= 100 de-clustered events. F2 lift >= 5 points AND 95% CI lower bound > 0. F3 lift > 0 in both halves (month-median split). F4 lift > 0 excluding JPY
crosses. F5 lift > 0 excluding the best pair. F6 the flagged reversal rate > 50%.

### Secondary (informational, no gates)
D1 on the app's 17:00-NY store 2021-2026; H4 NY-aligned; D1 native by era (2009-2020 / 2021-2026); `confirm5`; `bare_onset`; per-pair table.
**Not tested:** ADX-strength / ADX-peak conditions ("at its strongest"), other thresholds than 30, other windows than 5 and 10, entries, exits,
stops, costs. Each would be its own experiment; the two windows are fixed now and were chosen before any outcome, not tuned.

### Expected event counts (count-only, fixed 20-bar de-clustering, no outcome read)
`confirm10`: D1 native ~318, D1 NY-close ~99, H4 UTC ~786, H4 NY ~756. `confirm5`: ~247 / ~81 / ~633 / ~611.

### What has already been seen (full disclosure)
Experiments 2-6 in full (Experiment 6: the score > 30 against the opposite shading, evaluated ANYWHERE inside the shading, was a coin flip at 51.1% / 47.9%
against a same-regime baseline of 51.6% / 50.7%); the six screenshots; the descriptive co-occurrence numbers above; the count-only estimates.
No outcome of an onset-timed event on any bar.

### Result (run once on 2026-09-19; pre-registration commit `e4f6464`; outputs `data/backtest/crowded_reversal_exp7_*_2026-09/`)
Nothing changed between the pre-registration commit and these runs; the one-pair smoke (n=11, no conclusion drawn) altered no parameter or gate.

| `confirm10` (shading turns on, score > 30 in the previous 10 bars) | **P1 D1 native 2009-2026** | **P2 H4 UTC** | D1 NY-close 2021-26 (info) | H4 NY (info) | D1 native 2009-20 (info) | D1 native 2021-26 (info) |
|---|---|---|---|---|---|---|
| de-clustered events | **320** | **800** | 99 | 773 | 217 | 103 |
| reversal rate | 48.8% | 49.6% | 47.5% | 50.1% | 50.2% | 45.6% |
| other-onset baseline | 50.3% | 48.9% | 52.2% | 49.8% | 50.4% | 49.8% |
| **lift (95% CI)** | **-1.6% (-5.2 .. +2.3)** | **+0.7% (-2.0 .. +3.4)** | -4.7% (-11.0 .. +1.9) | +0.3% (-2.4 .. +3.0) | -0.1% (-4.4 .. +4.3) | -4.2% (-9.8 .. +2.3) |
| verdict | **FAIL** (F2, F3, F5, F6) | **FAIL** (F2, F3, F6) | INCONCLUSIVE (99 < 100) | FAIL | FAIL | FAIL |

Arms, P1 (D1): `confirm5` 248 events, 50.4% vs 51.2%, lift -0.8%; `bare_onset` 633 events, 50.4% vs same-regime bars 52.0%, lift -1.6%.
Arms, P2 (H4): `confirm5` 642, 50.0% vs 49.0%, +1.0%; `bare_onset` 1,526, 48.8% vs 50.6%, -1.8%.
Halves: P1 -2.6% / -0.3%; P2 -0.0% / +1.4%. Ex-JPY: P1 +2.4%, P2 0.0%. Ex-best-pair: P1 -2.4%, P2 +0.1%.

**Verdict (pre-registered): NOT SUPPORTED.** Both primaries fail F2 (lift >= 5 points), F3 (both halves) and F6 (rate > 50%). In plain terms:
- The bar where the opposite shading switches on, after a stretched score, reversed about half the time on both timeframes (48.8% / 49.6%),
  no more than any other onset bar (50.3% / 48.9%). The stretched score before it adds nothing measurable.
- The onset moment on its own carries no reversal information either (50.4% D1 / 48.8% H4; lifts -1.6% / -1.8% against same-regime bars).
- The co-occurrence Pieter saw is real (Experiment 7's descriptive section) but does not translate into a reversal: it is what a strong,
  stretched move looks like on the way, not where it ends.
**Caveats (stated, not excuses):** the outcome is a 1 x ATR reversal race, not a trade; ADX-strength / ADX-peak conditions and other
windows/thresholds were not tested (each would be its own pre-registered experiment); no costs, entries or exits were modelled. This was the
second look at the same data family, so no further variant of "score against the shading" should be run on this data without a
strong new reason; a forward (live) test is the only clean confirmation left.

## Caveat (added 2026-09-19, after Experiment 7) — the bar timestamps behind Experiments 2-7
Twelvedata's hourly forex timestamps are **Australia/Sydney local time** (UTC+10 AEST / UTC+11 AEDT), not UTC. Five TradingView candles (2020-2026, three
pairs) match our raw hourly rows at exactly that offset (0.8-6.8 pips of error vs 25-129 pips unshifted). Every store here (`d1_nyclose_long`, `h4_utc_long`,
`h4_ny_long`, `d1_utc_daily`) was built assuming UTC labels. So the "17:00 New York close" D1 bars were cut about 10 hours from 17:00 NY, the "H4 UTC" and
"H4 NY-aligned" blocks were 2-3 hours off their names, and the D1-native bars use Twelvedata's own day boundary. The candles are real; the boundaries were
misplaced. Consequences for the results: the statistical conclusions concern generic daily and 4-hour candles and are unlikely to hinge on the exact boundary,
BUT THAT IS NOT VERIFIED. **Before anything is built on Experiments 2-7, re-run the primary tests on correctly converted bars** (convert the hourly labels to
UTC with `Australia/Sydney` -> UTC, then aggregate). The pre-registered gates would be reused unchanged; this is a data-correctness re-run, not a new hypothesis.

### Status update (2026-09-19 evening)
The production pipeline now converts Twelvedata's Sydney-time labels to UTC (main, `fx_week_v2`) and reproduces TradingView (5 of 6 Crowd scores exact on Pieter's Data Window readings).
**The stores in `data/` here are still built on the OLD labels; Experiments 2-7 have NOT been re-run.** To do: rebuild the stores from `data/h1_cache/*_deep_h1.csv` via
`scanner/extend/fx_week.normalize_frame` (main), rebuild D1 NY-close and H4 NY/UTC bars, and re-run the primary tests with the pre-registered gates unchanged. Also check the port's `adx()`
against TradingView: on AUDUSD D1 26 Jan 2026 ours is 41.9 vs TradingView 35.8 (GBPUSD/CADJPY within 0.7); Experiments 6-7 used it for the strong-trend shading.

## Re-run of Experiments 2, 3, 4, 6 and 7 on CORRECTED bars (2026-09-19, evening)
Data-correctness re-run, NOT a new hypothesis: the pre-registered gates, parameters, race, baselines and seeds are unchanged; only the bars changed. Stores rebuilt by
`tools/build_fixed_stores.py` from the raw hourly cache: Sydney labels -> true UTC (main's `scanner/extend/fx_week.py`), closed-market rows dropped, then D1 on the 17:00-New-York close
(`data/fix_d1_ny`), H4 on the New-York-session blocks (`data/fix_h4_ny`, = the app's and TradingView's grid) and H4 on true UTC blocks (`data/fix_h4_utc`). Runner timeframes `d1c`, `h4cny`, `h4cutc`.
Not re-runnable: Experiment 5 and the D1 primaries of Experiments 6-7 (native daily bars back to 2009 have no hourly source; they use Twelvedata's own daily boundary, unchanged).

| Experiment (arm) | Original result | Corrected-bar result | Verdict |
|---|---|---|---|
| **2** D1 gradient, filter OFF (2021-26) | slope +0.041 per +20 pts (CI +0.018 .. +0.070); PASS all 7 gates | **slope +0.033 (CI +0.008 .. +0.062)**; halves +0.026 / +0.046; ex-JPY +0.043; every leave-one-pair-out > 0; **FAILS only gate 7** (composite not better than ATR-stretch alone) | small D1 tendency SURVIVES at a similar size; composite no better than one factor |
| 2, ADX filter effect (suppress minus off) | -0.015 (CI -0.026 .. -0.004) | -0.013 (CI -0.027 .. -0.000) | still evidence AGAINST the filter |
| **3** H4 UTC blocks, flagged, filter off | 176 events, lift -6.5% (CI -13.2 .. +0.6) | 193 events, lift **+1.9%** (CI -6.0 .. +10.5); gradient slope +0.005 | FAIL, unchanged in substance |
| 3 H4 NY-aligned | 206 events, lift +2.8% | 188 events, lift **-1.8%** (CI -10.0 .. +7.6); slope +0.006 | FAIL |
| **4** flags vs previous S/R, H4 UTC | 117 events, lift -3.4% (CI -9.7 .. +4.1) | 128 events, 22.7% vs 22.1%, lift **+0.6%** (CI -7.0 .. +8.1) | FAIL |
| **6** score > 30 vs opposite background, H4 UTC | 908 events, 47.9% vs 50.7%, lift -2.8% | 930 events, 50.9% vs 50.4%, lift **+0.5%** (CI -3.5 .. +4.9); fails F2-F5, F7 | NOT SUPPORTED |
| 6, D1 NY-close (info) | (D1 native 380 events, -0.5%) | 109 events, 57.8% vs 55.2%, lift +2.6% (CI -7.0 .. +13.1) | NOT SUPPORTED |
| **7** shading turns on after stretched score, H4 UTC | 800 events, 49.6% vs 48.9%, lift +0.7% | 813 events, 52.4% vs 52.0%, lift **+0.4%** (CI -2.2 .. +2.9) | NOT SUPPORTED |
| 7, D1 NY-close (info) | (D1 native 320 events, -1.6%) | 107 events, 49.5% vs 54.4%, lift -4.8% (CI -10.4 .. +2.1) | NOT SUPPORTED |

**Reading:** correcting the bar boundaries changed the numbers slightly and changed NO verdict. The one edge-like result, the small D1 tendency (+0.033 per +20 score points),
is still there and still small; the H4 findings, the S/R finding and both of Pieter's strategies remain coin flips. **Port check (AUDUSD D1 26 Jan 2026):** the research port's ADX (41.9) differs from TradingView's (35.8) on that bar while
GBPUSD/CADJPY agree within 0.7 — treated as price-feed noise (ADX is very sensitive to highs and lows), not a logic bug; it only affects the strong-trend shading used in Experiments 6-7.
Outputs: `data/backtest/crowded_reversal_{exp2,exp3,exp4,exp6,exp7}_fix_*`.

## Experiment 8 — D1 and H4 Crowd scores coincide (pre-registration, 2026-09-19)
**Written and committed BEFORE any outcome of this test has been computed.**

### Origin
Pieter, 2026-09-19: "What happens when the D1 and H4 crowded scores coincide? Any statistical edge there?" On the app's own grid: the CORRECTED bars (`d1c` = 17:00-NY close D1, `h4cny` = NY-session H4 blocks; 2021-2026, Legacy COT as before).
Experiments 2-7 (re-run on corrected bars) found only a small D1 tendency and nothing on H4 alone; this asks whether requiring BOTH timeframes at once concentrates anything. It is a NEW hypothesis, not a re-run.

### Definitions (all fixed now)
- **Score:** the Pine port's raw composite 0-100 top / bottom score (regime adjustment OFF). **T = 30, strictly greater** (Pieter's number, the primary). T = 60 (the flag line) is informational.
- **D1 state at an H4 bar:** the score of the most recent D1 bar COMPLETED BEFORE the H4 bar's trading day began (no look-ahead; conservative: even the day's last block uses the previous D1 bar).
- **Event (top):** rising edge of [H4 top score > T AND D1-state top score > T]; **bottom:** the same for the bottom score. De-clustered per pair as before.
- **Outcome:** the barrier race of Experiments 2-7 on H4 bars (1 x H4 ATR, 20 bars, same-bar-both -> continuation, timeout -> not a reversal), from the event bar's close. Success is a reversal, not profit.
- **Baseline (primary):** every H4 bar of the same pair, side and month (unconditional, month-clustered bootstrap 5000, seed 20260919). **Informational baseline** (`coincide_dmatched`): only H4 bars whose D1 state is > T on that side — does the H4 score add anything to the D1 state?
- **Comparison arm `h4_alone`:** H4 score > T while the D1 state is NOT > T (baseline unconditional).

### Primary test and gates (T = 30, H4 NY-aligned, `coincide` arm; ALL must hold)
F1 >= 100 de-clustered events (else INCONCLUSIVE). F2 lift >= 5 points AND 95% CI lower bound > 0. F3 lift > 0 in both halves (month-median split). F4 lift > 0 excluding JPY crosses. F5 lift > 0 excluding the best pair.
F6 the reversal rate > 50%. **F7 the `coincide` lift beats the `h4_alone` lift** (the D1 coincidence must add something to the H4 score alone).

### Secondary (informational, no gates)
T = 60 (**expected INCONCLUSIVE: ~7 events**); `coincide_dmatched`; `h4_alone`. **Not tested:** other thresholds (45 was only COUNTED, ~49 events, and is not planned), other timeframe pairs, D1-outcome variants, entries, exits, costs.

### Expected event counts (count-only, fixed 20-bar de-clustering, no outcome read)
T = 30: `coincide` ~233, `h4_alone` ~1,673. T = 60: `coincide` ~7, `h4_alone` ~125. (T = 45: `coincide` ~49, counted for reference only.)

### What has already been seen (full disclosure)
Experiments 2-7 in full, including the corrected-bar re-runs (H4 flags at T=60 and T=30-with-background were coin flips; the D1 gradient is small, +0.033 per +20 points); the count-only estimates above. No outcome of a D1-H4 coincidence event on any bar.
Multiplicity note: this is the ninth question asked of the same 12-pair, 2020-2026 data family; a PASS is a hypothesis for a FORWARD test, not a validated edge.
