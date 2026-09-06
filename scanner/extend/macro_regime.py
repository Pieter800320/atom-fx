"""
ATOM FX — Macro Archetype engine  (EXTEND, FUNCTIONAL_SPEC §6)

Deterministic classifier over the frozen cross-asset reads (`macro_assets`). It
names the macro regime (10-regime library A-J from the FX Macro Flow Handbook),
computes gold/USD overlays, a currency bias, and a distinct-axis confidence, and
produces a `macro_regime` object.

The handbook's key discipline (anti double-counting): a regime is confirmed by
DISTINCT evidence AXES, not by correlated indicators. Confidence = number of
distinct axes whose net read supports the chosen regime (High>=3, Medium=2,
Low<=1). Liquidity shock (E) is force-capped to Low (correlations unstable).

Rule #1: this reads the inputs of the frozen macro/regime/gold calcs and produces
a NEW interpretive object. It never alters a frozen calculation.

NOTE (v1): the signature scoring below is a first, tunable heuristic faithful to
the handbook's signatures; the deterministic classifier decides regime + bias, and
a later AI step (recommendation, §7) narrates. Refine the signatures against the
handbook as needed — none of it touches Rule #1.

2026-09-06 (Pieter's ask) — TWO CLOCKS, not one. The handbook itself repeatedly says
the single-day level is the wrong thing to classify a "regime" from (§4.3 "the rate
of change matters"; Mistake 4 "using levels without direction" — "VIX fell from 30
to 20 over several sessions. The path matters."). The old version classified the
regime NAME straight off `macro_assets`' own 1-day direction, which is noisier than
even the app's own daily risk read (`compute_macro` in scan_news.py) and had zero
persistence — a single ordinary day's wobble could swap "Recession Shock" for
"Growth-positive risk-on" outright. Now:
  - The regime NAME is picked from `macro_assets_w1` (5-trading-session view, built
    by `build_macro_assets_w1` below from the same already-fetched `w1_close` field
    `scan_news.py`'s own `compute_w1_regime` already trusts for its own weekly read —
    nothing new fetched, no existing calculation touched).
  - `macro_assets` (the existing 1-day view) now feeds two things instead of the
    regime pick itself: (1) the sudden-shock check (`vix_spike`, still deliberately
    fast — a real shock, e.g. the handbook's own August 2024 carry-unwind case study,
    IS a single-day event and would be smoothed away by a 5-day window), and (2) a
    new per-axis `confirms_today` tag in `evidence` — is today's daily move
    confirming, fighting, or silent on the axis's own W1 trend. That's the handbook's
    §26 Step 6 ("demand confirmation") made concrete and, per Pieter's own ask, the
    actual point of showing Evidence at all — not just a static supports/doesn't list.
  - Hysteresis: the regime only actually FLIPS if the new leader clears the
    STANDING code's own axis count (recomputed against TODAY's W1 inputs, not
    whatever count it had when it was last picked) — a tie keeps the standing
    regime rather than falling through to the old alphabetical-code tie-break.
    Same shape as `regime_h4`/`regime_w1`/`macro`'s own `stable` flags elsewhere in
    this codebase, just applied as an actual switching cost instead of a read-only
    flag, since the archetype's own `archetype_change` push alert needs to mean
    something when it fires.
"""

# code -> (name, strong currencies, weak currencies)
REGIME_LIB = {
    "A": ("Growth-positive risk-on",   ["AUD", "NZD", "CAD"], ["JPY", "CHF"]),
    "B": ("US rate dominance",         ["USD"],               ["EUR", "GBP", "AUD"]),
    "C": ("Disinflationary easing",    ["AUD", "NZD", "EUR", "GBP"], ["USD"]),
    "D": ("Recession shock",           ["JPY", "CHF"],        ["AUD", "NZD", "CAD"]),
    "E": ("Liquidity shock",           ["USD", "JPY", "CHF"], ["AUD", "NZD", "CAD", "EUR", "GBP"]),
    "F": ("Inflation shock",           ["USD", "CAD"],        ["JPY"]),
    "G": ("Oil supply shock",          ["USD", "JPY", "CHF"], ["EUR"]),
    "H": ("China / industrial slowdown", ["JPY", "CHF"],      ["AUD", "NZD", "CAD"]),
    "I": ("European energy shock",     ["USD", "CHF"],        ["EUR"]),
    "J": ("Crowded carry unwind",      ["JPY", "CHF"],        ["AUD", "NZD"]),
}

