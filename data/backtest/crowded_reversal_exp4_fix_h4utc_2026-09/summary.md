# Crowded Market flags -> previous support/resistance — exp4_fix_h4utc_2026-09

Mode `full` · timeframe `h4cutc` · target = most recent confirmed swing level (swing_n 5, 0.5-5 ATR away, <= 120 bars old), stop = equal distance on the other side (1:1), 20 bars, same-bar-both -> failure, timeout -> failure · baseline matched on pair x side x distance bucket · bootstrap 5000 (month-clustered, seed 20260919)

Price: `data/fix_h4_utc` (12 pairs). Bars in window 114915; a valid level exists on 82% of bars (resistance above) and 84% (support below). COT: Legacy futures-only. Commit `32f464cf5e`.

## Verdicts (flagged-event gates F1-F6)

- **regime = suppress:** INCONCLUSIVE — 79 de-clustered events < 100
- **regime = off:** FAIL — lift >= 0.05; 95% CI lower bound > 0; both halves lift > 0; excluding best pair (USDCHF) lift > 0

## Flagged events, regime = suppress

- **79 de-clustered events** (reached the level 20, stopped 22, timeout 37; same-bar ties 0). Top+bottom same-bar conflicts skipped: 0; flags with no valid level / no forward bars (excluded): 24.
- Flagged success rate 25.3% vs distance-matched baseline 21.9% -> **lift 3.4%** (95% CI -7.5% .. 16.0%).
- Halves (split after 2023-07): first -0.3% (n=41), second 7.7% (n=38). Excluding JPY crosses: 7.7% (n=38). Excluding best pair (USDCHF): 1.0% (n=69).

| gate | result |
|---|---|
| n de-clustered events >= 100 | FAIL |
| lift >= 0.05 | FAIL |
| 95% CI lower bound > 0 | FAIL |
| both halves lift > 0 | FAIL |
| excluding JPY crosses lift > 0 | pass |
| excluding best pair (USDCHF) lift > 0 | pass |
| beats best single-factor flagged lift (climax) | pass |

| pair | events | lift |
|---|---|---|
| AUDJPY | 4 | -17.0% |
| AUDUSD | 9 | 13.8% |
| CADJPY | 7 | -9.5% |
| EURJPY | 5 | 0.6% |
| EURUSD | 7 | -11.5% |
| GBPJPY | 7 | -5.0% |
| GBPUSD | 5 | -20.1% |
| NZDJPY | 7 | 19.6% |
| NZDUSD | 6 | 11.5% |
| USDCAD | 1 | 79.2% |
| USDCHF | 10 | 20.1% |
| USDJPY | 11 | 0.7% |

## Flagged events, regime = off

- **128 de-clustered events** (reached the level 29, stopped 29, timeout 70; same-bar ties 0). Top+bottom same-bar conflicts skipped: 0; flags with no valid level / no forward bars (excluded): 57.
- Flagged success rate 22.7% vs distance-matched baseline 22.1% -> **lift 0.6%** (95% CI -7.0% .. 8.1%).
- Halves (split after 2023-07): first 4.6% (n=62), second -2.8% (n=66). Excluding JPY crosses: 3.7% (n=59). Excluding best pair (USDCHF): -1.2% (n=114).

| gate | result |
|---|---|
| n de-clustered events >= 100 | pass |
| lift >= 0.05 | FAIL |
| 95% CI lower bound > 0 | FAIL |
| both halves lift > 0 | FAIL |
| excluding JPY crosses lift > 0 | pass |
| excluding best pair (USDCHF) lift > 0 | FAIL |
| beats best single-factor flagged lift (climax) | pass |

| pair | events | lift |
|---|---|---|
| AUDJPY | 10 | -7.0% |
| AUDUSD | 13 | 3.5% |
| CADJPY | 10 | -12.6% |
| EURJPY | 10 | -1.3% |
| EURUSD | 12 | -8.6% |
| GBPJPY | 11 | -10.3% |
| GBPUSD | 7 | -20.0% |
| NZDJPY | 10 | 18.1% |
| NZDUSD | 10 | 16.4% |
| USDCAD | 3 | 12.5% |
| USDCHF | 14 | 15.2% |
| USDJPY | 18 | -0.0% |

## What the matching is matching (filter-off events by level distance)

| level distance | events | flagged success | baseline success (all valid bars) | baseline bars |
|---|---|---|---|---|
| [0.5,1) ATR | 2 | 50.0% | 48.2% | 26484 |
| [1,2) ATR | 0 | n/a | 46.9% | 54621 |
| [2,3) ATR | 13 | 23.1% | 37.2% | 47213 |
| [3,5] ATR | 113 | 22.1% | 19.9% | 63185 |

## Single factors, same test (each factor's own rising edge)

| factor | events | flagged | baseline | lift | 95% CI |
|---|---|---|---|---|---|
| pct_b | 3694 | 25.1% | 25.9% | -0.8% | -2.2% .. 0.7% |
| z | 1657 | 20.9% | 22.6% | -1.6% | -3.8% .. 0.5% |
| stretch | 2015 | 22.1% | 23.9% | -1.8% | -3.5% .. 0.1% |
| rsi | 1846 | 21.4% | 22.8% | -1.4% | -3.3% .. 0.6% |
| div | 1377 | 34.2% | 34.9% | -0.7% | -3.5% .. 2.2% |
| climax | 1988 | 24.0% | 24.2% | -0.2% | -2.3% .. 2.0% |
| cot | 135 | 34.8% | 35.3% | -0.5% | -7.8% .. 6.4% |

## Known limits

- 'Previous support/resistance' is defined as the most recent confirmed swing (a judgment call); round numbers, multi-touch zones or a chart reader's discretion are different definitions and different experiments.
- Success = the level was reached before an equal adverse move within 20 bars. It is not profit: no costs, entry rule, sizing or discretionary exit.
- Bars overlap and the 12 pairs share USD/JPY and COT legs; one macro era per store; OHLC cannot sequence intrabar (tie -> failure).
