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
        AppInfoHelper.getLauncherPackages(context)
    }

    private val screenIgnoredApps: Set<String> by lazy {
        val combined = PULSE_IGNORED_APPS + launcherPackages
        Log.d(TAG, "Ignored Apps for Screen (inc. Launchers): $combined")
        combined
    }

    // --- SPLIT LOGIC EXPLANATION ---
    // We split the "Ignored Apps" list to support our Hybrid Metrics Model:
    // 1. AppProcessor: Needs to track EVERYTHING the user does, including Launchers.
    //    So we ONLY ignore strict System UIDs (PULSE_IGNORED_APPS).
    //    Result: Launchers generate SESSION_APP events with valid duration.
    
    // 2. ScreenProcessor: Needs to detect "Passive/Checking" behavior.
    //    If a user unlocks and only stays on the Launcher, that's a "Check" (Unlock No App).
    //    So we MUST ignore Launchers here.
    //    Result: Launchers trigger SESSION_UNLOCK_NOAPP events (for Glance Counting).

    // AppProcessor: Only ignore true system apps. Launchers are VALID apps here (we want their duration).
    private val appProcessor = AppSessionProcessor(ignoredApps = PULSE_IGNORED_APPS)
    
    // ScreenProcessor: Launchers are considered "No App" (part of the glance/check flow).
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
            
            // 2. Identify "Active" sessions for Gap Calculation
            // We ignore "Passive" sessions (Notification) when determining offline streaks.
            // However, GLANCE (Manual Screen On) counts as a break, so we keep it.
            val activeSessions = sortedSessions.filter { 
                it.type != PulseEvents.SESSION_NOTIFICATION
            }
            
            val offlineSessions = mutableListOf<AppSession>()
            
            if (activeSessions.isNotEmpty()) {
                for (i in 0 until activeSessions.size - 1) {
                    val currentSession = activeSessions[i]
                    val nextSession = activeSessions[i+1]
                    
                    val gap = nextSession.startTime - currentSession.endTime
                    
                    if (gap >= com.focux.pulse.utilities.PULSE_MIN_OFFLINE_THRESHOLD_MS) {
                        // Create offline session(s) spanning the entire gap
                        // This will naturally overlap with any passive sessions in between
                        val newOffline = SessionSplitter.createSessions(
                            pkg = "system",
                            start = currentSession.endTime,
                            end = nextSession.startTime,
                            type = PulseEvents.SESSION_OFFLINE
                        )
                        offlineSessions.addAll(newOffline)
                    }
                }
            }
            
            // 3. Merge Active, Passive, and Offline sessions
            val finalSessions = (sortedSessions + offlineSessions).sortedBy { it.startTime }

            Log.d(TAG, "Created ${finalSessions.size} total sessions (Active: ${activeSessions.size}, Offline: ${offlineSessions.size})")
            analyticsDao.insertSessions(finalSessions)
            
            // Delegate to DailySummaryProcessor (use finalSessions!)
            dailyProcessor.updateDailyStats(finalSessions, ignoredApps)
        }
    }
}
