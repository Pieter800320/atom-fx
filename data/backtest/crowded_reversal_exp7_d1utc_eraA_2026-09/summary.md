# Experiment 7 — shading turns on after a stretched score (d1utc, full)

Verdict (`confirm10`, pre-registered gates): **FAIL — lift >= 0.05; 95% CI lower bound > 0; both halves lift > 0; excluding best pair (USDCHF) lift > 0**

Onset bars in the window: green 184, red 241

| arm | events | flagged reversal rate | matched baseline | lift (95% CI) |
|---|---|---|---|---|
| confirm10 | 217 | 50.2% | 50.4% | -0.1% (-4.4% .. 4.3%) |
| confirm5 | 171 | 51.5% | 51.7% | -0.2% (-5.1% .. 5.0%) |
| bare_onset | 424 | 51.2% | 50.4% | 0.8% (-4.7% .. 6.1%) |

## Gates (`confirm10`)

- PASS: n de-clustered events >= 100
- FAIL: lift >= 0.05
- FAIL: 95% CI lower bound > 0
- FAIL: both halves lift > 0
- PASS: excluding JPY crosses lift > 0
- FAIL: excluding best pair (USDCHF) lift > 0
- PASS: F6 flagged reversal rate > 50%

Halves (split after 2014-12): first -2.9% (n=99), second 2.9% (n=118). Ex-JPY 2.9% (n=104). Ex-best-pair (USDCHF) -1.6%.
Outcomes: {'reversal': 109, 'continuation': 108, 'timeout': 0, 'ties': 3}.

## Per pair (`confirm10`)

  pair  n_events      lift  contribution
AUDJPY        18 -0.162698     -2.928571
AUDUSD        17  0.118207      2.009524
CADJPY        17 -0.070502     -1.198529
EURJPY        19  0.012406      0.235714
EURUSD        17 -0.042017     -0.714286
GBPJPY        20 -0.022222     -0.444444
GBPUSD        21 -0.055462     -1.164706
NZDJPY        22 -0.038369     -0.844118
NZDUSD        20  0.038091      0.761818
USDCAD        14 -0.057143     -0.800000
USDCHF        15  0.195362      2.930435
USDJPY        17  0.107843      1.833333
