package com.example.jikan.ui.settings

import com.example.jikan.data.AppRestriction

/**
 * Phase 10: display math for the restricted-app configuration screen.
 *
 * Pure Kotlin — enforcement stays in [com.example.jikan.screentime.AppRestrictionPolicy]
 * and the accessibility service; these functions only render today's usage
 * against the configured limit.
 */
data class RestrictionProgress(
    val usedMinutes: Int,
    val limitMinutes: Int,
    val remainingMinutes: Int,
    val isReached: Boolean,
)

/**
 * Today's progress for one restriction. `limitMinutes = 0` means "blocked on
 * open" per [AppRestriction], so any usage counts as reached.
 */
fun AppRestriction.todayProgress(usedMinutes: Int): RestrictionProgress {
    val reached = usedMinutes >= dailyLimitMinutes
    return RestrictionProgress(
        usedMinutes = usedMinutes,
        limitMinutes = dailyLimitMinutes,
        remainingMinutes = (dailyLimitMinutes - usedMinutes).coerceAtLeast(0),
        isReached = reached,
    )
}

/** One-line summary, e.g. "23m / 30m · 7m remaining" or "45m / 45m · Limit reached". */
fun RestrictionProgress.describe(): String {
    val fraction = "${usedMinutes}m / ${limitMinutes}m"
    return if (isReached) {
        "$fraction · Limit reached"
    } else {
        "$fraction · ${remainingMinutes}m remaining"
    }
}

/** Validates the editor's daily-limit field. Null means valid. */
fun validateDailyLimit(text: String): String? {
    val minutes = text.toIntOrNull()
    return when {
        minutes == null -> "Enter whole minutes."
        minutes < 0 -> "The limit can't be negative."
        else -> null
    }
}
