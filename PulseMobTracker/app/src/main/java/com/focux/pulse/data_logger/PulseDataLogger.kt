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

class PulseDataLogger(
    private val context: Context,
    private val rawDataDao: RawDataDao
) {
    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    suspend fun logUsageStats() = withContext(Dispatchers.IO) {
        if (!hasPermission()) {
            Log.e("PulseDataLogger", "Missing usage stats permission")
            return@withContext
        }

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

        if (rawDataList.isNotEmpty()) {
            Log.d("PulseDataLogger", "Inserting ${rawDataList.size} events")
            rawDataDao.insertAll(rawDataList)
        } else {
            Log.d("PulseDataLogger", "No relevant events found")
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

