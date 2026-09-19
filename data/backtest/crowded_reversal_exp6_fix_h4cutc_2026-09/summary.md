# Experiment 6 — score > 30 against the OPPOSITE background (h4cutc, full)

Verdict (`opposite` arm, pre-registered gates): **FAIL — lift >= 0.05; 95% CI lower bound > 0; both halves lift > 0; excluding JPY crosses lift > 0; excluding best pair (EURJPY) lift > 0; F7 lift beats score>30 with ANY background (the colour must add something)**

Bars evaluated by background: green 13360, red 11745, none 89810

| arm | events | flagged reversal rate | matched baseline | lift (95% CI) | top n / rate | bottom n / rate |
|---|---|---|---|---|---|---|
| opposite | 930 | 50.9% | 50.4% | 0.5% (-3.5% .. 4.9%) | 486 / 50.4% | 444 / 51.4% |
| with_trend | 36 | 52.8% | 48.2% | 4.6% (-8.2% .. 18.4%) | 8 / 75.0% | 28 / 46.4% |
| no_shading | 1890 | 48.9% | 48.9% | 0.1% (-2.7% .. 2.9%) | 1024 / 49.4% | 866 / 48.4% |
| any | 2674 | 49.5% | 48.9% | 0.5% (-2.1% .. 3.3%) | 1426 / 49.9% | 1248 / 49.0% |

## Gates (`opposite` arm)

- PASS: n de-clustered events >= 100
- FAIL: lift >= 0.05
- FAIL: 95% CI lower bound > 0
- FAIL: both halves lift > 0
- FAIL: excluding JPY crosses lift > 0
- FAIL: excluding best pair (EURJPY) lift > 0
- PASS: F6 flagged reversal rate > 50% (a 1:1 race needs it before any cost)
- FAIL: F7 lift beats score>30 with ANY background (the colour must add something)

Halves (split after 2023-07): first -1.9% (n=490), second 3.7% (n=440). Ex-JPY -1.8% (n=420). Ex-best-pair (EURJPY) -0.5%.
Outcomes: {'reversal': 473, 'continuation': 457, 'timeout': 0, 'ties': 17}; conflicts 0; censored flags 7.

## Per pair (`opposite`)

  pair  n_events      lift  contribution
AUDJPY        89 -0.063235     -5.627894
AUDUSD        62 -0.024519     -1.520160
CADJPY        82 -0.031276     -2.564593
EURJPY        80  0.106510      8.520833
EURUSD        77 -0.073581     -5.665704
GBPJPY        91  0.056458      5.137668
GBPUSD        70  0.033679      2.357502
NZDJPY        91  0.069750      6.347269
NZDUSD        59 -0.099423     -5.865980
USDCAD        81  0.089918      7.283391
USDCHF        71 -0.055984     -3.974845
USDJPY        77  0.000344      0.026468
