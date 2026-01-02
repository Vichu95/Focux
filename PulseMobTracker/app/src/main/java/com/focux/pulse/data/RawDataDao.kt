package com.focux.pulse.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RawDataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rawData: RawData)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<RawData>)

    @Query("SELECT * FROM raw_data ORDER BY timestamp DESC")
    suspend fun getAllEvents(): List<RawData>

    @Query("SELECT * FROM raw_data WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    suspend fun getEventsBetween(startTime: Long, endTime: Long): List<RawData>
}