VIX_SPIKE_PCT = 8.0   # |delta_pct| above which VIX counts as a "spike" (regime E/G/J) — stays
                      # on the fast 1-day read deliberately, see module doc comment above.

# W1 (5-trading-session) per-asset thresholds for `build_macro_assets_w1` — a first-pass
# heuristic, same "tune later" caveat as REGIME_LIB's own signatures. Where scan_news.py's
# own `compute_w1_regime` already scores an asset on this same `w1_close` basis, its numbers
# are reused verbatim (vix/spx/gold/dxy/copper) rather than re-derived, for consistency with
# the one W1 read this app already ships and trusts. The remaining assets (us10y/us3m/wti/btc/
# curve) aren't scored by `compute_w1_regime` at all, so their thresholds are new, scaled to
# roughly the same multiple over `build_macro_assets`' own 1-day thresholds that the reused
# ones already sit at (very roughly 5-6x) — a reasonable first cut, not a fitted constant.
# Values are (up_threshold, down_threshold); `is_bp` reads the pair on a basis-point diff
# instead of a percent-of-level diff (matches `build_macro_assets`' own us10y/us3m/curve).
_W1_THRESHOLDS = {
    "vix":    (20.0, -15.0, False),   # reused from compute_w1_regime
    "spx":    (3.0,  -3.0,  False),   # reused from compute_w1_regime
    "gold":   (4.0,  -3.0,  False),   # reused from compute_w1_regime
    "dxy":    (1.5,  -1.5,  False),   # reused from compute_w1_regime
    "copper": (2.0,  -2.0,  False),   # reused from compute_w1_regime
    "wti":    (6.0,  -6.0,  False),   # new — ~6x build_macro_assets' 1.0% daily
    "btc":    (15.0, -15.0, False),   # new — ~6x build_macro_assets' 2.5% daily
    "us10y":  (20.0, -20.0, True),    # new — ~5x build_macro_assets' 4bp daily
    "us3m":   (10.0, -10.0, True),    # new — ~5x build_macro_assets' 2bp daily
}
_W1_CURVE_THRESHOLD = 15.0  # bp — ~5x build_macro_assets' own 3bp daily curve threshold


def build_macro_assets_w1(macro: dict) -> dict:
    """
    W1 counterpart to `scan_news.py`'s own `build_macro_assets` — same raw `macro` dict
    (nothing new fetched: every asset's `w1_close` is already populated by `fetch_yf`), just
    read on the 5-trading-session basis instead of the 1-day one. Feeds `classify_macro_regime`'s
    regime PICK; `build_macro_assets`' existing 1-day output keeps feeding the fast confirm/
    diverge read instead of picking the name itself. Same shape as `build_macro_assets`' own
    per-asset dict (`direction` + a delta field + `label`) so `_axis_reads` below works
    unchanged on either one.
    """
    out = {}
    for key, d in macro.items():
        if not d or not d.get("close"):
            continue
        thresholds = _W1_THRESHOLDS.get(key)
        if not thresholds:
            continue
        prev = d.get("w1_close")
        if not prev:
            continue
        close = d["close"]
        label = d.get("label", key.upper())
        up_t, down_t, is_bp = thresholds
        change = (close - prev) * 100 if is_bp else (close / prev - 1) * 100
        direction = "up" if change > up_t else "down" if change < down_t else "flat"
        field = "delta_bp" if is_bp else "delta_pct"
        out[key] = {"direction": direction, field: round(change, 1), "label": label}

    y10 = macro.get("us10y", {})
    y3m = macro.get("us3m", {})
    if y10.get("close") and y10.get("w1_close") and y3m.get("close") and y3m.get("w1_close"):
        spread_now  = (y10["close"] - y3m["close"]) * 100
        spread_then = (y10["w1_close"] - y3m["w1_close"]) * 100
        delta_bp = spread_now - spread_then
        direction = "up" if delta_bp > _W1_CURVE_THRESHOLD else "down" if delta_bp < -_W1_CURVE_THRESHOLD else "flat"
        out["curve"] = {"direction": direction, "delta_bp": round(delta_bp, 1), "label": "10Y-3M"}
    return out


