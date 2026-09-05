"""
ATOM FX — Currency Conviction Score  (EXTEND, Signals Roadmap §4)

Adapted from `fx_technical/scanner/conviction.py`'s 6-input design. Combines COT
positioning, CSM extremes, extension and breadth into a per-currency conviction score
(-100 to +100): "is the structural/positioning environment supporting this currency, or
is it crowded/exhausted?"

Inputs 1-4 (COT position, COT OI momentum, COT disaggregated, CSM extreme) port
verbatim — same hysteresis-banded thresholds the original deliberately built in to
prevent weekly flip-flop near boundaries; not simplified away, per the roadmap's own
instruction.

Inputs 5-6 are ADAPTED, not ported verbatim — the original reads per-pair fields
(`is_extended()`, an RSI sub-vote) that only exist in `fx_technical`'s broader per-pair
score objects. ATOM FX's frozen `score.py` never computes or exposes either. Resolved
with Pieter (2026-09-04):
  - Input 5 (was is_extended()) -> reset_score against the *existing* RESET_MAX (55)
    threshold from `potential_config.py` — the same "is this pair overextended" gate the
    Entry factor already uses. Same ratio bands as the original.
  - Input 6 (was an RSI vote) -> the *existing* breadth.h4[CCY].pct EXTEND key (already
    "how broad is this currency's own move"), gated on breadth.dir agreeing with the
    pill-derived D1 direction. Same ratio bands as the original; disagreement between the
    two direction reads counts as the original's worst ("narrow confirmation") bucket.

Currency D1 direction is derived from `pills.d1` (bull/bull_strong -> +1,
bear/bear_strong -> -1, neutral -> skip) rather than the original's raw-score-threshold
method — same intent, ATOM's actual available field.

EWMA smoothing (alpha=0.6) prevents weekly flicker, exactly as the original.
"""

from scanner.config import PAIRS, CURRENCIES
from scanner.extend.potential_config import RESET_MAX

# ── Input 1: COT Net Speculator Position (verbatim) ──────────────────────────────

def _score_cot_position(noncomm_pct: float | None, prev_score: int = 0) -> int:
    """
    Bifurcated scoring with a hysteresis zone at each threshold.

    At extremes (>80 or <20): contrarian signal (crowded positioning).
    At moderate levels (60-80 or 20-40): trend-following (specs are right).
    At centre (40-60): neutral. Hysteresis prevents flip-flopping near boundaries.
    """
    if noncomm_pct is None:
        return 0

    p = noncomm_pct
    if p > 83:   return -2  # deeply crowded long -> contrarian bear
    if p > 77:   return -2 if prev_score <= -1 else -1  # hysteresis zone 77-83
    if p > 57:   return +1  # moderate long -> follow specs (bull)
    if p > 43:   return 0   # neutral zone
    if p > 23:   return -1  # moderate short -> follow specs (bear)
    if p > 17:   return +2 if prev_score >= 1 else +1   # hysteresis zone 17-23
    return +2               # deeply crowded short -> contrarian bull


# ── Input 2: COT Open Interest Momentum (verbatim) ────────────────────────────────

def _score_cot_oi(oi_current: float | None, oi_4w_ago: float | None,
                   currency_dir: int) -> int:
    """
    Rising OI in the direction of the trend = new participation = +1 (healthy).
    Falling OI = short covering / long liquidation = -1 (weak, not fresh flow).
    """
    if oi_current is None or oi_4w_ago is None or oi_4w_ago == 0 or currency_dir == 0:
        return 0

    change_pct = (oi_current - oi_4w_ago) / oi_4w_ago
    if abs(change_pct) < 0.03:  # < 3% change = effectively flat
        return 0
    return +1 if change_pct > 0 else -1


# ── Input 3: COT Disaggregated — Asset Mgr vs Leveraged Fund (verbatim) ───────────

def _score_cot_disagg(am_pct: float | None, lf_pct: float | None) -> int:
    """
    Both long -> +2 (structural AND tactical confirm). Both short -> -2.
    AM long / LF short -> 0 (structural bull but tactically fading).
    AM short / LF long -> -1 (tactical chase against structural flow).
    """
    if am_pct is None or lf_pct is None:
        return 0

    am_long, am_short = am_pct > 55, am_pct < 45
    lf_long, lf_short = lf_pct > 55, lf_pct < 45

    if am_long  and lf_long:  return +2
    if am_short and lf_short: return -2
    if am_long  and lf_short: return  0
    if am_short and lf_long:  return -1
    return 0


# ── Input 4: CSM Extreme (verbatim) ───────────────────────────────────────────────

