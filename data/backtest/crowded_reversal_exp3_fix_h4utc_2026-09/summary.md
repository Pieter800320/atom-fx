# Crowded Market reversal — exp3_fix_h4utc_2026-09

Mode `full` · timeframe `h4cutc` · primary = **flagged** · reversal = 1 x ATR(14) barrier race, 20 bars, same-bar-both -> continuation, timeout -> not a reversal · bootstrap 5000 (month-clustered, seed 20260919)

Price: `data/fix_h4_utc` (12 pairs), 77 months in the evaluation window. COT: Legacy futures-only 2006-01-03 .. 2026-09-15. Commit `32f464cf5e`.

## Verdicts

- **regime = suppress** — **flagged events (PRIMARY): FAIL — lift >= 0.05; 95% CI lower bound > 0; both halves lift > 0; excluding best pair (AUDUSD) lift > 0** · gradient (secondary): FAIL — slope per +20 pts >= 0.02; 95% CI lower bound > 0; both halves slope > 0; composite rho beats best single factor (stretch)
- **regime = off** — **flagged events (PRIMARY): FAIL — lift >= 0.05; 95% CI lower bound > 0; both halves lift > 0; excluding best pair (CADJPY) lift > 0** · gradient (secondary): FAIL — slope per +20 pts >= 0.02; 95% CI lower bound > 0; both halves slope > 0; composite rho beats best single factor (stretch)

**ADX regime filter (gradient slope, suppress minus off, per +20 pts):** 0.000 (95% CI -0.006 .. 0.007) -> NO demonstrated value: CI includes 0 — the data cannot justify the ADX filter.

## Flagged events (score >= 60, rising edge, de-clustered per pair)

### regime = suppress

- **106 de-clustered events** (reversal 53, continuation 53, timeout 0; same-bar ties 2). Top+bottom same-bar conflicts skipped: 0; flags on censored bars: 0.
- Flagged reversal rate 50.0% vs like-for-like baseline 48.6% -> **lift 1.4%** (95% CI -7.5% .. 11.6%).
- Halves (split after 2023-07): first -1.9% (n=58), second 5.5% (n=48). Excluding JPY crosses: 8.1% (n=49). Excluding best pair (AUDUSD): -1.7% (n=94).

| gate | result |
|---|---|
| n de-clustered events >= 100 | pass |
| lift >= 0.05 | FAIL |
| 95% CI lower bound > 0 | FAIL |
| both halves lift > 0 | FAIL |
| excluding JPY crosses lift > 0 | pass |
| excluding best pair (AUDUSD) lift > 0 | FAIL |
| beats best single-factor flagged lift (rsi) | pass |

| pair | events | lift |
|---|---|---|
| AUDJPY | 6 | -49.2% |
| AUDUSD | 12 | 26.0% |
| CADJPY | 8 | 25.5% |
| EURJPY | 7 | -4.2% |
| EURUSD | 9 | 17.8% |
| GBPJPY | 12 | -4.9% |
| GBPUSD | 8 | -36.5% |
| NZDJPY | 8 | 26.1% |
| NZDUSD | 7 | 8.1% |
| USDCAD | 1 | -49.4% |
| USDCHF | 12 | 17.5% |
| USDJPY | 16 | -17.4% |

### regime = off

- **193 de-clustered events** (reversal 98, continuation 95, timeout 0; same-bar ties 4). Top+bottom same-bar conflicts skipped: 0; flags on censored bars: 1.
- Flagged reversal rate 50.8% vs like-for-like baseline 48.8% -> **lift 1.9%** (95% CI -6.0% .. 10.5%).
- Halves (split after 2023-07): first -0.4% (n=104), second 4.9% (n=89). Excluding JPY crosses: 2.6% (n=91). Excluding best pair (CADJPY): -0.2% (n=177).