def _dir(ma, k):
    return ma.get(k, {}).get("direction", "flat")


def _pct(ma, k):
    v = ma.get(k, {})
    return v.get("delta_pct") if v.get("delta_pct") is not None else 0.0


def _arrow(direction: str) -> str:
    """"up"/"down" -> an arrow glyph, "flat" (or anything else) -> a flat dash — the one
    formatting every axis's "read" string in `_axis_reads` uses, so the Evidence cards never
    mix an arrow with a plain word again."""
    return {"up": "↑", "down": "↓"}.get(direction, "→")


def _axis_reads(ma: dict) -> dict:
    """Collapse the 10 instruments into 5 distinct axes with a net read each. Works on either
    the W1 dict or the 1-day dict — same shape, different clock (see module doc comment)."""
    vix, spx, copper, btc = _dir(ma, "vix"), _dir(ma, "spx"), _dir(ma, "copper"), _dir(ma, "btc")
    us10y, us3m, curve    = _dir(ma, "us10y"), _dir(ma, "us3m"), _dir(ma, "curve")
    dxy, wti, gold        = _dir(ma, "dxy"), _dir(ma, "wti"), _dir(ma, "gold")

    # Risk axis: SPX up / VIX down = risk-on
    risk_votes = 0
    risk_votes += 1 if spx == "up" else (-1 if spx == "down" else 0)
    risk_votes += 1 if vix == "down" else (-1 if vix == "up" else 0)
    risk_votes += 1 if copper == "up" else (-1 if copper == "down" else 0)
    risk_votes += 1 if btc == "up" else (-1 if btc == "down" else 0)
    risk_net = "risk_on" if risk_votes > 0 else ("risk_off" if risk_votes < 0 else "flat")

    rates_votes = (1 if us10y == "up" else (-1 if us10y == "down" else 0)) + \
                  (1 if us3m == "up" else (-1 if us3m == "down" else 0))
    rates_net = "up" if rates_votes > 0 else ("down" if rates_votes < 0 else "flat")

    # Every axis's "read" string uses the same arrow glyphs — Pieter's ask, 2026-09-06 — the
    # risk axis used to be the only one that did (SPX↑ VIX↓ Copper↑), the other four printed
    # the raw word ("US10Y up / US3M down") instead, an inconsistency visible on the Evidence
    # cards themselves.
    parts = []
    if spx != "flat":    parts.append(f"SPX{_arrow(spx)}")
    if vix != "flat":    parts.append(f"VIX{_arrow(vix)}")
    if copper != "flat": parts.append(f"Copper{_arrow(copper)}")

    return {
        "risk":      {"net": risk_net, "read": " ".join(parts) or "mixed"},
        "rates":     {"net": rates_net, "read": f"US10Y {_arrow(us10y)} / US3M {_arrow(us3m)}"},
        "usd":       {"net": dxy, "read": f"DXY {_arrow(dxy)}"},
        "commodity": {"net": "up" if (wti == "up" or copper == "up") else ("down" if (wti == "down" or copper == "down") else "flat"),
                      "read": f"WTI {_arrow(wti)} · Copper {_arrow(copper)}"},
        "safe_haven":{"net": gold, "read": f"Gold {_arrow(gold)}"},
    }


