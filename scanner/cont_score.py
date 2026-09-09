"""
FX Signal Board — Continuation Score (0-100)
Port of Forex1212 computeQAI() to Python.

Six components:
  1. TF Alignment      35%  - D1/H4/H1 pill direction agreement
  2. Entry Position    23%  - reset_score (H4) + atr_percentile (D1, H4 fallback)
  3. CSM Divergence    16%  - H4 CSM base vs quote spread
  4. Regime Fit        13%  - macro context supports trade direction
  5. Structure          5%  - H4 BOS/CHoCH agreement with trade direction
  6. Session Fit        8%  - is current UTC session optimal for this pair

2026-09-06 (Rule #1 sign-off — Pieter, see git log) — component 5 replaces the original
Forex1212 "Rate Differential" weight, which this app never populated (`rate_score = 5`,
a fixed neutral placeholder — no live rates feed, and Pieter's own call that a
manually-typed-and-saved rates workflow isn't worth building). H4 structure
(BOS/CHoCH, `structure.py`) was sitting completely unused by this score despite being
computed every scan and being the most direct available "does price action confirm
this trade right now" read — the natural replacement, not a new external dependency.

Gates (applied after weighted sum):
  - ADX < 20  -> score capped at 45
  - Counter-regime trade -> score capped (varies by pair type)
"""

from datetime import datetime, timezone

# Session definitions (UTC hours): start, end (wraps midnight if start > end)
_SESSIONS = {
    "SY": (22, 7),
    "TK": (23, 8),
    "LN": (7,  16),
    "NY": (12, 21),
}

_PAIR_SESSIONS = {
    "EURUSD": ["LN", "NY"],
    "GBPUSD": ["LN", "NY"],
    "USDJPY": ["TK", "NY"],
    "USDCHF": ["LN", "NY"],
    "AUDUSD": ["SY", "TK", "NY"],
    "USDCAD": ["NY"],
    "NZDUSD": ["SY", "TK", "NY"],
    "EURJPY": ["LN", "TK"],
    "GBPJPY": ["LN", "TK"],
    "AUDJPY": ["SY", "TK"],
    "NZDJPY": ["SY", "TK"],
    "CADJPY": ["TK", "NY"],
}

_RISK_BASES  = {"AUD", "NZD", "CAD"}
_SAFE_HAVENS = {"CHF", "JPY"}
_GROWTH_CCYS = {"EUR", "GBP"}


def _active_sessions(utc_hour):
    active = []
    for abbr, (start, end) in _SESSIONS.items():
        if start > end:
            if utc_hour >= start or utc_hour < end:
                active.append(abbr)
        else:
            if start <= utc_hour < end:
                active.append(abbr)
    return active


def _market_closed(utc_weekday, utc_hour):
    if utc_weekday == 5:
        return True
    if utc_weekday == 4 and utc_hour >= 22:
        return True
    if utc_weekday == 6 and utc_hour < 22:
        return True
    return False


def _structure_component(structure_h4, is_bull):
    """
    2026-09-06 (Rule #1 sign-off) — component 5, replacing the dead Rate Differential slot.

    structure_h4 : pairs.<PAIR>.structure.h4 shape — {"direction","event","strength","multiplier"}
                   or None. `strength` is 0.0-1.0 (structure.py, itself fixed the same day this
                   landed — a bull BOS's strength now measures distance from the level it actually
                   broke, not a fixed reference that pinned every BOS at 1.0).

    A neutral/missing read scores the same "no information" 5 every other component uses when its
    own input is missing (reset_comp/atr_comp above). An agreeing BOS is the strongest possible
    confirmation, scaled by how decisive the break was; an agreeing CHoCH is a live warning even
    though the trend label hasn't flipped yet (CHoCH means the LATEST close just broke back through
    the opposing extreme). Mirrored, dimmer, for an opposing structure: an opposing BOS is a direct
    structural contradiction of the trade; an opposing CHoCH is early evidence structure may be
    about to turn in the trade's favour, so it scores a little above a plain (eventless) opposition.
    """
    if not structure_h4:
        return 5
    direction = structure_h4.get("direction", "neutral")
    if direction == "neutral":
        return 5
    event    = structure_h4.get("event", "none")
    strength = structure_h4.get("strength") or 0.0
    agrees   = (direction == "bull") == is_bull
    if agrees:
        if event == "BOS":   return 10 if strength >= 0.5 else 8
        if event == "CHoCH": return 3
        return 6
    else:
        if event == "BOS":   return 1
        if event == "CHoCH": return 4
        return 3


