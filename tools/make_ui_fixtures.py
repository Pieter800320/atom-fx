"""
Generate UI STATE FIXTURES — full-contract signals.json documents, one per market
state, for building and testing the ATOM FX app (and a dev "gallery" screen) without
waiting for live data.

    python tools/make_ui_fixtures.py

Writes fixtures/state_*.json. These are HAND-DESIGNED representative states, not
scanner output — the app is a pure consumer, so they only need to be plausible and
to exercise every UI state (happy path, no-setups, and a stress regime).

Frozen calculations are NOT involved here; this never touches the scanner.
"""
import json, math, random
from pathlib import Path

OUT = Path(__file__).parent.parent / "fixtures"
PAIRS = ["EURUSD","GBPUSD","USDJPY","USDCHF","AUDUSD","USDCAD","NZDUSD",
         "EURJPY","GBPJPY","AUDJPY","NZDJPY","CADJPY"]
CCYS  = ["USD","EUR","GBP","JPY","CHF","AUD","CAD","NZD"]
SCHEMA = 1

BASE = {"EURUSD":1.166,"GBPUSD":1.360,"USDJPY":159.4,"USDCHF":0.805,"AUDUSD":0.718,
        "USDCAD":1.387,"NZDUSD":0.595,"EURJPY":185.7,"GBPJPY":216.8,"AUDJPY":114.4,
        "NZDJPY":94.8,"CADJPY":114.9}

STATE_MAP = {0:10,1:25,2:40,3:55,4:70,5:85,6:100}
def state_word(lvl):  return "low" if lvl<=2 else "watch" if lvl<=5 else "tradeable"

def spark(seed, base, trend, n=56):
    r = random.Random(seed); v = base; out=[]
    for _ in range(n):
        v += r.uniform(-1,1)*base*0.0018 + trend*base*0.0004
        out.append(round(v,5 if base<10 else 3))
    return out

def pair_block(pair, seed, lvl, direction, pot, factors, rank=None, blocked=None):
    b = BASE[pair]; sgn = 1 if direction=="bull" else -1 if direction=="bear" else 0
    d1p = "bull" if direction=="bull" else "bear" if direction=="bear" else "neutral"
    cmp = 60+ (pot-50)//4 if direction=="bull" else 40-(pot-50)//4 if direction=="bear" else 50
    cmp = max(5,min(95,cmp))
    ev = "BOS" if factors.get("structure") else ("CHoCH" if lvl>=4 and not factors.get("structure") else "none")
    return pair, {
        "pills": {"d1":d1p,"h4":d1p if lvl>=4 else "neutral","h1":d1p if lvl>=5 else "neutral"},
        "mom": {"d1":cmp+8*sgn,"dd1":6*sgn,"h4":cmp,"dh4":4*sgn,"h1":cmp-3,"dh1":2*sgn,
                "cmp":cmp,"dcmp4":sgn,"dcmp8":2*sgn,"dcmp12":sgn},
        "adx": 22+lvl*3, "d1_pct": round(0.05*sgn,2), "d5_pct": round(0.3*sgn,2),
        "prev_close": b, "prev5_close": round(b*(1-0.003*sgn),5),
        "cont": min(100, 30+lvl*11),
        "structure": {"h4":{"direction":direction if factors.get("structure") else "neutral",
                            "event":ev,"strength":0.78 if ev=="BOS" else 0.0,
                            "multiplier":1.23 if ev=="BOS" else 1.0},
                      "d1":{"direction":direction,"event":"none","strength":0.0,"multiplier":1.0}},
        "spark": {"d1":spark(seed,b,sgn), "h4":spark(seed+1,b,sgn*0.7), "h1":spark(seed+2,b,sgn*0.4)},
    }, {
        "direction":direction,"level":lvl,"state":state_word(lvl),"score":pot,
        "factors":factors,"setup_rank":rank,"blocked_at":blocked,
        "quality":round((pot-STATE_MAP[lvl])) if lvl in STATE_MAP else 0,
    }

def facs(n_pass, order=("regime","flow","breadth","momentum","structure","entry")):
    return {f:(i<n_pass) for i,f in enumerate(order)}, (None if n_pass>=6 else order[n_pass])

def csm_from(strengths):  # strengths: {ccy:val}
    return dict(strengths)

