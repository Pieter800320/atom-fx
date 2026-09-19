# Crowded Market reversal — exp2_fix_2026-09

Mode `full` · timeframe `d1c` · primary = **gradient** · reversal = 1 x ATR(14) barrier race, 20 bars, same-bar-both -> continuation, timeout -> not a reversal · bootstrap 5000 (month-clustered, seed 20260919)

Price: `data/fix_d1_ny` (12 pairs), 68 months in the evaluation window. COT: Legacy futures-only 2006-01-03 .. 2026-09-15. Commit `32f464cf5e`.

## Verdicts

- **regime = suppress** — **gradient (PRIMARY): FAIL — 95% CI lower bound > 0; composite rho beats best single factor (stretch)** · flagged events (secondary): INCONCLUSIVE — 16 de-clustered events < 100
- **regime = off** — **gradient (PRIMARY): FAIL — composite rho beats best single factor (stretch)** · flagged events (secondary): INCONCLUSIVE — 27 de-clustered events < 100

**ADX regime filter (gradient slope, suppress minus off, per +20 pts):** -0.013 (95% CI -0.027 .. -0.000) -> filter WORSENS the gradient (CI entirely below 0) — evidence against it.

## Flagged events (score >= 60, rising edge, de-clustered per pair)

### regime = suppress

- **16 de-clustered events** (reversal 9, continuation 7, timeout 0; same-bar ties 1). Top+bottom same-bar conflicts skipped: 0; flags on censored bars: 0.
- Flagged reversal rate 56.2% vs like-for-like baseline 48.5% -> **lift 7.8%** (95% CI -26.4% .. 41.1%).
- Halves (split after 2023-10): first 12.9% (n=10), second 1.4% (n=6). Excluding JPY crosses: 18.0% (n=6). Excluding best pair (USDCAD): 4.7% (n=15).

| gate | result |
|---|---|
| n de-clustered events >= 100 | FAIL |
| lift >= 0.05 | pass |
| 95% CI lower bound > 0 | FAIL |
| both halves lift > 0 | pass |
| excluding JPY crosses lift > 0 | pass |
| excluding best pair (USDCAD) lift > 0 | pass |
| beats best single-factor flagged lift (cot) | pass |

| pair | events | lift |
|---|---|---|
| AUDJPY | 1 | -53.7% |
| AUDUSD | 0 | n/a |
| CADJPY | 3 | 18.0% |
| EURJPY | 0 | n/a |
| EURUSD | 0 | n/a |
| GBPJPY | 2 | 4.4% |
| GBPUSD | 2 | 0.1% |
| NZDJPY | 2 | -3.6% |
| NZDUSD | 2 | 1.0% |
| USDCAD | 1 | 54.6% |
| USDCHF | 1 | 51.2% |
| USDJPY | 2 | 7.4% |

### regime = off

- **27 de-clustered events** (reversal 17, continuation 10, timeout 0; same-bar ties 1). Top+bottom same-bar conflicts skipped: 0; flags on censored bars: 0.
- Flagged reversal rate 63.0% vs like-for-like baseline 48.0% -> **lift 15.0%** (95% CI -9.3% .. 39.2%).
- Halves (split after 2023-10): first 29.5% (n=17), second -8.3% (n=10). Excluding JPY crosses: 9.2% (n=12). Excluding best pair (USDJPY): 11.9% (n=23).

| gate | result |
|---|---|
| n de-clustered events >= 100 | FAIL |
| lift >= 0.05 | pass |
| 95% CI lower bound > 0 | FAIL |
| both halves lift > 0 | FAIL |
| excluding JPY crosses lift > 0 | pass |
| excluding best pair (USDJPY) lift > 0 | pass |
| beats best single-factor flagged lift (cot) | pass |

| pair | events | lift |
|---|---|---|
| AUDJPY | 1 | -53.7% |
| AUDUSD | 2 | 0.1% |
| CADJPY | 4 | 26.6% |
| EURJPY | 1 | 55.4% |
| EURUSD | 2 | -1.2% |
| GBPJPY | 3 | 21.1% |
| GBPUSD | 3 | 16.6% |
| NZDJPY | 2 | -3.6% |
| NZDUSD | 2 | 1.0% |
| USDCAD | 2 | 4.6% |
| USDCHF | 1 | 51.2% |
| USDJPY | 4 | 32.4% |

### Single factors, same flagged-event test (each factor's own rising edge)

| factor | events | flagged | baseline | lift | 95% CI |
|---|---|---|---|---|---|
| pct_b | 906 | 50.8% | 49.2% | 1.6% | -3.3% .. 7.0% |
| z | 462 | 50.6% | 48.3% | 2.3% | -3.1% .. 8.2% |
| stretch | 472 | 52.3% | 48.0% | 4.3% | -1.6% .. 10.9% |
| rsi | 368 | 54.1% | 48.4% | 5.6% | -0.6% .. 13.0% |
| div | 268 | 50.0% | 48.3% | 1.7% | -5.7% .. 9.5% |
| climax | 270 | 45.9% | 50.2% | -4.2% | -12.4% .. 5.7% |
| cot | 62 | 56.5% | 49.4% | 7.1% | -4.4% .. 17.7% |

## Single factor vs the whole composite (filter off) — paired month-clustered bootstrap on within-stratum rho

