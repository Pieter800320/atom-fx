# Crowded Market reversal — exp3_h4ny_2026-09

Mode `full` · timeframe `h4ny` · primary = **flagged** · reversal = 1 x ATR(14) barrier race, 20 bars, same-bar-both -> continuation, timeout -> not a reversal · bootstrap 5000 (month-clustered, seed 20260919)

Price: `data/h4_ny_long` (12 pairs), 77 months in the evaluation window. COT: Legacy futures-only 2006-01-03 .. 2026-09-15. Commit `f8b162402e`.

## Verdicts

- **regime = suppress** — **flagged events (PRIMARY): FAIL — lift >= 0.05; 95% CI lower bound > 0; both halves lift > 0; beats best single-factor flagged lift (cot)** · gradient (secondary): FAIL — slope per +20 pts >= 0.02; 95% CI lower bound > 0; both halves slope > 0; composite rho beats best single factor (stretch)
- **regime = off** — **flagged events (PRIMARY): FAIL — lift >= 0.05; 95% CI lower bound > 0; beats best single-factor flagged lift (cot)** · gradient (secondary): FAIL — slope per +20 pts >= 0.02; 95% CI lower bound > 0; composite rho beats best single factor (stretch)

**ADX regime filter (gradient slope, suppress minus off, per +20 pts):** 0.000 (95% CI -0.007 .. 0.007) -> NO demonstrated value: CI includes 0 — the data cannot justify the ADX filter.

## Flagged events (score >= 60, rising edge, de-clustered per pair)

### regime = suppress

- **112 de-clustered events** (reversal 59, continuation 53, timeout 0; same-bar ties 0). Top+bottom same-bar conflicts skipped: 0; flags on censored bars: 0.
- Flagged reversal rate 52.7% vs like-for-like baseline 49.2% -> **lift 3.5%** (95% CI -6.4% .. 13.3%).
- Halves (split after 2023-07): first -2.9% (n=65), second 12.7% (n=47). Excluding JPY crosses: 8.1% (n=54). Excluding best pair (NZDUSD): 2.8% (n=100).

| gate | result |
|---|---|
| n de-clustered events >= 100 | pass |
| lift >= 0.05 | FAIL |
| 95% CI lower bound > 0 | FAIL |
| both halves lift > 0 | FAIL |
| excluding JPY crosses lift > 0 | pass |
| excluding best pair (NZDUSD) lift > 0 | pass |
| beats best single-factor flagged lift (cot) | FAIL |

| pair | events | lift |
|---|---|---|
| AUDJPY | 4 | -23.3% |
| AUDUSD | 6 | 17.3% |
| CADJPY | 10 | 0.5% |
| EURJPY | 7 | 8.5% |
| EURUSD | 6 | 17.1% |
| GBPJPY | 12 | 1.8% |
| GBPUSD | 7 | 8.0% |
| NZDJPY | 9 | 5.6% |
| NZDUSD | 12 | 9.3% |
| USDCAD | 8 | 13.0% |
| USDCHF | 15 | -2.8% |
| USDJPY | 16 | -5.5% |

### regime = off

- **206 de-clustered events** (reversal 107, continuation 99, timeout 0; same-bar ties 0). Top+bottom same-bar conflicts skipped: 0; flags on censored bars: 1.
- Flagged reversal rate 51.9% vs like-for-like baseline 49.2% -> **lift 2.8%** (95% CI -4.8% .. 10.9%).
- Halves (split after 2023-07): first 3.1% (n=110), second 2.9% (n=96). Excluding JPY crosses: 8.1% (n=101). Excluding best pair (NZDUSD): 1.1% (n=185).

| gate | result |
|---|---|
| n de-clustered events >= 100 | pass |
| lift >= 0.05 | FAIL |
| 95% CI lower bound > 0 | FAIL |
| both halves lift > 0 | pass |
| excluding JPY crosses lift > 0 | pass |
| excluding best pair (NZDUSD) lift > 0 | pass |
| beats best single-factor flagged lift (cot) | FAIL |

| pair | events | lift |
|---|---|---|
| AUDJPY | 8 | -10.8% |
| AUDUSD | 15 | 10.8% |
| CADJPY | 17 | -2.5% |
| EURJPY | 18 | 0.8% |
| EURUSD | 16 | 0.8% |
| GBPJPY | 17 | -6.5% |
| GBPUSD | 14 | 15.2% |
| NZDJPY | 18 | 6.0% |
| NZDUSD | 21 | 17.6% |
| USDCAD | 11 | 14.0% |
| USDCHF | 24 | -3.8% |
| USDJPY | 27 | -4.8% |

### Single factors, same flagged-event test (each factor's own rising edge)

