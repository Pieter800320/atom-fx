"""
FX Signal Board — hourly master scanner

Replaces scan_full.py + scan_hourly.py.

Flow:
  1. Fetch H1 OHLCV for 12 main pairs + 6 CSM cross pairs  (18 API calls)
  2. Aggregate H1 -> H4 + D1 via aggregator.py
  3. Pills (full Forex1212 formula) via pills.py / score.py
  4. MOM1212 (D1/H4/H1 + deltas + CMP) via mom1212.py
  5. CSM (D1 + H4 blend, 16-pair set) via csm.py
  6. ADX (H4)
  7. Correlation matrix via correlate.py
  8. H4 Regime via regime.py
  9. Cont. score (computeQAI port) via cont_score.py
 10. D1% / D5% / prev_close / prev5_close
 11. Preserve news/macro/analysis from previous signals.json
 12. Write signals.json
 13. Gold signal computation (gold_signal key)
 14. Telegram: Gold + H4 + H1 confirmed
"""

import json
import os
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

import numpy as np

ROOT = Path(__file__).parent.parent
sys.path.insert(0, str(ROOT))

from scanner.config     import PAIRS, TF_INTERVAL, TF_BARS
from scanner.fetch      import fetch_ohlcv
from scanner.aggregator import build_tfs
from scanner.pills      import classify_full
from scanner.mom1212    import compute_all as compute_mom
from scanner.csm        import compute_csm, STRENGTH_PAIRS
from scanner.regime     import classify_regime
from scanner.cont_score import compute_cont, pill_direction
from scanner.rank       import rank_pairs
from scanner.correlate  import compute_correlation
from scanner.score              import compute_reset_score, atr_percentile
from scanner.level_ema_alerts   import check_levels, check_ema_touches
from push.send_push             import send_push
# Alert-message helpers (send_telegram, send_push_alert, send_push_level_alert) moved to
# push/alert_helpers.py, 2026-09-04 (Signals Roadmap Phase 3) — scan_cot.py needs
# send_push_alert too, and importing one entry-point script from another is worse than a
# shared module. Same functions, same bodies, no behaviour change.
from push.alert_helpers         import send_push_alert, send_push_level_alert, send_telegram

# Extra pairs needed for CSM 16-pair set (not in main PAIRS list)
CSM_EXTRA = ["EUR/GBP", "EUR/CHF", "GBP/CHF", "AUD/NZD", "AUD/CAD", "GBP/AUD"]

SCAN_TF = "h1"  # primary fetch timeframe

# 2026-09-17 (Pieter's ask) — `ranked.top` used to be a flat top-3-by-rank slice regardless of
# how many pairs actually cleared rank.py's own cont>=45 qualifying gate or how weak the
# non-cont-qualifying ones scored on everything else. Checked live: on a strong trending day,
# 9 of 12 pairs can clear that gate at once (one broad USD-strength theme wearing 9 pair labels,
# not 9 independent setups), so a flat top-3 either hides real extra setups on a genuinely
# decorrelated day or, more often, just shows "the best 3 of a flood." Replaced with a score
# floor instead: every pair scoring >= this on rank.py's own 0-10 weighted scale gets in, no
# upper cap. 6.5 was picked by simulating it against 40 scans' worth of historical ranked.top
# scores (5% would show zero — rare enough to trust, unlike 7.0's 25%) — a defensible first
# pass, not a frozen number; tune freely. Also duplicated in scan_news.py's own
# call_ranked_analysis (same "small local copy, not shared" house style used throughout this
# codebase — a shared constant isn't worth a new module for one number).
RECOMMENDATION_MIN_SCORE = 6.5

# Keys written by a job other than scan_h1.py (scan_news.py's own cadence, scan_cot.py's
# weekly cadence, …) that must survive an hourly rebuild of `out` — scan_h1.py assembles
# `out` from scratch every run, so anything not listed here is silently dropped the next
# hour after that other job writes it. Bug precedent (2026-09-04): "conviction" was missing
# from this list for one hour after scan_cot.py first shipped, wiping the weekly COT data
# on the very next hourly scan. A new cross-cadence key needs adding here the same session
# it starts being written, not after.
PRESERVED_KEYS = (
    "regime_w1", "macro", "macro_assets", "macro_assets_w1",
    "catalyst", "ranked", "calendar", "week_ahead",
    "deep_analysis", "breaking", "last_alert", "gold_signal",
    "recommendation", "conviction",
)


# ── Helpers ───────────────────────────────────────────────────────────────────