def _regime_axes(code: str, ax: dict, ma: dict, vix_spike: bool) -> set:
    """Return the set of DISTINCT axes whose net read supports regime `code`. `ax` is always
    the W1 axis reads (and `ma` the same W1 per-asset dict `ax` was built from, for the two
    single-instrument checks below that need a raw direction rather than a collapsed axis
    net); `vix_spike` is precomputed from the fast 1-day read (see module doc comment — a
    shock is a today event, deliberately not smoothed into the W1 window)."""
    s = set()
    risk, rates, usd, comm, sh = ax["risk"]["net"], ax["rates"]["net"], ax["usd"]["net"], ax["commodity"]["net"], ax["safe_haven"]["net"]

    if code == "A":   # Growth-positive risk-on
        if risk == "risk_on": s.add("risk")
        if comm == "up": s.add("commodity")
    elif code == "B": # US rate dominance
        if rates == "up": s.add("rates")
        if usd == "up": s.add("usd")
    elif code == "C": # Disinflationary easing
        if rates == "down": s.add("rates")
        if risk == "risk_on": s.add("risk")
        if usd == "down": s.add("usd")
    elif code == "D": # Recession shock
        if risk == "risk_off": s.add("risk")
        if rates == "down": s.add("rates")
        if comm == "down": s.add("commodity")
        if sh == "up": s.add("safe_haven")
    elif code == "E": # Liquidity shock
        if vix_spike: s.add("risk")
        if usd == "up": s.add("usd")
        if sh == "up": s.add("safe_haven")
    elif code == "F": # Inflation shock
        if rates == "up": s.add("rates")
        if comm == "up": s.add("commodity")
    elif code == "G": # Oil supply shock
        if _dir(ma, "wti") == "up": s.add("commodity")
        if risk == "risk_off" or vix_spike: s.add("risk")
    elif code == "H": # China / industrial slowdown
        if _dir(ma, "copper") == "down": s.add("commodity")
        if risk == "risk_off": s.add("risk")
    elif code == "I": # European energy shock
        if usd == "up": s.add("usd")
        if _dir(ma, "wti") == "up": s.add("commodity")
    elif code == "J": # Crowded carry unwind
        if vix_spike: s.add("risk")
        if sh == "up": s.add("safe_haven")
    return s


def _confidence(n_axes: int, code: str) -> str:
    if code == "E":
        return "Low"   # liquidity shock: correlations unstable, force-cap
    return "High" if n_axes >= 3 else ("Medium" if n_axes == 2 else "Low")


def _confirmation(axis: str, ax_w1: dict, ax_daily: dict) -> str:
    """Is TODAY's daily move confirming, fighting, or silent on this axis's own W1 trend —
    the handbook's own §4.3/Mistake-4 "the path matters" question, made concrete per-axis.
    Deliberately compares the axis to ITSELF across the two clocks, not to whichever regime
    happens to be chosen — confirmation is a property of the axis, not of the regime label."""
    daily_net = ax_daily[axis]["net"]
    if daily_net == "flat":
        return "quiet"
    return "confirming" if daily_net == ax_w1[axis]["net"] else "diverging"


def _gold_overlay(ma: dict, ax: dict) -> str:
    if _dir(ma, "gold") != "up":
        return "neutral"
    # Defensive gold: rising alongside stress (VIX up / SPX down / yields down).
    if _dir(ma, "vix") == "up" or _dir(ma, "spx") == "down" or ax["rates"]["net"] == "down":
        return "defensive"
    return "diversification"


def _usd_regime(ma: dict, ax: dict, vix_spike: bool) -> str:
    dxy = _dir(ma, "dxy")
    if dxy != "up":
        return "growth_dominance" if ax["risk"]["net"] == "risk_on" else "neutral"
    if vix_spike:
        return "confidence_shock"
    if ax["rates"]["net"] == "up":
        return "rate_dominance"
    if ax["risk"]["net"] == "risk_off":
        return "global_risk_off"
    return "rate_dominance"


