# Crowded Market flags -> previous support/resistance — exp4_h4utc_2026-09

Mode `full` · timeframe `h4utc` · target = most recent confirmed swing level (swing_n 5, 0.5-5 ATR away, <= 120 bars old), stop = equal distance on the other side (1:1), 20 bars, same-bar-both -> failure, timeout -> failure · baseline matched on pair x side x distance bucket · bootstrap 5000 (month-clustered, seed 20260919)

Price: `data/h4_utc_long` (12 pairs). Bars in window 114657; a valid level exists on 82% of bars (resistance above) and 83% (support below). COT: Legacy futures-only. Commit `94f24665ed`.

## Verdicts (flagged-event gates F1-F6)

- **regime = suppress:** INCONCLUSIVE — 64 de-clustered events < 100
- **regime = off:** FAIL — lift >= 0.05; 95% CI lower bound > 0; both halves lift > 0; excluding JPY crosses lift > 0; excluding best pair (NZDUSD) lift > 0; beats best single-factor flagged lift (cot)

## Flagged events, regime = suppress

- **64 de-clustered events** (reached the level 16, stopped 14, timeout 34; same-bar ties 0). Top+bottom same-bar conflicts skipped: 0; flags with no valid level / no forward bars (excluded): 22.
- Flagged success rate 25.0% vs distance-matched baseline 21.4% -> **lift 3.6%** (95% CI -6.8% .. 15.6%).
- Halves (split after 2023-07): first 6.4% (n=36), second 0.9% (n=28). Excluding JPY crosses: 3.3% (n=33). Excluding best pair (EURJPY): 1.8% (n=61).

| gate | result |
|---|---|
| n de-clustered events >= 100 | FAIL |
| lift >= 0.05 | FAIL |
| 95% CI lower bound > 0 | FAIL |
| both halves lift > 0 | pass |
| excluding JPY crosses lift > 0 | pass |
| excluding best pair (EURJPY) lift > 0 | pass |
| beats best single-factor flagged lift (cot) | FAIL |

| pair | events | lift |
|---|---|---|
| AUDJPY | 2 | -16.9% |
| AUDUSD | 7 | 8.3% |
| CADJPY | 7 | -6.4% |
| EURJPY | 3 | 40.3% |
| EURUSD | 7 | -21.4% |
| GBPJPY | 4 | 4.4% |
| GBPUSD | 8 | 4.4% |
| NZDJPY | 5 | 15.0% |
| NZDUSD | 4 | 23.4% |
| USDCAD | 2 | 30.0% |
| USDCHF | 5 | 2.1% |
| USDJPY | 10 | -1.3% |

## Flagged events, regime = off

- **117 de-clustered events** (reached the level 22, stopped 31, timeout 64; same-bar ties 0). Top+bottom same-bar conflicts skipped: 0; flags with no valid level / no forward bars (excluded): 59.
- Flagged success rate 18.8% vs distance-matched baseline 22.2% -> **lift -3.4%** (95% CI -9.7% .. 4.1%).
- Halves (split after 2023-07): first 1.3% (n=58), second -7.4% (n=59). Excluding JPY crosses: -2.8% (n=58). Excluding best pair (NZDUSD): -4.4% (n=108).

| gate | result |
|---|---|
| n de-clustered events >= 100 | pass |
| lift >= 0.05 | FAIL |
| 95% CI lower bound > 0 | FAIL |
| both halves lift > 0 | FAIL |
| excluding JPY crosses lift > 0 | FAIL |
| excluding best pair (NZDUSD) lift > 0 | FAIL |
| beats best single-factor flagged lift (cot) | FAIL |

| pair | events | lift |
|---|---|---|
| AUDJPY | 6 | -2.6% |
| AUDUSD | 15 | -3.3% |
| CADJPY | 12 | -6.9% |
| EURJPY | 7 | 5.2% |
| EURUSD | 9 | -10.7% |
| GBPJPY | 9 | -9.5% |
| GBPUSD | 11 | -2.4% |
| NZDJPY | 10 | -2.2% |
| NZDUSD | 9 | 8.8% |
| USDCAD | 6 | -3.3% |
| USDCHF | 8 | -6.0% |
| USDJPY | 15 | -4.6% |

## What the matching is matching (filter-off events by level distance)

| level distance | events | flagged success | baseline success (all valid bars) | baseline bars |
|---|---|---|---|---|
| [0.5,1) ATR | 1 | 100.0% | 49.3% | 25615 |
| [1,2) ATR | 1 | 0.0% | 47.3% | 53118 |
| [2,3) ATR | 9 | 44.4% | 37.7% | 46764 |
| [3,5] ATR | 106 | 16.0% | 20.4% | 64002 |

## Single factors, same test (each factor's own rising edge)

| factor | events | flagged | baseline | lift | 95% CI |
|---|---|---|---|---|---|
| pct_b | 3608 | 26.4% | 26.3% | 0.1% | -1.3% .. 1.6% |
| z | 1572 | 19.8% | 22.8% | -3.1% | -5.4% .. -0.6% |
| stretch | 2089 | 22.7% | 24.4% | -1.7% | -3.7% .. 0.3% |
| rsi | 1841 | 22.0% | 23.5% | -1.5% | -3.4% .. 0.5% |
| div | 1328 | 35.8% | 35.5% | 0.3% | -2.5% .. 3.3% |
| climax | 1945 | 25.1% | 24.8% | 0.3% | -1.9% .. 2.5% |
| cot | 64 | 40.6% | 36.1% | 4.5% | -6.3% .. 15.6% |

## Known limits

- 'Previous support/resistance' is defined as the most recent confirmed swing (a judgment call); round numbers, multi-touch zones or a chart reader's discretion are different definitions and different experiments.
- Success = the level was reached before an equal adverse move within 20 bars. It is not profit: no costs, entry rule, sizing or discretionary exit.
- Bars overlap and the 12 pairs share USD/JPY and COT legs; one macro era per store; OHLC cannot sequence intrabar (tie -> failure).
