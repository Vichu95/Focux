package com.focux.pulse.data.source

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import android.util.Log
import com.focux.pulse.data.local.entities.PulseEvents
import com.focux.pulse.data.local.entities.RawData
import com.focux.pulse.data.local.dao.RawDataDao
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Responsible for the business logic of collecting usage data.
 * It queries the Android UsageStatsManager and persists relevant events to the local database.
 */
class SystemUsageSource(
    private val context: Context,
    private val rawDataDao: RawDataDao,
    private val analyticsDao: com.focux.pulse.data.local.dao.AnalyticsDao
) {
    companion object {
        // Global lock to prevent multiple workers from running historical collection simultaneously
        private val mutex = Mutex()
    }

    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    /**
     * Performs the data collection sync.
     * 1. Checks for permission.
     * 2. Determines the time range to query (starting from the last recorded event).
     * 3. Queries UsageStatsManager for system events.
     * 4. Filters and maps them to RawData entities.
     * 5. Inserts them into the database.
     *
     * This method is suspended and designed to run on the IO dispatcher.
     */
    suspend fun logUsageStats() = withContext(Dispatchers.IO) {
        if (!hasPermission()) {
            Log.e("SystemUsageSource", "Missing usage stats permission")
            return@withContext
        }

        // Incremental sync: Start from the last event we have, or default to 15 mins ago
        val lastTimestamp = rawDataDao.getLastEvent()?.timestamp ?: (System.currentTimeMillis() - 15 * 60 * 1000)
        val endTime = System.currentTimeMillis()
        
        // Ensure we don't query the future or invalid ranges
        // FIX: Add +1ms to avoid re-fetching the exact last event and burning an ID via INSERT OR IGNORE
        val startTime = if (lastTimestamp >= endTime) endTime - 15 * 60 * 1000 else lastTimestamp + 1

        Log.d("SystemUsageSource", "Querying events from $startTime to $endTime")

        val events = usageStatsManager.queryEvents(startTime, endTime)
        val usageEvents = mutableListOf<UsageEvents.Event>()

        while (events.hasNextEvent()) {
            val event = UsageEvents.Event()
            events.getNextEvent(event)
            usageEvents.add(event)
        }

        // Map system events to our domain entities, filtering out irrelevant types (UNKNOWN)
        val rawDataList = usageEvents.mapNotNull { event ->
            val label = PulseEvents.getLabel(event.eventType)
            if (label != PulseEvents.UNKNOWN) {
                RawData(
                    timestamp = event.timeStamp,
                    eventType = event.eventType,
                    packageName = event.packageName,
                    eventLabel = label,
                    readableTime = com.focux.pulse.utilities.TimeUtils.format(event.timeStamp)
                )
            } else {
                null
            }
        }

        if (rawDataList.isNotEmpty()) {
            val sortedEvents = rawDataList.sortedBy { it.timestamp }
            Log.d("SystemUsageSource", "Inserting ${sortedEvents.size} events (duplicates will be ignored by DB)")
            rawDataDao.insertAll(sortedEvents)
        } else {
            Log.d("SystemUsageSource", "No new events found")
        }
    }

    /**
     * Collects historical usage data from the last 7 days.
     * Only runs if 'history_imported' flag is FALSE (fresh install or after manual reset).
     * Protected by Mutex to ensure we don't have parallel execution churning IDs.
     */
    suspend fun collectHistoricalData() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!hasPermission()) {
                Log.e("SystemUsageSource", "Missing usage stats permission for historical data")
                return@withLock
            }

            // Check explicit flag first - most robust check
            val historyImported = analyticsDao.getState("history_imported")?.toBoolean() ?: false
            if (historyImported) {
                 Log.d("SystemUsageSource", "Historical data collection skipped - Flag 'history_imported' is TRUE")
                 return@withLock
            }

            // Fallback: Check if database is empty (in case state was wiped but not data? unlikely but safe)
            val existingData = rawDataDao.getLastEvent()
            if (existingData != null) {
                Log.d("SystemUsageSource", "Historical data collection skipped - Database is NOT empty")
                // Mark flag as true to avoid checking DB every time
                analyticsDao.updateState(com.focux.pulse.data.local.entities.SystemState("history_imported", "true"))
                return@withLock
            }

            Log.d("SystemUsageSource", "Starting 7-day historical data collection...")

            val endTime = System.currentTimeMillis()
            val startTime = endTime - (7 * 24 * 60 * 60 * 1000L)  // 7 days ago

            val events = usageStatsManager.queryEvents(startTime, endTime)
            val usageEvents = mutableListOf<UsageEvents.Event>()

            while (events.hasNextEvent()) {
                val event = UsageEvents.Event()
                events.getNextEvent(event)
                usageEvents.add(event)
            }

            val rawDataList = usageEvents.mapNotNull { event ->
                val label = PulseEvents.getLabel(event.eventType)
                if (label != PulseEvents.UNKNOWN) {
                    RawData(
                        timestamp = event.timeStamp,
                        eventType = event.eventType,
                        packageName = event.packageName,
                        eventLabel = label,
                        readableTime = com.focux.pulse.utilities.TimeUtils.format(event.timeStamp)
                    )
                } else {
                    null
                }
            }

            // Double-check: Make sure DB is STILL empty (redundant with Mutex but harmless)
            val freshCheck = rawDataDao.getLastEvent()
            if (freshCheck != null) {
                 Log.d("SystemUsageSource", "Historical data collection aborted - data appeared during fetch")
                 analyticsDao.updateState(com.focux.pulse.data.local.entities.SystemState("history_imported", "true"))
                 return@withLock
            }

            if (rawDataList.isNotEmpty()) {
                val sortedEvents = rawDataList.sortedBy { it.timestamp }
                Log.d("SystemUsageSource", "Inserting ${sortedEvents.size} historical events")
                rawDataDao.insertAll(sortedEvents)
            } else {
                Log.d("SystemUsageSource", "No historical events found")
            }
            
            // Mark history as imported!
            analyticsDao.updateState(com.focux.pulse.data.local.entities.SystemState("history_imported", "true"))
        }
    }

    private fun hasPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }
}

