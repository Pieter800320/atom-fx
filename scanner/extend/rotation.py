"""
ATOM FX — Currency Rotation  (EXTEND, 2026-09-10)

A quadrant view of the same two numbers the app already shows separately: CSM
(strength) and CSM Delta (momentum of that strength). No new calculation — this
reads both frozen-derived values csm.py/csm_delta.py already produce and adds a
rolling per-currency position history so the app can draw a short "comet trail"
of recent movement, not just today's dot.

Quadrant convention matches the real-world Relative Rotation Graph methodology
(strength on x, momentum-of-strength on y, split at the midline of each axis):
  x>=50, y>=0  -> "leading"     (strong and still gaining)
  x>=50, y<0   -> "weakening"   (strong but losing ground)
  x<50,  y<0   -> "lagging"     (weak and still losing)
  x<50,  y>=0  -> "improving"   (weak but gaining ground)

Rule #1: reads frozen `csm` and EXTEND `csm_delta` output only. Computes nothing
a frozen module doesn't already compute; never edits csm.py or csm_delta.py.
"""
from scanner.config import CURRENCIES
from scanner.extend import potential_config as cfg


def _quadrant(x: float, y: float) -> str:
    if x >= 50:
        return "leading" if y >= 0 else "weakening"
    return "improving" if y >= 0 else "lagging"


def compute_rotation(csm_h4: dict, csm_delta_h4: dict, prev_history: dict | None) -> dict:
    """
    csm_h4       : signals.json's csm["h4"] (frozen, 0-100 per currency).
    csm_delta_h4 : signals.json's csm_delta["h4"] (EXTEND, signed delta per currency).
    prev_history : this key's own history from the previous scan (round-trips
                   through signals.json the same way csm_dispersion_history does).

    Returns {"tf": "h4", "points": {CCY: {x, y, quadrant}},
             "history": {CCY: [[x, y], ...]}} (oldest first, capped at
             cfg.ROTATION_HISTORY_LEN).
    """
    prev_history = prev_history or {}
    points: dict = {}
    history: dict = {}

    for ccy in CURRENCIES:
        x = csm_h4.get(ccy)
        y = csm_delta_h4.get(ccy)
        if x is None or y is None:
            continue

        points[ccy] = {"x": x, "y": y, "quadrant": _quadrant(x, y)}

        trail = list(prev_history.get(ccy, [])) + [[x, y]]
        history[ccy] = trail[-cfg.ROTATION_HISTORY_LEN:]

    return {"tf": "h4", "points": points, "history": history}