| gate | result |
|---|---|
| n de-clustered events >= 100 | pass |
| lift >= 0.05 | FAIL |
| 95% CI lower bound > 0 | FAIL |
| both halves lift > 0 | FAIL |
| excluding JPY crosses lift > 0 | pass |
| excluding best pair (CADJPY) lift > 0 | FAIL |
| beats best single-factor flagged lift (rsi) | pass |

| pair | events | lift |
|---|---|---|
| AUDJPY | 14 | -20.3% |
| AUDUSD | 17 | 15.9% |
| CADJPY | 16 | 25.4% |
| EURJPY | 14 | -5.5% |
| EURUSD | 16 | -11.4% |
| GBPJPY | 17 | -5.7% |
| GBPUSD | 15 | -15.7% |
| NZDJPY | 14 | 22.1% |
| NZDUSD | 17 | 15.7% |
| USDCAD | 3 | -49.4% |
| USDCHF | 23 | 11.5% |
| USDJPY | 27 | -4.6% |

### Single factors, same flagged-event test (each factor's own rising edge)

| factor | events | flagged | baseline | lift | 95% CI |
|---|---|---|---|---|---|
| pct_b | 5896 | 49.4% | 49.0% | 0.4% | -1.3% .. 2.1% |
| z | 2756 | 48.3% | 48.9% | -0.5% | -2.9% .. 1.8% |
| stretch | 3226 | 48.8% | 48.8% | -0.1% | -2.1% .. 1.9% |
| rsi | 3007 | 50.0% | 48.8% | 1.1% | -1.1% .. 3.5% |
| div | 1674 | 49.6% | 48.8% | 0.8% | -2.1% .. 3.7% |
| climax | 2691 | 49.6% | 49.2% | 0.4% | -2.3% .. 3.2% |
| cot | 159 | 49.1% | 49.0% | 0.0% | -7.2% .. 7.7% |

## Single factor vs the whole composite (filter off) — paired month-clustered bootstrap on within-stratum rho

Replication of the composite's gradient (slope >= 0.02 with CI lower > 0): **CONTRADICTED**.
Rule (pre-registered, Experiment 5): single factor AS GOOD if the CI of rho(single) - rho(composite) has lower bound > -0.01; composite BETTER if the CI upper bound < 0; otherwise INCONCLUSIVE.

| single factor | rho | rho(single) - rho(composite) | 95% CI | reading |
|---|---|---|---|---|
| pct_b | 0.0027 | -0.0029 | -0.0125 .. 0.0063 | INCONCLUSIVE |
| z | 0.0019 | -0.0037 | -0.0114 .. 0.0042 | INCONCLUSIVE |
| stretch | 0.0065 | 0.0009 | -0.0069 .. 0.0093 | single factor AS GOOD (non-inferior) |
| rsi | 0.0040 | -0.0016 | -0.0103 .. 0.0074 | INCONCLUSIVE |
| div | 0.0037 | -0.0019 | -0.0134 .. 0.0090 | INCONCLUSIVE |
| climax | 0.0013 | -0.0043 | -0.0148 .. 0.0052 | INCONCLUSIVE |
| cot | 0.0010 | -0.0046 | -0.0181 .. 0.0088 | INCONCLUSIVE |

## Score gradient (every bar)

### regime = suppress

- Slope **0.006** reversal-probability per +20 score points (95% CI -0.004 .. 0.017); rho = 0.0051.
- Halves (split after 2023-07): -0.001 / 0.014. Excluding JPY crosses: 0.011. Weakest leave-one-pair-out: 0.004 (dropping AUDUSD).
- rho vs best single factor (stretch): -0.0014 (paired 95% CI -0.0139 .. 0.0104).

| gate | result |
|---|---|
| n_months >= 48 | pass |
| slope per +20 pts >= 0.02 | FAIL |
| 95% CI lower bound > 0 | FAIL |
| both halves slope > 0 | FAIL |
| excluding JPY crosses slope > 0 | pass |
| every leave-one-pair-out slope > 0 (worst: AUDUSD) | pass |
| composite rho beats best single factor (stretch) | FAIL |

