package com.focux.pulse.data.local.dao

import androidx.room.*
import com.focux.pulse.data.local.entities.AppInfo
import kotlinx.coroutines.flow.Flow

/**
 * DAO for AppInfo - manages app categories.
 */
@Dao
interface AppInfoDao {
    /**
     * Get app info by package name.
     */
    @Query("SELECT * FROM app_info WHERE packageName = :packageName")
    suspend fun getAppInfo(packageName: String): AppInfo?

    /**
     * Get all apps.
     */
    @Query("SELECT * FROM app_info ORDER BY appName")
    suspend fun getAllApps(): List<AppInfo>

    /**
     * Get all apps as a reactive Flow (for UI observation).
     */
    @Query("SELECT * FROM app_info ORDER BY appName")
    fun getAllAppsFlow(): Flow<List<AppInfo>>

    /**
     * Insert or update app info.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfNotExists(appInfo: AppInfo)

    /**
     * Batch insert apps (ignores existing).
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIfNotExists(apps: List<AppInfo>)

    /**
     * Update app name (useful if name resolution was fixed).
     */
    @Query("UPDATE app_info SET appName = :appName WHERE packageName = :packageName")
    suspend fun updateAppName(packageName: String, appName: String)

    /**
     * Update app category.
     */
    @Query("UPDATE app_info SET category = :category WHERE packageName = :packageName")
    suspend fun updateCategory(packageName: String, category: String)

    /**
     * Get apps by category.
     */
    @Query("SELECT * FROM app_info WHERE category = :category")
    suspend fun getAppsByCategory(category: String): List<AppInfo>
    /**
     * Delete all app info.
     */
    @Query("DELETE FROM app_info")
    suspend fun deleteAll()

    /**
     * Update custom mindful limits for a specific app.
     */
    @Query("UPDATE app_info SET sessionLimitMins = :sessionLimitMins, dailyLimitMins = :dailyLimitMins, dailyOpensLimit = :dailyOpensLimit WHERE packageName = :packageName")
    suspend fun updateAppLimits(packageName: String, sessionLimitMins: Int?, dailyLimitMins: Int?, dailyOpensLimit: Int?)
}
