"""
ATOM FX research — score-gradient test for a reversal indicator (Experiment 2, primary test).

EXTEND tier, research use only: pure numpy/pandas, no I/O, nothing frozen touched.

WHY THIS EXISTS
  The flagged-event test (barrier_race.Study) needs the score to cross a threshold, which is rare:
  ~41-91 de-clustered events over 19 years x 12 pairs (count-only estimate, 2026-09-19) — too few
  to detect a 5-point effect. This test asks the same question of EVERY bar instead: does the
  probability of a reversal RISE as the indicator's score rises? No threshold, no tuning knob.

OBSERVATIONS
  For every valid bar (inside the evaluation window, race not censored) and each side:
      side 'bottom': x = bottom score,  y = 1 if the barrier race resolved UP first  (reversal)
      side 'top'   : x = top score,     y = 1 if the barrier race resolved DOWN first
  The race is barrier_race.race_all (1 x ATR, 20 bars, same-bar-both -> continuation, timeout -> 0),
  i.e. exactly the pre-registered reversal definition, applied to all bars instead of flagged ones.

STATISTICS (all with pair x side fixed effects, i.e. within-stratum, so a pair's drift or the
up/down asymmetry of the sample cannot masquerade as an effect)
  slope  : pooled within-stratum OLS slope of y on x, reported per +20 score points.
  rho    : within-stratum correlation of y with x (scale-free -> comparable across predictors).
  buckets: reversal rate / baseline / lift per FIXED score bucket (edges pre-registered).
  All intervals: month-clustered bootstrap. Calendar months are resampled with replacement and
  every statistic — including each stratum's baseline — is recomputed from the resampled months
  (sufficient statistics per pair x side x month). The SAME resamples are reused for every
  statistic, so differences (composite vs single factor, filter on vs off) are properly paired.
"""
import numpy as np
import pandas as pd

from scanner.extend import barrier_race as br

BUCKET_EDGES = [0, 10, 20, 30, 40, 50, 60, 100.0001]     # pre-registered: [0,10) ... [50,60) [60,100]
SIDES = ("bottom", "top")
SLOPE_UNIT = 20.0                                        # slope reported per +20 score points


