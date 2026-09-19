# Experiment 6 — score > 30 against the OPPOSITE background (d1utc, full)

Verdict (`opposite` arm, pre-registered gates): **FAIL — lift >= 0.05; 95% CI lower bound > 0; both halves lift > 0; excluding JPY crosses lift > 0; excluding best pair (NZDJPY) lift > 0**

Bars evaluated by background: green 1780, red 1196, none 14908

| arm | events | flagged reversal rate | matched baseline | lift (95% CI) | top n / rate | bottom n / rate |
|---|---|---|---|---|---|---|
| opposite | 103 | 54.4% | 54.2% | 0.2% (-10.0% .. 9.8%) | 64 / 54.7% | 39 / 53.8% |
| with_trend | 5 | 20.0% | 43.4% | -23.4% (-51.8% .. 11.3%) | 0 / n/a | 5 / 20.0% |
| no_shading | 272 | 46.0% | 48.1% | -2.1% (-9.8% .. 7.3%) | 168 / 45.2% | 104 / 47.1% |
| any | 360 | 47.5% | 48.1% | -0.6% (-7.1% .. 7.0%) | 221 / 47.5% | 139 / 47.5% |

## Gates (`opposite` arm)

- PASS: n de-clustered events >= 100
- FAIL: lift >= 0.05
- FAIL: 95% CI lower bound > 0
- FAIL: both halves lift > 0
- FAIL: excluding JPY crosses lift > 0
- FAIL: excluding best pair (NZDJPY) lift > 0
- PASS: F6 flagged reversal rate > 50% (a 1:1 race needs it before any cost)
- PASS: F7 lift beats score>30 with ANY background (the colour must add something)

Halves (split after 2023-10): first 5.4% (n=60), second -6.6% (n=43). Ex-JPY -4.3% (n=44). Ex-best-pair (NZDJPY) -2.7%.
Outcomes: {'reversal': 56, 'continuation': 47, 'timeout': 0, 'ties': 4}; conflicts 0; censored flags 0.

## Per pair (`opposite`)

  pair  n_events      lift  contribution
AUDJPY         6 -0.042720     -0.256323
AUDUSD         8  0.257301      2.058408
CADJPY        15 -0.136033     -2.040493
EURJPY         5  0.264119      1.320596
EURUSD         7  0.002674      0.018717
GBPJPY         9  0.204678      1.842105
GBPUSD         5 -0.032159     -0.160794
NZDJPY         8  0.337805      2.702439
NZDUSD         6 -0.452157     -2.712941
USDCAD         7  0.068584      0.480087
USDCHF        11 -0.144968     -1.594647
USDJPY        16 -0.092493     -1.479889
