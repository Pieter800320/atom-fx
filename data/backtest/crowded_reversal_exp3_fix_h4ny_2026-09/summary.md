# Crowded Market reversal — exp3_fix_h4ny_2026-09

Mode `full` · timeframe `h4cny` · primary = **flagged** · reversal = 1 x ATR(14) barrier race, 20 bars, same-bar-both -> continuation, timeout -> not a reversal · bootstrap 5000 (month-clustered, seed 20260919)

Price: `data/fix_h4_ny` (12 pairs), 77 months in the evaluation window. COT: Legacy futures-only 2006-01-03 .. 2026-09-15. Commit `32f464cf5e`.

## Verdicts

- **regime = suppress** — **flagged events (PRIMARY): FAIL — lift >= 0.05; 95% CI lower bound > 0; both halves lift > 0** · gradient (secondary): FAIL — slope per +20 pts >= 0.02; 95% CI lower bound > 0; both halves slope > 0; composite rho beats best single factor (stretch)
- **regime = off** — **flagged events (PRIMARY): FAIL — lift >= 0.05; 95% CI lower bound > 0; both halves lift > 0; excluding best pair (NZDUSD) lift > 0; beats best single-factor flagged lift (stretch)** · gradient (secondary): FAIL — slope per +20 pts >= 0.02; 95% CI lower bound > 0; both halves slope > 0; composite rho beats best single factor (stretch)

**ADX regime filter (gradient slope, suppress minus off, per +20 pts):** -0.001 (95% CI -0.008 .. 0.005) -> NO demonstrated value: CI includes 0 — the data cannot justify the ADX filter.

## Flagged events (score >= 60, rising edge, de-clustered per pair)

### regime = suppress

- **102 de-clustered events** (reversal 54, continuation 48, timeout 0; same-bar ties 1). Top+bottom same-bar conflicts skipped: 0; flags on censored bars: 0.
- Flagged reversal rate 52.9% vs like-for-like baseline 49.3% -> **lift 3.7%** (95% CI -7.8% .. 15.5%).
- Halves (split after 2023-07): first -1.0% (n=54), second 9.6% (n=48). Excluding JPY crosses: 0.6% (n=52). Excluding best pair (NZDUSD): 2.4% (n=91).

| gate | result |
|---|---|
| n de-clustered events >= 100 | pass |
| lift >= 0.05 | FAIL |
| 95% CI lower bound > 0 | FAIL |
| both halves lift > 0 | FAIL |
| excluding JPY crosses lift > 0 | pass |
| excluding best pair (NZDUSD) lift > 0 | pass |
| beats best single-factor flagged lift (stretch) | pass |

| pair | events | lift |
|---|---|---|
| AUDJPY | 4 | 0.5% |
| AUDUSD | 8 | 12.4% |
| CADJPY | 11 | 14.0% |
| EURJPY | 6 | 17.6% |
| EURUSD | 8 | -24.4% |
| GBPJPY | 8 | 3.4% |
| GBPUSD | 8 | -11.7% |
| NZDJPY | 10 | 0.1% |
| NZDUSD | 11 | 14.5% |
| USDCAD | 3 | 18.0% |
| USDCHF | 14 | 0.5% |
| USDJPY | 11 | 5.1% |

### regime = off

- **188 de-clustered events** (reversal 89, continuation 99, timeout 0; same-bar ties 3). Top+bottom same-bar conflicts skipped: 0; flags on censored bars: 1.
- Flagged reversal rate 47.3% vs like-for-like baseline 49.2% -> **lift -1.8%** (95% CI -10.0% .. 7.6%).
- Halves (split after 2023-07): first -4.3% (n=108), second 2.0% (n=80). Excluding JPY crosses: 0.1% (n=97). Excluding best pair (NZDUSD): -4.3% (n=167).

| gate | result |
|---|---|
| n de-clustered events >= 100 | pass |
| lift >= 0.05 | FAIL |
| 95% CI lower bound > 0 | FAIL |
| both halves lift > 0 | FAIL |
| excluding JPY crosses lift > 0 | pass |
| excluding best pair (NZDUSD) lift > 0 | FAIL |
| beats best single-factor flagged lift (stretch) | FAIL |

| pair | events | lift |
|---|---|---|
| AUDJPY | 10 | -18.9% |
| AUDUSD | 16 | 12.9% |
| CADJPY | 20 | 0.4% |
| EURJPY | 13 | 5.0% |
| EURUSD | 16 | -30.7% |
| GBPJPY | 11 | -1.2% |
| GBPUSD | 15 | -2.5% |
| NZDJPY | 14 | 0.2% |
| NZDUSD | 21 | 17.6% |
| USDCAD | 9 | -4.7% |
| USDCHF | 20 | 0.3% |
| USDJPY | 23 | -9.9% |

### Single factors, same flagged-event test (each factor's own rising edge)

| factor | events | flagged | baseline | lift | 95% CI |
|---|---|---|---|---|---|
| pct_b | 5991 | 48.1% | 49.1% | -1.0% | -2.6% .. 0.6% |
| z | 2840 | 47.1% | 49.1% | -2.0% | -4.3% .. 0.4% |
| stretch | 3332 | 50.2% | 49.0% | 1.2% | -0.8% .. 3.2% |
| rsi | 2985 | 49.1% | 49.0% | 0.1% | -2.4% .. 2.7% |
| div | 1795 | 49.1% | 49.0% | 0.1% | -3.0% .. 3.5% |
| climax | 2605 | 47.3% | 49.3% | -2.1% | -4.9% .. 0.9% |
| cot | 77 | 48.1% | 49.4% | -1.3% | -12.0% .. 8.7% |

