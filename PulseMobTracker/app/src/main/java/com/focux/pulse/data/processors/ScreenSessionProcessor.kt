package com.focux.pulse.data.processors

import android.util.Log
import com.focux.pulse.data.local.entities.AppSession
import com.focux.pulse.data.local.entities.PulseEvents
import com.focux.pulse.data.local.entities.RawData
import com.focux.pulse.utilities.PULSE_JITTER_THRESHOLD_MS

/**
 * Validates and processes raw SCREEN_ON cycles into GLANCE/UNLOCK sessions.
 */
class ScreenSessionProcessor(
    private val ignoredApps: Set<String>
) {

    companion object {
        private const val TAG = "ScreenSessionProcessor"
    }

    /**
     * PASS 2: Process SCREEN_ON -> SCREEN_OFF cycles into GLANCE/UNLOCK sessions.
     */
    fun processScreenSessions(
        events: List<RawData>,
        initialLastProcessedId: Long
    ): Pair<List<AppSession>, Long> {
        val sessions = mutableListOf<AppSession>()
        val processedIndices = mutableSetOf<Int>()
        var lastSuccessfullyProcessedId = initialLastProcessedId

        for (i in events.indices) {
            if (i in processedIndices) continue

            val event = events[i]

            if (event.eventLabel == PulseEvents.SCREEN_ON) {
                val screenResult = processScreenCycle(events, i)

                if (screenResult == null) {
                    // Incomplete screen cycle - stop processing SCREEN sessions here
                    Log.d(TAG, "Incomplete SCREEN_ON at ID ${event.id}, stopping SCREEN pass.")
                    break
                }

                val (resultSessions, indicesToMark) = screenResult
                sessions.addAll(resultSessions)
                processedIndices.addAll(indicesToMark)
                lastSuccessfullyProcessedId = if (indicesToMark.isNotEmpty()) {
                    events[indicesToMark.last()].id
                } else {
                    event.id
                }
            } else {
                // Skip non-SCREEN_ON events in this pass
                lastSuccessfullyProcessedId = event.id
            }
        }

        return Pair(sessions, lastSuccessfullyProcessedId)
    }

    /**
     * Processes a single SCREEN_ON cycle to determine session type.
     */
    private fun processScreenCycle(
        events: List<RawData>,
        screenOnIndex: Int
    ): Pair<List<AppSession>, List<Int>>? {
        val screenOnEvent = events[screenOnIndex]
        val indicesToMark = mutableListOf<Int>()

        var hasUnlock = false
        var isUnlockApp = false
        var endTime: Long? = null

        for (j in (screenOnIndex + 1) until events.size) {
            val event = events[j]

            when (event.eventLabel) {
                PulseEvents.UNLOCK -> {
                    hasUnlock = true
                    indicesToMark.add(j)
                }
                PulseEvents.APP_OPEN -> {
                    // Skip ignored apps
                    val packageName = event.packageName ?: ""
                    if (packageName in ignoredApps) {
                        continue
                    }
                    // Valid app used! Mark this session as UNLOCK_APP
                    isUnlockApp = true
                    
                    // Don't mark index - leave for app processing
                }
                PulseEvents.LOCK -> {
                    if (hasUnlock) {
                        endTime = event.timestamp
                        indicesToMark.add(j)
                        
                        // Determine type: UNLOCK_APP (if app used) or UNLOCK_NOAPP (if no app used)
                        val type = if (isUnlockApp) PulseEvents.SESSION_UNLOCK_APP else PulseEvents.SESSION_UNLOCK_NOAPP
                        
                        val sessions = SessionSplitter.createSessions(
                            pkg = "system",
                            start = screenOnEvent.timestamp,
                            end = endTime,
                            type = type
                        )
                        return Pair(sessions, indicesToMark)
                    }
                    // LOCK without unlock - skip
                    indicesToMark.add(j)
                }
                PulseEvents.SCREEN_OFF -> {
                    if (!hasUnlock) {
                        // GLANCE: no unlock
                        endTime = event.timestamp
                        indicesToMark.add(j)
                        val sessions = SessionSplitter.createSessions(
                            pkg = "system",
                            start = screenOnEvent.timestamp,
                            end = endTime,
                            type = PulseEvents.SESSION_GLANCE
                        )
                        return Pair(sessions, indicesToMark)
                    }
                    
                    // Fallback: If unlocked but no LOCK event before SCREEN_OFF
                    endTime = event.timestamp
                    indicesToMark.add(j)
                    
                    val type = if (isUnlockApp) PulseEvents.SESSION_UNLOCK_APP else PulseEvents.SESSION_UNLOCK_NOAPP
                    
                    val sessions = SessionSplitter.createSessions(
                        pkg = "system",
                        start = screenOnEvent.timestamp,
                        end = endTime,
                        type = type
                    )
                    return Pair(sessions, indicesToMark)
                }
                PulseEvents.SCREEN_ON -> {
                    // Jitter check
                    if (event.timestamp - screenOnEvent.timestamp < PULSE_JITTER_THRESHOLD_MS) {
                        indicesToMark.add(j)
                        continue
                    }
                    // Incomplete cycle
                    return null 
                }
            }
        }
        return null
    }
}
