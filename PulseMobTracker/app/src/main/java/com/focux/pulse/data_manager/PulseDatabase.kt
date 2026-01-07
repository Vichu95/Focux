package com.focux.pulse.data_manager

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The main Room Database class for the application.
 * Defines the database configuration and serves as the main access point to the persisted data.
 */
@Database(
    entities = [RawData::class, SystemState::class, AppSession::class, DailyStats::class, AppInfo::class],
    version = 10,
    exportSchema = false
)
abstract class PulseDatabase : RoomDatabase() {

    abstract fun rawDataDao(): RawDataDao
    abstract fun analyticsDao(): AnalyticsDao
    abstract fun appInfoDao(): AppInfoDao

    companion object {
        @Volatile
        private var INSTANCE: PulseDatabase? = null

        /**
         * Returns the singleton instance of the PulseDatabase.
         * Ensures only one instance of the database exists to prevent race conditions.
         */
        fun getDatabase(context: Context): PulseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PulseDatabase::class.java,
                    "pulse_user.db"
                )
                .fallbackToDestructiveMigration() // Useful for dev/onboarding phase to avoid crash on schema change
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

