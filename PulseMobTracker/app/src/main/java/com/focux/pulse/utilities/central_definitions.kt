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
 * Sleep detection configuration.
 * A gap >= this threshold during sleep window is considered sleep.
 */
const val PULSE_SLEEP_THRESHOLD_MS = 3 * 60 * 60 * 1000L  // 3 hours

/**
 * Sleep window: 12 AM (00:00) to 6 AM (06:00)
 * Only gaps within this window are considered sleep candidates.
 */
const val PULSE_SLEEP_WINDOW_START_HOUR = 0   // 12 AM
const val PULSE_SLEEP_WINDOW_END_HOUR = 6     // 6 AM

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