Replication of the composite's gradient (slope >= 0.02 with CI lower > 0): **REPLICATES**.
Rule (pre-registered, Experiment 5): single factor AS GOOD if the CI of rho(single) - rho(composite) has lower bound > -0.01; composite BETTER if the CI upper bound < 0; otherwise INCONCLUSIVE.

| single factor | rho | rho(single) - rho(composite) | 95% CI | reading |
|---|---|---|---|---|
| pct_b | 0.0045 | -0.0282 | -0.0531 .. -0.0022 | composite BETTER |
| z | 0.0305 | -0.0022 | -0.0212 .. 0.0175 | INCONCLUSIVE |
| stretch | 0.0395 | 0.0068 | -0.0110 .. 0.0251 | INCONCLUSIVE |
| rsi | 0.0206 | -0.0121 | -0.0322 .. 0.0096 | INCONCLUSIVE |
| div | 0.0058 | -0.0269 | -0.0516 .. -0.0029 | composite BETTER |
| climax | -0.0039 | -0.0366 | -0.0642 .. -0.0093 | composite BETTER |
| cot | 0.0215 | -0.0112 | -0.0369 .. 0.0124 | INCONCLUSIVE |

## Score gradient (every bar)

### regime = suppress

- Slope **0.020** reversal-probability per +20 score points (95% CI -0.008 .. 0.051); rho = 0.0169.
- Halves (split after 2023-10): 0.006 / 0.036. Excluding JPY crosses: 0.036. Weakest leave-one-pair-out: 0.016 (dropping EURUSD).
- rho vs best single factor (stretch): -0.0226 (paired 95% CI -0.0459 .. -0.0012).

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
| [0,10) | 30300 | 49.2% | 49.6% | -0.4% | -0.8% .. 0.0% |
| [10,20) | 1417 | 52.6% | 48.7% | 3.8% | -0.9% .. 8.8% |
| [20,30) | 2418 | 52.5% | 49.5% | 3.1% | -0.7% .. 6.8% |
| [30,40) | 398 | 47.7% | 48.4% | -0.6% | -8.0% .. 7.9% |
| [40,50) | 130 | 43.8% | 48.7% | -4.9% | -14.5% .. 7.3% |
| [50,60) | 78 | 50.0% | 47.9% | 2.1% | -13.5% .. 19.4% |
| [60,100] | 27 | 51.9% | 49.1% | 2.7% | -19.1% .. 37.7% |

### regime = off

- Slope **0.033** reversal-probability per +20 score points (95% CI 0.008 .. 0.062); rho = 0.0327.
- Halves (split after 2023-10): 0.026 / 0.046. Excluding JPY crosses: 0.043. Weakest leave-one-pair-out: 0.031 (dropping NZDUSD).
- rho vs best single factor (stretch): -0.0068 (paired 95% CI -0.0251 .. 0.0110).

| gate | result |
|---|---|
| n_months >= 48 | pass |
| slope per +20 pts >= 0.02 | pass |
| 95% CI lower bound > 0 | pass |
| both halves slope > 0 | pass |
| excluding JPY crosses slope > 0 | pass |
| every leave-one-pair-out slope > 0 (worst: NZDUSD) | pass |
| composite rho beats best single factor (stretch) | FAIL |

| score bucket | obs | reversal rate | baseline | lift | 95% CI |
|---|---|---|---|---|---|
| [0,10) | 28999 | 48.9% | 49.6% | -0.7% | -1.2% .. -0.2% |
| [10,20) | 1697 | 53.2% | 48.7% | 4.5% | 0.3% .. 9.1% |
| [20,30) | 3093 | 52.6% | 49.2% | 3.4% | -0.3% .. 7.0% |
| [30,40) | 559 | 49.9% | 48.1% | 1.8% | -4.3% .. 8.8% |
| [40,50) | 210 | 51.4% | 48.5% | 3.0% | -4.2% .. 11.1% |
| [50,60) | 165 | 55.2% | 47.6% | 7.5% | -2.1% .. 18.4% |
| [60,100] | 45 | 55.6% | 48.7% | 6.9% | -11.7% .. 34.5% |

## Exploratory: ranging vs trending bars (filter-off score; NO gates, cannot promote or rescue a verdict)

'Trending' = the Pine's debounced ADX>=30 + EMA200-slope state (either direction) at the bar; 'ranging' = neither. Baselines are computed inside each stratum.

| stratum | observations | gradient slope per +20 pts (95% CI) | flagged events | flagged lift (95% CI) |
|---|---|---|---|---|
| trending | 5944 | 0.053 (0.014 .. 0.101) | 12 | 24.9% (-2.4% .. 49.2%) |
| ranging | 28824 | 0.023 (-0.006 .. 0.055) | 15 | 5.9% (-27.2% .. 41.4%) |

## Known limits

- D1/H4 OHLC cannot sequence intrabar: a bar touching both barriers is scored continuation, for every bar alike.
- Gradient observations are ALL bars, so consecutive observations overlap; the month-clustered bootstrap absorbs same-month dependence, not every cross-pair dependency (12 pairs share USD/JPY legs and COT legs).
- One macro era (~2020-2026, incl. the 2022 USD surge); nothing here speaks to earlier regimes.
- COT percentile is computed on a business-day calendar and reproduced TradingView's label to within ~0.3 percentile points.
- H4 stores: the provider omits about one H1 bar most Monday mornings (3-bar blocks); the UTC alignment keeps the Sunday-reopen and Friday-close STUB blocks (1-3 H1 bars), as the app's own aggregator does.