class GradientStudy:
    """Accumulate per-bar observations for several predictors over one shared set of outcomes.

    add_pair(pair, dates, result, eval_mask, predictors) for every pair, where predictors maps a
    name to (x_bottom, x_top) arrays aligned to the bars. Then slope()/rho()/buckets()/bootstrap."""

    def __init__(self):
        self._rows = []           # per pair: dict of arrays
        self.pairs = []

    def add_pair(self, pair, dates, result, eval_mask, predictors):
        valid = np.asarray(eval_mask, bool) & (np.asarray(result) != br.CENSORED)
        idx = np.flatnonzero(valid)
        months = np.asarray(pd.to_datetime(dates).strftime("%Y-%m"))[idx]
        y_bottom = br.is_reversal(np.asarray(result)[idx], "bottom").astype(float)
        y_top = br.is_reversal(np.asarray(result)[idx], "top").astype(float)
        self._rows.append({"pair": pair, "months": months, "y": (y_bottom, y_top),
                           "x": {k: (np.asarray(v[0], float)[idx], np.asarray(v[1], float)[idx])
                                 for k, v in predictors.items()}})
        self.pairs.append(pair)

    @property
    def months(self):
        """Sorted 'YYYY-MM' labels of every month that holds at least one observation."""
        self._finalize()
        return self._months

    # -- sufficient statistics -----------------------------------------------------------
    def _finalize(self):
        if getattr(self, "_ready", False):
            return
        self._months = sorted({m for r in self._rows for m in r["months"]})
        m_idx = {m: j for j, m in enumerate(self._months)}
        P, M = len(self._rows), len(self._months)
        names = list(self._rows[0]["x"]) if self._rows else []
        self.names = names
        shape = (P, 2, M)
        self.n = np.zeros(shape)
        self.sy = np.zeros(shape)
        self.sx = {k: np.zeros(shape) for k in names}
        self.sxx = {k: np.zeros(shape) for k in names}
        self.sxy = {k: np.zeros(shape) for k in names}
        K = len(BUCKET_EDGES) - 1
        self.bn = {k: np.zeros((P, 2, M, K)) for k in names}
        self.by = {k: np.zeros((P, 2, M, K)) for k in names}
        for p, r in enumerate(self._rows):
            mi = np.array([m_idx[m] for m in r["months"]], dtype=int)
            for s in range(2):
                y = r["y"][s]
                np.add.at(self.n[p, s], mi, 1.0)
                np.add.at(self.sy[p, s], mi, y)
                for k in names:
                    x = r["x"][k][s]
                    np.add.at(self.sx[k][p, s], mi, x)
                    np.add.at(self.sxx[k][p, s], mi, x * x)
                    np.add.at(self.sxy[k][p, s], mi, x * y)
                    b = np.clip(np.searchsorted(BUCKET_EDGES, x, side="right") - 1, 0, K - 1)
                    np.add.at(self.bn[k][p, s], (mi, b), 1.0)
                    np.add.at(self.by[k][p, s], (mi, b), y)
        self._ready = True

    # -- month multiplicities ------------------------------------------------------------
    def draw_counts(self, n_boot, seed):
        """(n_boot, n_months) resampling multiplicities, reused by every statistic (paired)."""
        self._finalize()
        rng = np.random.default_rng(seed)
        M = len(self.months)
        return np.stack([np.bincount(rng.integers(0, M, M), minlength=M) for _ in range(n_boot)]).astype(float)

    def _weights(self, counts, pair_mask=None, month_mask=None):
        """counts: (B, M) multiplicities (or (M,) -> treated as B=1). Returns (B, M) weights with
        month_mask applied, and the boolean pair selector (P,)."""
        c = np.atleast_2d(np.asarray(counts, float))
        if month_mask is not None:
            c = c * np.asarray(month_mask, float)[None, :]
        pm = np.ones(len(self._rows), bool) if pair_mask is None else np.asarray(pair_mask, bool)
        return c, pm

    def _agg(self, arr, c, pm):
        """Sum a (P,2,M) statistic over months with weights c (B,M) -> (B, P', 2)."""
        return np.einsum("psm,bm->bps", arr[pm], c)

    # -- statistics ----------------------------------------------------------------------
    def slope(self, name, counts, pair_mask=None, month_mask=None):
        """Within-stratum pooled OLS slope of y on x, per +20 score points. Returns (B,)."""
        self._finalize()
        c, pm = self._weights(counts, pair_mask, month_mask)
        N, Sx, Sy = self._agg(self.n, c, pm), self._agg(self.sx[name], c, pm), self._agg(self.sy, c, pm)
        Sxx, Sxy = self._agg(self.sxx[name], c, pm), self._agg(self.sxy[name], c, pm)
        with np.errstate(divide="ignore", invalid="ignore"):
            cxx = np.where(N > 0, Sxx - Sx ** 2 / N, 0.0)
            cxy = np.where(N > 0, Sxy - Sx * Sy / N, 0.0)
        den = cxx.sum(axis=(1, 2))
        with np.errstate(divide="ignore", invalid="ignore"):
            return SLOPE_UNIT * np.where(den > 0, cxy.sum(axis=(1, 2)) / den, np.nan)

    def rho(self, name, counts, pair_mask=None, month_mask=None):
        """Within-stratum correlation of y with x. Returns (B,)."""
        self._finalize()
        c, pm = self._weights(counts, pair_mask, month_mask)
        N, Sx, Sy = self._agg(self.n, c, pm), self._agg(self.sx[name], c, pm), self._agg(self.sy, c, pm)
        Sxx, Sxy = self._agg(self.sxx[name], c, pm), self._agg(self.sxy[name], c, pm)
        with np.errstate(divide="ignore", invalid="ignore"):
            cxx = np.where(N > 0, Sxx - Sx ** 2 / N, 0.0)
            cxy = np.where(N > 0, Sxy - Sx * Sy / N, 0.0)
            cyy = np.where(N > 0, Sy - Sy ** 2 / N, 0.0)          # y is 0/1, so y^2 = y
            den = np.sqrt(cxx.sum(axis=(1, 2)) * cyy.sum(axis=(1, 2)))
            return np.where(den > 0, cxy.sum(axis=(1, 2)) / den, np.nan)

    def buckets(self, name, counts, pair_mask=None, month_mask=None):
        """Per fixed bucket: (n_obs, reversal_rate, baseline_rate, lift), each (B, K).
        baseline = mean over the bucket's observations of their stratum's own reversal rate."""
        self._finalize()
        c, pm = self._weights(counts, pair_mask, month_mask)
        N, Sy = self._agg(self.n, c, pm), self._agg(self.sy, c, pm)
        with np.errstate(divide="ignore", invalid="ignore"):
            r = np.where(N > 0, Sy / N, np.nan)                                   # (B, P', 2)
        bn = np.einsum("psmk,bm->bpsk", self.bn[name][pm], c)
        by = np.einsum("psmk,bm->bpsk", self.by[name][pm], c)
        n_obs = bn.sum(axis=(1, 2))
        with np.errstate(divide="ignore", invalid="ignore"):
            rate = np.where(n_obs > 0, by.sum(axis=(1, 2)) / n_obs, np.nan)
            base = np.where(n_obs > 0, np.nansum(bn * r[..., None], axis=(1, 2)) / n_obs, np.nan)
        return n_obs, rate, base, rate - base

    # -- convenience ---------------------------------------------------------------------
    def ones(self):
        self._finalize()
        return np.ones(len(self.months))

    def month_masks(self, split_month):
        """(first_half_mask, second_half_mask) over self.months; split_month like '2023-06'
        belongs to the first half."""
        self._finalize()
        ms = np.array(self.months)
        return ms <= split_month, ms > split_month

    @staticmethod
    def ci(values, lo=2.5, hi=97.5):
        v = np.asarray(values, float)
        v = v[~np.isnan(v)]
        return (float(np.percentile(v, lo)), float(np.percentile(v, hi))) if len(v) else (np.nan, np.nan)
