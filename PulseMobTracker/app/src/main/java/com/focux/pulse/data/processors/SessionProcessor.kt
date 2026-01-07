package com.focux.pulse.data.processors

import android.content.Context
import android.util.Log
import com.focux.pulse.data.local.dao.*
import com.focux.pulse.data.local.entities.*
import com.focux.pulse.utilities.AppInfoHelper
import com.focux.pulse.utilities.PULSE_IGNORED_APPS

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
    }

    private val ignoredApps: Set<String> by lazy {
        val launchers = AppInfoHelper.getLauncherPackages(context)
        PULSE_IGNORED_APPS + launchers
    }

    private val appProcessor = AppSessionProcessor(ignoredApps)
    private val screenProcessor = ScreenSessionProcessor(ignoredApps)
    private val dailyProcessor = DailySummaryProcessor(context, analyticsDao, appInfoDao)

    /**
     * Main processing entry point.
     * Processes APP sessions and SCREEN sessions with SEPARATE bookmarks.
     * This ensures one type doesn't block the other.
     */
    suspend fun processPendingData() {
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
            // FIX: Sort by start time so DB IDs roughly correspond to time order
            val sortedSessions = allSessions.sortedBy { it.startTime }
            
            Log.d(TAG, "Created ${sortedSessions.size} total sessions")
            analyticsDao.insertSessions(sortedSessions)
            
            // Delegate to DailySummaryProcessor
            dailyProcessor.updateDailyStats(sortedSessions)
        }
    }
}
