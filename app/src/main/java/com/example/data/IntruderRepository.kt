package com.example.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class IntruderRepository(private val intruderDao: IntruderDao) {

    val allLogs: Flow<List<IntruderLog>> = intruderDao.getAllLogs()

    suspend fun insertLog(log: IntruderLog) = withContext(Dispatchers.IO) {
        intruderDao.insertLog(log)
    }

    suspend fun deleteLog(log: IntruderLog) = withContext(Dispatchers.IO) {
        try {
            val file = File(log.filePath)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            Log.e("IntruderRepository", "Failed to delete file: ${log.filePath}", e)
        }
        intruderDao.deleteLogById(log.id)
    }

    suspend fun clearAllLogs(logs: List<IntruderLog>) = withContext(Dispatchers.IO) {
        for (log in logs) {
            try {
                val file = File(log.filePath)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                Log.e("IntruderRepository", "Failed to delete file: ${log.filePath}", e)
            }
        }
        intruderDao.clearAllLogs()
    }
}
