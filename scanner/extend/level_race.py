"""
ATOM FX research — "back to the previous support/resistance" outcome (Experiment 4).

EXTEND tier, research use only: pure numpy/pandas, no I/O, nothing frozen touched.

THE QUESTION
  Pieter's chart impression: after a top/bottom flag, "if one trades back to the previous support/resistance,
  it often succeeds." This module makes that testable with an objective, look-ahead-free definition.

LEVELS (as of each bar t — only what was knowable at t's close)
  Swings come from the repo's own scanner.extend.swings.find_swings (strict-left / tolerant-right, swing_n=5).
  A swing at bar i uses bars i-5..i+5, so it is CONFIRMED only from bar i+5 on; at bar t only swings with
  i <= t - swing_n are visible.
    bottom flag (expects UP)   : target = the MOST RECENT confirmed swing HIGH lying between close + min_d*ATR and
                                 close + max_d*ATR, no older than `lookback` bars   ("previous resistance")
    top flag (expects DOWN)    : mirror image with swing LOWS                        ("previous support")
  No qualifying swing -> the bar has no valid level on that side and is excluded (flagged and baseline alike).

THE RACE (per bar, per side)
  D = distance from the close to the level. Target = the level; stop = the same distance D on the other side
  (1:1). Walk the next `horizon` bars' high/low:
    target touched first -> SUCCESS;  stop touched first -> STOP;  both inside one bar -> BOTH (cannot be
    sequenced from OHLC; counts as a FAILURE, conservative);  neither in `horizon` bars -> TIMEOUT (failure).
  "Success" means the level was reached before an equal adverse move — it is NOT profit (no costs, no entry rule).

MATCHED BASELINE (the part that keeps this honest)
  A stretched bar naturally sits farther from the previous level than an average bar, and a farther level is
  harder to reach in 20 bars, so a pooled baseline would be biased against flagged bars. The baseline is therefore
  measured per (pair, side, DISTANCE BUCKET): the same race from EVERY valid bar, bucketed by D/ATR into
  [0.5,1) [1,2) [2,3) [3,5]. A flagged event's lift = its outcome minus the baseline rate of ITS bucket.

STUDY
  LevelStudy mirrors barrier_race.Study's interface (add_pair, lift, per_pair, outcome_counts, .base, .conflicts,
  .censored_flags) so the pre-registered gate logic in tools.backtest_crowded_reversal.evaluate_flags is reused
  unchanged. Events are de-clustered per pair (a new flag opens no event until the previous race resolved); the
  bootstrap resamples calendar months and recomputes every bucket baseline from the resample.
"""
import bisect

import numpy as np
import pandas as pd

from scanner.extend.swings import find_swings

SUCCESS, STOP, BOTH, TIMEOUT, INVALID = 1, -1, 2, 0, -9
D_EDGES = [0.5, 1.0, 2.0, 3.0, 5.0000001]      # ATR-distance buckets: [0.5,1) [1,2) [2,3) [3,5]
N_BUCKETS = len(D_EDGES) - 1


def d_bucket(d):
    """Bucket index 0..3 for an ATR-normalised distance d (array or scalar); caller guarantees 0.5 <= d <= 5."""
    return np.clip(np.searchsorted(D_EDGES, d, side="right") - 1, 0, N_BUCKETS - 1)


def level_targets(high, low, close, atr, swing_n=5, lookback=120, min_d=0.5, max_d=5.0):
    """(up_level, down_level) arrays: the previous resistance above / support below each bar's close,
    or NaN. Uses only swings confirmed by that bar (index <= t - swing_n)."""
    high, low, close, atr = (np.asarray(a, float) for a in (high, low, close, atr))
    n = len(close)
    sw = find_swings(pd.DataFrame({"high": high, "low": low}), swing_n)
    hi_idx = [i for i, _ in sw["highs"]]
    hi_px = [p for _, p in sw["highs"]]
    lo_idx = [i for i, _ in sw["lows"]]
    lo_px = [p for _, p in sw["lows"]]
    up = np.full(n, np.nan)
    down = np.full(n, np.nan)
    for t in range(n):
        a = atr[t]
        if np.isnan(a) or a <= 0:
            continue
        c = close[t]
        cutoff = t - swing_n
        k = bisect.bisect_right(hi_idx, cutoff)
        for j in range(k - 1, -1, -1):
            if t - hi_idx[j] > lookback:
                break
            if c + min_d * a <= hi_px[j] <= c + max_d * a:
                up[t] = hi_px[j]
                break
        k = bisect.bisect_right(lo_idx, cutoff)
        for j in range(k - 1, -1, -1):
            if t - lo_idx[j] > lookback:
                break
            if c - max_d * a <= lo_px[j] <= c - min_d * a:
                down[t] = lo_px[j]
                break
    return up, down


