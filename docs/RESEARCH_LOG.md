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

## Parked ideas
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
- **CSM H1 flow extremes, aligned with the recommendation/trend (Pieter's idea, 2026-09-17).**
  Observation: when one currency's H1 flow (`csm_delta.h1`) is near its extreme high and the
  other leg's is near its extreme low, in the same direction the recommendation/trend/momentum
  already point, that fast H1 read might catch a pullback resuming into continuation before
  slower timeframes show it. **Two concrete risks to test for, not assume away, before trusting
  this:**
  (1) CSM is min-max normalised to 0-100 *every single scan* (`csm.py::_normalise`'s own doc
  comment: "min-max rescaling always stretches whatever gap exists to fill exactly 0-100 every
  scan") — some currency is ALWAYS near 100 and some ALWAYS near 0, even in a flat, undifferentiated
  market. "Maximal/minimal" as stated doesn't distinguish a real divergence from routine noise in
  a thin basket. The project already solved this exact problem for the strength reading
  (`scanner/extend/csm_dispersion.py`, gates on `raw_spread` percentile-ranked against its own
  rolling history, not a fixed threshold, since a raw threshold "looks the same whether there's
  real cross-currency signal or none") — the same dispersion-aware gating would need to apply to
  the delta/flow measure too, or the signal fires constantly and mostly means nothing.
  (2) A sharp flow spike on a fast timeframe (H1) is at least as consistent with short-term
  exhaustion about to mean-revert as it is with genuine continuation starting — "fast" cuts both
  ways, catches real shifts early AND catches noise early. This needs an actual empirical test,
  not intuition, and is the same ambiguity (momentum vs. mean-reversion at an extreme) that's
  historically hard to resolve without rigorous out-of-sample testing.
  Also worth checking before building: how much `csm_delta` already overlaps with what
  `rank_pairs()` itself scores (its `mom_dlt`/`csm` components) — if there's heavy overlap, this
  gate may be a stricter/later read of the same signal the recommendation already uses, not
  independent confirming evidence. Same cross-pair replay infrastructure cost as the
  recommendation-glyph idea above (CSM needs all 12 pairs at once). Entry timing (at the flow
  extreme itself, or once it starts reverting toward normal), and SL/TP, not yet specified.
  Not pre-registered.
