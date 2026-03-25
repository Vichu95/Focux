package com.focux.pulse.utilities

/**
 * Central Definitions for Pulse App Logic.
 * Contains configuration constants, thresholds, and behavior definitions.
 */

// Data Logging
const val PulseAppDataLoggingFrequency = 15

/**
 * Jitter threshold in milliseconds.
 * Events within this threshold are considered "jitter" and merged.
 * Based on observed timestamp differences of ~2ms for jitter events.
 */
const val PULSE_JITTER_THRESHOLD_MS = 10L

/**
 * Estimated duration for a "Glance" or "Check".
 * Used to calculate screen time contribution for:
 * 1. SESSION_GLANCE (Lockscreen check)
 * 2. SESSION_UNLOCK_NOAPP (Unlock -> Launcher -> Lock)
 */
const val PULSE_GLANCE_ESTIMATE_MS = 2000L

/**
 * Additional apps to ignore for screen session detection.
 * These apps won't trigger UNLOCK_APP when opened.
 * User can add custom packages here in the future.
 */
val PULSE_IGNORED_APPS: Set<String> = setOf(
    // Add more packages here as needed
)

/**
 * Sleep window: Target Bedtime is 11 PM (23:00) Previous Day.
 * Target Wakup is 7 AM (07:00) Current Day.
 * Used to identify sleep sessions.
 */
const val PULSE_SLEEP_TARGET_BEDTIME_HOUR = 23 // 11 PM
const val PULSE_SLEEP_TARGET_WAKEUP_HOUR = 7   // 7 AM

// Deprecated Old Constants (keeping for now to avoid breakages before full migration)
const val PULSE_SLEEP_WINDOW_START_HOUR = 0   
const val PULSE_SLEEP_WINDOW_END_HOUR = 6     

/**
 * Minimum duration for an offline session to be considered part of sleep.
 * Removes short "jitter" gaps (e.g. 15 mins).
 */
const val PULSE_MIN_SLEEP_OFFLINE_THRESHOLD_MS = 60 * 60 * 1000L // 60 minutes

/**
 * Minimum events to look ahead before skipping an unmatched APP_OPEN.
 * If we see this many more events without finding the matching APP_CLOSE,
 * safely skip and move on. This prevents blocking on missing CLOSE events.
 */
const val PULSE_UNMATCHED_SKIP_THRESHOLD = 15

/**
 * Minimum duration of a gap to be considered "OFFLINE".
 * Gaps smaller than this are ignored/merged to reduce jitter.
 */
const val PULSE_MIN_OFFLINE_THRESHOLD_MS = 60 * 1000L // 1 minute

/**
 * Value representing "No Limit" natively instead of relying on null pointers.
 */
const val NO_LIMIT = -99
