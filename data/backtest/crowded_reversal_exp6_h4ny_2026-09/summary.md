# Experiment 6 — score > 30 against the OPPOSITE background (h4ny, full)

Verdict (`opposite` arm, pre-registered gates): **FAIL — lift >= 0.05; 95% CI lower bound > 0; both halves lift > 0; excluding best pair (NZDUSD) lift > 0; F6 flagged reversal rate > 50% (a 1:1 race needs it before any cost); F7 lift beats score>30 with ANY background (the colour must add something)**

Bars evaluated by background: green 13134, red 11559, none 86872

| arm | events | flagged reversal rate | matched baseline | lift (95% CI) | top n / rate | bottom n / rate |
|---|---|---|---|---|---|---|
| opposite | 862 | 49.8% | 50.4% | -0.6% (-4.2% .. 3.4%) | 443 / 50.3% | 419 / 49.2% |
| with_trend | 25 | 64.0% | 48.4% | 15.6% (-4.0% .. 36.4%) | 7 / 71.4% | 18 / 61.1% |
| no_shading | 1857 | 48.0% | 49.1% | -1.1% (-4.0% .. 2.0%) | 1005 / 48.2% | 852 / 47.9% |
| any | 2601 | 48.6% | 49.1% | -0.5% (-3.0% .. 2.2%) | 1380 / 49.2% | 1221 / 48.0% |

## Gates (`opposite` arm)

- PASS: n de-clustered events >= 100
- FAIL: lift >= 0.05
- FAIL: 95% CI lower bound > 0
- FAIL: both halves lift > 0
- PASS: excluding JPY crosses lift > 0
- FAIL: excluding best pair (NZDUSD) lift > 0
- FAIL: F6 flagged reversal rate > 50% (a 1:1 race needs it before any cost)
- FAIL: F7 lift beats score>30 with ANY background (the colour must add something)

Halves (split after 2023-07): first -3.2% (n=447), second 2.6% (n=415). Ex-JPY 0.6% (n=410). Ex-best-pair (NZDUSD) -1.2%.
Outcomes: {'reversal': 429, 'continuation': 432, 'timeout': 1, 'ties': 9}; conflicts 0; censored flags 6.

## Per pair (`opposite`)

  pair  n_events      lift  contribution
AUDJPY        83 -0.038014     -3.155173
AUDUSD        51 -0.098733     -5.035379
CADJPY        81  0.014664      1.187817
EURJPY        68  0.005824      0.396034
EURUSD        75 -0.022390     -1.679215
GBPJPY        69  0.012153      0.838558
GBPUSD        76  0.060348      4.586449
NZDJPY        82 -0.063695     -5.223003
NZDUSD        70  0.065790      4.605282
USDCAD        64  0.032294      2.066821
USDCHF        74 -0.026968     -1.995606
USDJPY        69 -0.026854     -1.852926
