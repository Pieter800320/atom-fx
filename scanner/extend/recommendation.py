"""
ATOM FX — Recommendation engine v1  (EXTEND §6 / FUNCTIONAL_SPEC §7)

The machine decides WHAT; the AI explains WHY and WHAT WOULD BREAK IT.

v1 ships a single `recommendation` object synthesised from outputs the frozen
scan_news.py already produces. A DETERMINISTIC seed sets bias / action /
primary_pair / direction / confidence / next_catalyst; an optional Sonnet call
fills only the human-readable framing (headline / rationale / invalidation). The
model CANNOT change the seed — if its text contradicts the seed, the seed wins.

Rule #1 spirit preserved: no new trading numbers are invented; the recommendation
references only values already in signals.json.

Phase note: this is Phase 7 in the build plan. `build_seed` is fully deterministic
and unit-tested. The model call is guarded — on any failure (no API key, network,
bad JSON) it returns None and the caller writes NO `recommendation` key, so the app
falls back to the deterministic nucleus + existing deep_analysis.
"""
import json
from datetime import datetime, timezone

_BIAS_MAP = {"Risk-On": "risk_on", "Risk-Off": "risk_off"}


def _now_iso():
    return datetime.now(timezone.utc).isoformat()


def build_seed(signals: dict) -> dict:
    """Deterministic seed computed BEFORE any model call. Fully reproducible."""
    regime_h4 = signals.get("regime_h4", {}) or {}
    potential = signals.get("potential", {}) or {}
    ranked_top = (signals.get("ranked", {}) or {}).get("top", []) or []
    gold = signals.get("gold_signal", {}) or {}

    bias = _BIAS_MAP.get(regime_h4.get("regime"), "mixed")

    # action from the best potential state present
    states = {p.get("state") for p in potential.values()}
    if states & {"tradeable", "aplus"}:
        action = "trade"
    elif "watch" in states:
        action = "watch"
    else:
        action = "stand_aside"

    # primary pair: top of ranked, else highest-level potential pair
    if ranked_top:
        primary_pair = ranked_top[0].get("pair")
        direction = ranked_top[0].get("direction")
    elif potential:
        primary_pair = max(potential, key=lambda k: potential[k].get("level", 0))
        direction = potential[primary_pair].get("direction")
    else:
        primary_pair, direction = None, None

    # confidence: blend gold H4 confidence with regime confidence (worst-of the two,
    # ranked Low<Medium<High) — a simple, deterministic, defensible blend.
    order = {"Low": 0, "Medium": 1, "High": 2}
    inv = {0: "Low", 1: "Medium", 2: "High"}
    g = order.get(gold.get("h4_confidence"), 0)
    r = order.get(regime_h4.get("confidence"), 0)
    confidence = inv[min(g, r)]

    # next catalyst: earliest FUTURE calendar event
    next_catalyst = None
    events = (signals.get("calendar", {}) or {}).get("events", []) or []
    now = _now_iso()
    future = sorted((e for e in events if e.get("iso") and e["iso"] >= now),
                    key=lambda e: e["iso"])
    if future:
        next_catalyst = {"event": future[0].get("name"), "iso": future[0].get("iso")}
    elif events:
        # fall back to the last known event so the panel is never empty
        next_catalyst = {"event": events[-1].get("name"), "iso": events[-1].get("iso")}

    return {
        "bias": bias,
        "action": action,
        "primary_pair": primary_pair,
        "direction": direction,
        "confidence": confidence,
        "next_catalyst": next_catalyst,
    }


def _build_prompt(seed: dict, signals: dict) -> str:
    mr = signals.get("macro_regime", {}) or {}
    flow = signals.get("currency_flow", {}) or {}
    breaking = (signals.get("breaking", {}) or {}).get("headlines", []) or []
    catalyst = (signals.get("catalyst", {}) or {}).get("text", "")
    prim = mr.get("primary", {})
    return (
        f"Macro regime: {prim.get('code','?')} {prim.get('name','?')} "
        f"({prim.get('confidence','?')})\n"
        f"Bias: {seed['bias']} | Action: {seed['action']} | "
        f"Primary: {seed['primary_pair']} {seed['direction']} | "
        f"Confidence: {seed['confidence']}\n"
        f"Flow: leader {flow.get('leader')} (+{flow.get('leader_delta')}), "
        f"laggard {flow.get('laggard')} ({flow.get('laggard_delta')})\n"
        f"Catalyst check: {catalyst or 'none'}\n"
        f"Headlines: {' | '.join(breaking[:3]) if breaking else 'none'}\n"
        f"Next event: {seed.get('next_catalyst')}\n\n"
        "Return STRICT JSON only, no markdown:\n"
        '{ "headline": "<=10 words, imperative, names the theme",\n'
        '  "rationale": "40-60 words, plain English, cite the specific drivers",\n'
        '  "invalidation": "one sentence: what flips this" }\n'
        "Do NOT contradict the bias/action/primary/direction above."
    )


def _call_sonnet(prompt: str):
    """
    Guarded model call. Reuses scan_news._sonnet if present (same model constants).
    Returns a dict with headline/rationale/invalidation, or None on ANY failure.
    """
    try:
        from scanner import scan_news
        raw = scan_news._sonnet(prompt)          # same model constants as the reference
        if not raw:
            return None
        text = raw.strip()
        start, end = text.find("{"), text.rfind("}")
        if start == -1 or end == -1:
            return None
        obj = json.loads(text[start:end + 1])
        if not all(k in obj for k in ("headline", "rationale", "invalidation")):
            return None
        return {k: str(obj[k]).strip() for k in ("headline", "rationale", "invalidation")}
    except Exception as e:
        print(f"  [recommendation] model call skipped: {e}")
        return None


def build_recommendation(signals: dict, use_model: bool = True) -> dict | None:
    """
    Merge deterministic seed + (optional) model text into the `recommendation`
    object. Returns None if there is no primary pair to talk about (caller then
    writes no key). The seed is authoritative; the model only narrates.
    """
    seed = build_seed(signals)
    if not seed["primary_pair"]:
        return None

    text = _call_sonnet(_build_prompt(seed, signals)) if use_model else None
    if text is None:
        # Deterministic fallback framing so the object is still useful offline.
        ranked_text = (signals.get("ranked", {}) or {}).get("text", "")
        text = {
            "headline": f"{seed['action'].replace('_',' ').title()}: "
                        f"{seed['primary_pair']} {seed['direction']}",
            "rationale": ranked_text or
                         f"{seed['bias'].replace('_',' ')} bias; "
                         f"{seed['primary_pair']} leads the deterministic ranking.",
            "invalidation": "A regime flip on H4 or a reversal in the lead currency's flow.",
        }

    rec = {
        "headline":      text["headline"],
        "bias":          seed["bias"],
        "action":        seed["action"],
        "primary_pair":  seed["primary_pair"],
        "direction":     seed["direction"],
        "confidence":    seed["confidence"],
        "rationale":     text["rationale"],
        "invalidation":  text["invalidation"],
        "next_catalyst": seed["next_catalyst"],
        "generated_at":  _now_iso(),
    }
    return rec
