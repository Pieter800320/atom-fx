# Crowded Market reversal — exp5_eraA_2026-09

Mode `full` · timeframe `d1utc` · primary = **gradient** · reversal = 1 x ATR(14) barrier race, 20 bars, same-bar-both -> continuation, timeout -> not a reversal · bootstrap 5000 (month-clustered, seed 20260919)

Price: `data/d1_utc_daily` (12 pairs), 68 months in the evaluation window. COT: Legacy futures-only 2006-01-03 .. 2026-09-15. Commit `ebf0bee2e9`.

## Verdicts

- **regime = suppress** — **gradient (PRIMARY): FAIL — 95% CI lower bound > 0; composite rho beats best single factor (stretch)** · flagged events (secondary): INCONCLUSIVE — 17 de-clustered events < 100
- **regime = off** — **gradient (PRIMARY): FAIL — composite rho beats best single factor (stretch)** · flagged events (secondary): INCONCLUSIVE — 27 de-clustered events < 100

**ADX regime filter (gradient slope, suppress minus off, per +20 pts):** -0.013 (95% CI -0.026 .. -0.001) -> filter WORSENS the gradient (CI entirely below 0) — evidence against it.

## Flagged events (score >= 60, rising edge, de-clustered per pair)

### regime = suppress

- **17 de-clustered events** (reversal 10, continuation 7, timeout 0; same-bar ties 2). Top+bottom same-bar conflicts skipped: 0; flags on censored bars: 0.
- Flagged reversal rate 58.8% vs like-for-like baseline 48.6% -> **lift 10.2%** (95% CI -20.4% .. 44.7%).
- Halves (split after 2023-10): first 15.4% (n=11), second 1.6% (n=6). Excluding JPY crosses: 21.8% (n=7). Excluding best pair (USDCHF): 4.9% (n=15).

| gate | result |
|---|---|
| n de-clustered events >= 100 | FAIL |
| lift >= 0.05 | pass |
| 95% CI lower bound > 0 | FAIL |
| both halves lift > 0 | pass |
| excluding JPY crosses lift > 0 | pass |
| excluding best pair (USDCHF) lift > 0 | pass |
| beats best single-factor flagged lift (div) | pass |

| pair | events | lift |
|---|---|---|
| AUDJPY | 1 | -54.2% |
| AUDUSD | 0 | n/a |
| CADJPY | 2 | 0.6% |
| EURJPY | 0 | n/a |
| EURUSD | 1 | 49.5% |
| GBPJPY | 3 | 22.1% |
| GBPUSD | 2 | 0.3% |
| NZDJPY | 2 | -3.9% |
| NZDUSD | 2 | 1.1% |
| USDCAD | 0 | n/a |
| USDCHF | 2 | 50.1% |
| USDJPY | 2 | 7.7% |

### regime = off

- **27 de-clustered events** (reversal 18, continuation 9, timeout 0; same-bar ties 2). Top+bottom same-bar conflicts skipped: 0; flags on censored bars: 0.
- Flagged reversal rate 66.7% vs like-for-like baseline 47.8% -> **lift 18.8%** (95% CI -6.3% .. 42.8%).
- Halves (split after 2023-10): first 30.7% (n=18), second -3.5% (n=9). Excluding JPY crosses: 17.2% (n=12). Excluding best pair (USDJPY): 16.4% (n=23).

| gate | result |
|---|---|
| n de-clustered events >= 100 | FAIL |
| lift >= 0.05 | pass |
| 95% CI lower bound > 0 | FAIL |
| both halves lift > 0 | FAIL |
| excluding JPY crosses lift > 0 | pass |
| excluding best pair (USDJPY) lift > 0 | pass |
| beats best single-factor flagged lift (div) | pass |

| pair | events | lift |
|---|---|---|
| AUDJPY | 1 | -54.2% |
| AUDUSD | 2 | 0.1% |
| CADJPY | 3 | 18.3% |
| EURJPY | 1 | 56.4% |
| EURUSD | 1 | 49.5% |
| GBPJPY | 4 | 30.4% |
| GBPUSD | 3 | 16.5% |
| NZDJPY | 2 | -3.9% |
| NZDUSD | 3 | 17.4% |
| USDCAD | 1 | -45.1% |
| USDCHF | 2 | 50.1% |
| USDJPY | 4 | 32.7% |

### Single factors, same flagged-event test (each factor's own rising edge)

| factor | events | flagged | baseline | lift | 95% CI |
|---|---|---|---|---|---|
| pct_b | 916 | 50.5% | 49.1% | 1.4% | -3.6% .. 6.7% |
| z | 468 | 49.1% | 48.1% | 1.0% | -4.1% .. 6.7% |
| stretch | 461 | 51.8% | 47.8% | 4.0% | -1.3% .. 10.1% |
| rsi | 365 | 53.2% | 48.2% | 5.0% | -1.3% .. 11.9% |
| div | 271 | 53.9% | 48.0% | 5.8% | -1.3% .. 13.1% |
| climax | 258 | 43.4% | 50.5% | -7.0% | -15.0% .. 2.5% |
| cot | 65 | 49.2% | 49.5% | -0.2% | -11.8% .. 11.1% |

## Single factor vs the whole composite (filter off) — paired month-clustered bootstrap on within-stratum rho

Replication of the composite's gradient (slope >= 0.02 with CI lower > 0): **REPLICATES**.
Rule (pre-registered, Experiment 5): single factor AS GOOD if the CI of rho(single) - rho(composite) has lower bound > -0.01; composite BETTER if the CI upper bound < 0; otherwise INCONCLUSIVE.

