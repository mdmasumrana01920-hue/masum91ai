package com.example.data.db

import androidx.room.*
import com.example.data.model.CalendarEvent
import com.example.data.model.CommandLog
import com.example.data.model.Email
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarEventDao {
    @Query("SELECT * FROM calendar_events ORDER BY startTime ASC")
    fun getAllEvents(): Flow<List<CalendarEvent>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: CalendarEvent): Long

    @Update
    suspend fun updateEvent(event: CalendarEvent)

    @Delete
    suspend fun deleteEvent(event: CalendarEvent)

    @Query("DELETE FROM calendar_events")
    suspend fun clearEvents()
}

@Dao
interface CommandLogDao {
    @Query("SELECT * FROM command_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<CommandLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: CommandLog)

    @Query("DELETE FROM command_logs")
    suspend fun clearLogs()
}

@Dao
interface EmailDao {
    @Query("SELECT * FROM emails WHERE folder = :folder ORDER BY timestamp DESC")
    fun getEmailsByFolder(folder: String): Flow<List<Email>>

    @Query("SELECT * FROM emails ORDER BY timestamp DESC")
    fun getAllEmails(): Flow<List<Email>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmail(email: Email)

    @Update
    suspend fun updateEmail(email: Email)

    @Query("SELECT COUNT(*) FROM emails WHERE folder = 'INBOX' AND isRead = 0")
    fun getUnreadCount(): Flow<Int>

    @Query("DELETE FROM emails")
    suspend fun clearEmails()
}