| score bucket | obs | reversal rate | baseline | lift | 95% CI |
|---|---|---|---|---|---|
| [0,10) | 198511 | 48.9% | 49.0% | -0.1% | -0.3% .. 0.1% |
| [10,20) | 8320 | 49.3% | 48.8% | 0.4% | -1.3% .. 2.2% |
| [20,30) | 18068 | 49.8% | 49.1% | 0.7% | -0.7% .. 2.1% |
| [30,40) | 2742 | 49.5% | 48.9% | 0.6% | -2.5% .. 3.9% |
| [40,50) | 947 | 51.0% | 48.9% | 2.1% | -2.4% .. 6.9% |
| [50,60) | 589 | 47.2% | 49.0% | -1.8% | -7.4% .. 4.4% |
| [60,100] | 173 | 47.4% | 48.5% | -1.1% | -10.7% .. 10.3% |

### regime = off

- Slope **0.005** reversal-probability per +20 score points (95% CI -0.005 .. 0.017); rho = 0.0056.
- Halves (split after 2023-07): -0.002 / 0.015. Excluding JPY crosses: 0.008. Weakest leave-one-pair-out: 0.003 (dropping AUDUSD).
- rho vs best single factor (stretch): -0.0009 (paired 95% CI -0.0093 .. 0.0069).

| gate | result |
|---|---|
| n_months >= 48 | pass |
| slope per +20 pts >= 0.02 | FAIL |
| 95% CI lower bound > 0 | FAIL |
| both halves slope > 0 | FAIL |
| excluding JPY crosses slope > 0 | pass |
| every leave-one-pair-out slope > 0 (worst: AUDUSD) | pass |
| composite rho beats best single factor (stretch) | FAIL |

| score bucket | obs | reversal rate | baseline | lift | 95% CI |
|---|---|---|---|---|---|
| [0,10) | 187061 | 48.9% | 49.0% | -0.2% | -0.4% .. 0.1% |
| [10,20) | 10944 | 49.8% | 48.8% | 1.0% | -0.5% .. 2.6% |
| [20,30) | 23361 | 49.9% | 49.1% | 0.8% | -0.5% .. 2.2% |
| [30,40) | 4678 | 48.4% | 48.8% | -0.4% | -3.1% .. 2.6% |
| [40,50) | 1762 | 49.7% | 48.9% | 0.7% | -3.2% .. 4.9% |
| [50,60) | 1181 | 48.5% | 49.1% | -0.6% | -4.6% .. 4.0% |
| [60,100] | 363 | 48.5% | 48.8% | -0.4% | -7.3% .. 7.7% |

## Exploratory: ranging vs trending bars (filter-off score; NO gates, cannot promote or rescue a verdict)

'Trending' = the Pine's debounced ADX>=30 + EMA200-slope state (either direction) at the bar; 'ranging' = neither. Baselines are computed inside each stratum.

| stratum | observations | gradient slope per +20 pts (95% CI) | flagged events | flagged lift (95% CI) |
|---|---|---|---|---|
| trending | 49934 | 0.005 (-0.012 .. 0.026) | 87 | 2.4% (-10.5% .. 14.9%) |
| ranging | 179416 | 0.006 (-0.005 .. 0.017) | 106 | 1.5% (-7.5% .. 11.6%) |

## Known limits

- D1/H4 OHLC cannot sequence intrabar: a bar touching both barriers is scored continuation, for every bar alike.
- Gradient observations are ALL bars, so consecutive observations overlap; the month-clustered bootstrap absorbs same-month dependence, not every cross-pair dependency (12 pairs share USD/JPY legs and COT legs).
- One macro era (~2020-2026, incl. the 2022 USD surge); nothing here speaks to earlier regimes.
- COT percentile is computed on a business-day calendar and reproduced TradingView's label to within ~0.3 percentile points.
- H4 stores: the provider omits about one H1 bar most Monday mornings (3-bar blocks); the UTC alignment keeps the Sunday-reopen and Friday-close STUB blocks (1-3 H1 bars), as the app's own aggregator does.
