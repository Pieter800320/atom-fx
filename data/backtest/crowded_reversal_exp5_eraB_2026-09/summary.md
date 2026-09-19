# Crowded Market reversal — exp5_eraB_2026-09

Mode `full` · timeframe `d1utc` · primary = **gradient** · reversal = 1 x ATR(14) barrier race, 20 bars, same-bar-both -> continuation, timeout -> not a reversal · bootstrap 5000 (month-clustered, seed 20260919)

Price: `data/d1_utc_daily` (12 pairs), 144 months in the evaluation window. COT: Legacy futures-only 2006-01-03 .. 2026-09-15. Commit `ebf0bee2e9`.

## Verdicts

- **regime = suppress** — **gradient (PRIMARY): FAIL — slope per +20 pts >= 0.02; 95% CI lower bound > 0; both halves slope > 0; excluding JPY crosses slope > 0; every leave-one-pair-out slope > 0 (worst: EURJPY); composite rho beats best single factor (div)** · flagged events (secondary): INCONCLUSIVE — 32 de-clustered events < 100
- **regime = off** — **gradient (PRIMARY): FAIL — slope per +20 pts >= 0.02; 95% CI lower bound > 0; both halves slope > 0; excluding JPY crosses slope > 0; every leave-one-pair-out slope > 0 (worst: EURJPY); composite rho beats best single factor (div)** · flagged events (secondary): INCONCLUSIVE — 92 de-clustered events < 100

**ADX regime filter (gradient slope, suppress minus off, per +20 pts):** -0.000 (95% CI -0.012 .. 0.012) -> NO demonstrated value: CI includes 0 — the data cannot justify the ADX filter.

## Flagged events (score >= 60, rising edge, de-clustered per pair)

### regime = suppress

- **32 de-clustered events** (reversal 13, continuation 19, timeout 0; same-bar ties 1). Top+bottom same-bar conflicts skipped: 0; flags on censored bars: 0.
- Flagged reversal rate 40.6% vs like-for-like baseline 49.5% -> **lift -8.9%** (95% CI -22.7% .. 6.4%).
- Halves (split after 2014-12): first -5.9% (n=18), second -12.7% (n=14). Excluding JPY crosses: -5.5% (n=25). Excluding best pair (EURUSD): -14.8% (n=23).

| gate | result |
|---|---|
| n de-clustered events >= 100 | FAIL |
| lift >= 0.05 | FAIL |
| 95% CI lower bound > 0 | FAIL |
| both halves lift > 0 | FAIL |
| excluding JPY crosses lift > 0 | FAIL |
| excluding best pair (EURUSD) lift > 0 | FAIL |
| beats best single-factor flagged lift (div) | FAIL |

| pair | events | lift |
|---|---|---|
| AUDJPY | 2 | -1.2% |
| AUDUSD | 4 | -49.6% |
| CADJPY | 0 | n/a |
| EURJPY | 1 | 52.2% |
| EURUSD | 9 | 6.2% |
| GBPJPY | 1 | -48.2% |
| GBPUSD | 7 | 7.3% |
| NZDJPY | 0 | n/a |
| NZDUSD | 1 | -52.4% |
| USDCAD | 0 | n/a |
| USDCHF | 4 | 1.4% |
| USDJPY | 3 | -49.2% |

### regime = off

- **92 de-clustered events** (reversal 42, continuation 50, timeout 0; same-bar ties 2). Top+bottom same-bar conflicts skipped: 0; flags on censored bars: 0.
- Flagged reversal rate 45.7% vs like-for-like baseline 49.9% -> **lift -4.2%** (95% CI -14.5% .. 7.8%).
- Halves (split after 2014-12): first -0.7% (n=50), second -8.6% (n=42). Excluding JPY crosses: -12.1% (n=53). Excluding best pair (NZDJPY): -7.6% (n=85).

