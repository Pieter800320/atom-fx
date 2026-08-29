"""
ATOM FX — Six-factor Potential engine  (EXTEND §5.4)

Produces, deterministically in Python, the level/state/factor map the Energy Wheel
visualises. The engine reads ONLY frozen values (+ §5.1/§5.3 EXTEND outputs). The
app must never recompute this in Kotlin (spec §41: "expose, don't recompute").

Sequential factors — a pair advances outward only by passing each in order:
    1 REGIME  2 FLOW  3 BREADTH  4 MOMENTUM  5 STRUCTURE  6 ENTRY
`level` = count of consecutive passes from factor 1; the first failure sets
`blocked_at` and stops advancement regardless of later factors.

These pass-conditions are PRESENTATION CONFIG (potential_config.py), not trading
logic — they re-express frozen outputs into a hierarchy. Tuning them changes only
where nodes sit on the wheel, never any frozen number (Rule #1 safe).
"""
from scanner import rank
from scanner.extend import potential_config as cfg

_RISK_REGIMES = ("Risk-On", "Risk-Off")


def _direction(pills: dict) -> str:
    """Directional thesis = the D1 pill (matches the frozen rank.py gate)."""
    d1 = (pills or {}).get("d1", "neutral")
    if d1 in ("bull", "bull_strong"):
        return "bull"
    if d1 in ("bear", "bear_strong"):
        return "bear"
    return "none"


# ── Individual factor tests (each returns bool) ────────────────────────────────
def _f_regime(pair, direction, regime_h4):
    if (regime_h4 or {}).get("regime") not in _RISK_REGIMES:
        return False
    # rank._regime_sc-equivalent, evaluated against the H4 regime.
    fit = rank._regime_sc(pair, direction, regime_h4)
    return fit >= cfg.REGIME_FIT_MIN


def _f_flow(base, quote, direction, csm_delta_h4):
    spread = csm_delta_h4.get(base, 0.0) - csm_delta_h4.get(quote, 0.0)
    return spread >= cfg.FLOW_MIN if direction == "bull" else spread <= -cfg.FLOW_MIN


def _f_breadth(base, quote, direction, breadth_h4):
    b = breadth_h4.get(base, {})
    q = breadth_h4.get(quote, {})
    if direction == "bull":
        base_ok  = b.get("dir") == "strong" and b.get("pct", 0.0) >= cfg.BREADTH_MIN
        quote_ok = q.get("dir") == "weak"   and q.get("pct", 0.0) >= cfg.BREADTH_MIN
    else:
        base_ok  = b.get("dir") == "weak"   and b.get("pct", 0.0) >= cfg.BREADTH_MIN
        quote_ok = q.get("dir") == "strong" and q.get("pct", 0.0) >= cfg.BREADTH_MIN
    return base_ok or quote_ok


def _f_momentum(cmp_val, direction):
    if cmp_val is None:
        return False
    return cmp_val >= cfg.CMP_BULL if direction == "bull" else cmp_val <= cfg.CMP_BEAR


def _f_structure(structure_h4, direction):
    s = structure_h4 or {}
    if s.get("direction") != direction:
        return False
    if s.get("event") == "CHoCH":
        return False
    if cfg.STRUCTURE_REQUIRE_BOS and s.get("event") != "BOS":
        return False
    return True


def _f_entry(cont, reset_score, atr_pct):
    if cont is None or cont < cfg.ENTRY_CONT_MIN:
        return False
    if reset_score is not None and reset_score > cfg.RESET_MAX:
        return False
    if atr_pct is not None and not (cfg.ATR_LO <= atr_pct <= cfg.ATR_HI):
        return False
    return True


def _state(level: int, setup_rank) -> str:
    if level >= 6:
        if setup_rank is not None and setup_rank >= cfg.APLUS_RANK:
            return "aplus"
        return "tradeable"
    if level > cfg.STATE_WATCH_MAX:
        return "tradeable"
    if level > cfg.STATE_LOW_MAX:
        return "watch"
    return "low"


