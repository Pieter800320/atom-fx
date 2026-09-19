> **PLUMBING CHECK ONLY.** No edge conclusion may be drawn from any number below.

# Crowded Market flags -> previous support/resistance — smoke_levels_2026-09

Mode `smoke` · timeframe `h4utc` · target = most recent confirmed swing level (swing_n 5, 0.5-5 ATR away, <= 120 bars old), stop = equal distance on the other side (1:1), 20 bars, same-bar-both -> failure, timeout -> failure · baseline matched on pair x side x distance bucket · bootstrap 100 (month-clustered, seed 20260919)

Price: `data/h4_utc_long` (1 pairs). Bars in window 899; a valid level exists on 86% of bars (resistance above) and 77% (support below). COT: Legacy futures-only. Commit `94f24665ed`.

## Verdicts (flagged-event gates F1-F6)

- **regime = suppress:** PLUMBING ONLY — no verdict (smoke run)
- **regime = off:** PLUMBING ONLY — no verdict (smoke run)

## Flagged events, regime = suppress

- **1 de-clustered events** (reached the level 0, stopped 0, timeout 1; same-bar ties 0). Top+bottom same-bar conflicts skipped: 0; flags with no valid level / no forward bars (excluded): 0.
- Flagged success rate 0.0% vs distance-matched baseline 21.0% -> **lift -21.0%** (95% CI -32.7% .. -14.3%).
- Halves (split after 2026-06): first -32.2% (n=1), second n/a (n=0). Excluding JPY crosses: -21.0% (n=1). Excluding best pair (EURUSD): n/a (n=0).

| gate | result |
|---|---|
| n de-clustered events >= 100 | FAIL |
| lift >= 0.05 | FAIL |
| 95% CI lower bound > 0 | FAIL |
| both halves lift > 0 | FAIL |
| excluding JPY crosses lift > 0 | FAIL |
| excluding best pair (EURUSD) lift > 0 | FAIL |
| beats best single-factor flagged lift (climax) | FAIL |

| pair | events | lift |
|---|---|---|
| EURUSD | 1 | -21.0% |

## Flagged events, regime = off

- **1 de-clustered events** (reached the level 0, stopped 0, timeout 1; same-bar ties 0). Top+bottom same-bar conflicts skipped: 0; flags with no valid level / no forward bars (excluded): 0.
- Flagged success rate 0.0% vs distance-matched baseline 21.0% -> **lift -21.0%** (95% CI -32.7% .. -14.3%).
- Halves (split after 2026-06): first -32.2% (n=1), second n/a (n=0). Excluding JPY crosses: -21.0% (n=1). Excluding best pair (EURUSD): n/a (n=0).

| gate | result |
|---|---|
| n de-clustered events >= 100 | FAIL |
| lift >= 0.05 | FAIL |
| 95% CI lower bound > 0 | FAIL |
| both halves lift > 0 | FAIL |
| excluding JPY crosses lift > 0 | FAIL |
| excluding best pair (EURUSD) lift > 0 | FAIL |
| beats best single-factor flagged lift (climax) | FAIL |

| pair | events | lift |
|---|---|---|
| EURUSD | 1 | -21.0% |

## What the matching is matching (filter-off events by level distance)

| level distance | events | flagged success | baseline success (all valid bars) | baseline bars |
|---|---|---|---|---|
| [0.5,1) ATR | 0 | n/a | 45.8% | 201 |
| [1,2) ATR | 0 | n/a | 46.8% | 451 |
| [2,3) ATR | 0 | n/a | 37.0% | 381 |
| [3,5] ATR | 1 | 0.0% | 20.7% | 435 |

## Single factors, same test (each factor's own rising edge)

| factor | events | flagged | baseline | lift | 95% CI |
|---|---|---|---|---|---|
| pct_b | 30 | 33.3% | 27.6% | 5.7% | -1.7% .. 13.2% |
| z | 11 | 0.0% | 25.4% | -25.4% | -31.4% .. -12.3% |
| stretch | 11 | 9.1% | 28.6% | -19.5% | -28.9% .. -3.5% |
| rsi | 12 | 25.0% | 20.6% | 4.4% | -7.1% .. 17.7% |
| div | 6 | 33.3% | 35.8% | -2.4% | -43.1% .. 13.1% |
| climax | 15 | 33.3% | 26.3% | 7.1% | -11.1% .. 23.7% |
| cot | 0 | n/a | n/a | n/a | n/a .. n/a |

## Known limits

- 'Previous support/resistance' is defined as the most recent confirmed swing (a judgment call); round numbers, multi-touch zones or a chart reader's discretion are different definitions and different experiments.
- Success = the level was reached before an equal adverse move within 20 bars. It is not profit: no costs, entry rule, sizing or discretionary exit.
- Bars overlap and the 12 pairs share USD/JPY and COT legs; one macro era per store; OHLC cannot sequence intrabar (tie -> failure).