| gate | result |
|---|---|
| n de-clustered events >= 100 | FAIL |
| lift >= 0.05 | FAIL |
| 95% CI lower bound > 0 | FAIL |
| both halves lift > 0 | FAIL |
| excluding JPY crosses lift > 0 | FAIL |
| excluding best pair (NZDJPY) lift > 0 | FAIL |
| beats best single-factor flagged lift (div) | FAIL |

| pair | events | lift |
|---|---|---|
| AUDJPY | 13 | -4.4% |
| AUDUSD | 5 | -49.8% |
| CADJPY | 6 | 16.5% |
| EURJPY | 3 | 16.4% |
| EURUSD | 23 | -2.1% |
| GBPJPY | 6 | -0.0% |
| GBPUSD | 11 | 4.7% |
| NZDJPY | 7 | 36.7% |
| NZDUSD | 6 | -34.0% |
| USDCAD | 2 | -49.8% |
| USDCHF | 6 | -15.6% |
| USDJPY | 4 | -24.1% |

### Single factors, same flagged-event test (each factor's own rising edge)

| factor | events | flagged | baseline | lift | 95% CI |
|---|---|---|---|---|---|
| pct_b | 1863 | 48.3% | 49.6% | -1.4% | -4.2% .. 1.7% |
| z | 861 | 48.1% | 49.7% | -1.6% | -5.4% .. 2.7% |
| stretch | 868 | 48.6% | 49.6% | -1.0% | -4.7% .. 3.2% |
| rsi | 852 | 45.7% | 49.7% | -4.0% | -8.0% .. 0.3% |
| div | 490 | 53.3% | 49.6% | 3.7% | -0.9% .. 8.2% |
| climax | 459 | 42.3% | 49.6% | -7.3% | -12.7% .. -1.4% |
| cot | 165 | 49.1% | 49.9% | -0.8% | -8.6% .. 7.5% |

## Single factor vs the whole composite (filter off) — paired month-clustered bootstrap on within-stratum rho

Replication of the composite's gradient (slope >= 0.02 with CI lower > 0): **CONTRADICTED**.
Rule (pre-registered, Experiment 5): single factor AS GOOD if the CI of rho(single) - rho(composite) has lower bound > -0.01; composite BETTER if the CI upper bound < 0; otherwise INCONCLUSIVE.

| single factor | rho | rho(single) - rho(composite) | 95% CI | reading |
|---|---|---|---|---|
| pct_b | -0.0012 | 0.0001 | -0.0174 .. 0.0171 | INCONCLUSIVE |
| z | -0.0043 | -0.0031 | -0.0161 .. 0.0104 | INCONCLUSIVE |
| stretch | 0.0033 | 0.0046 | -0.0075 .. 0.0164 | single factor AS GOOD (non-inferior) |
| rsi | -0.0066 | -0.0053 | -0.0198 .. 0.0089 | INCONCLUSIVE |
| div | 0.0095 | 0.0107 | -0.0072 .. 0.0273 | single factor AS GOOD (non-inferior) |
| climax | -0.0096 | -0.0083 | -0.0298 .. 0.0119 | INCONCLUSIVE |
| cot | -0.0044 | -0.0031 | -0.0196 .. 0.0128 | INCONCLUSIVE |

## Score gradient (every bar)

### regime = suppress

- Slope **-0.001** reversal-probability per +20 score points (95% CI -0.019 .. 0.018); rho = -0.0011.
- Halves (split after 2014-12): 0.014 / -0.016. Excluding JPY crosses: -0.011. Weakest leave-one-pair-out: -0.004 (dropping EURJPY).
- rho vs best single factor (div): -0.0105 (paired 95% CI -0.0234 .. 0.0036).

| gate | result |
|---|---|
| n_months >= 48 | pass |
| slope per +20 pts >= 0.02 | FAIL |
| 95% CI lower bound > 0 | FAIL |
| both halves slope > 0 | FAIL |
| excluding JPY crosses slope > 0 | FAIL |
| every leave-one-pair-out slope > 0 (worst: EURJPY) | FAIL |
| composite rho beats best single factor (div) | FAIL |

