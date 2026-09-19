# Experiment 6 — score > 30 against the OPPOSITE background (d1utc, full)

Verdict (`opposite` arm, pre-registered gates): **FAIL — lift >= 0.05; 95% CI lower bound > 0; both halves lift > 0; excluding JPY crosses lift > 0; excluding best pair (EURJPY) lift > 0; F6 flagged reversal rate > 50% (a 1:1 race needs it before any cost)**

Bars evaluated by background: green 2962, red 3658, none 30672

| arm | events | flagged reversal rate | matched baseline | lift (95% CI) | top n / rate | bottom n / rate |
|---|---|---|---|---|---|---|
| opposite | 277 | 49.8% | 49.8% | 0.1% (-5.7% .. 6.6%) | 126 / 50.8% | 151 / 49.0% |
| with_trend | 2 | 50.0% | 47.9% | 2.1% (-50.5% .. 55.8%) | 0 / n/a | 2 / 50.0% |
| no_shading | 493 | 49.1% | 49.8% | -0.7% (-5.1% .. 3.9%) | 254 / 50.4% | 239 / 47.7% |
| any | 722 | 48.5% | 49.8% | -1.3% (-5.4% .. 3.1%) | 361 / 49.3% | 361 / 47.6% |

## Gates (`opposite` arm)

- PASS: n de-clustered events >= 100
- FAIL: lift >= 0.05
- FAIL: 95% CI lower bound > 0
- FAIL: both halves lift > 0
- FAIL: excluding JPY crosses lift > 0
- FAIL: excluding best pair (EURJPY) lift > 0
- FAIL: F6 flagged reversal rate > 50% (a 1:1 race needs it before any cost)
- PASS: F7 lift beats score>30 with ANY background (the colour must add something)

Halves (split after 2014-12): first 5.2% (n=121), second -2.2% (n=156). Ex-JPY -5.4% (n=142). Ex-best-pair (EURJPY) -1.5%.
Outcomes: {'reversal': 138, 'continuation': 139, 'timeout': 0, 'ties': 5}; conflicts 0; censored flags 0.

## Per pair (`opposite`)

  pair  n_events      lift  contribution
AUDJPY        15 -0.184720     -2.770797
AUDUSD        17 -0.031812     -0.540811
CADJPY        19  0.152654      2.900424
EURJPY        23  0.169028      3.887650
EURUSD        29 -0.167623     -4.861075
GBPJPY        32  0.036250      1.160000
GBPUSD        25  0.037789      0.944714
NZDJPY        21  0.054198      1.138162
NZDUSD        30  0.021532      0.645958
USDCAD        23 -0.147841     -3.400351
USDCHF        18 -0.022608     -0.406939
USDJPY        25  0.057697      1.442432
