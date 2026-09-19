"""
ATOM FX research — score_gradient.py tests.

Synthetic data with PLANTED effects (and planted nulls) so each statistic is checked against a
direct, independent computation. No network, nothing frozen imported.

Run:  python -m tests.test_score_gradient      (or: pytest tests/test_score_gradient.py)
"""
import numpy as np
import pandas as pd

from scanner.extend import barrier_race as br
from scanner.extend import score_gradient as sg


def _dates(n):
    return pd.bdate_range("2021-01-04", periods=n)


def _make(pairs_cfg, n=1200, seed=0):
    """pairs_cfg: {pair: (base_rate, x_mean, slope_per_point)}. x is the same score for both sides;
    P(bottom reversal) = clip(base + slope*(x-x_mean)); result UP if reversal else DOWN."""
    rng = np.random.default_rng(seed)
    gs = sg.GradientStudy()
    raw = {}
    for pair, (base, xm, slope) in pairs_cfg.items():
        x = np.clip(rng.normal(xm, 15, n), 0, 100)
        p = np.clip(base + slope * (x - xm), 0.01, 0.99)
        y = (rng.random(n) < p).astype(float)
        result = np.where(y == 1, br.UP, br.DOWN)
        gs.add_pair(pair, _dates(n), result, np.ones(n, bool), {"score": (x, x)})
        raw[pair] = (x, y)
    return gs, raw


def _direct_slope(raw):
    """Within-(pair, side) pooled OLS slope per +20 points, from the raw arrays. The 'top' side has
    y_top = 1 - y_bottom here (UP/DOWN complementary), same x."""
    num = den = 0.0
    for x, y in raw.values():
        for yy in (y, 1 - y):
            xd, yd = x - x.mean(), yy - yy.mean()
            num += (xd * yd).sum()
            den += (xd * xd).sum()
    return 20.0 * num / den


def test_slope_matches_direct_computation():
    gs, raw = _make({"EURUSD": (0.4, 30, 0.004), "GBPUSD": (0.5, 20, 0.003)})
    got = gs.slope("score", gs.ones())[0]
    assert abs(got - _direct_slope(raw)) < 1e-9


def test_planted_positive_effect_is_detected_with_ci_above_zero():
    # y_top = 1 - y_bottom has the OPPOSITE dependence on x, so a symmetric x cancels in the
    # pooled slope by construction — plant the effect on BOTH sides instead:
    rng = np.random.default_rng(3)
    gs, n = sg.GradientStudy(), 1500
    for pair in ("EURUSD", "GBPUSD", "USDJPY"):
        x = np.clip(rng.normal(30, 15, n), 0, 100)
        p = np.clip(0.35 + 0.004 * (x - 30), 0.01, 0.99)
        yb = rng.random(n) < p                         # bottom reversal (up first)
        yt = rng.random(n) < p                         # top reversal (down first), independent draw
        result = np.where(yb, br.UP, np.where(yt, br.DOWN, br.TIMEOUT))
        gs.add_pair(pair, _dates(n), result, np.ones(n, bool), {"score": (x, x)})
    counts = gs.draw_counts(500, seed=1)
    b = gs.slope("score", counts)
    lo, hi = gs.ci(b)
    assert 0.05 < gs.slope("score", gs.ones())[0] < 0.11          # planted: 0.004 * 20 = 0.08
    assert lo > 0


def test_pure_noise_gives_ci_containing_zero():
    rng = np.random.default_rng(5)
    gs, n = sg.GradientStudy(), 1500
    for pair in ("EURUSD", "GBPUSD"):
        x = np.clip(rng.normal(30, 15, n), 0, 100)
        result = np.where(rng.random(n) < 0.4, br.UP, br.DOWN)   # independent of x
        gs.add_pair(pair, _dates(n), result, np.ones(n, bool), {"score": (x, x)})
    lo, hi = gs.ci(gs.slope("score", gs.draw_counts(500, seed=2)))
    assert lo < 0 < hi


