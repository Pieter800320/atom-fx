# Experiment 7 — shading turns on after a stretched score (d1utc, full)

Verdict (`confirm10`, pre-registered gates): **FAIL — lift >= 0.05; 95% CI lower bound > 0; both halves lift > 0; excluding best pair (AUDUSD) lift > 0; F6 flagged reversal rate > 50%**

Onset bars in the window: green 307, red 338

| arm | events | flagged reversal rate | matched baseline | lift (95% CI) |
|---|---|---|---|---|
| confirm10 | 320 | 48.8% | 50.3% | -1.6% (-5.2% .. 2.3%) |
| confirm5 | 248 | 50.4% | 51.2% | -0.8% (-5.0% .. 3.5%) |
| bare_onset | 633 | 50.4% | 52.0% | -1.6% (-6.1% .. 2.5%) |

## Gates (`confirm10`)

- PASS: n de-clustered events >= 100
- FAIL: lift >= 0.05
- FAIL: 95% CI lower bound > 0
- FAIL: both halves lift > 0
- PASS: excluding JPY crosses lift > 0
- FAIL: excluding best pair (AUDUSD) lift > 0
- FAIL: F6 flagged reversal rate > 50%

Halves (split after 2017-10): first -2.6% (n=157), second -0.3% (n=163). Ex-JPY 2.4% (n=153). Ex-best-pair (AUDUSD) -2.4%.
Outcomes: {'reversal': 156, 'continuation': 164, 'timeout': 0, 'ties': 6}.

## Per pair (`confirm10`)

  pair  n_events      lift  contribution
AUDJPY        29 -0.088025     -2.552727
AUDUSD        22  0.090909      2.000000
CADJPY        25 -0.142378     -3.559441
EURJPY        29  0.011034      0.320000
EURUSD        28 -0.006281     -0.175862
GBPJPY        28 -0.047984     -1.343548
GBPUSD        30 -0.073872     -2.216165
NZDJPY        27 -0.023847     -0.643875
NZDUSD        25  0.060429      1.510714
USDCAD        25  0.039130      0.978261
USDCHF        23  0.065876      1.515152
USDJPY        29 -0.029885     -0.866667
