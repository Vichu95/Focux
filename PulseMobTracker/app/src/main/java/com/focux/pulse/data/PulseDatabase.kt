package com.focux.pulse.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [RawData::class], version = 1, exportSchema = false)
abstract class PulseDatabase : RoomDatabase() {

    abstract fun rawDataDao(): RawDataDao

    companion object {
        @Volatile
        private var INSTANCE: PulseDatabase? = null

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