def load_signals():
    path = ROOT / "data" / "signals.json"
    if not path.exists():
        return {}
    try:
        with open(path) as f:
            return json.load(f)
    except Exception as e:
        # 2026-09-17 (full-system audit) — this used to swallow ANY parse failure identically
        # to "no prior scan ever ran," silently dropping every PRESERVED_KEYS field and
        # disarming every edge-triggered alert (state_alerts.py, _recommendation_alerts) for
        # this run, with no way to tell the difference after the fact except diffing against
        # the previous commit. A corrupted signals.json is a real, previously-seen failure mode
        # (see scan_h1.yml's own comment on the stash/rebase incident this guarded against) —
        # loud beats silent here, even though the fallback behavior (treat as fresh) is
        # unchanged, since there's no safe way to recover partial state from a broken file.
        print(f"  [load_signals] ERROR: {path} exists but failed to parse ({e}) — "
              f"treating as a fresh run. PRESERVED_KEYS and every edge-triggered alert are "
              f"disarmed for this scan.")
        return {}


def save_signals(data: dict):
    path = ROOT / "data" / "signals.json"
    path.parent.mkdir(exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)


def regime_emoji(regime: str) -> str:
    return {"Risk-Off": "🔴", "Risk-On": "🟢", "Mixed": "🟡", "Ranging": "⚪"}.get(regime, "")


def _gold_signal_should_push(gs_direction: str, qualifies_now: bool, prev_gold: dict) -> bool:
    """
    2026-09-17 (Pieter's explicit sign-off — see ARCHITECTURE.md §5.2's own discussion of why
    this firing-condition change was allowed, not a silent Rule #1 deviation) — edge-triggers
    the Gold Signal push, same convention every other alert in the app uses (Signals Roadmap §1:
    "never for a condition that's merely still true"). Previously this pushed every single hour
    `qualifies_now` held, with no comparison against `prev` at all — the one alert in the whole
    system that could spam identical information for many consecutive hours during one sustained
    regime. The underlying qualifying condition itself (gs_direction/h4_confirmed/h1_confirmed/
    h4_conf, computed by the frozen gold-signal block in `main()`) is completely untouched —
    this only gates the push: fires on the not-qualifying -> qualifying transition, or a
    direction flip while it stays qualifying, same shape `_recommendation_alerts` (below) uses.

    `prev_gold` is last scan's own `out["gold_signal"]` dict (or `{}` on a first-ever run —
    `qualifies_now` is still checked first, so a first run correctly fires exactly like the old
    unconditional behavior did, no special-casing needed).
    """
    if not qualifies_now:
        return False
    prev_qualified = (
        prev_gold.get("direction") not in (None, "neutral")
        and prev_gold.get("h4_confirmed")
        and prev_gold.get("h1_confirmed")
        and prev_gold.get("h4_confidence") in ("Medium", "High")
    )
    return not (prev_qualified and prev_gold.get("direction") == gs_direction)


def _recommendation_alerts(out: dict, prev: dict) -> list:
    """
    Signals Roadmap §1 — edge-triggered "recommendation changed" push. Fires once per pair
    that, versus the previous scan's `ranked.top`, either newly clears the score floor into the
    fresh top (`out["ranked"]["top"]`, re-ranked hourly by the frozen `rank.py::rank_pairs` —
    see the call site in `main()`) or stays in the top but flips direction (long<->short). Same
    edge-triggered convention `scanner/extend/state_alerts.py`'s own detectors use (compare
    this scan vs `prev`, one alert per transition, first-ever run with no `prev` never
    fires) — kept local to this file rather than added to that module so this addition stays
    a small, self-contained, easy-to-merge diff (`scan_h1.py` is also edited on the
    trend-pullback branch).
    """
    if not prev:
        return []
    prev_top = {
        r["pair"]: r.get("direction")
        for r in (prev.get("ranked") or {}).get("top", [])
        if r.get("pair")
    }
    alerts = []
    for r in out.get("ranked", {}).get("top", []):
        pair      = r.get("pair")
        direction = r.get("direction")
        if not pair or not direction:
            continue
        if pair in prev_top and prev_top[pair] == direction:
            continue  # unchanged — still in the top, same direction
        is_new   = pair not in prev_top
        dir_word = "LONG" if direction == "bull" else "SHORT"
        verb     = "entered the top setups" if is_new else f"flipped to {dir_word}"
        alerts.append({
            "type":     "recommendation",
            "pair":     pair,
            "msg":      f"<b>{pair} — Recommendation Alert</b>\n{verb} · score {r.get('score', 0):.1f}",
            "deeplink": f"atomfx://pair/{pair}",
            "direction": direction,
        })
    return alerts


