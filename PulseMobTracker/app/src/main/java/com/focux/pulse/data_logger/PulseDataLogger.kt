package com.focux.pulse.data_logger

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import android.util.Log
import com.focux.pulse.data_manager.PulseEvents
import com.focux.pulse.data_manager.RawData
import com.focux.pulse.data_manager.RawDataDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Responsible for the business logic of collecting usage data.
 * It queries the Android UsageStatsManager and persists relevant events to the local database.
 */
class PulseDataLogger(
    private val context: Context,
    private val rawDataDao: RawDataDao
) {
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
            Log.e("PulseDataLogger", "Missing usage stats permission")
            return@withContext
        }

        // Incremental sync: Start from the last event we have, or default to 15 mins ago
        val lastTimestamp = rawDataDao.getLastEvent()?.timestamp ?: (System.currentTimeMillis() - 15 * 60 * 1000)
        val endTime = System.currentTimeMillis()
        
        // Ensure we don't query the future or invalid ranges
        val startTime = if (lastTimestamp >= endTime) endTime - 15 * 60 * 1000 else lastTimestamp

        Log.d("PulseDataLogger", "Querying events from $startTime to $endTime")

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
                    eventLabel = label
                )
            } else {
                null
            }
        }

        // Deduplicate: Filter out events that already exist in the database
        // A duplicate has the same timestamp, eventType, and packageName
        val existingEvents = rawDataDao.getEventsBetween(startTime, endTime)
        val existingSet = existingEvents.map { "${it.timestamp}_${it.eventType}_${it.packageName}" }.toSet()
        
        val uniqueNewEvents = rawDataList.filter { event ->
            "${event.timestamp}_${event.eventType}_${event.packageName}" !in existingSet
        }

        if (uniqueNewEvents.isNotEmpty()) {
            Log.d("PulseDataLogger", "Inserting ${uniqueNewEvents.size} unique events (${rawDataList.size - uniqueNewEvents.size} duplicates filtered)")
            rawDataDao.insertAll(uniqueNewEvents)
        } else {
            Log.d("PulseDataLogger", "No new unique events found")
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