| single factor | rho | rho(single) - rho(composite) | 95% CI | reading |
|---|---|---|---|---|
| pct_b | 0.0029 | -0.0324 | -0.0571 .. -0.0076 | composite BETTER |
| z | 0.0312 | -0.0041 | -0.0234 .. 0.0161 | INCONCLUSIVE |
| stretch | 0.0391 | 0.0038 | -0.0146 .. 0.0230 | INCONCLUSIVE |
| rsi | 0.0199 | -0.0155 | -0.0349 .. 0.0058 | INCONCLUSIVE |
| div | 0.0091 | -0.0262 | -0.0496 .. -0.0027 | composite BETTER |
| climax | -0.0091 | -0.0444 | -0.0719 .. -0.0166 | composite BETTER |
| cot | 0.0253 | -0.0100 | -0.0339 .. 0.0119 | INCONCLUSIVE |

## Score gradient (every bar)

### regime = suppress

- Slope **0.023** reversal-probability per +20 score points (95% CI -0.005 .. 0.053); rho = 0.0197.
- Halves (split after 2023-10): 0.014 / 0.036. Excluding JPY crosses: 0.044. Weakest leave-one-pair-out: 0.019 (dropping EURUSD).
- rho vs best single factor (stretch): -0.0195 (paired 95% CI -0.0434 .. 0.0019).

| gate | result |
|---|---|
| n_months >= 48 | pass |
| slope per +20 pts >= 0.02 | pass |
| 95% CI lower bound > 0 | FAIL |
| both halves slope > 0 | pass |
| excluding JPY crosses slope > 0 | pass |
| every leave-one-pair-out slope > 0 (worst: EURUSD) | pass |
| composite rho beats best single factor (stretch) | FAIL |

| score bucket | obs | reversal rate | baseline | lift | 95% CI |
|---|---|---|---|---|---|
| [0,10) | 30678 | 49.1% | 49.5% | -0.4% | -0.9% .. 0.0% |
| [10,20) | 1416 | 52.7% | 48.6% | 4.1% | -0.6% .. 9.0% |
| [20,30) | 2519 | 52.3% | 49.5% | 2.8% | -1.0% .. 6.3% |
| [30,40) | 403 | 49.4% | 48.2% | 1.2% | -5.6% .. 9.2% |
| [40,50) | 139 | 39.6% | 48.8% | -9.3% | -18.9% .. 3.6% |
| [50,60) | 105 | 56.2% | 48.5% | 7.7% | -6.9% .. 19.7% |
| [60,100] | 28 | 60.7% | 48.6% | 12.1% | -9.1% .. 46.2% |

### regime = off

- Slope **0.036** reversal-probability per +20 score points (95% CI 0.011 .. 0.063); rho = 0.0353.
- Halves (split after 2023-10): 0.034 / 0.046. Excluding JPY crosses: 0.048. Weakest leave-one-pair-out: 0.033 (dropping EURUSD).
- rho vs best single factor (stretch): -0.0038 (paired 95% CI -0.0230 .. 0.0146).

| gate | result |
|---|---|
| n_months >= 48 | pass |
| slope per +20 pts >= 0.02 | pass |
| 95% CI lower bound > 0 | pass |
| both halves slope > 0 | pass |
| excluding JPY crosses slope > 0 | pass |
| every leave-one-pair-out slope > 0 (worst: EURUSD) | pass |
| composite rho beats best single factor (stretch) | FAIL |

| score bucket | obs | reversal rate | baseline | lift | 95% CI |
|---|---|---|---|---|---|
| [0,10) | 29393 | 48.8% | 49.6% | -0.8% | -1.3% .. -0.3% |
| [10,20) | 1714 | 53.5% | 48.6% | 4.9% | 0.9% .. 9.4% |
| [20,30) | 3180 | 52.5% | 49.2% | 3.3% | -0.2% .. 6.8% |
| [30,40) | 559 | 52.6% | 47.9% | 4.7% | -1.1% .. 11.4% |
| [40,50) | 217 | 46.1% | 48.6% | -2.5% | -9.5% .. 6.5% |
| [50,60) | 182 | 55.5% | 47.6% | 7.9% | -1.5% .. 16.5% |
| [60,100] | 43 | 65.1% | 48.0% | 17.1% | -1.9% .. 41.2% |

## Exploratory: ranging vs trending bars (filter-off score; NO gates, cannot promote or rescue a verdict)

'Trending' = the Pine's debounced ADX>=30 + EMA200-slope state (either direction) at the bar; 'ranging' = neither. Baselines are computed inside each stratum.

| stratum | observations | gradient slope per +20 pts (95% CI) | flagged events | flagged lift (95% CI) |
|---|---|---|---|---|
| trending | 5854 | 0.062 (0.024 .. 0.110) | 11 | 32.9% (6.0% .. 53.5%) |
| ranging | 29434 | 0.024 (-0.004 .. 0.057) | 16 | 8.6% (-24.2% .. 45.0%) |

## Known limits

- D1/H4 OHLC cannot sequence intrabar: a bar touching both barriers is scored continuation, for every bar alike.
- Gradient observations are ALL bars, so consecutive observations overlap; the month-clustered bootstrap absorbs same-month dependence, not every cross-pair dependency (12 pairs share USD/JPY legs and COT legs).
- One macro era (~2020-2026, incl. the 2022 USD surge); nothing here speaks to earlier regimes.
- COT percentile is computed on a business-day calendar and reproduced TradingView's label to within ~0.3 percentile points.
- H4 stores: the provider omits about one H1 bar most Monday mornings (3-bar blocks); the UTC alignment keeps the Sunday-reopen and Friday-close STUB blocks (1-3 H1 bars), as the app's own aggregator does.