def level_race_all(high, low, close, atr, up_level, down_level, horizon=20):
    """First-passage result of the 1:1 target-vs-stop race for EVERY bar, both sides.

    Returns dict: code_b/code_t (SUCCESS, STOP, BOTH, TIMEOUT, INVALID), k_b/k_t (bars to resolution),
    d_b/d_t (level distance in ATR). 'b' = bottom flag (target above), 't' = top flag (target below)."""
    high, low, close, atr = (np.asarray(a, float) for a in (high, low, close, atr))
    n = len(close)
    out = {f"{k}_{s}": (np.full(n, INVALID, int) if k == "code" else np.zeros(n, int) if k == "k" else np.full(n, np.nan))
           for k in ("code", "k", "d") for s in ("b", "t")}
    for t in range(n):
        if t + horizon >= n or np.isnan(atr[t]) or atr[t] <= 0:
            continue
        for side, level in (("b", up_level[t]), ("t", down_level[t])):
            if np.isnan(level):
                continue
            c = close[t]
            D = abs(level - c)
            up_side = side == "b"
            stop = c - D if up_side else c + D
            code, kk = TIMEOUT, horizon
            for j in range(1, horizon + 1):
                hit_target = high[t + j] >= level if up_side else low[t + j] <= level
                hit_stop = low[t + j] <= stop if up_side else high[t + j] >= stop
                if hit_target and hit_stop:
                    code, kk = BOTH, j
                    break
                if hit_target:
                    code, kk = SUCCESS, j
                    break
                if hit_stop:
                    code, kk = STOP, j
                    break
            out[f"code_{side}"][t], out[f"k_{side}"][t], out[f"d_{side}"][t] = code, kk, D / atr[t]
    return out