def build(state, regime, csm_h4, csm_delta, flow, macro_regime, rec, layout, calendar=True):
    pairs={}; potential={}
    for i,(pair,(lvl,d,pot)) in enumerate(layout.items()):
        f,blk = facs(lvl)
        rk = round(6+(pot-70)/12,1) if lvl>=5 else (round(4+lvl,1) if lvl>=3 else None)
        _,pb,pp = pair_block(pair,1000+i,lvl,d,pot,f,rk,blk)
        pairs[pair]=pb; potential[pair]=pp
    tradeable=[{"pair":p,"direction":v[0]=="_" and "bull" or layout[p][1],"score":round(6+(layout[p][2]-70)/12,2)}
               for p,v in layout.items() if layout[p][0]==6]
    doc = {
        "schema_version":SCHEMA,
        "updated":"2026-08-28T09:42:00+00:00",
        "state_name":state,
        "regime_d1":regime["d1"],"regime_h4":regime["h4"],"regime_h1":regime["h1"],
        "csm":{"d1":csm_h4,"h4":csm_h4,"h1":csm_h4},
        "csm_delta":{"d1":csm_delta,"h4":csm_delta,"h1":csm_delta},
        "currency_flow":flow,
        "breadth":{"h4":{c:{"support":min(7,3+i%5),"total":7 if c=="USD" else (4 if c in("GBP","JPY") else 3),
                          "pct":round(min(1.0,(3+i%5)/ (7 if c=="USD" else 4)),2),
                          "band":"Strong"} for i,c in enumerate(CCYS)}},
        "pairs":pairs,
        "potential":potential,
        "ranked":{"text":rec.get("rationale",""),"top":sorted(
                    [{"pair":p,"direction":layout[p][1],"score":round(6+(layout[p][2]-70)/12,2)}
                     for p in layout if layout[p][0]>=5], key=lambda x:-x["score"])[:3],
                  "updated":"2026-08-28T09:42:00+00:00"},
        "macro_regime":macro_regime,
        "recommendation":rec,
        "macro_assets":MACRO_ASSETS,
        "gold_signal":{"direction":"neutral","gold_pct":0.3,"h4_confirmed":False,
                       "h4_confidence":"Low","h1_confirmed":False,"updated":"2026-08-28T09:00:00+00:00"},
        "breaking":{"headlines":rec.get("headlines",["Markets quiet ahead of data"]),"updated":"2026-08-28T09:42:00+00:00"},
        "calendar":{"events":CALENDAR if calendar else [],"updated":"2026-08-28T06:00:00+00:00"},
        "correlations":{"pairs":PAIRS,"matrix":[[1.0 if i==j else round(0.4*math.cos((i-j)),2) for j in range(12)] for i in range(12)]},
    }
    return doc

MACRO_ASSETS = {
 "vix":{"value":15.2,"delta_pct":-1.6,"direction":"flat","label":"VIX"},
 "spx":{"value":7675.7,"delta_pct":0.4,"direction":"up","label":"S&P 500"},
 "gold":{"value":4672.2,"delta_pct":1.6,"direction":"up","label":"Gold"},
 "dxy":{"value":99.1,"delta_pct":-0.0,"direction":"flat","label":"DXY"},
 "copper":{"value":6.71,"delta_pct":1.7,"direction":"up","label":"Copper"},
 "us10y":{"value":4.66,"delta_bp":2.5,"direction":"flat","label":"US 10Y"},
 "us3m":{"value":3.69,"delta_bp":-1.5,"direction":"flat","label":"US 3M"},
 "wti":{"value":81.8,"delta_pct":-0.5,"direction":"flat","label":"WTI Oil"},
 "btc":{"value":78674,"delta_pct":0.1,"direction":"flat","label":"Bitcoin"},
 "curve":{"value":97.4,"delta_bp":4.0,"direction":"up","label":"10Y-3M"},
}
CALENDAR = [
 {"day":"Thu","time":"12:30","iso":"2026-08-27T12:30:00+00:00","currency":"USD","name":"PCE Price Index","forecast":"2.8%","previous":"2.7%","note":"Fed preferred inflation gauge"},
 {"day":"Thu","time":"22:40","iso":"2026-08-27T22:40:00+00:00","currency":"AUD","name":"RBA Rate Decision","forecast":"4.35%","previous":"4.35%","note":"Hold expected"},
]

