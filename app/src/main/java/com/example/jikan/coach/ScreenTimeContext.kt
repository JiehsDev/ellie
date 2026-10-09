package com.example.jikan.coach

import com.example.jikan.screentime.ScreenTimeSummary

/**
 * Phase 7: the structured context the local model receives.
 *
 * This is a typealias — not a second data class — so [ScreenTimeSummary]
 * stays the single source of truth. The model never sees raw Android usage
 * events and is never asked to calculate statistics; every number here was
 * already computed deterministically by ScreenTimeCalculator.
 */
typealias ScreenTimeContext = ScreenTimeSummary
