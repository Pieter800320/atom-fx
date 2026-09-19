package com.pieter.atomfx.push

/**
 * Signals Roadmap §5c Phase 2 — the per-person half of the Crowd score alert.
 *
 * The backend (`state_alerts.py::_crowd_score_alerts`) sends one `crowd_score` push for EVERY rise of a pair's
 * Crowd top/bottom score into a higher band (any / 20 / 40 / 60) and puts the new `level` and the `timeframe` in the
 * push data. The push topic is shared by every device, so each person's minimum level can only be applied here.
 */

/** The minimum-level choices a person can pick per timeframe; 0 means "any level above zero". */
val CROWD_MIN_LEVEL_CHOICES: List<Int> = listOf(0, 20, 40, 60)

const val CROWD_MIN_LEVEL_D1_DEFAULT = 40
const val CROWD_MIN_LEVEL_H4_DEFAULT = 60

/** "Any" for 0, otherwise the number — the label a level choice wears in Settings. */
fun crowdMinLevelLabel(level: Int): String = if (level <= 0) "ANY" else level.toString()

/**
 * True when a `crowd_score` push should be shown: its [level] reaches the person's minimum for its [timeframe]
 * ("d1" / "h4"). A push with no readable level or timeframe is shown, not silently dropped — the backend always
 * sends both, so a missing one means something is off and the person is better told than kept in the dark.
 */
fun crowdAlertPasses(timeframe: String?, level: Double?, minD1: Int, minH4: Int): Boolean {
    if (level == null) return true
    val minimum = when (timeframe) {
        "d1" -> minD1
        "h4" -> minH4
        else -> return true
    }
    return level > 0.0 && level >= minimum
}