def reg(name,conf,score): return {"regime":name,"confidence":conf,"score":score,"stable":True}

# ── STATE 1: RISK-ON (canonical) ────────────────────────────────────────────────
def risk_on():
    layout={"EURUSD":(6,"bull",86),"GBPUSD":(4,"bull",68),"USDJPY":(1,"neutral",24),
            "USDCHF":(1,"neutral",18),"AUDUSD":(6,"bull",74),"USDCAD":(1,"neutral",20),
            "NZDUSD":(5,"bull",66),"EURJPY":(6,"bull",79),"GBPJPY":(4,"bull",62),
            "AUDJPY":(4,"bull",70),"NZDJPY":(4,"bull",61),"CADJPY":(2,"neutral",40)}
    csm={"USD":28,"EUR":72,"GBP":63,"JPY":31,"CHF":38,"AUD":76,"CAD":54,"NZD":81}
    delta={"USD":-10,"EUR":8,"GBP":2,"JPY":-7,"CHF":-5,"AUD":4,"CAD":1,"NZD":3}
    flow={"leader":"EUR","leader_delta":8,"laggard":"USD","laggard_delta":-10,
          "absolute_leader":"NZD","absolute_laggard":"USD","driver_spread":18,"tf":"h4"}
    mr={"primary":{"code":"A","name":"Growth-positive risk-on","confidence":"High","distinct_axes":3},
        "secondary":{"code":"B","name":"US rate dominance","confidence":"Low"},
        "gold_overlay":"diversification","usd_regime":"growth_dominance",
        "currency_bias":{"strong":["AUD","NZD","CAD"],"weak":["JPY","CHF"]},
        "evidence":[{"axis":"risk","read":"SPX↑ VIX↓ Copper↑","supports":True},
                    {"axis":"rates","read":"US10Y↑ modest","supports":True},
                    {"axis":"usd","read":"DXY flat","supports":False},
                    {"axis":"commodity","read":"Copper↑ WTI firm","supports":True}],
        "conflicts":["Rates rising while risk-on — check whether growth or policy leads"],
        "narrative":"Broad risk appetite with firm cyclical commodities favours the dollar-bloc growth currencies over funders.",
        "updated":"2026-08-28T09:42:00+00:00"}
    rec={"headline":"Favour JPY-cross longs while risk-on holds","bias":"risk_on","action":"watch",
         "primary_pair":"EURUSD","direction":"bull","confidence":"Medium",
         "rationale":"EUR strengthening broadly (+8, breadth 7/8) while USD weakens across the board; the JPY-crosses lead.",
         "invalidation":"A DXY reclaim of 99.4 or VIX back above 20 pulls the outer cluster inward.",
         "next_catalyst":{"event":"PCE Price Index","iso":"2026-08-27T12:30:00+00:00"},
         "headlines":["DXY breaks 99.38 as EUR/USD and GBP/USD extend gains","Copper firms on China demand — AUD bid"],
         "generated_at":"2026-08-28T09:42:00+00:00"}
    return build("risk_on",{"d1":reg("Risk-On","High",9.9),"h4":reg("Risk-On","High",9.2),"h1":reg("Risk-On","Medium",7.0)},
                 csm,delta,flow,mr,rec,layout)