def _score_csm_extreme(csm_value: float | None) -> int:
    """Contrarian: high CSM = broadly bought (upside limited); low = broadly sold."""
    if csm_value is None:
        return 0
    if csm_value > 88:  return -2
    if csm_value > 72:  return -1
    if csm_value < 12:  return +2
    if csm_value < 28:  return +1
    return 0


# ── Input 5: Extension Composite (ADAPTED — reset_score, not is_extended()) ──────

def _score_extension(pairs_block: dict, currency: str, d1_direction: int) -> int:
    """
    Aggregate "is this pair overextended" (reset_score > RESET_MAX, the same threshold
    the Entry factor already gates on) across every pair involving this currency. Only
    counts extensions in the direction of the currency's own D1 bias.
    """
    if d1_direction == 0:
        return 0

    relevant_pairs = [p for p in PAIRS if currency in p.split("/")]
    if not relevant_pairs:
        return 0

    extended_count = 0
    total = 0
    for pair in relevant_pairs:
        key = pair.replace("/", "")
        pdata = pairs_block.get(key, {})
        if not pdata:
            continue
        total += 1

        reset_score = pdata.get("reset_score")
        if reset_score is None or reset_score <= RESET_MAX:
            continue

        base, quote = pair.split("/")
        pill_dir = (pdata.get("pills") or {}).get("d1")
        if pill_dir in ("bull", "bull_strong"):
            pair_dir_int = 1
        elif pill_dir in ("bear", "bear_strong"):
            pair_dir_int = -1
        else:
            continue

        currency_pair_dir = pair_dir_int if currency == base else -pair_dir_int
        if currency_pair_dir == d1_direction:
            extended_count += 1

    if total == 0:
        return 0

    ratio = extended_count / total
    if ratio == 0:      return +1  # no extension, clean runway
    if ratio < 0.34:    return  0
    if ratio < 0.67:    return -1  # majority extended
    return -2                       # broadly extended


# ── Input 6: Breadth confirmation (ADAPTED — breadth.pct, not an RSI vote) ───────

def _score_breadth(breadth_h4: dict, currency: str, d1_direction: int) -> int:
    """
    breadth.h4[CCY] already answers "how broad is this currency's own move" (share of
    its CSM contributions agreeing with its own net direction). Gate that ratio on
    whether breadth's own direction read agrees with the pill-derived D1 direction —
    disagreement between the two reads is the least-confirming case.
    """
    if d1_direction == 0:
        return 0

    b = breadth_h4.get(currency, {})
    if not b or b.get("total", 0) == 0:
        return 0

    breadth_dir_int = {"strong": 1, "weak": -1}.get(b.get("dir"), 0)
    if breadth_dir_int != d1_direction:
        return -1  # the two direction reads disagree — narrow/unreliable confirmation

    pct = b.get("pct", 0.0)
    if pct > 0.75: return +2
    if pct > 0.50: return +1
    if pct > 0.25: return  0
    return -1


# ── Currency D1 direction helper (ADAPTED — pills.d1, not a raw score threshold) ──

def _currency_d1_direction(pairs_block: dict, currency: str) -> int:
    """Net D1 directional bias for a currency from its pairs' D1 pills. +1/-1/0."""
    relevant = [p for p in PAIRS if currency in p.split("/")]
    votes = []
    for pair in relevant:
        key = pair.replace("/", "")
        pill_dir = (pairs_block.get(key, {}).get("pills") or {}).get("d1")
        if pill_dir in ("bull", "bull_strong"):
            dir_int = 1
        elif pill_dir in ("bear", "bear_strong"):
            dir_int = -1
        else:
            continue
        base, _ = pair.split("/")
        if currency != base:
            dir_int = -dir_int
        votes.append(dir_int)

    if not votes:
        return 0
    net = sum(votes)
    if net > 0:  return +1
    if net < 0:  return -1
    return 0


# ── EWMA smoother (verbatim) ──────────────────────────────────────────────────────

def _ewma(new_val: float, prev_val: float | None, alpha: float = 0.6) -> float:
    """3-week EWMA. alpha=0.6: current week counts 60%, previous smoothed 40%."""
    if prev_val is None:
        return new_val
    return round(alpha * new_val + (1 - alpha) * prev_val, 1)


# Theoretical max: +1 +1 +2 +2 +1 +2 = +9. Theoretical min: -2 -1 -2 -2 -2 -1 = -10.
# Symmetric +-10 denominator, same as the original.
SCORE_MAX = 10.0


# ── Master conviction computation ─────────────────────────────────────────────────

