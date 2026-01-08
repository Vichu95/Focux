package com.focux.pulse.data.local.dao

import androidx.room.Dao
import com.focux.pulse.data.local.entities.RawData
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Data Access Object (DAO) for accessing RawData in the database.
 * Provides methods to insert events and query them by time range.
 */
@Dao
interface RawDataDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(rawData: RawData)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
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

    /**
     * Fetches raw events that have an ID greater than the provided lastId.
     * This is crucial for the Data Processor to only fetch and process *new* events.
     */
    @Query("SELECT * FROM raw_data WHERE id > :lastId")
    suspend fun getEventsSince(lastId: Long): List<RawData>

    /**
     * Deletes all raw data. Used for database reset.
     */
    @Query("DELETE FROM raw_data")
    suspend fun deleteAll()

    /**
     * Resets the auto-increment counter for raw_data.
     */
    @Query("DELETE FROM sqlite_sequence WHERE name = 'raw_data'")
    suspend fun resetSequence()

    /**
     * Atomically deletes all raw data and resets the ID sequence.
     */
    @Transaction
    suspend fun clearAllAndReset() {
        deleteAll()
        resetSequence()
    }
}