| factor | events | flagged | baseline | lift | 95% CI |
|---|---|---|---|---|---|
| pct_b | 5646 | 49.5% | 49.2% | 0.4% | -1.4% .. 2.1% |
| z | 2674 | 48.5% | 49.1% | -0.6% | -3.2% .. 2.0% |
| stretch | 3272 | 49.5% | 49.0% | 0.5% | -1.5% .. 2.7% |
| rsi | 2941 | 49.9% | 49.0% | 0.9% | -1.2% .. 3.2% |
| div | 1643 | 48.0% | 49.0% | -1.0% | -3.7% .. 2.0% |
| climax | 2607 | 49.6% | 49.3% | 0.2% | -2.5% .. 3.1% |
| cot | 77 | 55.8% | 49.2% | 6.6% | -5.4% .. 17.7% |

## Score gradient (every bar)

### regime = suppress

- Slope **0.005** reversal-probability per +20 score points (95% CI -0.005 .. 0.015); rho = 0.0040.
- Halves (split after 2023-07): -0.002 / 0.013. Excluding JPY crosses: 0.008. Weakest leave-one-pair-out: 0.001 (dropping AUDUSD).
- rho vs best single factor (stretch): -0.0057 (paired 95% CI -0.0182 .. 0.0067).

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
| [0,10) | 192828 | 49.2% | 49.2% | -0.1% | -0.2% .. 0.1% |
| [10,20) | 8004 | 49.3% | 49.0% | 0.3% | -1.3% .. 2.0% |
| [20,30) | 17386 | 49.8% | 49.3% | 0.5% | -0.9% .. 1.9% |
| [30,40) | 2681 | 48.8% | 49.0% | -0.2% | -2.9% .. 2.5% |
| [40,50) | 1012 | 49.8% | 49.1% | 0.7% | -3.8% .. 5.6% |
| [50,60) | 539 | 50.8% | 49.3% | 1.5% | -3.8% .. 7.0% |
| [60,100] | 200 | 51.5% | 49.1% | 2.4% | -4.6% .. 10.2% |

### regime = off

- Slope **0.004** reversal-probability per +20 score points (95% CI -0.005 .. 0.015); rho = 0.0045.
- Halves (split after 2023-07): 0.000 / 0.010. Excluding JPY crosses: 0.009. Weakest leave-one-pair-out: 0.001 (dropping AUDUSD).
- rho vs best single factor (stretch): -0.0052 (paired 95% CI -0.0131 .. 0.0023).

| gate | result |
|---|---|
| n_months >= 48 | pass |
| slope per +20 pts >= 0.02 | FAIL |
| 95% CI lower bound > 0 | FAIL |
| both halves slope > 0 | pass |
| excluding JPY crosses slope > 0 | pass |
| every leave-one-pair-out slope > 0 (worst: AUDUSD) | pass |
| composite rho beats best single factor (stretch) | FAIL |

| score bucket | obs | reversal rate | baseline | lift | 95% CI |
|---|---|---|---|---|---|
| [0,10) | 181746 | 49.1% | 49.2% | -0.1% | -0.4% .. 0.1% |
| [10,20) | 10612 | 50.8% | 49.0% | 1.8% | 0.3% .. 3.3% |
| [20,30) | 22487 | 49.6% | 49.2% | 0.4% | -0.8% .. 1.6% |
| [30,40) | 4433 | 48.3% | 48.9% | -0.7% | -3.3% .. 2.0% |
| [40,50) | 1828 | 48.6% | 49.1% | -0.5% | -3.8% .. 3.4% |
| [50,60) | 1138 | 48.7% | 49.3% | -0.6% | -4.2% .. 3.5% |
| [60,100] | 406 | 52.2% | 49.1% | 3.1% | -3.0% .. 10.0% |

## Exploratory: ranging vs trending bars (filter-off score; NO gates, cannot promote or rescue a verdict)

'Trending' = the Pine's debounced ADX>=30 + EMA200-slope state (either direction) at the bar; 'ranging' = neither. Baselines are computed inside each stratum.

| stratum | observations | gradient slope per +20 pts (95% CI) | flagged events | flagged lift (95% CI) |
|---|---|---|---|---|
| trending | 49162 | 0.007 (-0.010 .. 0.027) | 96 | 1.9% (-8.6% .. 12.4%) |
| ranging | 173488 | 0.003 (-0.007 .. 0.014) | 112 | 3.5% (-6.4% .. 13.3%) |

## Known limits

- D1/H4 OHLC cannot sequence intrabar: a bar touching both barriers is scored continuation, for every bar alike.
- Gradient observations are ALL bars, so consecutive observations overlap; the month-clustered bootstrap absorbs same-month dependence, not every cross-pair dependency (12 pairs share USD/JPY legs and COT legs).
- One macro era (~2020-2026, incl. the 2022 USD surge); nothing here speaks to earlier regimes.
- COT percentile is computed on a business-day calendar and reproduced TradingView's label to within ~0.3 percentile points.
- H4 stores: the provider omits about one H1 bar most Monday mornings (3-bar blocks); the UTC alignment keeps the Sunday-reopen and Friday-close STUB blocks (1-3 H1 bars), as the app's own aggregator does.
