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

VIX_SPIKE_PCT = 8.0   # |delta_pct| above which VIX counts as a "spike" (regime E/G/J)


def _dir(ma, k):
    return ma.get(k, {}).get("direction", "flat")


def _pct(ma, k):
    v = ma.get(k, {})
    return v.get("delta_pct") if v.get("delta_pct") is not None else 0.0


def _axis_reads(ma: dict) -> dict:
    """Collapse the 10 instruments into 5 distinct axes with a net read each."""
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

    parts = []
    if spx != "flat":    parts.append(f"SPX{'↑' if spx=='up' else '↓'}")
    if vix != "flat":    parts.append(f"VIX{'↑' if vix=='up' else '↓'}")
    if copper != "flat": parts.append(f"Copper{'↑' if copper=='up' else '↓'}")

    return {
        "risk":      {"net": risk_net, "read": " ".join(parts) or "mixed"},
        "rates":     {"net": rates_net, "read": f"US10Y {us10y} / US3M {us3m}"},
        "usd":       {"net": dxy, "read": f"DXY {dxy}"},
        "commodity": {"net": "up" if (wti == "up" or copper == "up") else ("down" if (wti == "down" or copper == "down") else "flat"),
                      "read": f"WTI {wti} · Copper {copper}"},
        "safe_haven":{"net": gold, "read": f"Gold {gold}"},
    }


def _regime_axes(code: str, ax: dict, ma: dict) -> set:
    """Return the set of DISTINCT axes whose net read supports regime `code`."""
    s = set()
    risk, rates, usd, comm, sh = ax["risk"]["net"], ax["rates"]["net"], ax["usd"]["net"], ax["commodity"]["net"], ax["safe_haven"]["net"]
    vix_spike = _dir(ma, "vix") == "up" and abs(_pct(ma, "vix")) >= VIX_SPIKE_PCT

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


def _gold_overlay(ma: dict, ax: dict) -> str:
    if _dir(ma, "gold") != "up":
        return "neutral"
    # Defensive gold: rising alongside stress (VIX up / SPX down / yields down).
    if _dir(ma, "vix") == "up" or _dir(ma, "spx") == "down" or ax["rates"]["net"] == "down":
        return "defensive"
    return "diversification"


def _usd_regime(ma: dict, ax: dict) -> str:
    dxy = _dir(ma, "dxy")
    if dxy != "up":
        return "growth_dominance" if ax["risk"]["net"] == "risk_on" else "neutral"
    if _dir(ma, "vix") == "up" and abs(_pct(ma, "vix")) >= VIX_SPIKE_PCT:
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


def classify_macro_regime(macro_assets: dict, updated: str = None) -> dict:
    """Returns the `macro_regime` object, or {} if macro_assets is empty."""
    ma = macro_assets or {}
    if not ma:
        return {}

    ax = _axis_reads(ma)

    scored = []  # (distinct_axes, code)
    for code in REGIME_LIB:
        n = len(_regime_axes(code, ax, ma))
        scored.append((n, code))
    # Deterministic ordering: more axes first, then library order (A..J).
    order = list(REGIME_LIB.keys())
    scored.sort(key=lambda t: (-t[0], order.index(t[1])))

    top_n, top_code = scored[0]
    sec_n, sec_code = scored[1]

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

    supporting = _regime_axes(top_code, ax, ma)
    evidence = [
        {"axis": a, "read": ax[a]["read"], "supports": a in supporting}
        for a in ("risk", "rates", "usd", "commodity", "safe_haven")
    ]

    out = {
        "primary": primary,
        "secondary": secondary,
        "gold_overlay": _gold_overlay(ma, ax),
        "usd_regime": _usd_regime(ma, ax),
        "currency_bias": bias,
        "evidence": evidence,
        "conflicts": _conflicts(ax),
        "narrative": _narrative(name, bias),
    }
    if updated:
        out["updated"] = updated
    return out
