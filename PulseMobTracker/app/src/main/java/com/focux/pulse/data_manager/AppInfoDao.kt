package com.focux.pulse.data_manager

import androidx.room.*

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
     * Update app category.
     */
    @Query("UPDATE app_info SET category = :category WHERE packageName = :packageName")
    suspend fun updateCategory(packageName: String, category: String)

    /**
     * Get apps by category.
     */
    @Query("SELECT * FROM app_info WHERE category = :category")
    suspend fun getAppsByCategory(category: String): List<AppInfo>
}