def _score(level: int, setup_rank) -> tuple:
    rank_val = setup_rank if setup_rank is not None else cfg.NEUTRAL_RANK
    quality = (rank_val - 5.0) / 5.0 * cfg.QUALITY_SPAN
    quality = max(-cfg.QUALITY_SPAN, min(cfg.QUALITY_SPAN, quality))
    quality = int(round(quality))
    score = max(0, min(100, round(cfg.BASE_SCORE[level] + quality)))
    return score, quality


def compute_pair_potential(pair, pair_block, ctx):
    """
    pair       : "EURUSD"
    pair_block : frozen pairs.<PAIR> dict (pills, mom, cont, ...)
    ctx        : dict with regime_h4, csm_delta (full), breadth (full),
                 setup_ranks{pair:score}, reset{pair}, atr_pct{pair}
    """
    base, quote = pair[:3], pair[3:]
    direction = _direction(pair_block.get("pills", {}))
    setup_rank = ctx["setup_ranks"].get(pair)

    factors = {name: False for name in cfg.FACTOR_ORDER}

    # No directional D1 pill -> nucleus (level 0), all factors false.
    if direction == "none":
        score, quality = _score(0, setup_rank)
        return {
            "direction": "none", "level": 0, "state": "low", "score": score,
            "factors": factors,
            "setup_rank": round(setup_rank, 1) if setup_rank is not None else None,
            "blocked_at": None, "quality": quality,
        }

    csm_delta_h4 = ctx["csm_delta"].get("h4", {})
    breadth_h4   = ctx["breadth"].get("h4", {})
    cmp_val = (pair_block.get("mom") or {}).get("cmp")
    structure_h4 = (pair_block.get("structure") or {}).get("h4", {})
    cont = pair_block.get("cont")

    tests = {
        "regime":   lambda: _f_regime(pair, direction, ctx["regime_h4"]),
        "flow":     lambda: _f_flow(base, quote, direction, csm_delta_h4),
        "breadth":  lambda: _f_breadth(base, quote, direction, breadth_h4),
        "momentum": lambda: _f_momentum(cmp_val, direction),
        "structure":lambda: _f_structure(structure_h4, direction),
        "entry":    lambda: _f_entry(cont, ctx["reset"].get(pair), ctx["atr_pct"].get(pair)),
    }

    level = 0
    blocked_at = None
    for name in cfg.FACTOR_ORDER:
        if tests[name]():
            factors[name] = True
            level += 1
        else:
            blocked_at = name
            break  # remaining factors stay False

    state = _state(level, setup_rank)
    score, quality = _score(level, setup_rank)
    return {
        "direction": direction,
        "level": level,
        "state": state,
        "score": score,
        "factors": factors,
        "setup_rank": round(setup_rank, 1) if setup_rank is not None else None,
        "blocked_at": blocked_at,
        "quality": quality,
    }


def build_setup_ranks(signals: dict) -> dict:
    """Frozen setup rank per qualifying pair -> {pair: score}. Uses rank.py verbatim."""
    return {r["pair"]: r["score"] for r in rank.rank_pairs(signals)}


def compute_potential(signals: dict, csm_delta: dict, breadth: dict,
                      reset: dict = None, atr_pct: dict = None) -> dict:
    """
    Top-level entry. `signals` must already hold frozen pairs (with structure
    attached), regime_h4, csm, csm.d1, macro_assets. reset/atr_pct are optional
    per-pair maps (present when run inside the scan; entry factor still evaluates
    on cont alone if they are absent).
    Returns {PAIR: {...potential...}}.
    """
    ctx = {
        "regime_h4":   signals.get("regime_h4", {}),
        "csm_delta":   csm_delta,
        "breadth":     breadth,
        "setup_ranks": build_setup_ranks(signals),
        "reset":       reset or {},
        "atr_pct":     atr_pct or {},
    }
    out = {}
    for pair, block in signals.get("pairs", {}).items():
        out[pair] = compute_pair_potential(pair, block, ctx)
    return out
