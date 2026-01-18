package com.focux.pulse.data.processors

import android.util.Log
import com.focux.pulse.data.local.entities.AppSession
import com.focux.pulse.data.local.entities.PulseEvents
import com.focux.pulse.data.local.entities.RawData
import com.focux.pulse.utilities.PULSE_JITTER_THRESHOLD_MS

import com.focux.pulse.utilities.TimeUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Validates and processes raw SCREEN_ON cycles into GLANCE/UNLOCK sessions.
 *
 * IMPORTANT: This processor receives the "Combined" ignored list (System + Launchers).
 * It treats Launcher usage as "No Valid App", resulting in SESSION_UNLOCK_NOAPP.
 * This allows us to count "Unlock -> Menu -> Lock" flows as "Checks/Glances".
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
        var lastNotificationTimestamp: Long = 0
        
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        for (i in events.indices) {
            if (i in processedIndices) continue

            val event = events[i]

            if (event.eventLabel == PulseEvents.NOTIFICATION) {
                // 1. Create Point Session for Notification
                // "For timing of the notification, just use start time as notification time, and end time as same."
                val notifSession = AppSession(
                    packageName = event.packageName ?: "notification",
                    startTime = event.timestamp,
                    endTime = event.timestamp,
                    duration = 0,
                    type = PulseEvents.SESSION_NOTIFICATION,
                    date = sdf.format(Date(event.timestamp)),
                    startTimeStr = TimeUtils.format(event.timestamp),
                    endTimeStr = TimeUtils.format(event.timestamp)
                )
                sessions.add(notifSession)
                
                // Track timestamp for correlation
                lastNotificationTimestamp = event.timestamp
                
                // Mark processed
                processedIndices.add(i)
                lastSuccessfullyProcessedId = event.id
            } 
            else if (event.eventLabel == PulseEvents.SCREEN_ON) {
                val screenResult = processScreenCycle(events, i, lastNotificationTimestamp, sdf)

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
                // Skip non-SCREEN_ON events in this pass (but advance ID if it's safe)
                // Actually safer to only advance if we are sure it's processed or irrelevant
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
        screenOnIndex: Int,
        lastNotificationTimestamp: Long,
        sdf: SimpleDateFormat
    ): Pair<List<AppSession>, List<Int>>? {
        val screenOnEvent = events[screenOnIndex]
        val indicesToMark = mutableListOf<Int>()
        indicesToMark.add(screenOnIndex) // Mark SCREEN_ON as processed

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
                        
                        val session = AppSession(
                            packageName = "system",
                            startTime = screenOnEvent.timestamp,
                            endTime = endTime,
                            duration = endTime - screenOnEvent.timestamp,
                            type = type,
                            date = sdf.format(Date(screenOnEvent.timestamp)),
                            startTimeStr = TimeUtils.format(screenOnEvent.timestamp),
                            endTimeStr = TimeUtils.format(endTime)
                        )
                        return Pair(listOf(session), indicesToMark)
                    }
                    // LOCK without unlock - skip
                    indicesToMark.add(j)
                }
                PulseEvents.SCREEN_OFF -> {
                    if (!hasUnlock) {
                        // GLANCE or NOTIFICATION-GLANCE
                        endTime = event.timestamp
                        indicesToMark.add(j)
                        
                        // Check for Notification Correlation (within 2 seconds)
                        val isNotificationDriven = (screenOnEvent.timestamp - lastNotificationTimestamp) in 0..2000
                        val type = if (isNotificationDriven) PulseEvents.SESSION_NOTIFICATION else PulseEvents.SESSION_GLANCE
                        
                        val session = AppSession(
                            packageName = "system",
                            startTime = screenOnEvent.timestamp,
                            endTime = endTime,
                            duration = endTime - screenOnEvent.timestamp,
                            type = type,
                            date = sdf.format(Date(screenOnEvent.timestamp)),
                            startTimeStr = TimeUtils.format(screenOnEvent.timestamp),
                            endTimeStr = TimeUtils.format(endTime)
                        )
                        return Pair(listOf(session), indicesToMark)
                    }
                    
                    // Fallback: If unlocked but no LOCK event before SCREEN_OFF
                    endTime = event.timestamp
                    indicesToMark.add(j)
                    
                    val type = if (isUnlockApp) PulseEvents.SESSION_UNLOCK_APP else PulseEvents.SESSION_UNLOCK_NOAPP
                    
                    val session = AppSession(
                        packageName = "system",
                        startTime = screenOnEvent.timestamp,
                        endTime = endTime,
                        duration = endTime - screenOnEvent.timestamp,
                        type = type,
                        date = sdf.format(Date(screenOnEvent.timestamp)),
                        startTimeStr = TimeUtils.format(screenOnEvent.timestamp),
                        endTimeStr = TimeUtils.format(endTime)
                    )
                    return Pair(listOf(session), indicesToMark)
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
