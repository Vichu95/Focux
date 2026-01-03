package com.focux.pulse.data_manager

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The main Room Database class for the application.
 * Defines the database configuration and serves as the main access point to the persisted data.
 */
@Database(entities = [RawData::class], version = 1, exportSchema = false)
abstract class PulseDatabase : RoomDatabase() {

    abstract fun rawDataDao(): RawDataDao

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
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

