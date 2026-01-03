package com.focux.pulse.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.focux.pulse.data_manager.PulseDatabase
import com.focux.pulse.data_logger.PulseDataLogger

/**
 * Background worker scheduled by WorkManager.
 * Acts as the entry point for periodic data collection.
 * It initializes the Database and delegates the actual logging logic to PulseDataLogger.
 */
class DataCollectionWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val database = PulseDatabase.getDatabase(applicationContext)
            val logger = PulseDataLogger(applicationContext, database.rawDataDao())
            logger.logUsageStats()
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}

