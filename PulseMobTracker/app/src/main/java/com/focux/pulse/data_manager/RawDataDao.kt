package com.focux.pulse.data_manager

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Data Access Object (DAO) for accessing RawData in the database.
 * Provides methods to insert events and query them by time range.
 */
@Dao
interface RawDataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rawData: RawData)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<RawData>)

    @Query("SELECT * FROM raw_data ORDER BY timestamp DESC")
    suspend fun getAllEvents(): List<RawData>

    /**
     * Retrieves events within a specific time window.
     * Useful for analyzing sessions or daily summaries.
     */
    @Query("SELECT * FROM raw_data WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    suspend fun getEventsBetween(startTime: Long, endTime: Long): List<RawData>

    /**
     * Gets the most recent event recorded in the database.
     * Used by the DataLogger to determine where to start querying for new events (incremental sync).
     */
    @Query("SELECT * FROM raw_data ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastEvent(): RawData?
}

