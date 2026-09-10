"""
ATOM FX — Market Pulse  (EXTEND, 2026-09-10)

"Is this a market where today's signals can be trusted, or is it noise?" One
bounded 0-100 composite, blending four already-computed EXTEND/frozen axes —
no new calculation invented, only aggregation of values that already exist:

  unanimity         mean(breadth.h4[*].pct) * 100          — is agreement broad or narrow?
  regime_clarity    macro_regime.primary.distinct_axes / 5  — how many evidence axes agree?
  separation        csm_dispersion percentile (h4)          — is today's CSM spread real?
  volatility_phase  % of the 12 pairs with bb_d1.width_trend == "expanding"

Deliberately excludes Conviction (COT) — it only updates weekly and would make
a daily composite sticky/stale between COT releases; it stays its own separate,
slower overlay rather than folding into this one.

Rule #1: reads frozen/EXTEND values only (breadth.py, macro_regime.py,
csm_dispersion.py, bb_touch.py's own output). Computes no trading number itself,
only an aggregate confidence read on top of numbers those modules already own.
"""
from scanner.config import PAIRS
from scanner.extend import potential_config as cfg


def _unanimity(breadth_h4: dict) -> float | None:
    vals = [e.get("pct") for e in breadth_h4.values() if e and e.get("pct") is not None]
    if not vals:
        return None
    return round(sum(vals) / len(vals) * 100, 1)


def _regime_clarity(macro_regime: dict | None) -> float | None:
    if not macro_regime:
        return None
    axes = (macro_regime.get("primary") or {}).get("distinct_axes")
    if axes is None:
        return None
    return round(axes / 5 * 100, 1)


def _volatility_phase(pairs: dict) -> float | None:
    trends = []
    for pair in PAIRS:
        key = pair.replace("/", "")
        bb = (pairs.get(key) or {}).get("bb_d1")
        if bb and bb.get("width_trend"):
            trends.append(bb["width_trend"])
    if not trends:
        return None
    return round(sum(1 for t in trends if t == "expanding") / len(trends) * 100, 1)


def _band(score: float) -> str:
    if score >= cfg.PULSE_HIGH:
        return "confirmed"
    if score >= cfg.PULSE_MODERATE:
        return "mixed"
    return "noise"


def compute_pulse(breadth_h4: dict, macro_regime: dict | None,
                   dispersion_pct_h4: float | None, pairs: dict,
                   prev_history: list | None) -> dict:
    """
    Returns {"score": float|None, "band": str|None,
             "axes": {"unanimity", "regime_clarity", "separation", "volatility_phase"},
             "history": [floats...]} (oldest first, capped at cfg.PULSE_HISTORY_LEN).
    score/band are None when fewer than 2 of the 4 axes are available (e.g. the
    dispersion window hasn't filled yet) — a 1-axis "composite" would just be
    that one axis wearing a misleading label.
    """
    axes = {
        "unanimity": _unanimity(breadth_h4),
        "regime_clarity": _regime_clarity(macro_regime),
        "separation": dispersion_pct_h4,
        "volatility_phase": _volatility_phase(pairs),
    }
    available = [v for v in axes.values() if v is not None]

    score = round(sum(available) / len(available), 1) if len(available) >= 2 else None
    band = _band(score) if score is not None else None

    history = list(prev_history or [])
    if score is not None:
        history.append(score)
    history = history[-cfg.PULSE_HISTORY_LEN:]

    return {"score": score, "band": band, "axes": axes, "history": history}
