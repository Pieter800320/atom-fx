"""
ATOM FX — Structure exposure  (EXTEND §5.3)

structure.py already runs inside score.py for H4 and D1. This module is a pure
read-and-copy: it lifts the raw structure dict per pair out of the per-TF score
results and returns it shaped for pairs.<PAIR>.structure.{h4,d1}.

No recompute, no second BOS detector — Rule #1 forbids re-deriving structure.
"""

_NEUTRAL = {"direction": "neutral", "event": "none", "strength": 0.0, "multiplier": 1.0}


def _tf_structure(scores: dict, tf: str) -> dict:
    sc = (scores or {}).get(tf)
    if not sc or "structure" not in sc:
        return dict(_NEUTRAL)
    s = sc["structure"]
    return {
        "direction":  s.get("direction", "neutral"),
        "event":      s.get("event", "none"),
        "strength":   s.get("strength", 0.0),
        "multiplier": s.get("multiplier", 1.0),
    }


def structure_for_pair(scores: dict) -> dict:
    """scores = classify_full(...)['scores'] for one pair. Returns {'h4':..,'d1':..}."""
    return {"h4": _tf_structure(scores, "h4"), "d1": _tf_structure(scores, "d1")}


def attach_structure(pairs_out: dict, pair_scores: dict) -> None:
    """Mutate pairs_out in place, adding a 'structure' sub-key to each pair block."""
    for key, block in pairs_out.items():
        block["structure"] = structure_for_pair(pair_scores.get(key))