def d_pct(df, bars_back: int):
    """Return % change over bars_back bars on D1 aggregated data."""
    if df is None or len(df) < bars_back + 1:
        return None
    prev  = float(df["close"].iloc[-(bars_back + 1)])
    close = float(df["close"].iloc[-1])
    if prev == 0:
        return None
    return round((close / prev - 1) * 100, 2)


# ── Main ──────────────────────────────────────────────────────────────────────

def main():
    print("=== FX Signal Board — Hourly Scan ===")
    now = datetime.now(timezone.utc)

    prev             = load_signals()
    prev_d1_regime   = prev.get("regime_d1")
    prev_h4_regime   = prev.get("regime_h4")
    prev_h1_regime   = prev.get("regime_h1")
    prev_regime_name = (prev_h4_regime or {}).get("regime", "Unknown")

    # ── 1. Fetch H1 OHLCV ────────────────────────────────────────────────────
    all_pairs  = PAIRS + [p for p in CSM_EXTRA if p not in PAIRS]
    total_fetches = len(all_pairs)
    print(f"\n[1/9] Fetching H1 OHLCV for {total_fetches} pairs "
          f"({TF_BARS['h1']} bars each)…")

    raw_ohlcv = {}   # { "EURUSD": h1_df }
    for i, pair in enumerate(all_pairs):
        key = pair.replace("/", "")
        print(f"  [{i+1}/{total_fetches}] {key} H1")
        try:
            df = fetch_ohlcv(pair, TF_INTERVAL["h1"], TF_BARS["h1"])
            if df is not None:
                raw_ohlcv[key] = df
        except RuntimeError as e:
            # Daily credit limit — abort fetch loop immediately
            print(f"  ✗ {e}")
            print(f"  Aborting fetch — {len(raw_ohlcv)}/{total_fetches} pairs retrieved.")
            break
        except Exception as e:
            print(f"  ⚠ {key} skipped: {e}")
        # No manual sleep — fetch.py _rate_wait() handles spacing dynamically

    # Guard: fewer than half the main pairs = don't overwrite good data
    main_fetched = sum(1 for p in PAIRS if p.replace("/", "") in raw_ohlcv)
    if main_fetched < len(PAIRS) // 2:
        print(f"\n✗ Only {main_fetched}/{len(PAIRS)} main pairs fetched — "
              f"keeping existing signals.json to avoid overwriting good data.")
        return

    # ── 2. Aggregate H1 -> H4 + D1 ────────────────────────────────────────────
    print("\n[2/9] Aggregating H1 -> H4 + D1…")
    ohlcv = {}   # { "EURUSD": {"h1": df, "h4": df, "d1": df} }
    for key, h1_df in raw_ohlcv.items():
        tfs = build_tfs(h1_df)
        ohlcv[key] = tfs
        print(f"  {key}: H1={len(tfs['h1'])} H4={len(tfs['h4'])} D1={len(tfs['d1'])}")

    # ── 3. Pills (full Forex1212 formula) ─────────────────────────────────────
    print("\n[3/9] Computing pills (EMA200/50 + MACD + DMI + ADX weight)…")
    pair_pills  = {}   # { "EURUSD": {"d1": "bear", ...} }
    pair_scores = {}   # { "EURUSD": {"d1": result, "h4": result, "h1": result} }

    for key in ohlcv:
        tfs    = {tf: ohlcv[key][tf] for tf in ("d1", "h4", "h1")}
        result = classify_full(tfs)
        pair_pills[key]  = result["pills"]
        pair_scores[key] = result["scores"]
        print(f"  {key}: {result['pills']}")

    # ── 4. MOM1212 ────────────────────────────────────────────────────────────
    print("\n[4/9] Computing MOM1212 (D1/H4/H1 + deltas + CMP)…")
    pair_mom = {}
    for key in [p.replace("/", "") for p in PAIRS]:
        tfs = {tf: ohlcv.get(key, {}).get(tf) for tf in ("d1", "h4", "h1")}
        pair_mom[key] = compute_mom(tfs)
        print(f"  {key}: CMP={pair_mom[key].get('cmp')}")

    # ── 5. CSM ────────────────────────────────────────────────────────────────
    print("\n[5/9] Computing CSM (16-pair D1+H4 blend)…")
    csm = compute_csm(ohlcv)
    print(f"  D1: {dict(sorted(csm['d1'].items(), key=lambda x: -x[1]))}")
    print(f"  H4: {dict(sorted(csm['h4'].items(), key=lambda x: -x[1]))}")
    print(f"  H1: {dict(sorted(csm['h1'].items(), key=lambda x: -x[1]))}")

    # CSM dispersion percentile (EXTEND, 2026-09-06, Rule #1 sign-off) — computed here,
    # ahead of the usual "EXTEND layer" block further down, because regime classification
    # and compute_cont() (both frozen, steps 8/9 below) need it. History round-trips through
    # signals.json itself (prev.csm_dispersion_history -> this scan's csm_dispersion_history),
    # same pattern conviction.py's own prev_conviction already uses — no separate state file.
    # 2026-09-09 fix — this is a TOP-LEVEL signals.json key, deliberately NOT nested inside
    # "csm": the Android app's Signals.kt models the whole csm object as a blanket
    # Map<String, Map<String, Double>> (every existing sub-key — d1/h4/h1/dispersion — really is
    # TF/currency -> Double), and dispersion_history's TF -> List<Double> shape broke that
    # assumption, crashing every on-device parse (SignalsRepository fell back to Unavailable/
    # stale cache with the failure silently swallowed — found live on Pieter's phone). This key
    # is pure backend round-trip state the app has no use for; ignoreUnknownKeys skips an
    # unmodeled top-level key harmlessly, which is simpler than teaching Kotlin a new type for
    # data it never reads.
    # Wrapped in its own try/except so an EXTEND-layer failure can never block frozen
    # regime/cont computation — falling back to {"d1": None, ...} (every TF's gate off) is
    # exactly the same "no history yet" state a fresh run already produces on purpose.
    csm_dispersion_pct = {"d1": None, "h4": None, "h1": None}
    csm_dispersion_history_out = (prev.get("csm_dispersion_history") or {})
    _low_dispersion_pct = 20  # matches csm_dispersion.LOW_DISPERSION_PCT; fallback if that import itself fails
    try:
        from scanner.extend import csm_dispersion as _csm_dispersion
        csm_dispersion_pct, csm_dispersion_history_out = _csm_dispersion.compute_dispersion_percentile(
            csm.get("dispersion", {}), prev.get("csm_dispersion_history"),
        )
        _low_dispersion_pct = _csm_dispersion.LOW_DISPERSION_PCT
        print(f"  CSM dispersion percentile: {csm_dispersion_pct}")
    except Exception as e:
        print(f"  [extend] csm_dispersion error (frozen data unaffected): {e}")

    def _csm_unreliable(tf: str) -> bool:
        pct = csm_dispersion_pct.get(tf)
        return pct is not None and pct < _low_dispersion_pct

    # ── 6. ADX + per-pair entry metrics ───────────────────────────────────────
    print("\n[6/9] Extracting ADX, reset_score, atr_percentile…")
    pair_adx        = {}
    pair_reset      = {}
    pair_atr_pct    = {}

    for key in [p.replace("/", "") for p in PAIRS]:
        sc = pair_scores.get(key, {})

        # ADX: from H4 score raw indicators (already computed in score_pair)
        h4_score = sc.get("h4")
        adx_val  = (h4_score["raw"]["adx"] if h4_score and h4_score.get("raw") else None)
        pair_adx[key] = adx_val

        # Reset score: computed from H4 closes, direction from D1 pill
        d1_dir   = pair_pills.get(key, {}).get("d1", "neutral")
        h4_df    = ohlcv.get(key, {}).get("h4")
        if h4_df is not None and len(h4_df) >= 34:
            pair_reset[key] = compute_reset_score(
                h4_df["close"].values, direction=d1_dir
            )
        else:
            pair_reset[key] = None

        # ATR percentile: from D1 (or H4 as proxy if D1 is short)
        d1_df = ohlcv.get(key, {}).get("d1")
        h4_df = ohlcv.get(key, {}).get("h4")
        ap = atr_percentile(d1_df) if d1_df is not None and len(d1_df) >= 52 else None
        if ap is None and h4_df is not None and len(h4_df) >= 52:
            ap = atr_percentile(h4_df)
        pair_atr_pct[key] = ap

        print(f"  {key}: ADX={adx_val} reset={pair_reset[key]} atr_pct={ap}")

    # ── 7. Correlation matrix ─────────────────────────────────────────────────
    print("\n[7/9] Computing correlation matrix…")
    correlations = compute_correlation(ohlcv)

    # ── 8. D1 / H4 / H1 Regime ───────────────────────────────────────────────
    print("\n[8/9] Computing D1 / H4 / H1 regimes…")
    regime_d1 = classify_regime(csm["d1"], pair_pills, prev_d1_regime, tf="d1", csm_unreliable=_csm_unreliable("d1"))
    regime_h4 = classify_regime(csm["h4"], pair_pills, prev_h4_regime, tf="h4", csm_unreliable=_csm_unreliable("h4"))
    regime_h1 = classify_regime(csm["h1"], pair_pills, prev_h1_regime, tf="h1", csm_unreliable=_csm_unreliable("h1"))
    new_regime_name = regime_h4["regime"]

    # Confluence: all three agree on the same non-Mixed/non-Ranging regime
    aligned_regime = None
    if (regime_d1["regime"] == regime_h4["regime"] == regime_h1["regime"]
            and regime_h4["regime"] not in ("Mixed", "Ranging")):
        aligned_regime = regime_h4["regime"]

    print(f"  D1: {regime_d1['regime']} {regime_d1['confidence']}")
    print(f"  H4: {regime_h4['regime']} {regime_h4['confidence']} "
          f"(was: {prev_regime_name})")
    print(f"  H1: {regime_h1['regime']} {regime_h1['confidence']}")
    if aligned_regime:
        print(f"  ⚡ CONFLUENCE: all 3 TFs → {aligned_regime}")

    # ── 9. Cont. score + assemble pairs ───────────────────────────────────────
    print("\n[9/9] Computing cont. scores + assembling pairs…")
    pairs_out = {}

    for pair in PAIRS:
        key   = pair.replace("/", "")
        pills = pair_pills.get(key, {})
        mom   = pair_mom.get(key, {})
        adx   = pair_adx.get(key)

        # 2026-09-06 (Rule #1 sign-off) — feeds compute_cont's new Structure component
        # (replacing the dead Rate Differential slot); same score_pair() H4 result already
        # used above for `pair_adx`, just reading its own "structure" key instead of "raw".
        h4_structure = (pair_scores.get(key, {}).get("h4") or {}).get("structure")

        cont = compute_cont(
            pair           = key,
            pills          = pills,
            adx            = adx,
            csm_h4         = csm["h4"],
            regime_h4      = regime_h4,
            reset_score    = pair_reset.get(key),
            atr_pct        = pair_atr_pct.get(key),
            structure_h4   = h4_structure,
            csm_unreliable = _csm_unreliable("h4"),
        )

        d1_df       = ohlcv.get(key, {}).get("d1")
        d1p         = d_pct(d1_df, 1)
        d5p         = d_pct(d1_df, 5)
        prev_close  = (round(float(d1_df["close"].iloc[-2]), 6)
                       if d1_df is not None and len(d1_df) >= 2 else None)
        prev5_close = (round(float(d1_df["close"].iloc[-6]), 6)
                       if d1_df is not None and len(d1_df) >= 6 else None)

        pairs_out[key] = {
            "pills":       pills,
            "mom":         mom,
            "adx":         adx,
            "d1_pct":      d1p,
            "d5_pct":      d5p,
            "prev_close":  prev_close,
            "prev5_close": prev5_close,
            "cont":        cont,
            # Additive, 2026-09-03 — already computed above (step 6/9) to feed compute_cont() and
            # potential.py's ENTRY gate; just also surfacing them so the app can show real numbers
            # instead of "Not available yet". No calculation changed, no existing key touched.
            "reset_score": pair_reset.get(key),
            "atr_pct":     pair_atr_pct.get(key),
            # 2026-09-09 (Pieter's ask) — found live: the wheel's Overall wing filled by `cont`
            # (this pair's own D1-else-H4 direction, via pill_direction — exactly what compute_cont
            # itself used above to decide whether to score at all) but tinted by
            # signals.potential[pair].direction, a DIFFERENT field from the older, demoted
            # six-factor gate, which could disagree with cont's own basis or be entirely absent
            # (forcing a neutral/grey tint regardless of what cont's own direction actually was).
            # Exposing the SAME value compute_cont already used, so the app can tint Overall off
            # the number it's actually displaying instead of an unrelated subsystem.
            "direction":   pill_direction(pills),
        }
        print(f"  {key}: cont={cont}%")

    # ── Assemble signals.json ─────────────────────────────────────────────────
    # Preserve keys written by scan_news.py — not touched by hourly scan
    preserved = {k: prev.get(k) for k in PRESERVED_KEYS if prev.get(k)}

    out = {
        "updated":      now.isoformat(),
        "regime_d1":    regime_d1,
        "regime_h4":    regime_h4,
        "regime_h1":    regime_h1,
        "csm":          csm,
        # Top-level, not nested in "csm" — see this variable's own assignment above for why.
        "csm_dispersion_history": csm_dispersion_history_out,
        "correlations": correlations,
        "pairs":        pairs_out,
        **preserved,
    }

    # ── Hourly recommendation ranking ─────────────────────────────────────────
    # rank.py is FROZEN (Rule #1) — imported read-only above, never edited. Every input it
    # reads off `out` here (pairs/csm/regime_d1) was just computed fresh this same scan;
    # only `macro_assets` (the 10%-weight `cross` component) rides the preserved news-cadence
    # value (PRESERVED_KEYS, above) — cadence rides the existing hourly scan_h1 trigger, no
    # scheduler change. HOME's StatusStrip/Watchlist glyphs only ever read `ranked.top`
    # (pair/direction/score, never `ranked.text`), so overwriting just `.top` here and leaving
    # `ranked.text`/`ranked.updated` (the Haiku narrative, still news-cadence) untouched is
    # safe — checked directly, nothing in the Android app renders `ranked.text` today.
    # 2026-09-17 (Pieter's ask) — score floor (RECOMMENDATION_MIN_SCORE, above), not a flat
    # top-3 slice: every qualifying pair scoring >= the floor gets in, no upper cap. rank_pairs()
    # already returns its results sorted by score descending, so this is a straight filter, no
    # re-sort needed.
    fresh_ranked = rank_pairs(out)
    out["ranked"] = {
        **out.get("ranked", {}),
        "top": [
            {"pair": r["pair"], "direction": r["direction"], "score": r["score"]}
            for r in fresh_ranked if r["score"] >= RECOMMENDATION_MIN_SCORE
        ],
    }
    recommendation_alerts_list = _recommendation_alerts(out, prev)

    save_signals(out)
    print(f"\n✓ signals.json saved")

    # ── Level alerts + EMA touch alerts ───────────────────────────────────────
    print("\n[Alerts] Checking level alerts + EMA touches…")

    # Build per-pair H4 last bar (high/low/close) and H4 EMA values
    _pair_prices = {}
    _pair_emas   = {}
    for pair in PAIRS:
        key  = pair.replace("/", "")
        sc   = pair_scores.get(key, {})
        h4r  = (sc.get("h4") or {}).get("raw") or {}
        h4df = ohlcv.get(key, {}).get("h4")

        # Use H4 last bar high/low/close for wick-accurate EMA touch detection
        if h4df is not None and len(h4df) >= 1:
            _pair_prices[key] = {
                "high":  float(h4df["high"].iloc[-1]),
                "low":   float(h4df["low"].iloc[-1]),
                "close": float(h4df["close"].iloc[-1]),
            }

        if h4r.get("ema200"):
            _pair_emas[key] = {
                "ema200": h4r["ema200"],
                "ema50":  h4r.get("ema50"),
            }

    # ── Gold signal computation ───────────────────────────────────────────────
    ma          = out.get("macro_assets", {})
    gold_data   = ma.get("gold", {})
    gold_pct    = gold_data.get("delta_pct")
    gold_dir    = gold_data.get("direction", "flat")   # up/down/flat

    h4_regime   = regime_h4.get("regime", "")
    h4_conf     = regime_h4.get("confidence", "Low")
    h1_regime   = regime_h1.get("regime", "")

    # Determine gold signal direction
    RISK_OFF_REGIMES = ("Risk-Off",)
    RISK_ON_REGIMES  = ("Risk-On",)

    if gold_dir == "down" and h4_regime in RISK_OFF_REGIMES:
        gs_direction = "bear"
        h4_confirmed = True
    elif gold_dir == "up" and h4_regime in RISK_ON_REGIMES:
        gs_direction = "bull"
        h4_confirmed = True
    else:
        gs_direction = "neutral"
        h4_confirmed = False

    h1_confirmed = (
        (gs_direction == "bear" and h1_regime in RISK_OFF_REGIMES) or
        (gs_direction == "bull" and h1_regime in RISK_ON_REGIMES)
    )

    gold_signal = {
        "direction":      gs_direction,
        "gold_pct":       round(gold_pct, 2) if gold_pct is not None else None,
        "h4_confirmed":   h4_confirmed,
        "h4_confidence":  h4_conf,
        "h1_confirmed":   h1_confirmed,
        "updated":        now.isoformat(),
    }
    out["gold_signal"] = gold_signal
    print(f"\n🟡 Gold signal: {gs_direction.upper()} | H4: {h4_confirmed} ({h4_conf}) | H1: {h1_confirmed}")

    # ── EXTEND layer (additive; reads frozen values only — Rule #1 safe) ──────
    # Everything below writes NEW signals.json keys. It never changes a frozen
    # calculation or a firing condition. If it errors, the frozen data above is
    # already assembled and still saved.
    print("\n[Extend] Computing additive analytics…")
    state_alerts_list: list = []
    try:
        from scanner.extend import csm_delta       as _csm_delta
        from scanner.extend import breadth         as _breadth
        from scanner.extend import structure_expose as _structure_expose
        from scanner.extend import bb_touch         as _bb_touch
        from scanner.extend import spark           as _spark
        from scanner.extend import potential       as _potential
        from scanner.extend import macro_regime    as _macro_regime
        from scanner.extend import recommendation  as _recommendation
        from scanner.extend import potential_config as _xcfg
        from scanner.extend import state_alerts    as _state_alerts
        from scanner.extend import rotation        as _rotation
        from scanner.extend import market_pulse    as _market_pulse

        out["csm_delta"]     = _csm_delta.compute_csm_delta(ohlcv, csm)
        out["currency_flow"] = _csm_delta.compute_currency_flow(csm, out["csm_delta"])
        out["breadth"]       = _breadth.compute_breadth(ohlcv)
        _structure_expose.attach_structure(out["pairs"], pair_scores)   # pairs.<PAIR>.structure
        _bb_touch.attach_bb_d1(out["pairs"], ohlcv, raw_ohlcv)          # pairs.<PAIR>.bb_d1
        out["percent_b_board"] = _bb_touch.compute_board_percent_b(out["pairs"])
        out["percent_b_currency"] = _bb_touch.compute_currency_percent_b(raw_ohlcv)
        out["spark"]         = _spark.compute_spark(ohlcv)

        # Rotation / Pulse / Thrust (2026-09-10) — all pure aggregation of values
        # already computed above this scan (csm, csm_delta, breadth, bb_d1) plus
        # the macro_regime block assembled just below. History round-trips through
        # signals.json the same way csm_dispersion_history already does.
        out["rotation"] = _rotation.compute_rotation(
            csm.get("h4", {}), out["csm_delta"].get("h4", {}),
            (prev.get("rotation") or {}).get("history"),
        )
        out["breadth_thrust"] = _breadth.compute_thrust(
            out["breadth"]["h4"], (prev.get("breadth_thrust") or {}).get("history"),
        )
        # csm_dispersion_pct was already computed in step 5 (ahead of frozen regime
        # classification) but never exposed to signals.json — exposing it now, only
        # as the input Pulse needs; see its own computation above for why it isn't
        # nested inside "csm" and stays top-level.
        out["csm_dispersion_pct"] = csm_dispersion_pct
        out["potential"]     = _potential.compute_potential(
            out, out["csm_delta"], out["breadth"],
            reset=pair_reset, atr_pct=pair_atr_pct,
        )

        # 2026-09-06 (Pieter's ask) — the regime NAME is picked from the W1 (5-session) view
        # now, not the 1-day one; the 1-day `macro_assets` still feeds the fast confirm/diverge
        # evidence tag and the sudden-shock check. `prev` (already loaded above for the D1/H4/H1
        # regime comparisons) supplies the standing regime for the new hysteresis gate — see
        # macro_regime.py's own module doc comment. Both preserved keys, so they're already
        # sitting in `out` from the last scan_news.py run even on hours it doesn't itself run.
        mr = _macro_regime.classify_macro_regime(
            out.get("macro_assets_w1", {}),
            out.get("macro_assets", {}),
            prev_regime=prev.get("macro_regime"),
            updated=now.isoformat(),
            # 2026-09-10 (Pieter's ask) — same "colour, not input" `themes` scan_news.py's own
            # tag_theme() already produces, now actually fed back in (see macro_regime.py's own
            # _news_corroboration doc comment). `out["breaking"]` is already `prev`'s copy by
            # this point on hours scan_news.py doesn't itself run (PRESERVED_KEYS, above).
            news_themes=(out.get("breaking") or {}).get("themes"),
        )
        if mr:
            out["macro_regime"] = mr

        out["pulse"] = _market_pulse.compute_pulse(
            out["breadth"]["h4"], out.get("macro_regime"),
            csm_dispersion_pct.get("h4"), out.get("pairs", {}),
            (prev.get("pulse") or {}).get("history"),
        )

        # v1 recommendation: deterministic seed + deterministic framing (no hourly
        # API cost). Phase 7 swaps in the AI-narrated version on the 12h cadence in
        # scan_news.py (arch §6). Seed is authoritative either way.
        rec = _recommendation.build_recommendation(out, use_model=False)
        if rec:
            out["recommendation"] = rec

        out["schema_version"] = _xcfg.SCHEMA_VERSION
        print(f"  Extend OK — schema_version={_xcfg.SCHEMA_VERSION}, "
              f"potential pairs={len(out.get('potential', {}))}")

        # State-transition alerts (Signals Roadmap §2) — last, since it reads the
        # potential/structure/macro_regime keys this same block just computed.
        state_alerts_list = _state_alerts.compute_state_alerts(out, prev)
    except Exception as e:
        import traceback
        print(f"  [extend] error (frozen data still saved): {e}")
        traceback.print_exc()

    save_signals(out)

    # Recommendation-ranking edge-trigger (computed earlier, independent of the EXTEND try
    # block above) rides the same generic alert-dict push loop below.
    state_alerts_list = state_alerts_list + recommendation_alerts_list

    # ── State-transition alerts push (Signals Roadmap §2) ─────────────────────
    if state_alerts_list:
        print(f"\n[State alerts] {len(state_alerts_list)} transition(s) this scan…")
    for alert in state_alerts_list:
        print(f"  🔔 {alert['type']} — {alert.get('pair', '')}")
        # Any field beyond the ones send_push_alert already names (e.g. archetype_change's
        # regime_code) rides through automatically via `extra` — no per-type wiring needed here.
        extra = {k: v for k, v in alert.items() if k not in ("type", "msg", "deeplink", "direction", "pair")}
        send_push_alert(alert["msg"], alert["type"], alert["deeplink"], direction=alert.get("direction"), extra=extra or None)

    # ── Push: Gold + H4 (Medium/High) + H1 confirmed (Telegram fallback) ─────
    _pair_closes = {k: v["close"] for k, v in _pair_prices.items()}
    check_levels(_pair_closes, send_push_level_alert)
    # EMA touch alerts removed — Gold signal is the only proactive alert

    # NOTE (2026-09-17, Pieter's ask) — when Level Alert (price-level alerts UI + PAT sync,
    # Functional Spec §9, BUILD_STATUS.md item 7) actually ships: align its push message to the
    # "{pair} — Level Alert" title convention every other alert type now uses (see
    # BUILD_STATUS.md's "Alert push title standardization" row). It was skipped in that pass —
    # the message itself lives in level_ema_alerts.py::check_levels, FROZEN, and this feature is
    # currently dormant (Settings toggle hardcoded disabled) — so it still reads
    # "Level Alert — {pair}" (pair second, not first). Changing that message text needs the
    # usual Rule #1 stop-and-ask conversation before editing that file, same as any other
    # frozen-file touch.

    gold_qualifies_now = (
        gs_direction != "neutral"
        and h4_confirmed
        and h1_confirmed
        and h4_conf in ("Medium", "High")
    )
    gold_should_push = _gold_signal_should_push(gs_direction, gold_qualifies_now, prev.get("gold_signal") or {})

    if gold_should_push:
        # Build pair list from ranked top setups
        ranked_top = out.get("ranked", {}).get("top", [])[:3]
        pairs_line = " | ".join(
            f"{r['pair']} {'▲' if r['direction']=='bull' else '▼'}"
            for r in ranked_top
        ) if ranked_top else "—"

        # 2026-09-17 (Pieter's ask) — every alert's push title now follows one shape,
        # "{Entity} — {Category} Alert" (Entity omitted for a market-wide alert like this one,
        # same as Regime below), with the specifics moved into the body. Was "Gold Signal:
        # {BEAR — USD bid}"; the "USD bid"/"Risk-On" descriptor moves into the body's first line.
        dir_word   = "BEAR" if gs_direction == "bear" else "BULL"
        dir_detail = "USD bid" if gs_direction == "bear" else "Risk-On"
        gp_str     = f"{gold_pct:+.1f}%" if gold_pct is not None else ""

        msg = (
            f"<b>Gold Signal Alert — {dir_word}</b>\n"
            f"{dir_detail} · Gold {gp_str} | H4 {h4_regime} ({h4_conf}) | H1 {h1_regime}\n"
            f"Setups: {pairs_line}\n"
            f"{now.strftime('%H:%M')} UTC"
        )
        print(f"\n🚨 Gold signal push → {gs_direction.upper()}")
        send_push_alert(msg, "gold_signal", "atomfx://regime", direction=gs_direction)
        out["last_alert"] = now.isoformat()
        save_signals(out)
    else:
        print(
            f"\nNo Telegram: direction={gs_direction} h4={h4_confirmed} h1={h1_confirmed} "
            f"conf={h4_conf} qualifies_now={gold_qualifies_now} already_pushed_this_state={gold_qualifies_now and not gold_should_push}"
        )

    print("=== Hourly Scan complete ===")


if __name__ == "__main__":
    import sys
    if "--test-push" in sys.argv:
        print("Sending push test message…")
        ok = send_push(
            "\U0001f916 ATOM FX",
            "Push test OK — if you see this, the FCM pipeline is wired correctly.",
            {"type": "test"},
        )
        print("OK" if ok else "FAILED (or FCM_SERVICE_ACCOUNT not set)")
    else:
        main()