## Single factor vs the whole composite (filter off) — paired month-clustered bootstrap on within-stratum rho

Replication of the composite's gradient (slope >= 0.02 with CI lower > 0): **CONTRADICTED**.
Rule (pre-registered, Experiment 5): single factor AS GOOD if the CI of rho(single) - rho(composite) has lower bound > -0.01; composite BETTER if the CI upper bound < 0; otherwise INCONCLUSIVE.

| single factor | rho | rho(single) - rho(composite) | 95% CI | reading |
|---|---|---|---|---|
| pct_b | 0.0004 | -0.0063 | -0.0158 .. 0.0027 | INCONCLUSIVE |
| z | 0.0015 | -0.0052 | -0.0134 .. 0.0029 | INCONCLUSIVE |
| stretch | 0.0091 | 0.0024 | -0.0061 .. 0.0114 | single factor AS GOOD (non-inferior) |
| rsi | 0.0035 | -0.0031 | -0.0121 .. 0.0063 | INCONCLUSIVE |
| div | 0.0050 | -0.0016 | -0.0126 .. 0.0085 | INCONCLUSIVE |
| climax | -0.0035 | -0.0101 | -0.0199 .. -0.0012 | composite BETTER |
| cot | 0.0033 | -0.0033 | -0.0159 .. 0.0099 | INCONCLUSIVE |

## Score gradient (every bar)

### regime = suppress

- Slope **0.005** reversal-probability per +20 score points (95% CI -0.005 .. 0.015); rho = 0.0043.
- Halves (split after 2023-07): -0.001 / 0.013. Excluding JPY crosses: 0.008. Weakest leave-one-pair-out: 0.002 (dropping AUDUSD).
- rho vs best single factor (stretch): -0.0048 (paired 95% CI -0.0169 .. 0.0066).

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
| [0,10) | 203486 | 49.1% | 49.2% | -0.1% | -0.3% .. 0.1% |
| [10,20) | 8583 | 49.6% | 49.0% | 0.6% | -1.0% .. 2.3% |
| [20,30) | 18742 | 50.0% | 49.4% | 0.6% | -0.9% .. 2.1% |
| [30,40) | 2651 | 49.7% | 49.0% | 0.7% | -2.5% .. 3.8% |
| [40,50) | 991 | 50.3% | 49.2% | 1.0% | -3.3% .. 5.9% |
| [50,60) | 577 | 48.5% | 49.5% | -1.0% | -6.5% .. 5.2% |
| [60,100] | 184 | 51.1% | 49.1% | 2.0% | -9.4% .. 14.8% |

### regime = off

- Slope **0.006** reversal-probability per +20 score points (95% CI -0.003 .. 0.018); rho = 0.0067.
- Halves (split after 2023-07): -0.001 / 0.017. Excluding JPY crosses: 0.008. Weakest leave-one-pair-out: 0.004 (dropping AUDUSD).
- rho vs best single factor (stretch): -0.0024 (paired 95% CI -0.0114 .. 0.0061).

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
| [0,10) | 192018 | 49.1% | 49.2% | -0.2% | -0.4% .. 0.1% |
| [10,20) | 11483 | 49.9% | 49.0% | 0.9% | -0.5% .. 2.4% |
| [20,30) | 23965 | 49.9% | 49.3% | 0.7% | -0.7% .. 2.0% |
| [30,40) | 4430 | 49.4% | 49.0% | 0.5% | -2.6% .. 3.6% |
| [40,50) | 1795 | 51.1% | 49.2% | 1.9% | -1.7% .. 6.1% |
| [50,60) | 1164 | 50.1% | 49.4% | 0.7% | -3.3% .. 5.7% |
| [60,100] | 359 | 47.9% | 49.2% | -1.3% | -8.6% .. 7.7% |

## Exploratory: ranging vs trending bars (filter-off score; NO gates, cannot promote or rescue a verdict)

'Trending' = the Pine's debounced ADX>=30 + EMA200-slope state (either direction) at the bar; 'ranging' = neither. Baselines are computed inside each stratum.

| stratum | observations | gradient slope per +20 pts (95% CI) | flagged events | flagged lift (95% CI) |
|---|---|---|---|---|
| trending | 51298 | 0.009 (-0.008 .. 0.030) | 86 | -8.6% (-17.8% .. 2.6%) |
| ranging | 183916 | 0.005 (-0.006 .. 0.016) | 102 | 3.7% (-7.8% .. 15.5%) |

## Known limits

- D1/H4 OHLC cannot sequence intrabar: a bar touching both barriers is scored continuation, for every bar alike.
- Gradient observations are ALL bars, so consecutive observations overlap; the month-clustered bootstrap absorbs same-month dependence, not every cross-pair dependency (12 pairs share USD/JPY legs and COT legs).
- One macro era (~2020-2026, incl. the 2022 USD surge); nothing here speaks to earlier regimes.
- COT percentile is computed on a business-day calendar and reproduced TradingView's label to within ~0.3 percentile points.
- H4 stores: the provider omits about one H1 bar most Monday mornings (3-bar blocks); the UTC alignment keeps the Sunday-reopen and Friday-close STUB blocks (1-3 H1 bars), as the app's own aggregator does.
