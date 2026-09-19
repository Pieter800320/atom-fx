# Experiment 6 — score > 30 against the OPPOSITE background (d1utc, full)

Verdict (`opposite` arm, pre-registered gates): **FAIL — lift >= 0.05; 95% CI lower bound > 0; both halves lift > 0; excluding JPY crosses lift > 0; excluding best pair (EURJPY) lift > 0**

Bars evaluated by background: green 4742, red 4854, none 45580

| arm | events | flagged reversal rate | matched baseline | lift (95% CI) | top n / rate | bottom n / rate |
|---|---|---|---|---|---|---|
| opposite | 380 | 51.1% | 51.6% | -0.5% (-5.7% .. 5.2%) | 190 / 52.1% | 190 / 50.0% |
| with_trend | 7 | 28.6% | 46.7% | -18.2% (-49.8% .. 21.7%) | 0 / n/a | 7 / 28.6% |
| no_shading | 765 | 48.0% | 49.6% | -1.6% (-5.7% .. 2.8%) | 422 / 48.3% | 343 / 47.5% |
| any | 1082 | 48.2% | 49.5% | -1.4% (-4.9% .. 2.5%) | 582 / 48.6% | 500 / 47.6% |

## Gates (`opposite` arm)

- PASS: n de-clustered events >= 100
- FAIL: lift >= 0.05
- FAIL: 95% CI lower bound > 0
- FAIL: both halves lift > 0
- FAIL: excluding JPY crosses lift > 0
- FAIL: excluding best pair (EURJPY) lift > 0
- PASS: F6 flagged reversal rate > 50% (a 1:1 race needs it before any cost)
- PASS: F7 lift beats score>30 with ANY background (the colour must add something)

Halves (split after 2017-10): first -0.4% (n=198), second 0.2% (n=182). Ex-JPY -6.1% (n=186). Ex-best-pair (EURJPY) -2.0%.
Outcomes: {'reversal': 194, 'continuation': 186, 'timeout': 0, 'ties': 9}; conflicts 0; censored flags 0.

## Per pair (`opposite`)

  pair  n_events      lift  contribution
AUDJPY        21 -0.151764     -3.187038
AUDUSD        25  0.060029      1.500715
CADJPY        34  0.028577      0.971612
EURJPY        28  0.178722      5.004225
EURUSD        36 -0.146877     -5.287575
GBPJPY        41  0.070089      2.873645
GBPUSD        30  0.014707      0.441203
NZDJPY        29  0.128145      3.716205
NZDUSD        36 -0.076562     -2.756233
USDCAD        30 -0.110546     -3.316382
USDCHF        29 -0.069551     -2.016966
USDJPY        41 -0.000089     -0.003644
