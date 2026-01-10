package com.focux.pulse.data.processors

import android.util.Log
import com.focux.pulse.data.local.entities.AppSession
import com.focux.pulse.data.local.entities.PulseEvents
import com.focux.pulse.data.local.entities.RawData
import com.focux.pulse.utilities.PULSE_JITTER_THRESHOLD_MS
import com.focux.pulse.utilities.PULSE_UNMATCHED_SKIP_THRESHOLD

/**
 * Validates and processes raw APP_OPEN/CLOSE events into AppSessions.
 * 
 * IMPORTANT: This processor receives the "Strict" ignored list (System only).
 * It DOES track Launcher/Home Screen usage as valid 'SESSION_APP' events,
 * allowing us to capture the duration spent navigating menus.
 *
 * Handles:
 * - Jitter (rapid open/close)
 * - Missing CLOSE events (implicit close)
 * - Session capping (max 8 hours)
 */
class AppSessionProcessor(
    private val ignoredApps: Set<String>
) {

    companion object {
        private const val TAG = "AppSessionProcessor"
        private const val MAX_SESSION_DURATION_MS = 8 * 60 * 60 * 1000L // 8 hours
    }

    /**
     * PASS 1: Process APP_OPEN -> APP_CLOSE pairs into SESSION_APP entries.
     */
    fun processAppSessions(
        events: List<RawData>,
        initialLastProcessedId: Long
    ): Pair<List<AppSession>, Long> {
        val sessions = mutableListOf<AppSession>()
        val processedIndices = mutableSetOf<Int>()
        var lastSuccessfullyProcessedId = initialLastProcessedId

        for (i in events.indices) {
            if (i in processedIndices) continue

            val event = events[i]

            when (event.eventLabel) {
                PulseEvents.APP_OPEN -> {
                    val closeResult = findRealCloseEvent(events, i, event.packageName ?: "")

                    if (closeResult == null) {
                        // Unmatched OPEN - check if we should skip or wait
                        val eventsAfter = events.size - i - 1
                        
                        if (eventsAfter >= PULSE_UNMATCHED_SKIP_THRESHOLD) {
                            // Enough events have passed, safe to skip this unmatched OPEN
                            Log.d(TAG, "Skipping unmatched APP_OPEN at ID ${event.id} for ${event.packageName} (${eventsAfter} events after)")
                            lastSuccessfullyProcessedId = event.id
                            continue
                        } else {
                            // Not enough events yet, wait for more data
                            Log.d(TAG, "Unmatched APP_OPEN at ID ${event.id}, waiting for more data (${eventsAfter} events after)")
                            break
                        }
                    }

                    val (closeTime, indicesToMark) = closeResult
                    val duration = closeTime - event.timestamp
                    
                    // Only create session if duration is meaningful (not jitter)
                    if (duration >= PULSE_JITTER_THRESHOLD_MS) {
                        val finalCloseTime = if (duration > MAX_SESSION_DURATION_MS) {
                            Log.w(TAG, "Cap session > 8h: $duration for ${event.packageName}")
                            event.timestamp + MAX_SESSION_DURATION_MS
                        } else {
                            closeTime
                        }

                        // Create Session (No Splitting)
                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                        sessions.add(AppSession(
                            packageName = event.packageName ?: "unknown",
                            startTime = event.timestamp,
                            endTime = finalCloseTime,
                            duration = finalCloseTime - event.timestamp,
                            type = PulseEvents.SESSION_APP,
                            date = sdf.format(java.util.Date(event.timestamp)),
                            startTimeStr = com.focux.pulse.utilities.TimeUtils.format(event.timestamp),
                            endTimeStr = com.focux.pulse.utilities.TimeUtils.format(finalCloseTime)
                        ))
                    }

                    processedIndices.addAll(indicesToMark)
                    processedIndices.add(i)  // Mark the OPEN as processed too
                    lastSuccessfullyProcessedId = if (indicesToMark.isNotEmpty()) {
                        events[indicesToMark.last()].id
                    } else {
                        event.id
                    }
                }

                PulseEvents.APP_CLOSE -> {
                    // Orphan CLOSE - skip
                    lastSuccessfullyProcessedId = event.id
                }

                else -> {
                    // Skip non-APP events in this pass
                    lastSuccessfullyProcessedId = event.id
                }
            }
        }

        return Pair(sessions, lastSuccessfullyProcessedId)
    }

    /**
     * Finds the real close event for an APP_OPEN, handling jitter.
     */
    private fun findRealCloseEvent(
        events: List<RawData>,
        startIndex: Int,
        packageName: String
    ): Pair<Long, List<Int>>? {
        val indicesToMark = mutableListOf<Int>()
        var currentOpenIndex = startIndex

        while (true) {
            var nextSamePackageIndex: Int? = null
            var hasOtherAppInBetween = false

            for (j in (currentOpenIndex + 1) until events.size) {
                val event = events[j]

                // Global Terminators: Lock or Screen Off ends the session immediately
                if (event.eventLabel == PulseEvents.LOCK || event.eventLabel == PulseEvents.SCREEN_OFF) {
                    // Session ends at lock/screen off
                    // We mark this event as "visited" for the bookmark, logic-wise it's fine
                    indicesToMark.add(j) 
                    return Pair(event.timestamp, indicesToMark)
                }

                if (event.eventLabel == PulseEvents.APP_OPEN && event.packageName != packageName) {
                     // Check ignore list - if it's a valid app switch, terminate current session
                     if (event.packageName !in ignoredApps) {
                         // Session ends at the start of the next app
                         // Do NOT mark this event as consumed, it will be processed as next session
                         return Pair(event.timestamp, indicesToMark)
                     }
                    hasOtherAppInBetween = true
                }

                if (event.packageName == packageName &&
                    (event.eventLabel == PulseEvents.APP_OPEN || event.eventLabel == PulseEvents.APP_CLOSE)) {
                    nextSamePackageIndex = j
                    break
                }
            }

            if (nextSamePackageIndex == null) {
                return null
            }

            val nextEvent = events[nextSamePackageIndex]

            if (nextEvent.eventLabel == PulseEvents.APP_CLOSE) {
                // Found a CLOSE. Now check if there's immediate jitter (OPEN right after this CLOSE)
                // Look for the next same-package event after this CLOSE
                for (k in (nextSamePackageIndex + 1) until events.size) {
                    val afterEvent = events[k]
                    
                    // If another app opened, no jitter - break
                    if (afterEvent.eventLabel == PulseEvents.APP_OPEN && afterEvent.packageName != packageName) {
                        break
                    }
                    
                    // Found same package event
                    if (afterEvent.packageName == packageName) {
                        if (afterEvent.eventLabel == PulseEvents.APP_OPEN) {
                            val gap = afterEvent.timestamp - nextEvent.timestamp
                            if (gap < PULSE_JITTER_THRESHOLD_MS) {
                                // JITTER! Mark CLOSE and OPEN, continue from new OPEN
                                indicesToMark.add(nextSamePackageIndex) // Mark CLOSE
                                indicesToMark.add(k)                     // Mark jitter OPEN
                                currentOpenIndex = k
                                break // Break inner loop, continue outer while
                            }
                        }
                        // Either not OPEN or gap too large - this is real close
                        indicesToMark.add(nextSamePackageIndex)
                        return Pair(nextEvent.timestamp, indicesToMark)
                    }
                }
                
                // If we get here from break (jitter found), continue the while loop
                if (currentOpenIndex != startIndex && indicesToMark.contains(nextSamePackageIndex)) {
                    continue
                }
                
                // No event found after CLOSE, this is real close
                indicesToMark.add(nextSamePackageIndex)
                return Pair(nextEvent.timestamp, indicesToMark)
                
            } else {
                // Another OPEN without CLOSE in between
                if (!hasOtherAppInBetween) {
                    // Same app opened again - use this as implicit close
                    return Pair(nextEvent.timestamp, indicesToMark)
                } else {
                    // Other app in between, this OPEN is real close point
                    return Pair(nextEvent.timestamp, indicesToMark)
                }
            }
        }
    }
}
