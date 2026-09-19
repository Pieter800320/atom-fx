"""
ATOM FX research — barrier-race event study for reversal indicators.

EXTEND tier, research use only: pure functions, no I/O, nothing frozen touched.

THE TEST (pre-registered in docs/RESEARCH_LOG.md before any conclusive run)
  From a flag bar's CLOSE, with A = ATR(14) at that bar, draw two barriers at close +/- 1.0*A
  and walk the next `horizon` bars' high/low. Whichever barrier is touched first decides:
      bottom flag (expects UP):   up-first  = reversal,      down-first = continuation
      top flag    (expects DOWN): down-first = reversal,     up-first   = continuation
      neither within `horizon` bars = timeout (counts as NOT a reversal, kept in the denominator).
  TIE RULE: a bar whose range touches BOTH barriers cannot be sequenced from daily OHLC, so it
  resolves as CONTINUATION (the conservative case). The identical rule, timeout and horizon are
  applied to flagged events and to the baseline — it is the same function for both.

DE-CLUSTERING
  Overlapping windows are not independent samples. Per pair, an event opens only at a flag bar
  AFTER the previous event has resolved (barrier touched, or horizon reached). Both the >=100
  events floor and the bootstrap use these de-clustered events, never raw flagged bars.

BASELINE (like-for-like, measured — never assumed 50%)
  The same race started from EVERY valid bar of the same pair over the same evaluation window,
  per direction. A flagged event's baseline is the rate for ITS pair and ITS direction; the lift
  is the event's outcome minus that rate, averaged. This controls for pair drift and for the
  up/down asymmetry of each pair over the sample.

INFERENCE
  Month-clustered bootstrap: calendar months are resampled with replacement and BOTH the flagged
  events and the baseline counts are recomputed from the resampled months, so baseline noise and
  serial clustering are both in the interval. Fixed seed (part of the pre-registration).
"""
import numpy as np
import pandas as pd

UP, DOWN, BOTH, TIMEOUT, CENSORED = 1, -1, 2, 0, -9


def race_all(high, low, close, atr, horizon=20, mult=1.0):
    """First-passage result for a race started at EVERY bar.

    Returns (result, k): result[i] in {UP, DOWN, BOTH, TIMEOUT, CENSORED}; k[i] = number of bars
    until resolution (horizon on TIMEOUT). CENSORED = ATR unavailable or fewer than `horizon`
    forward bars remain (those bars are excluded everywhere — flagged and baseline alike)."""
    n = len(close)
    result = np.full(n, CENSORED, dtype=int)
    k = np.zeros(n, dtype=int)
    for i in range(n):
        a = atr[i]
        if np.isnan(a) or a <= 0 or i + horizon >= n:
            continue
        up_lvl, dn_lvl = close[i] + mult * a, close[i] - mult * a
        result[i], k[i] = TIMEOUT, horizon
        for j in range(1, horizon + 1):
            hit_up, hit_dn = high[i + j] >= up_lvl, low[i + j] <= dn_lvl
            if hit_up and hit_dn:
                result[i], k[i] = BOTH, j
                break
            if hit_up:
                result[i], k[i] = UP, j
                break
            if hit_dn:
                result[i], k[i] = DOWN, j
                break
    return result, k


def is_reversal(result, side):
    """side 'bottom' (expects up) or 'top' (expects down). BOTH counts as continuation."""
    return result == (UP if side == "bottom" else DOWN)


def declustered_events(top_new, bot_new, result, k, eval_mask):
    """Non-overlapping events for ONE pair. Flags on bars outside eval_mask, or on censored bars,
    are ignored. If a top and a bottom flag fire on the same bar the bar is skipped (conflicting
    evidence — there is no single expected direction) and counted in the returned `conflicts`.
    Returns (events, conflicts): events = list of (bar_index, side)."""
    events, conflicts, busy_until = [], 0, -1
    for i in range(len(result)):
        t, b = bool(top_new[i]), bool(bot_new[i])
        if not (t or b) or not eval_mask[i] or result[i] == CENSORED:
            continue
        if t and b:
            conflicts += 1
            continue
        if i <= busy_until:
            continue
        events.append((i, "top" if t else "bottom"))
        busy_until = i + int(k[i])
    return events, conflicts


def baseline_counts(result, eval_mask, months):
    """Per (direction, month) reversal counts and totals over ALL valid bars in the window.
    Returns dict month -> {"bottom": (successes, total), "top": (successes, total)}."""
    out = {}
    valid = eval_mask & (result != CENSORED)
    for i in np.flatnonzero(valid):
        m = months[i]
        rec = out.setdefault(m, {"bottom": [0, 0], "top": [0, 0]})
        for side in ("bottom", "top"):
            rec[side][1] += 1
            rec[side][0] += int(is_reversal(result[i], side))
    return out