# ── STATE 2: RISK-OFF ───────────────────────────────────────────────────────────
def risk_off():
    layout={"EURUSD":(4,"bear",66),"GBPUSD":(4,"bear",64),"USDJPY":(2,"neutral",38),
            "USDCHF":(3,"bear",52),"AUDUSD":(6,"bear",80),"USDCAD":(4,"bull",63),
            "NZDUSD":(6,"bear",78),"EURJPY":(6,"bear",82),"GBPJPY":(5,"bear",74),
            "AUDJPY":(6,"bear",84),"NZDJPY":(5,"bear",72),"CADJPY":(4,"bear",60)}
    csm={"USD":78,"EUR":45,"GBP":42,"JPY":88,"CHF":74,"AUD":18,"CAD":30,"NZD":15}
    delta={"USD":6,"EUR":-3,"GBP":-4,"JPY":11,"CHF":7,"AUD":-9,"CAD":-2,"NZD":-8}
    flow={"leader":"JPY","leader_delta":11,"laggard":"AUD","laggard_delta":-9,
          "absolute_leader":"JPY","absolute_laggard":"NZD","driver_spread":20,"tf":"h4"}
    mr={"primary":{"code":"D","name":"Recession shock","confidence":"High","distinct_axes":3},
        "secondary":{"code":"J","name":"Crowded carry unwind","confidence":"Medium"},
        "gold_overlay":"defensive","usd_regime":"global_risk_off",
        "currency_bias":{"strong":["JPY","CHF","USD"],"weak":["AUD","NZD","CAD"]},
        "evidence":[{"axis":"risk","read":"SPX↓ VIX↑ Copper↓","supports":True},
                    {"axis":"rates","read":"yields↓","supports":True},
                    {"axis":"safe_haven","read":"Gold↑ JPY↑ CHF↑","supports":True}],
        "conflicts":[],
        "narrative":"Risk aversion and falling yields drive defensive demand — funders and havens bid, the dollar-bloc sold.",
        "updated":"2026-08-28T09:42:00+00:00"}
    rec={"headline":"Sell the dollar-bloc into risk-off","bias":"risk_off","action":"trade",
         "primary_pair":"AUDJPY","direction":"bear","confidence":"High",
         "rationale":"VIX up, copper down and yields lower confirm a growth scare; JPY and CHF lead while AUD/NZD lag broadly.",
         "invalidation":"VIX back below 15 with SPX reclaiming its range flips the read.",
         "next_catalyst":{"event":"PCE Price Index","iso":"2026-08-27T12:30:00+00:00"},
         "headlines":["Equities slide as growth fears mount","Yen surges as carry trades unwind"],
         "generated_at":"2026-08-28T09:42:00+00:00"}
    ma=dict(MACRO_ASSETS); # tweak for risk-off feel handled in narrative; keep asset table generic
    return build("risk_off",{"d1":reg("Risk-Off","High",1.2),"h4":reg("Risk-Off","High",1.5),"h1":reg("Risk-Off","Medium",2.5)},
                 csm,delta,flow,mr,rec,layout)

# ── STATE 3: RANGING — NO SETUPS ────────────────────────────────────────────────
def ranging():
    layout={p:( (3 if p=="EURUSD" else 2 if p in("AUDJPY","GBPUSD") else 1),
                "neutral", (55 if p=="EURUSD" else 40 if p in("AUDJPY","GBPUSD") else 22)) for p in PAIRS}
    csm={"USD":52,"EUR":55,"GBP":48,"JPY":50,"CHF":47,"AUD":53,"CAD":49,"NZD":51}
    delta={c:0 for c in CCYS}; delta["EUR"]=2; delta["GBP"]=-1
    flow={"leader":"EUR","leader_delta":2,"laggard":"GBP","laggard_delta":-1,
          "absolute_leader":"EUR","absolute_laggard":"CHF","driver_spread":3,"tf":"h4"}
    mr={"primary":{"code":None,"name":"No dominant regime","confidence":"Low","distinct_axes":0},
        "secondary":None,"gold_overlay":None,"usd_regime":None,
        "currency_bias":{"strong":[],"weak":[]},
        "evidence":[{"axis":"risk","read":"SPX flat VIX flat","supports":False},
                    {"axis":"rates","read":"yields flat","supports":False}],
        "conflicts":["No axis is decisive — mixed, range-bound tape"],
        "narrative":"No macro axis is decisive; the tape is range-bound with no broad currency theme.",
        "updated":"2026-08-28T09:42:00+00:00"}
    rec={"headline":"Mixed signals — no directional bias","bias":"mixed","action":"stand_aside",
         "primary_pair":None,"direction":"neutral","confidence":"Low",
         "rationale":"No regime has confirmed across distinct axes; breadth is thin and momentum is neutral. Wait for a catalyst.",
         "invalidation":"A decisive move in VIX or the front-end would establish a regime.",
         "next_catalyst":{"event":"PCE Price Index","iso":"2026-08-27T12:30:00+00:00"},
         "headlines":["Markets drift in tight ranges ahead of PCE"],
         "generated_at":"2026-08-28T09:42:00+00:00"}
    return build("ranging_no_setups",{"d1":reg("Ranging","Low",5.0),"h4":reg("Ranging","Low",5.0),"h1":reg("Mixed","Low",5.0)},
                 csm,delta,flow,mr,rec,layout)

