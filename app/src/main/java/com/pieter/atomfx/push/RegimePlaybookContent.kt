package com.pieter.atomfx.push

/**
 * The Regime Playbook — proof-of-concept for the "living handbook" vision (Pieter,
 * 2026-09-04): ATOM FX as a handbook you never finish reading, because live data keeps
 * generating fresh worked examples of the same underlying concepts. This is the first
 * installment — the regime/archetype chapter only, not the full FX Macro Flow Handbook
 * rewrite — proven on the `archetype_change` alert before scaling further.
 *
 * Source: `FX_Macro_Flow_Handbook_Expanded.pdf` (Desktop), deepened against the gaps a
 * professional-trader-style review of that handbook found (positioning/COT underweighted,
 * no carry-risk-premium theory, demand-vs-supply distinctions left implicit), and
 * reconciled against `scanner/extend/macro_regime.py`'s actual REGIME_LIB/`_regime_axes`
 * logic — NOT the handbook's own Ch.23 scenario matrix, which uses different letters for
 * the same concepts (the app's own "B: US rate dominance" is the handbook's own
 * "Scenario C"; two app regimes have no Ch.23 scenario at all). The app's A-J is the
 * single source of truth from here on; the standalone rewritten document uses the same
 * lettering.
 *
 * [buildRegimeExplanation] is the "sorted for this exact moment" mechanism: which axis
 * fragments and conflict fragments actually get assembled depends on the LIVE
 * `macro_regime` object's own `evidence`/`conflicts` fields, not a static lookup —
 * two firings of the same regime code with different confirming axes read differently.
 */
data class RegimePlaybookEntry(
    val code: String,
    val name: String,
    val coreStory: String,
    val confirmingAxes: List<String>,
    val confidenceNote: String,
    val biasMechanism: String,
    val falsificationChecklist: List<String>,
    val historicalNote: String? = null,
)

data class RegimeExplanation(
    val entry: RegimePlaybookEntry,
    val axisFragments: List<String>,
    val conflictFragments: List<String>,
)

/** Generic per-axis explanations (5, reused across every regime that axis can support) —
 *  keyed to match `MacroEvidence.axis` exactly ("risk","rates","usd","commodity","safe_haven"). */
val AXIS_FRAGMENTS: Map<String, String> = mapOf(
    "risk" to "The Risk axis reads SPX, VIX, Copper and BTC together — is capital becoming more or less willing to hold risky assets? The BIS has documented that changes in global investor risk capacity move exchange rates even without any change in the underlying economics of the countries involved, so this axis often moves first and fastest.",
    "rates" to "The Rates axis reads US10Y and US3M together — what is the market pricing for the path of US monetary policy? Read the direction, not just the level: a yield that's merely high but falling tells a different story than one that's low but rising. This is one of the most important single inputs into USD.",
    "usd" to "The USD axis reads DXY — but DXY is an outcome of FX prices (~58% EUR-weighted), not an independent driver in its own right. When it lines up with the other axes it confirms the regime; when it moves alone or against them, that's usually the more informative case, not the less.",
    "commodity" to "The Commodity axis reads WTI and Copper together — is the world's terms-of-trade shock improving or deteriorating for commodity exporters? Distinguish a demand-driven move (says something about global growth) from a supply-driven one (a shock specific to that commodity) before reading it as a growth signal.",
    "safe_haven" to "The Safe-haven axis reads Gold — but gold responds to real yields, nominal yields, USD, inflation expectations and central-bank demand all at once, so a single direction doesn't tell you which force is dominant. Cross-check the Gold Overlay (defensive vs. diversification) for that distinction.",
)

/** Keyed to the exact conflict strings `macro_regime.py::_conflicts()` produces — only
 *  three exist by construction, so this is a direct match, not a heuristic. */
val CONFLICT_FRAGMENTS: Map<String, String> = mapOf(
    "Rates rising while risk-on — check whether growth or policy is leading" to
        "This is the handbook's central \"good dollar vs. bad dollar\" distinction. Rates rising because growth is strong is a different regime than rates rising because the market is pricing a hawkish central bank against weakening growth — the first is a genuine risk-on/rate-dominance blend; the second usually breaks down into a rates-led USD move working against, not with, the risk-on currencies.",
    "Yields falling while risk-off — recession fear vs disinflationary easing" to
        "Falling yields don't automatically mean a weaker dollar — this is the handbook's single most important apparent contradiction. If the safe-haven bid is doing the driving, USD, JPY and CHF can all strengthen together even as yields fall, because investors are seeking liquidity, not return. If disinflation without a growth scare is doing the driving, the dollar more often weakens. Check whether equities are falling with the yield (recession fear) or holding up (clean disinflation).",
    "USD bid despite risk-on — rate differential vs risk flow tension" to
        "Two different forces are pulling in different directions here: risk appetite favours AUD/NZD/CAD over JPY/CHF, but a US-specific rate or growth advantage can simultaneously bid USD against everything, including the risk-on currencies. Cross-check cyclical crosses like AUDJPY (which isolate the risk component from the USD component) rather than reading AUDUSD alone.",
)