class Study:
    """Accumulates events + baseline counts across pairs, then computes lifts.

    add_pair(pair, dates, result, k, top_new, bot_new, eval_mask) for each pair; then use
    summary() / lift(). `label` names the flag definition being studied (e.g. 'score>=60 suppress')."""

    def __init__(self, label, horizon):
        self.label, self.horizon = label, horizon
        self.events = []                # dicts
        self.base = {}                  # (pair, side, month) -> [successes, total]
        self.conflicts = 0
        self.censored_flags = 0

    def add_pair(self, pair, dates, result, k, top_new, bot_new, eval_mask, base_counts=None):
        months = np.asarray(pd.to_datetime(dates).strftime("%Y-%m"))
        ev, conflicts = declustered_events(top_new, bot_new, result, k, eval_mask)
        self.conflicts += conflicts
        self.censored_flags += int(((np.asarray(top_new) | np.asarray(bot_new))
                                    & eval_mask & (result == CENSORED)).sum())
        for i, side in ev:
            self.events.append({
                "pair": pair, "date": pd.Timestamp(dates[i]), "month": months[i], "side": side,
                "outcome": ("reversal" if is_reversal(result[i], side)
                            else "timeout" if result[i] == TIMEOUT else "continuation"),
                "tie": bool(result[i] == BOTH), "k": int(k[i]), "y": int(is_reversal(result[i], side)),
            })
        bc = base_counts if base_counts is not None else baseline_counts(result, eval_mask, months)
        for m, rec in bc.items():
            for side in ("bottom", "top"):
                cell = self.base.setdefault((pair, side, m), [0, 0])
                cell[0] += rec[side][0]
                cell[1] += rec[side][1]

    # -- arrays for the bootstrap --------------------------------------------------------
    def _arrays(self, pairs=None, month_filter=None):
        ev = pd.DataFrame(self.events)
        if pairs is not None and len(ev):
            ev = ev[ev["pair"].isin(pairs)]
        all_months = sorted({m for (_, _, m) in self.base})
        if month_filter is not None:
            all_months = [m for m in all_months if month_filter(m)]
            if len(ev):
                ev = ev[ev["month"].isin(all_months)]
        pair_list = sorted({p for (p, _, _) in self.base if pairs is None or p in pairs})
        m_idx = {m: j for j, m in enumerate(all_months)}
        p_idx = {p: j for j, p in enumerate(pair_list)}
        bk = np.zeros((len(pair_list), 2, len(all_months)))
        bn = np.zeros_like(bk)
        for (p, side, m), (s, t) in self.base.items():
            if p in p_idx and m in m_idx:
                d = 0 if side == "bottom" else 1
                bk[p_idx[p], d, m_idx[m]] += s
                bn[p_idx[p], d, m_idx[m]] += t
        if len(ev):
            e_p = ev["pair"].map(p_idx).to_numpy()
            e_d = (ev["side"] == "top").astype(int).to_numpy()
            e_m = ev["month"].map(m_idx).to_numpy()
            e_y = ev["y"].to_numpy(float)
        else:
            e_p = e_d = e_m = np.array([], dtype=int)
            e_y = np.array([], dtype=float)
        return bk, bn, e_p, e_d, e_m, e_y, pair_list, all_months

    def lift(self, pairs=None, month_filter=None, n_boot=5000, seed=20260919):
        """Point estimate + month-clustered bootstrap CI of (flagged reversal rate - like-for-like
        baseline rate). Returns a dict; lift/CI are NaN when there are no events."""
        bk, bn, e_p, e_d, e_m, e_y, pair_list, months = self._arrays(pairs, month_filter)
        n_ev = len(e_y)
        res = {"n_events": int(n_ev), "n_months": len(months), "flagged_rate": np.nan,
               "baseline_rate": np.nan, "lift": np.nan, "ci_lo": np.nan, "ci_hi": np.nan}
        if n_ev == 0 or len(months) == 0:
            return res
        with np.errstate(divide="ignore", invalid="ignore"):
            rate = bk.sum(axis=2) / bn.sum(axis=2)
        base_like = rate[e_p, e_d]
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
            vals = e_y - r[e_p, e_d]
            ok = w > 0
            ok &= ~np.isnan(vals)
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
        """Point-estimate lift per pair (no CI) — for the 'best pair' exclusion and the table."""
        rows = []
        ev = pd.DataFrame(self.events)
        pairs = sorted({p for (p, _, _) in self.base})
        for p in pairs:
            sub = ev[ev["pair"] == p] if len(ev) else ev
            r = self.lift(pairs=[p], n_boot=0) if len(sub) else None
            rows.append({"pair": p, "n_events": int(len(sub)),
                         "lift": (r["lift"] if r else np.nan),
                         "contribution": (r["lift"] * len(sub) if r and not np.isnan(r["lift"]) else 0.0)})
        return pd.DataFrame(rows)
