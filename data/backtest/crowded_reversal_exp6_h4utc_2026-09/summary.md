# Experiment 6 — score > 30 against the OPPOSITE background (h4utc, full)

Verdict (`opposite` arm, pre-registered gates): **FAIL — lift >= 0.05; 95% CI lower bound > 0; both halves lift > 0; excluding JPY crosses lift > 0; excluding best pair (GBPUSD) lift > 0; F6 flagged reversal rate > 50% (a 1:1 race needs it before any cost); F7 lift beats score>30 with ANY background (the colour must add something)**

Bars evaluated by background: green 14182, red 11721, none 88754

| arm | events | flagged reversal rate | matched baseline | lift (95% CI) | top n / rate | bottom n / rate |
|---|---|---|---|---|---|---|
| opposite | 908 | 47.9% | 50.7% | -2.8% (-6.1% .. 1.1%) | 489 / 47.6% | 419 / 48.2% |
| with_trend | 31 | 45.2% | 47.7% | -2.5% (-18.2% .. 14.7%) | 5 / 40.0% | 26 / 46.2% |
| no_shading | 1874 | 48.3% | 49.0% | -0.7% (-3.4% .. 2.2%) | 1006 / 49.5% | 868 / 47.0% |
| any | 2670 | 47.9% | 49.1% | -1.1% (-3.6% .. 1.6%) | 1423 / 48.6% | 1247 / 47.2% |

## Gates (`opposite` arm)

- PASS: n de-clustered events >= 100
- FAIL: lift >= 0.05
- FAIL: 95% CI lower bound > 0
- FAIL: both halves lift > 0
- FAIL: excluding JPY crosses lift > 0
- FAIL: excluding best pair (GBPUSD) lift > 0
- FAIL: F6 flagged reversal rate > 50% (a 1:1 race needs it before any cost)
- FAIL: F7 lift beats score>30 with ANY background (the colour must add something)

Halves (split after 2023-07): first -6.0% (n=456), second 1.1% (n=452). Ex-JPY -0.0% (n=445). Ex-best-pair (GBPUSD) -3.3%.
Outcomes: {'reversal': 435, 'continuation': 471, 'timeout': 2, 'ties': 14}; conflicts 0; censored flags 7.

## Per pair (`opposite`)

  pair  n_events      lift  contribution
AUDJPY        71 -0.086123     -6.114698
AUDUSD        63 -0.124568     -7.847788
CADJPY        83  0.009060      0.751986
EURJPY        77 -0.116557     -8.974907
EURUSD        83  0.013994      1.161519
GBPJPY        70 -0.142934    -10.005394
GBPUSD        78  0.024104      1.880120
NZDJPY        82  0.013647      1.119085
NZDUSD        65  0.020449      1.329164
USDCAD        77  0.022040      1.697104
USDCHF        79  0.021373      1.688440
USDJPY        80 -0.024630     -1.970430