def test_fixed_effects_remove_between_pair_confounding():
    # pair A: high base rate AND high scores; pair B: low base rate AND low scores; within each
    # pair y is independent of x. A naive pooled slope would be large; the FE slope must be ~0.
    rng = np.random.default_rng(9)
    gs, n = sg.GradientStudy(), 3000
    for pair, base, xm in (("EURUSD", 0.8, 60.0), ("GBPUSD", 0.2, 10.0)):
        x = np.clip(rng.normal(xm, 8, n), 0, 100)
        yb = rng.random(n) < base
        gs.add_pair(pair, _dates(n), np.where(yb, br.UP, br.DOWN), np.ones(n, bool), {"score": (x, x)})
    assert abs(gs.slope("score", gs.ones())[0]) < 0.03


def test_rho_matches_numpy_corrcoef_within_strata():
    gs, raw = _make({"EURUSD": (0.4, 30, 0.004)})
    x, y = raw["EURUSD"]
    xd = np.concatenate((x - x.mean(), x - x.mean()))
    yd = np.concatenate((y - y.mean(), (1 - y) - (1 - y).mean()))
    expected = (xd * yd).sum() / np.sqrt((xd ** 2).sum() * (yd ** 2).sum())
    assert abs(gs.rho("score", gs.ones())[0] - expected) < 1e-9


def test_buckets_partition_all_observations_and_lift_is_rate_minus_baseline():
    gs, raw = _make({"EURUSD": (0.4, 30, 0.004), "GBPUSD": (0.5, 20, 0.003)})
    n_obs, rate, base, lift = gs.buckets("score", gs.ones())
    assert n_obs[0].sum() == 2 * 2 * 1200                       # 2 pairs x 2 sides x 1200 bars
    assert np.allclose(lift[0], rate[0] - base[0], equal_nan=True)
    # the observation-weighted mean lift across buckets is exactly 0 (baseline is each stratum's own mean)
    w = n_obs[0] / n_obs[0].sum()
    assert abs(np.nansum(w * lift[0])) < 1e-9


def test_bucket_edges_are_left_closed():
    gs = sg.GradientStudy()
    x = np.array([0.0, 9.99, 10.0, 59.99, 60.0, 100.0])
    gs.add_pair("EURUSD", _dates(6), np.full(6, br.UP), np.ones(6, bool), {"score": (x, x)})
    n_obs = gs.buckets("score", gs.ones())[0][0]
    # per side: [0,10)=2, [10,20)=1, ..., [50,60)=1, [60,100]=2 ; two sides
    assert list(n_obs) == [4, 2, 0, 0, 0, 2, 4]


def test_censored_and_outside_window_bars_are_excluded():
    gs, n = sg.GradientStudy(), 50
    result = np.full(n, br.UP)
    result[-5:] = br.CENSORED
    mask = np.ones(n, bool)
    mask[:10] = False
    x = np.linspace(0, 100, n)
    gs.add_pair("EURUSD", _dates(n), result, mask, {"score": (x, x)})
    assert gs.buckets("score", gs.ones())[0][0].sum() == 2 * (n - 10 - 5)


def test_paired_resamples_and_determinism():
    gs, _ = _make({"EURUSD": (0.4, 30, 0.004)})
    a, b = gs.draw_counts(50, seed=7), gs.draw_counts(50, seed=7)
    assert (a == b).all() and (a.sum(axis=1) == len(gs.months)).all()
    assert np.allclose(gs.slope("score", a), gs.slope("score", b))


def test_month_masks_partition_the_months():
    gs, _ = _make({"EURUSD": (0.4, 30, 0.004)})
    first, second = gs.month_masks(gs.months[len(gs.months) // 2])
    assert (first ^ second).all() and first.any() and second.any()


def test_pair_mask_leaves_one_out():
    gs, raw = _make({"EURUSD": (0.4, 30, 0.004), "GBPUSD": (0.5, 20, -0.004)})
    only_eur = gs.slope("score", gs.ones(), pair_mask=[True, False])[0]
    assert abs(only_eur - _direct_slope({"EURUSD": raw["EURUSD"]})) < 1e-9


if __name__ == "__main__":
    import sys
    import pytest
    sys.exit(pytest.main([__file__, "-q"]))