def compute_conviction(cot_data: dict, pairs_block: dict, csm_d1: dict, breadth: dict,
                        prev_conviction: dict | None = None) -> dict:
    """
    cot_data        : output of cot.fetch_cot_data()
    pairs_block     : signals["pairs"] (frozen per-pair pills + reset_score, already
                      computed by scan_h1.py — no re-fetch, no recompute here)
    csm_d1          : signals["csm"]["d1"] (frozen CCY -> 0-100)
    breadth         : signals["breadth"] (EXTEND, {"h4": {...}, "d1": {...}})
    prev_conviction : previous scan's `conviction` key, for EWMA + hysteresis

    Returns {"currencies": {...}, "pairs": {...}, "cot_date", "cot_stale"}.
    """
    cot_currencies = cot_data.get("currencies", {})
    prev_currencies = (prev_conviction or {}).get("currencies", {})
    cot_stale = cot_data.get("cot_stale", True)
    breadth_h4 = breadth.get("h4", {})

    currency_scores = {}
    currency_raw = {}

    for ccy in CURRENCIES:
        cot_ccy = cot_currencies.get(ccy, {})
        available = cot_ccy.get("available", False)

        prev_ccy = prev_currencies.get(ccy, {})
        prev_comp = prev_ccy.get("components", {})
        prev_conv = prev_ccy.get("conviction")

        d1_dir = _currency_d1_direction(pairs_block, ccy)

        if available and not cot_stale:
            s_pos = _score_cot_position(cot_ccy.get("noncomm_pct"), prev_comp.get("cot_position", 0))
            s_oi = _score_cot_oi(cot_ccy.get("oi_current"), cot_ccy.get("oi_4w_ago"), d1_dir)
            s_disagg = _score_cot_disagg(cot_ccy.get("am_pct"), cot_ccy.get("lf_pct"))
        else:
            s_pos = s_oi = s_disagg = 0

        s_csm = _score_csm_extreme(csm_d1.get(ccy))
        s_ext = _score_extension(pairs_block, ccy, d1_dir)
        s_breadth = _score_breadth(breadth_h4, ccy, d1_dir)

        raw = s_pos + s_oi + s_disagg + s_csm + s_ext + s_breadth
        normalised = round(raw / SCORE_MAX * 100)

        smoothed = _ewma(normalised, prev_conv, alpha=0.6)
        smoothed_int = int(round(smoothed))

        currency_raw[ccy] = normalised
        currency_scores[ccy] = {
            "conviction": smoothed_int,
            "direction":  d1_dir,
            "components": {
                "cot_position": s_pos,
                "cot_oi":       s_oi,
                "cot_disagg":   s_disagg,
                "csm_extreme":  s_csm,
                "extension":    s_ext,
                "breadth":      s_breadth,
            },
            "raw": raw,
            "cot_available": available and not cot_stale,
        }

    pair_scores = {}
    for pair in PAIRS:
        base, quote = pair.split("/")
        b_raw = currency_raw.get(base, 0)
        q_raw = currency_raw.get(quote, 0)
        pair_conv = int(round((b_raw - q_raw) / 2))
        pair_scores[pair.replace("/", "")] = max(-100, min(100, pair_conv))

    return {
        "currencies": currency_scores,
        "pairs":      pair_scores,
        "cot_date":   cot_data.get("cot_date", "unknown"),
        "cot_stale":  cot_stale,
    }


# ── conviction_extreme alert (edge-triggered, same style as state_alerts.py) ─────

EXTREME_THRESHOLD = 80


def compute_conviction_alerts(conviction: dict, prev_conviction: dict | None) -> list:
    """
    Fires when a currency's conviction score newly crosses into a crowded/extreme band
    (|score| >= 80) — never for a currency that was already extreme last week. First-ever
    run (no prev_conviction) never fires, same guard as state_alerts.compute_state_alerts.
    """
    if not prev_conviction:
        return []

    prev_currencies = prev_conviction.get("currencies", {})
    alerts = []
    for ccy, entry in conviction.get("currencies", {}).items():
        score = entry.get("conviction")
        if score is None or abs(score) < EXTREME_THRESHOLD:
            continue
        prev_score = prev_currencies.get(ccy, {}).get("conviction")
        if prev_score is not None and abs(prev_score) >= EXTREME_THRESHOLD:
            continue  # already extreme last week — not a new transition
        direction = "bull" if score > 0 else "bear"
        dir_word = "bullish" if direction == "bull" else "bearish"
        alerts.append({
            "type": "conviction_extreme",
            "msg": f"<b>{ccy} — Conviction Extreme</b>\nConviction {score:+d} · {dir_word}",
            "deeplink": f"atomfx://currency/{ccy}",
            "direction": direction,
        })
    return alerts