val REGIME_PLAYBOOK: Map<String, RegimePlaybookEntry> = listOf(
    RegimePlaybookEntry(
        code = "A", name = "Growth-positive risk-on",
        coreStory = "The classic \"everything works\" backdrop: global growth expectations are improving, equities are firm, volatility is compressed, and commodity demand is picking up alongside it. AUD, NZD and (via oil) CAD get support from two independent channels at once — the general risk-on bid every cyclical currency gets, plus their own specific commodity-export exposure — while JPY and CHF lose their safe-haven premium as capital rotates back into carry.",
        confirmingAxes = listOf("risk", "commodity"),
        confidenceNote = "This regime can only ever be confirmed by 2 axes (Risk and Commodity) by construction — Rates, USD and Safe-haven never count toward it. A Medium reading here is its structural ceiling, not a weak signal; don't wait for a \"High\" the code can't produce.",
        biasMechanism = "AUD/NZD/CAD strengthen because risk appetite AND their own terms-of-trade improve together; JPY/CHF weaken because the defensive bid unwinds and carry-trade funding demand returns.",
        falsificationChecklist = listOf(
            "Is Copper actually confirming, or is Risk carrying this alone? A risk-on tape with flat/falling Copper is a thinner version of this regime.",
            "Is USD also bid? If DXY is rising at the same time, check the conflict callout — a US-specific rate story can be fighting the risk-on currencies even as global risk appetite improves.",
            "Is this a genuine growth improvement, or a Fed-easing-driven risk rally? The two can look identical on the Risk axis but imply different USD behaviour (see Regime C).",
        ),
    ),
    RegimePlaybookEntry(
        code = "B", name = "US rate dominance",
        coreStory = "US yields and the expected Fed path are rising relative to the rest of the world, and the dollar is responding to that differential specifically — not to a broad risk-off move. This is the \"clean\" USD-strength regime: US assets are simply offering a better relative return, so capital rotates toward them. It hurts EUR, GBP and AUD most directly because they're the currencies with the largest, most liquid rate differentials against the US to lose.",
        confirmingAxes = listOf("rates", "usd"),
        confidenceNote = "Structural Medium ceiling (2 axes: Rates, USD) — same caveat as Regime A.",
        biasMechanism = "USD strengthens on relative-return grounds, not safety demand; EUR/GBP/AUD weaken because their own central banks are relatively less hawkish, not because anything is structurally wrong with those economies.",
        falsificationChecklist = listOf(
            "Is this rates story showing up as a genuine yield rise, or is USD strength actually coming from a risk-off bid instead (see Regime D/E)? Check the Risk axis independently.",
            "Is the rate move driven by growth strength (bear steepening) or a hawkish surprise against weakening growth? These carry different follow-through.",
            "Is DXY confirming for the right reason, or is it just EUR-specific weakness dressed up as broad USD strength? DXY is ~58% EUR-weighted.",
        ),
        historicalNote = "The 2014-15 episode is the closest real precedent — IMF data shows DXY appreciated roughly 16% while commodity prices fell roughly 32% over that period, and the RBA's own analysis attributed the associated AUD decline to a convergence of falling commodity prices, China uncertainty and shifting US rate expectations — not any single one of them.",
    ),
    RegimePlaybookEntry(
        code = "C", name = "Disinflationary easing",
        coreStory = "Inflation is cooling without a growth scare, so the market can price Fed easing while equities stay firm or even rally — falling yields here are risk-positive, not risk-negative, because the market reads the cut as \"insurance\" rather than \"rescue\". USD weakens on the rate-differential effect while AUD, NZD, EUR and GBP all benefit — a broader beneficiary basket than most regimes, because nothing is actually going wrong anywhere else.",
        confirmingAxes = listOf("rates", "risk", "usd"),
        confidenceNote = "One of only two regimes (with D) that can actually reach High confidence — it has 3 confirmable axes.",
        biasMechanism = "AUD/NZD/EUR/GBP strengthen against USD because US yields are falling in isolation, not because those economies are outperforming — this is a USD-driven basket move, not four independent stories.",
        falsificationChecklist = listOf(
            "Is SPX actually resilient, or just not yet reacting? A growth scare disguised as disinflation shows up as risk-off on the Risk axis even while yields fall — that's Regime D, not this one.",
            "Is the easing priced because inflation is genuinely cooling, or because growth is cracking? The handbook's Scenario D vs. Scenario E distinction is exactly this question.",
            "Which currency in the basket is actually leading? If only EUR or only GBP is moving, this may be currency-specific (ECB/BoE-driven), not a broad USD-driven regime.",
        ),
    ),
    RegimePlaybookEntry(
        code = "D", name = "Recession shock",
        coreStory = "This is the handbook's single most important \"apparent contradiction\": yields are falling because the market expects the Fed to cut in response to a growth scare, but the dollar can still strengthen, because the safe-haven bid for USD, JPY and CHF overwhelms the yield-differential effect. Don't read falling US yields as automatically USD-negative — check whether equities are falling WITH the yield. If they are, this is Regime D, not Regime C.",
        confirmingAxes = listOf("risk", "rates", "commodity", "safe_haven"),
        confidenceNote = "Can reach High confidence (4 confirmable axes) — the richest-evidenced regime in the library, alongside C.",
        biasMechanism = "JPY/CHF strengthen on defensive flows and carry-trade unwinding; AUD/NZD/CAD weaken because they're the currencies most exposed to a growth slowdown via commodity demand and risk appetite simultaneously.",
        falsificationChecklist = listOf(
            "Is USD actually strengthening, or is this JPY/CHF-only? If DXY isn't confirming, the safe-haven bid may be more European/Japanese-specific than a true global flight to the dollar.",
            "Is Gold confirming as \"defensive\" (rising with VIX up / yields down) or \"diversification\" (rising despite calm markets)? Only the defensive read belongs to this regime — check the Gold Overlay field directly.",
            "How fast did this develop? A shock that develops over weeks reads very differently from one triggered by a single data surprise.",
        ),
        historicalNote = "The clearest real-world version of this exact mechanism (safe-haven USD strength despite falling yields) was the 2020 COVID shock — see Regime E, a close sibling of this one, for that case.",
    ),
    RegimePlaybookEntry(
        code = "E", name = "Liquidity shock",
        coreStory = "A genuine liquidity/deleveraging event — not just risk-off, but a scramble for cash and funding that can briefly break normal cross-asset relationships altogether. VIX spikes hard (this regime specifically requires a large jump, not just a directional move), and USD, JPY and CHF can all strengthen together purely on funding demand, regardless of what yields or growth expectations are doing.",
        confirmingAxes = listOf("risk", "usd", "safe_haven"),
        confidenceNote = "Force-capped to Low confidence in the app, always — regardless of how many axes actually light up. The handbook's own reasoning: correlations are least reliable exactly when this regime is real, so the confidence label deliberately never oversells it. This is also the strongest argument in the whole library for treating positioning as a primary input, not a footnote: the August 2024 case study below shows a macro trigger that was, by itself, modest, turning violent because of how much leveraged positioning had to unwind. ATOM FX's own Conviction score (Currency Detail sheet) is the live version of exactly that check — a currency showing extreme, one-sided positioning right before a liquidity event is the single best warning sign the handbook's narrative-only \"positioning is a multiplier\" framing doesn't give you a tool for.",
        biasMechanism = "USD/JPY/CHF strengthen on pure liquidity/funding demand — not a rate story or even a classic safe-haven story, but a scramble for the world's most liquid, most fundable currencies. AUD/NZD/CAD/EUR/GBP all weaken together as leveraged positions get unwound indiscriminately.",
        falsificationChecklist = listOf(
            "Is this actually a liquidity event, or just a large risk-off move? Check the VIX move size specifically — this regime requires a genuine spike, not a routine risk-off drift.",
            "Are normal relationships holding, or breaking? If AUD/USD, gold and yields are all still moving in their \"normal\" direction just more sharply, this may be Regime D rather than a true liquidity event.",
            "Is Conviction data showing extreme positioning anywhere? A currency already crowded going into a shock is the one most likely to move furthest and fastest once it starts.",
        ),
        historicalNote = "2020 COVID is the textbook case: extreme VIX, collapsing growth expectations, severe commodity weakness and unusual USD strength despite collapsing yields, because liquidity and capital preservation dominated every other consideration.",
    ),
    RegimePlaybookEntry(
        code = "F", name = "Inflation shock",
        coreStory = "Inflation expectations and commodity prices are rising together while the Fed is expected to stay restrictive — a genuinely mixed regime, because commodity currencies can get an initial terms-of-trade lift from the same prices that are also driving the inflation story, even as the broader restrictive-policy backdrop eventually weighs on risk assets. USD often benefits from the \"higher for longer\" rate story even while the inflation itself is the underlying problem.",
        confirmingAxes = listOf("rates", "commodity"),
        confidenceNote = "Structural Medium ceiling (2 axes: Rates, Commodity).",
        biasMechanism = "USD and CAD both benefit initially — USD from the restrictive-policy expectation, CAD from oil specifically — while JPY weakens as the rate differential against a still-low BOJ widens.",
        falsificationChecklist = listOf(
            "Is the commodity move demand-driven (says something about growth) or a pure inflation/supply story? These have opposite implications for how long CAD's benefit lasts.",
            "Has equity weakness shown up yet? This regime often starts equity-neutral and only later becomes risk-negative as rates bite.",
            "Is gold rising as an inflation hedge, or is real-yield direction actually offsetting it? The two forces can cancel out and leave gold looking falsely \"quiet\".",
        ),
        historicalNote = "2022 is the clearest real precedent: US inflation surged, the Fed tightened aggressively, and — per the IMF — the dollar had appreciated 22% against JPY and 13% against EUR by October, an unusual episode specifically because USD strengthened while commodity prices also rose, breaking the more typical inverse USD/commodity relationship (see Regime B's 2014-15 case for the more typical version).",
    ),
    RegimePlaybookEntry(
        code = "G", name = "Oil supply shock",
        coreStory = "Oil rises specifically because of a supply disruption, not a demand/growth story — the critical distinction for CAD. If broader risk appetite and growth expectations hold steady, CAD gets a clean terms-of-trade benefit. But if the same shock also triggers a risk-off reaction, CAD's oil benefit can be entirely overwhelmed by risk-off pressure hitting it from the other direction — the same commodity move can help or hurt CAD depending on what else is happening at the same moment.",
        confirmingAxes = listOf("commodity", "risk"),
        confidenceNote = "Structural Medium ceiling (2 axes).",
        biasMechanism = "USD/JPY/CHF are the \"weak growth, rising uncertainty\" side of a supply shock; the CAD side is genuinely ambiguous and depends entirely on whether Risk is confirming this regime or fighting it.",
        falsificationChecklist = listOf(
            "Is Copper confirming alongside WTI, or diverging? A supply-specific oil shock often leaves Copper flat or rising (no demand damage); a demand-driven oil move tends to bring Copper down with it — the cleanest single check for which kind of shock this is.",
            "Is CAD actually responding, or flat? A flat CAD despite a sharp WTI move is itself informative — it suggests the risk-off and commodity-benefit forces are cancelling out.",
            "Is this genuinely supply-side (geopolitical, OPEC) or could it be a demand surprise? The two require completely different follow-through assumptions.",
        ),
    ),
    RegimePlaybookEntry(
        code = "H", name = "China / industrial slowdown",
        coreStory = "Copper falling is the cleanest single tell here — it's the metal most directly tied to Chinese and global industrial demand, so a genuine slowdown shows up there before almost anywhere else in this data set. AUD is the handbook's own preferred proxy for this regime specifically because Australia's export base is so concentrated in exactly the commodities (iron ore, coal) a Chinese industrial slowdown hits hardest.",
        confirmingAxes = listOf("commodity", "risk"),
        confidenceNote = "Structural Medium ceiling (2 axes).",
        biasMechanism = "AUD/NZD/CAD weaken on the terms-of-trade damage from falling industrial-commodity demand; JPY/CHF strengthen as the accompanying risk-off flows into the two classic funding/safe-haven currencies.",
        falsificationChecklist = listOf(
            "Is Copper falling on demand grounds, or is this a supply-side move (mine strikes, inventory swings)? Only a demand-driven fall genuinely signals this regime.",
            "Is AUD actually the currency showing the most damage, or is another cyclical currency leading? If AUD is holding up better than NZD/CAD, the China-specific story may be weaker than a broader risk-off move.",
            "Is there independent confirmation from Chinese data outside this app (PMI, credit growth) if you have access to it? Copper alone is a proxy, not a direct measurement.",
        ),
        historicalNote = "The RBA's own 2015 analysis, cited directly in the handbook, attributed that year's substantial AUD decline to a convergence of falling commodity prices, uncertainty about China's outlook and shifting US monetary-policy expectations — a genuine multi-factor episode, not a single-cause one.",
    ),
    RegimePlaybookEntry(
        code = "I", name = "European energy shock",
        coreStory = "European energy prices spike, European growth expectations deteriorate, and the shock is specific enough to Europe that it shows up as EUR weakness and USD/CHF strength rather than a broad global risk-off move. This is a regional-shock regime, not a global one — the tell is that the damage concentrates in EUR (and secondarily GBP) rather than spreading evenly across every risk-sensitive currency.",
        confirmingAxes = listOf("usd", "commodity"),
        confidenceNote = "Structural Medium ceiling (2 axes).",
        biasMechanism = "USD/CHF strengthen partly on safe-haven grounds and partly because they're simply not exposed to Europe's specific energy-import vulnerability; EUR weakens directly on its own terms-of-trade and growth damage.",
        falsificationChecklist = listOf(
            "Is this damage EUR-specific, or is it showing up broadly across AUD/NZD/CAD too? A truly regional shock should leave the commodity-currency bloc relatively unaffected — if they're falling too, this may be a broader risk-off event wearing an energy-shock costume.",
            "Is GBP moving with EUR or diverging from it? EUR/GBP is a cleaner read on UK-specific conditions once the common European/USD factor is removed.",
            "Is CHF strengthening for safe-haven reasons or because it's structurally less energy-exposed than the euro area? Both push the same direction, so this one is hard to falsify from price alone.",
        ),
        historicalNote = "The 2022 European energy crisis is the textbook example — the combination of the Ukraine war, Europe's energy-import dependence, surging inflation and a widening Fed/ECB policy divergence placed sustained, concentrated pressure on EUR specifically, distinct from the broader global inflation-shock story unfolding at the same time (see Regime F).",
    ),
    RegimePlaybookEntry(
        code = "J", name = "Crowded carry unwind",
        coreStory = "This regime is about positioning, not fundamentals — the handbook's own clearest statement is worth quoting directly: \"a market can move far more violently than the initial macro news appears to justify because positioning and leverage amplify the move.\" JPY has historically been the primary carry-funding currency: borrow JPY cheaply, buy a higher-yielding currency, profit from the differential — a trade that works only as long as volatility stays low and the funding currency stays weak. When VIX spikes and JPY starts to strengthen, the trade goes underwater fast, forcing leveraged unwinds that push JPY higher still, in a self-reinforcing loop that has nothing to do with any new information about the Japanese economy.",
        confirmingAxes = listOf("risk", "safe_haven"),
        confidenceNote = "Structural Medium ceiling (2 axes) — and, like Regime E, a case where the confidence label genuinely understates how violent the move can be, because the amplification comes from positioning the axis-based classifier doesn't see. Academically, carry trades are a compensated risk premium, not a free lunch (the forward-premium puzzle: high-yield currencies empirically tend not to depreciate by the interest differential the way textbook interest-rate parity predicts) — carry traders are paid, on average, to bear exactly this crash risk. This regime is that risk showing up.",
        biasMechanism = "JPY/CHF strengthen as leveraged carry positions unwind — buying back the currency sold to fund the trade; AUD/NZD weaken as the higher-yielding side of the same trade gets sold to close it out. This is flow-driven, not fundamentals-driven — precisely why it can move further and faster than the triggering news alone would justify.",
        falsificationChecklist = listOf(
            "Is JPY the currency doing the strengthening, or is this a broader flight to USD/CHF too? A JPY-specific move is the cleaner carry-unwind signature; broad-based defensive strength looks more like Regime D or E.",
            "How large was the VIX move, and how fast? This regime is defined by speed and magnitude, not just direction.",
            "Is Conviction data showing JPY (or the funding currency generally) as extremely crowded short beforehand? That positioning extreme is the actual fuel for this regime — check it directly rather than inferring it after the fact.",
        ),
        historicalNote = "The early-August 2024 episode is the handbook's own worked example: US labour data raised recession concerns, the BOJ had just tightened policy, volatility jumped, and the BIS estimated broad yen-funded carry exposure around ¥40 trillion at the time — a scale of positioning that turned a moderate macro surprise into a violent, fast repricing.",
    ),
).associateBy { it.code }

/** Assembles the explanation for the LIVE regime — the "sorted for this exact moment"
 *  step. Only axis fragments where `supports` is true, and only conflict fragments that
 *  actually fired, are included; two firings of the same code read differently. */
fun buildRegimeExplanation(code: String?, supportingAxes: List<String>, conflicts: List<String>): RegimeExplanation? {
    val entry = REGIME_PLAYBOOK[code] ?: return null
    return RegimeExplanation(
        entry = entry,
        axisFragments = supportingAxes.mapNotNull { AXIS_FRAGMENTS[it] },
        conflictFragments = conflicts.mapNotNull { CONFLICT_FRAGMENTS[it] },
    )
}