def pill_direction(pills: dict) -> str | None:
    """
    "Which way is this pair actually pointing" — D1's pill decides; H4's own pill is the
    fallback when D1 reads neutral (2026-09-06, Rule #1 sign-off — see compute_cont's own
    D1-gate doc comment for why). Shared here, not reimplemented, so rank.py and
    state_alerts.py can't independently drift from the same answer compute_cont itself
    uses — they used to re-derive "bull if d1 pill says so, else bear if.., else None"
    inline, missing this fallback: a pair whose D1 hadn't confirmed yet but whose H4 already
    had could score a real, qualifying Continuation Score yet still get excluded from
    ranking (or show a blank direction on its own Setup alert) for "having no direction",
    contradicting its own score.
    """
    d1 = pills.get("d1", "neutral")
    if d1 in ("bull", "bull_strong"): return "bull"
    if d1 in ("bear", "bear_strong"): return "bear"
    h4 = pills.get("h4", "neutral")
    if h4 in ("bull", "bull_strong"): return "bull"
    if h4 in ("bear", "bear_strong"): return "bear"
    return None


def compute_cont(pair, pills, adx, csm_h4, regime_h4,
                 reset_score=None, atr_pct=None, structure_h4=None, csm_unreliable=False):
    """
    Compute continuation score for one pair (0-100).

    pair           : "EURUSD" etc.
    pills          : {"d1": "bear", "h4": "bear_strong", "h1": "bear"}
    adx            : H4 ADX float or None
    csm_h4         : {"USD": 80, "EUR": 20, ...}
    regime_h4      : {"regime": "Risk-Off", ...}
    reset_score    : 0-100 from compute_reset_score() or None
    atr_pct        : 0-100 from atr_percentile() or None
    structure_h4   : pairs.<PAIR>.structure.h4 dict or None (2026-09-06, see _structure_component)
    csm_unreliable : bool, default False (2026-09-06, see the CSM DIVERGENCE section below)
    """
    base  = pair[:3]
    quote = pair[3:]

    d1_pill = pills.get("d1", "neutral")
    h4_pill = pills.get("h4", "neutral")
    h1_pill = pills.get("h1", "neutral")

    # Every component below scores RELATIVE TO a direction, so the score needs one to mean
    # anything — but forcing 0 whenever D1 alone hasn't confirmed yet silenced exactly the
    # setup worth flagging: H4/H1 already aligned, structure already confirming, D1 just
    # hasn't caught up. See pill_direction()'s own doc comment for the fallback rule.
    direction = pill_direction(pills)
    if direction is None:
        return 0
    is_bull = direction == "bull"
    is_bear = direction == "bear"

    d1_strong = "_strong" in d1_pill
    h4_strong = "_strong" in h4_pill

    # 1. TF ALIGNMENT (35%)
    h4m = h4_pill in ("bull", "bull_strong") if is_bull else h4_pill in ("bear", "bear_strong")
    h4n = h4_pill == "neutral"
    h1m = h1_pill in ("bull", "bull_strong") if is_bull else h1_pill in ("bear", "bear_strong")
    h1n = h1_pill == "neutral"

    # 2026-09-06 (Rule #1 sign-off) — bug fix: "H4 dissents, H1 agrees" (D1+H1 both support the
    # trade, only H4 doesn't) used to fall through the missing branch into the catch-all `else`,
    # scoring identically to a full three-way conflict (H4 AND H1 both against the trade). It's
    # the direct mirror of the "H4 agrees, H1 dissents" branch two lines up, so it gets the same
    # score that branch already does (5) — one dissenting timeframe, not a real conflict, whichever
    # side it's on.
    if   h4m and h1m:               align_score = 10 if (d1_strong or h4_strong) else 9
    elif h4m and h1n:               align_score = 7
    elif h4m and not h1m and not h1n: align_score = 5
    elif h4n and h1m:               align_score = 5
    elif h4n and h1n:               align_score = 3
    elif not h4m and not h4n and h1m: align_score = 5
    else:                           align_score = 1

    # 2. ENTRY POSITION (23%)
    reset_comp = (5  if reset_score is None else
                  2  if reset_score <= 20   else
                  4  if reset_score <= 35   else
                  7  if reset_score <= 50   else 10)
    atr_comp   = (5  if atr_pct is None     else
                  10 if 20 <= atr_pct <= 70 else
                  7  if atr_pct < 20        else 3)
    entry_score = round(reset_comp * 0.6 + atr_comp * 0.4)

    # 3. CSM DIVERGENCE (16%)
    csm_base  = csm_h4.get(base,  50)
    csm_quote = csm_h4.get(quote, 50)
    csm_div   = (csm_base - csm_quote) if is_bull else (csm_quote - csm_base)
    csm_score = (10 if csm_div >= 30 else
                 7  if csm_div >= 15 else
                 5  if csm_div >= 5  else
                 3  if csm_div >= -5 else 1)
    # 2026-09-06 (Rule #1 sign-off) — csm_h4 is always min-max rescaled to fill 0-100 every
    # scan (csm.py::_normalise), so csm_div's absolute size alone can't tell "today's real
    # H4 dispersion is wide" from "today's whole basket is thin and got stretched anyway".
    # A caller should pass True when H4's own CSM dispersion percentile
    # (scanner.extend.csm_dispersion) ranks unusually low against its recent history — caps
    # this component at the same "no reliable info" 5 every other component already falls
    # back to, rather than letting a rescaled-noise gap earn the full 10. Default False: no
    # behaviour change unless a caller opts in.
    if csm_unreliable:
        csm_score = min(csm_score, 5)

    # 4. REGIME FIT (13%)
    regime          = regime_h4.get("regime", "Mixed")
    is_risk_pair    = (base in _RISK_BASES or
                       (quote in _RISK_BASES and base not in _SAFE_HAVENS))
    is_safe_haven   = base in _SAFE_HAVENS or quote in _SAFE_HAVENS
    is_growth_pair  = base in _GROWTH_CCYS or quote in _GROWTH_CCYS

    reg_score = 5
    if regime == "Risk-On":
        if   is_bull and is_risk_pair:   reg_score = 10
        elif is_bull and is_growth_pair: reg_score = 8
        elif is_bull:                    reg_score = 6
        elif is_bear and is_safe_haven:  reg_score = 2
        elif is_bear and is_risk_pair:   reg_score = 3
        elif is_bear:                    reg_score = 4
    elif regime == "Risk-Off":
        if   is_bear and is_safe_haven:  reg_score = 10
        elif is_bear and is_growth_pair: reg_score = 7
        elif is_bear:                    reg_score = 6
        elif is_bull and is_risk_pair:   reg_score = 2
        elif is_bull and is_growth_pair: reg_score = 3
        elif is_bull:                    reg_score = 4
    elif regime == "Ranging":
        reg_score = 4

    # 5. STRUCTURE CONFIRMATION (5%) — see _structure_component's own doc comment.
    structure_score = _structure_component(structure_h4, is_bull)

    # 6. SESSION FIT (8%)
    now       = datetime.now(timezone.utc)
    closed    = _market_closed(now.weekday(), now.hour)
    active    = _active_sessions(now.hour)
    pair_sess = _PAIR_SESSIONS.get(pair, [])
    sess_match = any(s in pair_sess for s in active)

    sess_score = (3  if closed     else
                  3  if not active else
                  10 if sess_match else 2)

    # Weighted sum
    raw = round((
        align_score     * 0.35 +
        entry_score     * 0.23 +
        csm_score       * 0.16 +
        reg_score       * 0.13 +
        structure_score * 0.05 +
        sess_score      * 0.08
    ) * 10)

    # ADX gate
    capped = raw
    if adx is not None and adx < 20:
        capped = min(capped, 45)

    # Regime cap
    if regime == "Risk-Off" and is_bull:
        lim    = 40 if is_risk_pair else 70 if is_safe_haven else 50
        capped = min(capped, lim)
    elif regime == "Risk-On" and is_bear:
        lim    = 40 if is_safe_haven else 70 if is_risk_pair else 50
        capped = min(capped, lim)

    return min(100, max(0, capped))
