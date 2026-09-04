"""
ATOM FX — State-transition alerts  (EXTEND, Signals Roadmap §2)

Six cheap, edge-triggered push alerts riding the existing hourly scan. Every detector
here reads values already computed this scan (frozen + EXTEND) and compares them
against the previous scan's `signals.json` (`prev`, loaded once at the top of
`scan_h1.py::main()` before this run overwrote the file). Nothing here recomputes a
trading number — Rule #1 safe.

Edge-triggered, not level-triggered: each detector fires only on a transition (state A
-> state B between this scan and the last), never on "still true this hour too." A
first-ever run (no `prev`) never fires anything — there's nothing to transition from.

Returns a list of `{type, msg, deeplink, direction?}` dicts, one per transition found
this scan. `msg` follows the existing Gold Signal convention consumed by
`scan_h1.py::send_push_alert` (`_msg_to_title_body`: first line becomes the push title,
remaining lines the body; `<b>` tags are harmless since the Telegram fallback parses
HTML).
"""

_ATR_SPIKE_THRESHOLD = 90
_ALIGNED_PILLS = ("bull_strong", "bear_strong")


def _pair_potential_alerts(out: dict, prev: dict) -> list:
    alerts = []
    for pair, entry in out.get("potential", {}).items():
        state = entry.get("state")
        if state not in ("tradeable", "aplus"):
            continue
        prev_state = prev.get("potential", {}).get(pair, {}).get("state")
        if state == prev_state:
            continue
        direction = entry.get("direction")
        setup_rank = entry.get("setup_rank")
        rank_str = f"{setup_rank:.1f}/10" if setup_rank is not None else "—"
        dir_word = "LONG" if direction == "bull" else "SHORT" if direction == "bear" else "—"
        state_label = "A+ Setup" if state == "aplus" else "Tradeable"
        alerts.append({
            "type": "potential_state",
            "pair": pair,
            "msg": f"<b>{pair} reached {state_label}</b>\n{dir_word} · Setup {rank_str}",
            "deeplink": f"atomfx://pair/{pair}",
            "direction": direction if direction in ("bull", "bear") else None,
        })
    return alerts


def _structure_event_alerts(out: dict, prev: dict) -> list:
    alerts = []
    for pair, block in out.get("pairs", {}).items():
        h4 = (block.get("structure") or {}).get("h4", {})
        event = h4.get("event")
        if event not in ("BOS", "CHoCH"):
            continue
        prev_h4 = (prev.get("pairs", {}).get(pair, {}).get("structure") or {}).get("h4", {})
        if event == prev_h4.get("event"):
            continue
        direction = h4.get("direction")
        dir_word = "bullish" if direction == "bull" else "bearish" if direction == "bear" else "neutral"
        alerts.append({
            "type": "structure_event",
            "pair": pair,
            "msg": f"<b>{pair} — {event} on H4</b>\n{dir_word} · strength {h4.get('strength', 0.0):.2f}",
            "deeplink": f"atomfx://pair/{pair}",
            "direction": direction if direction in ("bull", "bear") else None,
        })
    return alerts


def _regime_flip_alert(out: dict, prev: dict) -> list:
    prev_h4 = prev.get("regime_h4")
    h4 = out.get("regime_h4", {})
    if not prev_h4 or h4.get("stable") is not False:
        return []
    old = prev_h4.get("regime", "Unknown")
    new = h4.get("regime", "Unknown")
    if old == new:
        return []
    return [{
        "type": "regime_flip",
        "msg": f"<b>Regime: {old} → {new}</b>\nH4 confidence: {h4.get('confidence', 'Low')}",
        "deeplink": "atomfx://regime",
        "direction": None,
    }]


def _archetype_change_alert(out: dict, prev: dict) -> list:
    prev_mr = prev.get("macro_regime")
    mr = out.get("macro_regime")
    if not prev_mr or not mr:
        return []
    prev_code = (prev_mr.get("primary") or {}).get("code")
    primary = mr.get("primary") or {}
    new_code = primary.get("code")
    if not new_code or new_code == prev_code:
        return []
    old_name = (prev_mr.get("primary") or {}).get("name", "Unknown")
    new_name = primary.get("name", "Unknown")
    narrative = mr.get("narrative") or ""
    return [{
        "type": "archetype_change",
        "msg": f"<b>Macro: {old_name} → {new_name}</b>\n{narrative}".rstrip(),
        "deeplink": "atomfx://regime",
        "direction": None,
    }]


def _volatility_spike_alerts(out: dict, prev: dict) -> list:
    alerts = []
    for pair, block in out.get("pairs", {}).items():
        atr_pct = block.get("atr_pct")
        if atr_pct is None or atr_pct < _ATR_SPIKE_THRESHOLD:
            continue
        prev_atr = prev.get("pairs", {}).get(pair, {}).get("atr_pct")
        if prev_atr is not None and prev_atr >= _ATR_SPIKE_THRESHOLD:
            continue
        alerts.append({
            "type": "volatility_spike",
            "pair": pair,
            "msg": f"<b>{pair} — volatility expanding</b>\nATR percentile {atr_pct}",
            "deeplink": f"atomfx://pair/{pair}",
            "direction": None,
        })
    return alerts


def _pills_aligned(pills: dict) -> str | None:
    """Returns 'bull_strong'/'bear_strong' if d1/h4/h1 all agree on one, else None."""
    values = {pills.get("d1"), pills.get("h4"), pills.get("h1")}
    if len(values) == 1:
        only = next(iter(values))
        if only in _ALIGNED_PILLS:
            return only
    return None


def _tf_alignment_alerts(out: dict, prev: dict) -> list:
    alerts = []
    for pair, block in out.get("pairs", {}).items():
        aligned = _pills_aligned(block.get("pills", {}))
        if aligned is None:
            continue
        prev_pills = prev.get("pairs", {}).get(pair, {}).get("pills", {})
        if _pills_aligned(prev_pills) == aligned:
            continue
        direction = "bull" if aligned == "bull_strong" else "bear"
        dir_word = "BULL" if direction == "bull" else "BEAR"
        alerts.append({
            "type": "tf_alignment",
            "pair": pair,
            "msg": f"<b>{pair} — D1/H4/H1 aligned {dir_word}</b>\nStrong {'Buy' if direction == 'bull' else 'Sell'} across all three timeframes",
            "deeplink": f"atomfx://pair/{pair}",
            "direction": direction,
        })
    return alerts


def compute_state_alerts(out: dict, prev: dict) -> list:
    """Top-level entry — returns every state-transition alert found this scan."""
    if not prev:
        return []
    alerts = []
    alerts += _pair_potential_alerts(out, prev)
    alerts += _structure_event_alerts(out, prev)
    alerts += _regime_flip_alert(out, prev)
    alerts += _archetype_change_alert(out, prev)
    alerts += _volatility_spike_alerts(out, prev)
    alerts += _tf_alignment_alerts(out, prev)
    return alerts
