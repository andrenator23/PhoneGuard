package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface IntruderDao {
    @Query("SELECT * FROM intruder_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<IntruderLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: IntruderLog)

    @Query("DELETE FROM intruder_logs WHERE id = :id")
    suspend fun deleteLogById(id: Int)

    @Query("DELETE FROM intruder_logs")
    suspend fun clearAllLogs()
}