# ── STATE 4: LIQUIDITY SHOCK ────────────────────────────────────────────────────
def liquidity_shock():
    layout={"EURUSD":(3,"bear",52),"GBPUSD":(3,"bear",50),"USDJPY":(2,"neutral",42),
            "USDCHF":(3,"bull",54),"AUDUSD":(5,"bear",76),"USDCAD":(4,"bull",66),
            "NZDUSD":(5,"bear",74),"EURJPY":(5,"bear",78),"GBPJPY":(4,"bear",70),
            "AUDJPY":(6,"bear",86),"NZDJPY":(5,"bear",73),"CADJPY":(4,"bear",64)}
    csm={"USD":85,"EUR":40,"GBP":36,"JPY":90,"CHF":80,"AUD":10,"CAD":22,"NZD":12}
    delta={"USD":12,"EUR":-6,"GBP":-7,"JPY":15,"CHF":9,"AUD":-14,"CAD":-4,"NZD":-11}
    flow={"leader":"JPY","leader_delta":15,"laggard":"AUD","laggard_delta":-14,
          "absolute_leader":"JPY","absolute_laggard":"AUD","driver_spread":29,"tf":"h4"}
    mr={"primary":{"code":"E","name":"Liquidity shock","confidence":"Low","distinct_axes":2},
        "secondary":{"code":"D","name":"Recession shock","confidence":"Low"},
        "gold_overlay":"defensive","usd_regime":"global_risk_off",
        "currency_bias":{"strong":["USD","JPY","CHF"],"weak":["AUD","NZD","CAD","EUR","GBP"]},
        "evidence":[{"axis":"risk","read":"VIX spikes, SPX↓ hard","supports":True},
                    {"axis":"safe_haven","read":"USD JPY CHF bid","supports":True}],
        "conflicts":["Correlations unstable — normal relationships may not hold (confidence forced LOW)"],
        "narrative":"Volatility spike and funding stress: capital preservation dominates. Correlations are unstable, so confidence is capped.",
        "updated":"2026-08-28T09:42:00+00:00"}
    rec={"headline":"Stand aside — liquidity stress, correlations unstable","bias":"risk_off","action":"stand_aside",
         "primary_pair":None,"direction":"neutral","confidence":"Low",
         "rationale":"A VIX spike with funding stress means the market's plumbing is the regime; historical relationships can break. Preserve capital.",
         "invalidation":"VIX normalising and correlations re-stabilising would restore a tradable regime.",
         "next_catalyst":{"event":"PCE Price Index","iso":"2026-08-27T12:30:00+00:00"},
         "headlines":["VIX spikes above 35 as funding stress spreads","Forced deleveraging hits carry trades"],
         "generated_at":"2026-08-28T09:42:00+00:00"}
    ma=dict(MACRO_ASSETS)
    ma["vix"]={"value":36.4,"delta_pct":42.0,"direction":"up","label":"VIX"}
    ma["spx"]={"value":7100.0,"delta_pct":-3.6,"direction":"down","label":"S&P 500"}
    doc=build("liquidity_shock",{"d1":reg("Risk-Off","High",0.5),"h4":reg("Risk-Off","High",0.8),"h1":reg("Risk-Off","High",1.0)},
              csm,delta,flow,mr,rec,layout)
    doc["macro_assets"]=ma
    return doc

def main():
    OUT.mkdir(parents=True, exist_ok=True)
    states={"state_risk_on":risk_on(),"state_risk_off":risk_off(),
            "state_ranging_no_setups":ranging(),"state_liquidity_shock":liquidity_shock()}
    for name,doc in states.items():
        p=OUT/f"{name}.json"
        with open(p,"w") as f: json.dump(doc,f,indent=2)
        print(f"✓ {p.name}  ({len(json.dumps(doc))//1024} KB, {len(doc['pairs'])} pairs, "
              f"{sum(1 for x in doc['potential'].values() if x['level']==6)} tradeable)")

if __name__=="__main__":
    main()
