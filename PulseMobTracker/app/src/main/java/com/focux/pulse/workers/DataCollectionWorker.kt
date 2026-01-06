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
            
            // 1. Collect Raw Data (includes historical on first run)
            val logger = PulseDataLogger(applicationContext, database.rawDataDao())
            logger.collectHistoricalData()  // Only runs if DB is empty
            logger.logUsageStats()
            
            // 2. Process Data into Stats
            val processor = com.focux.pulse.data_logger.PulseDataProcessor(
                applicationContext,
                database.rawDataDao(),
                database.analyticsDao(),
                database.appInfoDao()
            )
            processor.processPendingData()
            
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}