def _conflicts(ax: dict) -> list:
    out = []
    if ax["rates"]["net"] == "up" and ax["risk"]["net"] == "risk_on":
        out.append("Rates rising while risk-on — check whether growth or policy is leading")
    if ax["rates"]["net"] == "down" and ax["risk"]["net"] == "risk_off":
        out.append("Yields falling while risk-off — recession fear vs disinflationary easing")
    if ax["usd"]["net"] == "up" and ax["risk"]["net"] == "risk_on":
        out.append("USD bid despite risk-on — rate differential vs risk flow tension")
    return out


def _narrative(name, bias) -> str:
    strong = " · ".join(bias["strong"]) if bias["strong"] else "—"
    weak = " · ".join(bias["weak"]) if bias["weak"] else "—"
    return f"{name}: favour {strong} over {weak}."


def classify_macro_regime(
    macro_assets_w1: dict,
    macro_assets_daily: dict | None = None,
    prev_regime: dict | None = None,
    updated: str = None,
) -> dict:
    """Returns the `macro_regime` object, or {} if there's no W1 read yet to classify from."""
    ma_w1 = macro_assets_w1 or {}
    ma_daily = macro_assets_daily or {}
    if not ma_w1:
        return {}

    ax = _axis_reads(ma_w1)              # regime-picking axis reads — W1 basis
    ax_daily = _axis_reads(ma_daily) if ma_daily else ax   # today's fast read, same shape
    vix_spike = _dir(ma_daily, "vix") == "up" and abs(_pct(ma_daily, "vix")) >= VIX_SPIKE_PCT

    scored = []  # (distinct_axes, code)
    for code in REGIME_LIB:
        n = len(_regime_axes(code, ax, ma_w1, vix_spike))
        scored.append((n, code))
    # Deterministic ordering: more axes first, then library order (A..J).
    order = list(REGIME_LIB.keys())
    scored.sort(key=lambda t: (-t[0], order.index(t[1])))

    top_n, top_code = scored[0]

    # Hysteresis — only accept a new leader if it clears the STANDING code's own axis count,
    # recomputed against TODAY's W1 inputs (not whatever count it had when it was last picked).
    # A tie keeps the standing regime. See module doc comment for why this exists.
    prev_code = ((prev_regime or {}).get("primary") or {}).get("code")
    if prev_code and prev_code in REGIME_LIB and prev_code != top_code:
        prev_n = len(_regime_axes(prev_code, ax, ma_w1, vix_spike))
        if top_n <= prev_n:
            top_code, top_n = prev_code, prev_n

    sec_n, sec_code = next(t for t in scored if t[1] != top_code)

    name, strong, weak = REGIME_LIB[top_code]
    bias = {"strong": strong, "weak": weak}
    primary = {
        "code": top_code, "name": name,
        "confidence": _confidence(top_n, top_code),
        "distinct_axes": top_n,
    }
    sec_name = REGIME_LIB[sec_code][0]
    secondary = {"code": sec_code, "name": sec_name,
                 "confidence": _confidence(sec_n, sec_code)}

    supporting = _regime_axes(top_code, ax, ma_w1, vix_spike)
    evidence = [
        {
            "axis": a,
            "read": ax[a]["read"],
            "supports": a in supporting,
            "confirms_today": _confirmation(a, ax, ax_daily),
        }
        for a in ("risk", "rates", "usd", "commodity", "safe_haven")
    ]

    out = {
        "primary": primary,
        "secondary": secondary,
        "gold_overlay": _gold_overlay(ma_w1, ax),
        "usd_regime": _usd_regime(ma_w1, ax, vix_spike),
        "currency_bias": bias,
        "evidence": evidence,
        "conflicts": _conflicts(ax),
        "narrative": _narrative(name, bias),
    }
    if updated:
        out["updated"] = updated
    return out
