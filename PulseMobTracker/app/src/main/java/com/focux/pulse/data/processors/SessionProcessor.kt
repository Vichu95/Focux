package com.focux.pulse.data.processors

import android.content.Context
import android.util.Log
import com.focux.pulse.data.local.dao.*
import com.focux.pulse.data.local.entities.*
import com.focux.pulse.utilities.AppInfoHelper
import com.focux.pulse.utilities.PULSE_IGNORED_APPS

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The "Brain" of the analytics pipeline.
 * Orchestrates the processing of raw events into sessions and daily stats
 * by delegating to specialized processors.
 */
class SessionProcessor(
    context: Context,
    private val rawDataDao: RawDataDao,
    private val analyticsDao: AnalyticsDao,
    appInfoDao: AppInfoDao
) {
    companion object {
        private const val TAG = "SessionProcessor"
        private val mutex = Mutex() // Global lock to prevent race conditions (e.g. periodic vs immediate)
    }

    private val launcherPackages: Set<String> by lazy {
        AppInfoHelper.getLauncherPackages(context).toSet()
    }

    private val screenIgnoredApps: Set<String> by lazy {
        val combined = PULSE_IGNORED_APPS + launcherPackages
        Log.d(TAG, "Ignored Apps for Screen (inc. Launchers): $combined")
        combined
    }

    // --- SPLIT LOGIC EXPLANATION ---
    // We split the "Ignored Apps" lists to support our Hybrid Metrics Model AND Raw Data Logging:
    
    // 1. AppProcessor: (USER REQUEST: LOG EVERYTHING)
    //    We pass an EMPTY set of ignored apps.
    //    Why? We want the `app_sessions` table to be a true "Raw Record" of usage.
    //    It will contain System Apps, Launchers, Settings, etc.
    //    Filtering happens LATER during the "Summary/Metrics" phase.
    
    // 2. ScreenProcessor: (Needs Noise Filtering)
    //    We MUST ignore System Apps + Launchers here.
    //    Why? To distinguish "Real Usage" from "Brief Checks".
    //    "Unlock -> Launcher -> Lock" = SESSION_UNLOCK_NOAPP (Glance).
    //    If we didn't ignore Launcher, it would look like SESSION_UNLOCK_APP.

    // AppProcessor: Log EVERYTHING. No filtering at ingestion time.
    private val appProcessor = AppSessionProcessor(ignoredApps = emptySet())
    
    // ScreenProcessor: Ignore System+Launcher to preserve Glance/Check classification logic.
    private val screenProcessor = ScreenSessionProcessor(ignoredApps = screenIgnoredApps)
    private val dailyProcessor = DailySummaryProcessor(context, analyticsDao, appInfoDao)

    /**
     * Main processing entry point.
     * Processes APP sessions and SCREEN sessions with SEPARATE bookmarks.
     * This ensures one type doesn't block the other.
     * Protected by Mutex to ensure atomic processing and prevent duplicate sessions.
     */
    suspend fun processPendingData() = mutex.withLock {
        val allSessions = mutableListOf<AppSession>()
        
        // PASS 1: Process APP sessions (independent bookmark)
        val lastAppId = analyticsDao.getState("last_processed_app_id")?.toLong() ?: 0L
        val appEvents = rawDataDao.getEventsSince(lastAppId)
        if (appEvents.isNotEmpty()) {
            val sortedAppEvents = appEvents.sortedBy { it.timestamp }
            
            // Delegate to AppSessionProcessor
            val (appSessions, newAppId) = appProcessor.processAppSessions(sortedAppEvents, lastAppId)
            
            allSessions.addAll(appSessions)
            if (newAppId > lastAppId) {
                analyticsDao.updateState(SystemState("last_processed_app_id", newAppId.toString()))
            }
            Log.d(TAG, "APP pass: ${appSessions.size} sessions, bookmark: $lastAppId -> $newAppId")
        }
        
        // PASS 2: Process SCREEN sessions (independent bookmark)
        val lastScreenId = analyticsDao.getState("last_processed_screen_id")?.toLong() ?: 0L
        val screenEvents = rawDataDao.getEventsSince(lastScreenId)
        if (screenEvents.isNotEmpty()) {
            val sortedScreenEvents = screenEvents.sortedBy { it.timestamp }
            
            // Delegate to ScreenSessionProcessor
            val (screenSessions, newScreenId) = screenProcessor.processScreenSessions(sortedScreenEvents, lastScreenId)
            
            allSessions.addAll(screenSessions)
            if (newScreenId > lastScreenId) {
                analyticsDao.updateState(SystemState("last_processed_screen_id", newScreenId.toString()))
            }
            Log.d(TAG, "SCREEN pass: ${screenSessions.size} sessions, bookmark: $lastScreenId -> $newScreenId")
        }

        // Save all sessions and update stats
        if (allSessions.isNotEmpty()) {
            // 1. Strict Sort by Start Time
            val sortedSessions = allSessions.sortedBy { it.startTime }
            
            // 1.5 Clean Phantom Sessions (Apps running without Screen On)
            val refinedSessions = cleanPhantomSessions(sortedSessions)
            
            // 2. OFFLINE CALCULATION (Pre-Insert)
            // Goal: Find gaps between [DB History] and [New Batch], and within [New Batch].
            val lastOfflineCheckId = analyticsDao.getState("last_offline_check_id")?.toLong() ?: 0L
            val historySessions = analyticsDao.getSessionsStartingFromId(lastOfflineCheckId)
            
            // Filter History for Active Only (Ignore previous offline/notifications)
            val activeHistory = historySessions.filter { 
                it.type != PulseEvents.SESSION_NOTIFICATION && it.type != PulseEvents.SESSION_OFFLINE
            }
            // Filter New Batch for Active Only
            val activeNew = refinedSessions.filter { 
                it.type != PulseEvents.SESSION_NOTIFICATION
            }
            
            // Combined Check List (History + New)
            val sessionsToCheck = (activeHistory + activeNew).sortedBy { it.startTime }
            
            val offlineSessions = mutableListOf<AppSession>()
            
            if (sessionsToCheck.isNotEmpty()) {
                for (i in 0 until sessionsToCheck.size - 1) {
                    val currentSession = sessionsToCheck[i]
                    val nextSession = sessionsToCheck[i+1]
                    
                    val gap = nextSession.startTime - currentSession.endTime
                    
                    if (gap >= com.focux.pulse.utilities.PULSE_MIN_OFFLINE_THRESHOLD_MS) {
                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                        val newOffline = AppSession(
                            packageName = "system",
                            startTime = currentSession.endTime,
                            endTime = nextSession.startTime,
                            duration = nextSession.startTime - currentSession.endTime,
                            type = PulseEvents.SESSION_OFFLINE,
                            date = sdf.format(java.util.Date(currentSession.endTime)),
                            startTimeStr = com.focux.pulse.utilities.TimeUtils.format(currentSession.endTime),
                            endTimeStr = com.focux.pulse.utilities.TimeUtils.format(nextSession.startTime)
                        )
                        offlineSessions.add(newOffline)
                    }
                }
            }
            
            // 3. COMBINE & SORT & INSERT
            // We combine the New Active sessions + New Offline sessions.
            // Sorting by StartTime ensures they are interleaved correctly in the DB (resolving "Chunk" issue).
            val finalSessions = (refinedSessions + offlineSessions).sortedBy { it.startTime }
            
            Log.d(TAG, "Inserting ${finalSessions.size} sessions (Active: ${refinedSessions.size}, New Offline: ${offlineSessions.size})")
            analyticsDao.insertSessions(finalSessions)
            
            // 4. UPDATE POINTER
            // Now that they are inserted, we need to update the pointer to the LAST row in the DB.
            // This ensures next time we fetch everything starting from the end of this batch.
            // We fetch the latest ID from the DB.
            val newLastId = analyticsDao.getLastRowId() ?: lastOfflineCheckId
            if (newLastId > lastOfflineCheckId) {
                 analyticsDao.updateState(SystemState("last_offline_check_id", newLastId.toString()))
            }

            // 5. Delegate to DailySummaryProcessor 
            dailyProcessor.updateDailyStats(finalSessions, PULSE_IGNORED_APPS)
        }
    }

    /**
     * Reclassifies App Sessions that occur without a corresponding Screen Session as NOTIFICATIONS.
     * This prevents background app activity (e.g. Wellbeing, Launcher updates) from breaking offline streaks.
     */
    private suspend fun cleanPhantomSessions(sessions: List<AppSession>): List<AppSession> {
        // Identify "Interactive" windows (Screen On + Unlocked or Glanced)
        val interactiveSessions = sessions.filter {
            it.type == PulseEvents.SESSION_GLANCE ||
            it.type == PulseEvents.SESSION_UNLOCK_NOAPP ||
            it.type == PulseEvents.SESSION_UNLOCK_APP
        }

        val result = mutableListOf<AppSession>()
        
        for (session in sessions) {
            if (session.type == PulseEvents.SESSION_APP) {
                // Check overlap: StartA < EndB && EndA > StartB
                val hasOverlap = interactiveSessions.any { screen ->
                    session.startTime < screen.endTime && session.endTime > screen.startTime
                }
                
                if (hasOverlap) {
                    result.add(session)
                } else {
                    // Fallback: Check DB for Screen State at session start
                    val lastScreenEvent = analyticsDao.getLastScreenStateEvent(session.startTime)
                    val isScreenOn = lastScreenEvent?.eventLabel == PulseEvents.SCREEN_ON
                    
                    if (isScreenOn) {
                        // Valid Session (part of ongoing screen session not yet in this batch)
                        result.add(session)
                    } else {
                        Log.d(TAG, "Phantom App Detected: ${session.packageName} at ${session.startTimeStr}. Reclassifying as NOTIFICATION.")
                        result.add(session.copy(type = PulseEvents.SESSION_NOTIFICATION))
                    }
                }
            } else {
                result.add(session)
            }
        }
        
        return result
    }
}