| score bucket | obs | reversal rate | baseline | lift | 95% CI |
|---|---|---|---|---|---|
| [0,10) | 65440 | 49.7% | 49.7% | 0.1% | -0.3% .. 0.3% |
| [10,20) | 2904 | 49.0% | 49.7% | -0.7% | -3.7% .. 2.6% |
| [20,30) | 4950 | 49.0% | 49.8% | -0.8% | -3.5% .. 1.9% |
| [30,40) | 737 | 51.4% | 49.8% | 1.6% | -3.6% .. 6.9% |
| [40,50) | 223 | 51.1% | 49.7% | 1.4% | -5.5% .. 8.6% |
| [50,60) | 271 | 54.2% | 49.7% | 4.5% | -3.2% .. 12.7% |
| [60,100] | 59 | 45.8% | 49.7% | -4.0% | -13.7% .. 8.4% |

### regime = off

- Slope **-0.001** reversal-probability per +20 score points (95% CI -0.020 .. 0.020); rho = -0.0013.
- Halves (split after 2014-12): 0.006 / -0.007. Excluding JPY crosses: -0.009. Weakest leave-one-pair-out: -0.004 (dropping EURJPY).
- rho vs best single factor (div): -0.0107 (paired 95% CI -0.0273 .. 0.0072).

| gate | result |
|---|---|
| n_months >= 48 | pass |
| slope per +20 pts >= 0.02 | FAIL |
| 95% CI lower bound > 0 | FAIL |
| both halves slope > 0 | FAIL |
| excluding JPY crosses slope > 0 | FAIL |
| every leave-one-pair-out slope > 0 (worst: EURJPY) | FAIL |
| composite rho beats best single factor (div) | FAIL |

| score bucket | obs | reversal rate | baseline | lift | 95% CI |
|---|---|---|---|---|---|
| [0,10) | 61969 | 49.8% | 49.7% | 0.1% | -0.4% .. 0.5% |
| [10,20) | 3500 | 48.5% | 49.7% | -1.2% | -4.3% .. 2.1% |
| [20,30) | 6609 | 48.8% | 49.8% | -1.0% | -3.4% .. 1.6% |
| [30,40) | 1161 | 53.1% | 49.8% | 3.3% | -1.1% .. 8.1% |
| [40,50) | 494 | 47.8% | 49.9% | -2.1% | -7.6% .. 4.2% |
| [50,60) | 664 | 53.6% | 49.8% | 3.8% | -1.6% .. 10.0% |
| [60,100] | 187 | 46.0% | 50.0% | -4.0% | -12.2% .. 7.0% |

## Exploratory: ranging vs trending bars (filter-off score; NO gates, cannot promote or rescue a verdict)

'Trending' = the Pine's debounced ADX>=30 + EMA200-slope state (either direction) at the bar; 'ranging' = neither. Baselines are computed inside each stratum.

| stratum | observations | gradient slope per +20 pts (95% CI) | flagged events | flagged lift (95% CI) |
|---|---|---|---|---|
| trending | 13240 | 0.002 (-0.028 .. 0.040) | 60 | -0.8% (-13.4% .. 14.4%) |
| ranging | 61344 | -0.002 (-0.020 .. 0.017) | 32 | -8.9% (-22.5% .. 6.5%) |

## Known limits

- D1/H4 OHLC cannot sequence intrabar: a bar touching both barriers is scored continuation, for every bar alike.
- Gradient observations are ALL bars, so consecutive observations overlap; the month-clustered bootstrap absorbs same-month dependence, not every cross-pair dependency (12 pairs share USD/JPY legs and COT legs).
- One macro era (~2020-2026, incl. the 2022 USD surge); nothing here speaks to earlier regimes.
- COT percentile is computed on a business-day calendar and reproduced TradingView's label to within ~0.3 percentile points.
- H4 stores: the provider omits about one H1 bar most Monday mornings (3-bar blocks); the UTC alignment keeps the Sunday-reopen and Friday-close STUB blocks (1-3 H1 bars), as the app's own aggregator does.
