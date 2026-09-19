# Experiment 6 — score > 30 against the OPPOSITE background (d1, full)

Verdict (`opposite` arm, pre-registered gates): **INCONCLUSIVE — 95 de-clustered events < 100**

Bars evaluated by background: green 1507, red 997, none 15143

| arm | events | flagged reversal rate | matched baseline | lift (95% CI) | top n / rate | bottom n / rate |
|---|---|---|---|---|---|---|
| opposite | 95 | 52.6% | 53.5% | -0.8% (-10.2% .. 8.5%) | 62 / 54.8% | 33 / 48.5% |
| with_trend | 2 | 0.0% | 47.1% | -47.1% (-58.7% .. -34.0%) | 0 / n/a | 2 / 0.0% |
| no_shading | 296 | 48.3% | 48.4% | -0.1% (-5.9% .. 6.9%) | 169 / 46.2% | 127 / 51.2% |
| any | 371 | 49.1% | 48.4% | 0.7% (-4.0% .. 6.5%) | 216 / 49.1% | 155 / 49.0% |

## Gates (`opposite` arm)

- FAIL: n de-clustered events >= 100
- FAIL: lift >= 0.05
- FAIL: 95% CI lower bound > 0
- FAIL: both halves lift > 0
- FAIL: excluding JPY crosses lift > 0
- FAIL: excluding best pair (NZDJPY) lift > 0
- PASS: F6 flagged reversal rate > 50% (a 1:1 race needs it before any cost)
- FAIL: F7 lift beats score>30 with ANY background (the colour must add something)

Halves (split after 2023-10): first 4.4% (n=46), second -5.1% (n=49). Ex-JPY -5.8% (n=44). Ex-best-pair (NZDJPY) -2.4%.
Outcomes: {'reversal': 50, 'continuation': 45, 'timeout': 0, 'ties': 1}; conflicts 0; censored flags 0.

## Per pair (`opposite`)

  pair  n_events      lift  contribution
AUDJPY         3 -0.152104     -0.456311
AUDUSD         6 -0.049311     -0.295866
CADJPY        10 -0.001500     -0.015000
EURJPY         4  0.029032      0.116129
EURUSD         7 -0.025284     -0.176991
GBPJPY        10  0.125641      1.256410
GBPUSD         9 -0.000215     -0.001938
NZDJPY         9  0.145863      1.312771
NZDUSD         6 -0.272549     -1.635294
USDCAD         6 -0.188725     -1.132353
USDCHF        10  0.068918      0.689181
USDJPY        15 -0.030263     -0.453950
