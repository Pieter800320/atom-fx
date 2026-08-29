"""
ATOM FX — CSM Delta & Currency Flow  (EXTEND §5.1)

Distinguishes "strong" from "getting stronger". Adds a *flow* reading on top of
the frozen Currency Strength Model.

Method — deterministic offset recompute (no state file): compute CSM now (reuse
the value already computed this scan) and CSM at a defined bar-offset in the past
by calling the SAME frozen csm.py functions on a sliced price history. Fully
reproducible from the current fetch; needs no persistence.

Rule #1: this module reads frozen values and calls frozen csm functions. It never
modifies csm.py or any frozen output.
"""
from scanner import csm
from scanner.config import CURRENCIES
from scanner.extend import potential_config as cfg


# ── slicing helper ─────────────────────────────────────────────────────────────
def _slice_ohlcv(ohlcv: dict, drops: dict) -> dict:
    """
    Return a shallow copy of ohlcv with each timeframe df truncated from the end
    by `drops[tf]` bars. Frozen csm functions read the *tail* of each series, so
    dropping tail bars yields the "N bars ago" snapshot. Frozen dfs are untouched.
    """
    out = {}
    for key, tfs in ohlcv.items():
        sliced = {}
        for tf, df in tfs.items():
            n = drops.get(tf, 0)
            if df is not None and n > 0 and len(df) > n:
                sliced[tf] = df.iloc[: len(df) - n]
            else:
                sliced[tf] = df
        out[key] = sliced
    return out


_PAST_FN = {
    "d1": csm.compute_csm_d1,
    "h4": csm.compute_csm_h4,
    "h1": csm.compute_csm_h1,
}


def compute_csm_delta(ohlcv: dict, csm_now: dict) -> dict:
    """
    csm_now : the frozen csm dict already computed this scan {"d1":{...},...}.
    Returns {"d1":{CCY: signed delta}, "h4":{...}, "h1":{...}}.
    """
    delta = {}
    for tf in ("d1", "h4", "h1"):
        past_ohlcv = _slice_ohlcv(ohlcv, cfg.PAST_SLICE[tf])
        csm_past = _PAST_FN[tf](past_ohlcv)
        now = csm_now.get(tf, {})
        delta[tf] = {
            c: round(now.get(c, 0.0) - csm_past.get(c, 0.0), cfg.CSM_DELTA_DP)
            for c in CURRENCIES
        }
    return delta


# ── Currency Flow object (derived from csm_delta[FLOW_TF] + frozen csm[FLOW_TF]) ─
def compute_currency_flow(csm_now: dict, csm_delta: dict) -> dict:
    """
    leader/laggard   = strongest positive / negative *driver* (by delta)
    absolute_*       = highest / lowest absolute *strength* (by frozen CSM level)
    The leader vs absolute_leader distinction is intentional (UI §7, §20).
    """
    tf = cfg.FLOW_TF
    d = csm_delta.get(tf, {})
    s = csm_now.get(tf, {})
    if not d or not s:
        return {}

    # Deterministic tie-breaking: iterate CURRENCIES in fixed order.
    leader   = max(CURRENCIES, key=lambda c: d.get(c, 0.0))
    laggard  = min(CURRENCIES, key=lambda c: d.get(c, 0.0))
    abs_lead = max(CURRENCIES, key=lambda c: s.get(c, 0.0))
    abs_lag  = min(CURRENCIES, key=lambda c: s.get(c, 0.0))

    leader_delta  = d.get(leader, 0.0)
    laggard_delta = d.get(laggard, 0.0)
    return {
        "leader":           leader,
        "leader_delta":     leader_delta,
        "laggard":          laggard,
        "laggard_delta":    laggard_delta,
        "absolute_leader":  abs_lead,
        "absolute_laggard": abs_lag,
        "driver_spread":    round(leader_delta - laggard_delta, cfg.CSM_DELTA_DP),
        "tf":               tf,
    }
