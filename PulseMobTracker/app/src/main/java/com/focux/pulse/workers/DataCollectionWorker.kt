package com.focux.pulse.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.focux.pulse.data_manager.PulseDatabase
import com.focux.pulse.data_logger.PulseDataLogger

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

