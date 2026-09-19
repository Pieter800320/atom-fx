> **PLUMBING CHECK ONLY.** Ran the ported logic end-to-end on a short cache to prove it
> executes. **No edge conclusion may be drawn from any number below.**

# Crowded Market reversal — Experiment 2 (smoke_gradient_2026-09)

Mode `smoke` · reversal = 1 x ATR(14) barrier race, 20 bars, same-bar-both -> continuation, timeout -> not a reversal · bootstrap 500 (month-clustered, seed 20260919)

Price: `data/d1_nyclose` (12 pairs), 14 months in the evaluation window. COT: Legacy futures-only 2006-01-03 .. 2026-09-15. Commit `34a4d73b33`.

## Verdicts (primary: score-gradient gates)

- **regime = suppress:** PLUMBING ONLY — no verdict (smoke run on a short cache)
- **regime = off:** PLUMBING ONLY — no verdict (smoke run on a short cache)

**ADX regime filter (suppress minus off, slope per +20 pts):** -0.012 (95% CI -0.030 .. 0.005) -> NO demonstrated value: CI includes 0 — the data cannot justify the ADX filter.

## Composite score, regime = suppress

- Gradient slope: **0.104** reversal-probability per +20 score points (95% CI 0.042 .. 0.192); within-stratum correlation rho = 0.0812.
- Halves (split after 2026-01): first 0.058, second 0.181. Excluding JPY crosses: 0.090. Weakest leave-one-pair-out: 0.093 (dropping GBPJPY).
- rho vs best single factor (stretch): -0.0137 (paired 95% CI -0.0603 .. 0.0410).

| gate | result |
|---|---|
| n_months >= 48 | FAIL |
| slope per +20 pts >= 0.02 | pass |
| 95% CI lower bound > 0 | pass |
| both halves slope > 0 | pass |
| excluding JPY crosses slope > 0 | pass |
| every leave-one-pair-out slope > 0 (worst: GBPJPY) | pass |
| composite rho beats best single factor (stretch) | FAIL |

| score bucket | obs | reversal rate | baseline | lift | 95% CI |
|---|---|---|---|---|---|
| [0,10) | 6007 | 47.8% | 49.3% | -1.5% | -2.3% .. -0.9% |
| [10,20) | 293 | 62.5% | 47.3% | 15.2% | 10.7% .. 22.0% |
| [20,30) | 385 | 57.9% | 48.1% | 9.8% | 1.2% .. 18.8% |
| [30,40) | 64 | 56.2% | 45.3% | 11.0% | -0.8% .. 38.3% |
| [40,50) | 27 | 48.1% | 45.1% | 3.0% | -23.5% .. 34.3% |
| [50,60) | 10 | 60.0% | 43.1% | 16.9% | -28.5% .. 57.2% |
| [60,100] | 8 | 50.0% | 43.3% | 6.7% | -22.4% .. 61.1% |

## Composite score, regime = off

- Gradient slope: **0.116** reversal-probability per +20 score points (95% CI 0.059 .. 0.196); within-stratum correlation rho = 0.0960.
- Halves (split after 2026-01): first 0.083, second 0.175. Excluding JPY crosses: 0.109. Weakest leave-one-pair-out: 0.107 (dropping GBPJPY).
- rho vs best single factor (stretch): 0.0012 (paired 95% CI -0.0343 .. 0.0448).

| gate | result |
|---|---|
| n_months >= 48 | FAIL |
| slope per +20 pts >= 0.02 | pass |
| 95% CI lower bound > 0 | pass |
| both halves slope > 0 | pass |
| excluding JPY crosses slope > 0 | pass |
| every leave-one-pair-out slope > 0 (worst: GBPJPY) | pass |
| composite rho beats best single factor (stretch) | pass |

| score bucket | obs | reversal rate | baseline | lift | 95% CI |
|---|---|---|---|---|---|
| [0,10) | 5907 | 47.6% | 49.4% | -1.8% | -2.6% .. -1.3% |
| [10,20) | 329 | 61.7% | 47.0% | 14.7% | 10.3% .. 21.4% |
| [20,30) | 427 | 58.3% | 47.9% | 10.4% | 3.6% .. 17.8% |
| [30,40) | 79 | 60.8% | 45.3% | 15.5% | 3.3% .. 38.5% |
| [40,50) | 32 | 43.8% | 45.2% | -1.4% | -23.1% .. 24.4% |
| [50,60) | 11 | 63.6% | 43.3% | 20.3% | -18.1% .. 57.5% |
| [60,100] | 9 | 55.6% | 43.5% | 12.1% | -9.3% .. 61.1% |

## Single factors, same statistic (each factor's own contribution to the 0-100 scale)

| factor | slope per +20 pts | 95% CI | rho |
|---|---|---|---|
| pct_b | 0.246 | 0.117 .. 0.428 | 0.0567 |
| z | 0.471 | 0.274 .. 0.763 | 0.0912 |
| stretch | 0.586 | 0.344 .. 0.989 | 0.0948 |
| rsi | 0.452 | 0.289 .. 0.782 | 0.0846 |
| div | 0.004 | -0.071 .. 0.108 | 0.0019 |
| climax | 0.167 | -0.097 .. 0.472 | 0.0159 |
| cot | 0.138 | -0.005 .. 0.262 | 0.0458 |

## Secondary (informational): flagged events, score >= 60

- **score suppress:** 4 de-clustered events (reversal 2, continuation 2, timeout 0, same-bar ties 0); flagged 50.0% vs baseline 43.3% -> lift 6.7% (95% CI -42.0% .. 60.8%) — below the 100-event floor, read as INCONCLUSIVE.
- **score off:** 4 de-clustered events (reversal 2, continuation 2, timeout 0, same-bar ties 0); flagged 50.0% vs baseline 43.3% -> lift 6.7% (95% CI -42.0% .. 60.8%) — below the 100-event floor, read as INCONCLUSIVE.

## Known limits

- D1 OHLC cannot sequence intrabar: a bar touching both barriers is scored continuation, for every bar alike.
- Observations are ALL bars, so consecutive observations overlap and are serially dependent; the month-clustered bootstrap absorbs same-month dependence, not every cross-pair dependency (12 pairs share USD/JPY legs and COT legs).
- COT percentile is computed on a business-day calendar and reproduced TradingView's label to within ~0.3 percentile points on 2026-09-19 (net positions matched exactly); attributed, not verified, to TradingView counting chart bars.
- Evaluation history is what Twelvedata's H1 depth allows (~2020-01 onward) — one macro regime era, including the 2022 USD surge; a positive result here says nothing about earlier regimes.
