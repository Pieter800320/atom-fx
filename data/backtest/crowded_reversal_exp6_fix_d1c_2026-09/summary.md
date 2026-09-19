# Experiment 6 — score > 30 against the OPPOSITE background (d1c, full)

Verdict (`opposite` arm, pre-registered gates): **FAIL — lift >= 0.05; 95% CI lower bound > 0; both halves lift > 0; excluding JPY crosses lift > 0**

Bars evaluated by background: green 1775, red 1246, none 14603

| arm | events | flagged reversal rate | matched baseline | lift (95% CI) | top n / rate | bottom n / rate |
|---|---|---|---|---|---|---|
| opposite | 109 | 57.8% | 55.2% | 2.6% (-7.0% .. 13.1%) | 64 / 59.4% | 45 / 55.6% |
| with_trend | 5 | 20.0% | 38.3% | -18.3% (-47.7% .. 3.4%) | 1 / 0.0% | 4 / 25.0% |
| no_shading | 265 | 44.9% | 48.3% | -3.4% (-10.4% .. 5.3%) | 161 / 45.3% | 104 / 44.2% |
| any | 356 | 47.2% | 48.4% | -1.2% (-7.3% .. 5.9%) | 213 / 47.9% | 143 / 46.2% |

## Gates (`opposite` arm)

- PASS: n de-clustered events >= 100
- FAIL: lift >= 0.05
- FAIL: 95% CI lower bound > 0
- FAIL: both halves lift > 0
- FAIL: excluding JPY crosses lift > 0
- PASS: excluding best pair (GBPJPY) lift > 0
- PASS: F6 flagged reversal rate > 50% (a 1:1 race needs it before any cost)
- PASS: F7 lift beats score>30 with ANY background (the colour must add something)

Halves (split after 2023-10): first 10.3% (n=57), second -4.4% (n=52). Ex-JPY -1.9% (n=49). Ex-best-pair (GBPJPY) 0.9%.
Outcomes: {'reversal': 63, 'continuation': 46, 'timeout': 0, 'ties': 4}; conflicts 0; censored flags 0.

## Per pair (`opposite`)

  pair  n_events      lift  contribution
AUDJPY         5  0.252775      1.263875
AUDUSD         9  0.090909      0.818182
CADJPY        18 -0.025273     -0.454921
EURJPY         5  0.277778      1.388889
EURUSD        10  0.141718      1.417180
GBPJPY        10  0.201163      2.011628
GBPUSD         8  0.045238      0.361905
NZDJPY         7  0.110476      0.773333
NZDUSD         5 -0.232188     -1.160941
USDCAD         8  0.031038      0.248306
USDCHF         9 -0.288759     -2.598828
USDJPY        15 -0.080321     -1.204819
