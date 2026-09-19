# Crowded Market reversal — Experiment 2 (exp2_2026-09)

Mode `full` · reversal = 1 x ATR(14) barrier race, 20 bars, same-bar-both -> continuation, timeout -> not a reversal · bootstrap 5000 (month-clustered, seed 20260919)

Price: `data/d1_nyclose_long` (12 pairs), 68 months in the evaluation window. COT: Legacy futures-only 2006-01-03 .. 2026-09-15. Commit `bac23ac33f`.

## Verdicts (primary: score-gradient gates)

- **regime = suppress:** FAIL — 95% CI lower bound > 0; composite rho beats best single factor (stretch)
- **regime = off:** PASS

**ADX regime filter (suppress minus off, slope per +20 pts):** -0.015 (95% CI -0.026 .. -0.004) -> filter WORSENS the gradient (CI entirely below 0) — evidence against it.

## Composite score, regime = suppress

- Gradient slope: **0.026** reversal-probability per +20 score points (95% CI -0.002 .. 0.059); within-stratum correlation rho = 0.0232.
- Halves (split after 2023-10): first 0.003, second 0.052. Excluding JPY crosses: 0.037. Weakest leave-one-pair-out: 0.023 (dropping GBPJPY).
- rho vs best single factor (stretch): -0.0086 (paired 95% CI -0.0318 .. 0.0157).

| gate | result |
|---|---|
| n_months >= 48 | pass |
| slope per +20 pts >= 0.02 | pass |
| 95% CI lower bound > 0 | FAIL |
| both halves slope > 0 | pass |
| excluding JPY crosses slope > 0 | pass |
| every leave-one-pair-out slope > 0 (worst: GBPJPY) | pass |
| composite rho beats best single factor (stretch) | FAIL |

| score bucket | obs | reversal rate | baseline | lift | 95% CI |
|---|---|---|---|---|---|
| [0,10) | 30147 | 49.1% | 49.6% | -0.5% | -1.0% .. -0.1% |
| [10,20) | 1456 | 52.7% | 48.7% | 4.1% | -0.1% .. 8.8% |
| [20,30) | 2525 | 53.2% | 49.6% | 3.5% | -0.5% .. 7.8% |
| [30,40) | 410 | 47.6% | 48.3% | -0.7% | -7.1% .. 7.2% |
| [40,50) | 140 | 52.1% | 48.5% | 3.6% | -6.1% .. 15.5% |
| [50,60) | 97 | 54.6% | 48.3% | 6.3% | -12.5% .. 24.4% |
| [60,100] | 39 | 56.4% | 47.8% | 8.6% | -10.1% .. 35.3% |

## Composite score, regime = off

- Gradient slope: **0.041** reversal-probability per +20 score points (95% CI 0.018 .. 0.070); within-stratum correlation rho = 0.0410.
- Halves (split after 2023-10): first 0.032, second 0.058. Excluding JPY crosses: 0.046. Weakest leave-one-pair-out: 0.038 (dropping GBPJPY).
- rho vs best single factor (stretch): 0.0092 (paired 95% CI -0.0102 .. 0.0292).

| gate | result |
|---|---|
| n_months >= 48 | pass |
| slope per +20 pts >= 0.02 | pass |
| 95% CI lower bound > 0 | pass |
| both halves slope > 0 | pass |
| excluding JPY crosses slope > 0 | pass |
| every leave-one-pair-out slope > 0 (worst: GBPJPY) | pass |
| composite rho beats best single factor (stretch) | pass |

| score bucket | obs | reversal rate | baseline | lift | 95% CI |
|---|---|---|---|---|---|
| [0,10) | 28965 | 48.8% | 49.7% | -0.9% | -1.4% .. -0.4% |
| [10,20) | 1762 | 52.6% | 48.5% | 4.1% | 0.3% .. 8.4% |
| [20,30) | 3117 | 53.9% | 49.3% | 4.6% | 1.0% .. 8.3% |
| [30,40) | 550 | 50.4% | 48.2% | 2.2% | -3.2% .. 8.8% |
| [40,50) | 200 | 53.5% | 48.1% | 5.4% | -2.2% .. 15.1% |
| [50,60) | 163 | 58.3% | 47.7% | 10.6% | -2.0% .. 23.1% |
| [60,100] | 57 | 59.6% | 47.0% | 12.6% | -2.2% .. 33.8% |

## Single factors, same statistic (each factor's own contribution to the 0-100 scale)

| factor | slope per +20 pts | 95% CI | rho |
|---|---|---|---|
| pct_b | 0.050 | -0.031 .. 0.150 | 0.0120 |
| z | 0.138 | 0.020 .. 0.263 | 0.0277 |
| stretch | 0.173 | 0.052 .. 0.317 | 0.0318 |
| rsi | 0.133 | 0.064 .. 0.224 | 0.0314 |
| div | 0.042 | -0.004 .. 0.088 | 0.0199 |
| climax | 0.102 | -0.039 .. 0.266 | 0.0106 |
| cot | 0.034 | -0.024 .. 0.091 | 0.0167 |

## Secondary (informational): flagged events, score >= 60

- **score suppress:** 25 de-clustered events (reversal 16, continuation 9, timeout 0, same-bar ties 0); flagged 64.0% vs baseline 47.9% -> lift 16.1% (95% CI -0.6% .. 37.4%) — below the 100-event floor, read as INCONCLUSIVE.
- **score off:** 32 de-clustered events (reversal 20, continuation 12, timeout 0, same-bar ties 0); flagged 62.5% vs baseline 47.5% -> lift 15.0% (95% CI -0.7% .. 34.4%) — below the 100-event floor, read as INCONCLUSIVE.

## Known limits

- D1 OHLC cannot sequence intrabar: a bar touching both barriers is scored continuation, for every bar alike.
- Observations are ALL bars, so consecutive observations overlap and are serially dependent; the month-clustered bootstrap absorbs same-month dependence, not every cross-pair dependency (12 pairs share USD/JPY legs and COT legs).
- COT percentile is computed on a business-day calendar and reproduced TradingView's label to within ~0.3 percentile points on 2026-09-19 (net positions matched exactly); attributed, not verified, to TradingView counting chart bars.
- Evaluation history is what Twelvedata's H1 depth allows (~2020-01 onward) — one macro regime era, including the 2022 USD surge; a positive result here says nothing about earlier regimes.
