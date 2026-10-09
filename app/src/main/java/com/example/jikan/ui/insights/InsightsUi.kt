package com.example.jikan.ui.insights

import com.example.jikan.screentime.UsageChange
import com.example.jikan.screentime.UsageChangeDirection

/**
 * Phase 11: display helpers for the Insights screen.
 *
 * Pure Kotlin — every number shown here was already calculated
 * deterministically by ScreenTimeCalculator. The AI (if ever used on this
 * screen) receives these strings as facts and never does analytics.
 */

fun formatMinutes(minutes: Int): String {
    if (minutes < 60) return "${minutes}m"
    val hours = minutes / 60
    val rest = minutes % 60
    return if (rest == 0) "${hours}h" else "${hours}h ${rest}m"
}

/** "↓ 18m vs yesterday" — today compared to yesterday, from the computed change. */
fun describeDayComparison(change: UsageChange, previousDayMinutes: Int): String = when {
    previousDayMinutes <= 0 -> "First day of tracking."
    change.deltaMinutes == 0 -> "Same as yesterday."
    change.direction == UsageChangeDirection.DOWN -> "↓ ${formatMinutes(-change.deltaMinutes)} vs yesterday"
    else -> "↑ ${formatMinutes(change.deltaMinutes)} vs yesterday"
}

/** "12m below your 7-day average" — today compared to the weekly average. */
fun describeAverageComparison(change: UsageChange): String = when {
    change.deltaMinutes == 0 -> "Right at your 7-day average."
    change.direction == UsageChangeDirection.DOWN ->
        "${formatMinutes(-change.deltaMinutes)} below your 7-day average"
    else -> "${formatMinutes(change.deltaMinutes)} above your 7-day average"
}