class LevelStudy:
    """Events + distance-matched baseline for the level-return outcome. Interface mirrors barrier_race.Study."""

    def __init__(self, label, horizon):
        self.label, self.horizon = label, horizon
        self.events = []
        self.base = {}            # (pair, "side|bucket", month) -> [successes, total]
        self.conflicts = 0
        self.censored_flags = 0   # flags with no valid level / not enough forward bars (excluded)

    def add_pair(self, pair, dates, lv, top_new, bot_new, eval_mask, base_counts=None):
        months = np.asarray(pd.to_datetime(dates).strftime("%Y-%m"))
        n = len(months)
        busy_until = -1
        for i in range(n):
            t, b = bool(top_new[i]), bool(bot_new[i])
            if not (t or b) or not eval_mask[i]:
                continue
            if t and b:
                self.conflicts += 1
                continue
            s = "t" if t else "b"
            code = int(lv[f"code_{s}"][i])
            if code == INVALID:
                self.censored_flags += 1
                continue
            if i <= busy_until:
                continue
            kk = int(lv[f"k_{s}"][i])
            bucket = int(d_bucket(lv[f"d_{s}"][i]))
            self.events.append({
                "pair": pair, "date": pd.Timestamp(dates[i]), "month": months[i], "side": "top" if t else "bottom",
                "outcome": "reversal" if code == SUCCESS else "timeout" if code == TIMEOUT else "continuation",
                "tie": code == BOTH, "k": kk, "y": int(code == SUCCESS), "bucket": bucket,
                "stratum": f"{'top' if t else 'bottom'}|{bucket}"})
            busy_until = i + kk
        bc = base_counts if base_counts is not None else self.baseline_counts(lv, eval_mask, months)
        for (m, stratum), (succ, tot) in bc.items():
            cell = self.base.setdefault((pair, stratum, m), [0, 0])
            cell[0] += succ
            cell[1] += tot

    @staticmethod
    def baseline_counts(lv, eval_mask, months):
        """{(month, 'side|bucket'): (successes, total)} over every valid bar in the window."""
        out = {}
        for s, side in (("b", "bottom"), ("t", "top")):
            code = np.asarray(lv[f"code_{s}"])
            valid = np.asarray(eval_mask, bool) & (code != INVALID)
            idx = np.flatnonzero(valid)
            if not len(idx):
                continue
            bk = d_bucket(np.asarray(lv[f"d_{s}"])[idx])
            for i, b in zip(idx, bk):
                cell = out.setdefault((months[i], f"{side}|{int(b)}"), [0, 0])
                cell[1] += 1
                cell[0] += int(code[i] == SUCCESS)
        return {k: tuple(v) for k, v in out.items()}

    # -- inference ----------------------------------------------------------------------
    def _arrays(self, pairs=None, month_filter=None):
        ev = pd.DataFrame(self.events)
        if pairs is not None and len(ev):
            ev = ev[ev["pair"].isin(pairs)]
        months = sorted({m for (_, _, m) in self.base})
        if month_filter is not None:
            months = [m for m in months if month_filter(m)]
            if len(ev):
                ev = ev[ev["month"].isin(months)]
        rows = sorted({(p, s) for (p, s, _) in self.base if pairs is None or p in pairs})
        r_idx, m_idx = {r: j for j, r in enumerate(rows)}, {m: j for j, m in enumerate(months)}
        bk = np.zeros((len(rows), len(months)))
        bn = np.zeros_like(bk)
        for (p, s, m), (succ, tot) in self.base.items():
            if (p, s) in r_idx and m in m_idx:
                bk[r_idx[(p, s)], m_idx[m]] += succ
                bn[r_idx[(p, s)], m_idx[m]] += tot
        if len(ev):
            e_r = np.array([r_idx[(p, s)] for p, s in zip(ev["pair"], ev["stratum"])], dtype=int)
            e_m = ev["month"].map(m_idx).to_numpy()
            e_y = ev["y"].to_numpy(float)
        else:
            e_r = e_m = np.array([], dtype=int)
            e_y = np.array([], dtype=float)
        return bk, bn, e_r, e_m, e_y, months

    def lift(self, pairs=None, month_filter=None, n_boot=5000, seed=20260919):
        bk, bn, e_r, e_m, e_y, months = self._arrays(pairs, month_filter)
        res = {"n_events": int(len(e_y)), "n_months": len(months), "flagged_rate": np.nan,
               "baseline_rate": np.nan, "lift": np.nan, "ci_lo": np.nan, "ci_hi": np.nan}
        if len(e_y) == 0 or len(months) == 0:
            return res
        with np.errstate(divide="ignore", invalid="ignore"):
            rate = bk.sum(axis=1) / bn.sum(axis=1)
        base_like = rate[e_r]
        res["flagged_rate"] = float(e_y.mean())
        res["baseline_rate"] = float(np.nanmean(base_like))
        res["lift"] = float(np.nanmean(e_y - base_like))
        if n_boot <= 0:
            return res
        rng = np.random.default_rng(seed)
        lifts = np.empty(n_boot)
        for b in range(n_boot):
            counts = np.bincount(rng.integers(0, len(months), len(months)), minlength=len(months))
            with np.errstate(divide="ignore", invalid="ignore"):
                r = (bk @ counts) / (bn @ counts)
            w = counts[e_m].astype(float)
            vals = e_y - r[e_r]
            ok = (w > 0) & ~np.isnan(vals)
            lifts[b] = (w[ok] * vals[ok]).sum() / w[ok].sum() if w[ok].sum() > 0 else np.nan
        res["ci_lo"], res["ci_hi"] = (float(x) for x in np.nanpercentile(lifts, [2.5, 97.5]))
        return res

    def outcome_counts(self):
        ev = pd.DataFrame(self.events)
        if ev.empty:
            return {"reversal": 0, "continuation": 0, "timeout": 0, "ties": 0}
        vc = ev["outcome"].value_counts()
        return {"reversal": int(vc.get("reversal", 0)), "continuation": int(vc.get("continuation", 0)),
                "timeout": int(vc.get("timeout", 0)), "ties": int(ev["tie"].sum())}

    def per_pair(self):
        ev = pd.DataFrame(self.events)
        rows = []
        for p in sorted({q for (q, _, _) in self.base}):
            sub = ev[ev["pair"] == p] if len(ev) else ev
            r = self.lift(pairs=[p], n_boot=0) if len(sub) else None
            rows.append({"pair": p, "n_events": int(len(sub)), "lift": (r["lift"] if r else np.nan),
                         "contribution": (r["lift"] * len(sub) if r and not np.isnan(r["lift"]) else 0.0)})
        return pd.DataFrame(rows)

    def bucket_mix(self):
        """Share of events per distance bucket — shows what the matched baseline is matching."""
        ev = pd.DataFrame(self.events)
        if ev.empty:
            return [0.0] * N_BUCKETS
        vc = ev["bucket"].value_counts(normalize=True)
        return [float(vc.get(b, 0.0)) for b in range(N_BUCKETS)]
