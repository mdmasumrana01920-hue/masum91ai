package com.example.data.repository

import android.annotation.SuppressLint
import android.content.Context
import com.example.BuildConfig
import com.example.data.db.CalendarEventDao
import com.example.data.db.CommandLogDao
import com.example.data.db.EmailDao
import com.example.data.gemini.*
import com.example.data.model.CalendarEvent
import com.example.data.model.CommandLog
import com.example.data.model.Email
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.Locale

class AssistantRepository(
    private val commandLogDao: CommandLogDao,
    private val emailDao: EmailDao,
    private val calendarEventDao: CalendarEventDao,
    private val context: Context
) {
    val allLogs: Flow<List<CommandLog>> = commandLogDao.getAllLogs()
    val allEmails: Flow<List<Email>> = emailDao.getAllEmails()
    val unreadEmailCount: Flow<Int> = emailDao.getUnreadCount()
    val allLocalEvents: Flow<List<CalendarEvent>> = calendarEventDao.getAllEvents()

    suspend fun insertLog(log: CommandLog) = commandLogDao.insertLog(log)
    suspend fun clearLogs() = commandLogDao.clearLogs()

    suspend fun insertEmail(email: Email) = emailDao.insertEmail(email)
    suspend fun updateEmail(email: Email) = emailDao.updateEmail(email)
    suspend fun clearEmails() = emailDao.clearEmails()

    // Database / ContentProvider Calendar integration
    suspend fun insertCalendarEvent(event: CalendarEvent): Long {
        val insertedId = calendarEventDao.insertEvent(event)
        
        // Parallel sync to native Android Calendar if permissions allow
        if (hasCalendarPermission() && event.systemEventId == null) {
            val systemId = insertNativeCalendarEvent(
                title = event.title,
                description = event.description,
                location = event.location,
                startTime = event.startTime,
                endTime = event.endTime
            )
            if (systemId != null) {
                calendarEventDao.updateEvent(event.copy(id = insertedId.toInt(), systemEventId = systemId))
            }
        }
        return insertedId
    }

    suspend fun deleteCalendarEvent(event: CalendarEvent) {
        calendarEventDao.deleteEvent(event)
        // Try deleting from System Calendar ifSynced
        if (hasCalendarPermission() && event.systemEventId != null) {
            deleteNativeCalendarEvent(event.systemEventId)
        }
    }

    suspend fun clearCalendarEvents() = calendarEventDao.clearEvents()

    fun hasCalendarPermission(): Boolean {
        val readPerm = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CALENDAR
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val writePerm = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.WRITE_CALENDAR
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        return readPerm && writePerm
    }

    @SuppressLint("Range")
    fun getNativeCalendarEvents(): List<CalendarEvent> {
        val events = mutableListOf<CalendarEvent>()
        if (!hasCalendarPermission()) return events

        try {
            val projection = arrayOf(
                android.provider.CalendarContract.Events._ID,
                android.provider.CalendarContract.Events.TITLE,
                android.provider.CalendarContract.Events.DESCRIPTION,
                android.provider.CalendarContract.Events.DTSTART,
                android.provider.CalendarContract.Events.DTEND,
                android.provider.CalendarContract.Events.EVENT_LOCATION
            )

            // Fetch events in range: within last 7 days and next 30 days
            val now = System.currentTimeMillis()
            val selection = "${android.provider.CalendarContract.Events.DTSTART} >= ? AND ${android.provider.CalendarContract.Events.DTSTART} <= ?"
            val selectionArgs = arrayOf(
                (now - 7 * 86400000L).toString(),
                (now + 30 * 86400000L).toString()
            )

            val cursor = context.contentResolver.query(
                android.provider.CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${android.provider.CalendarContract.Events.DTSTART} ASC"
            )

            cursor?.use {
                val idIdx = it.getColumnIndex(android.provider.CalendarContract.Events._ID)
                val titleIdx = it.getColumnIndex(android.provider.CalendarContract.Events.TITLE)
                val descIdx = it.getColumnIndex(android.provider.CalendarContract.Events.DESCRIPTION)
                val startIdx = it.getColumnIndex(android.provider.CalendarContract.Events.DTSTART)
                val endIdx = it.getColumnIndex(android.provider.CalendarContract.Events.DTEND)
                val locIdx = it.getColumnIndex(android.provider.CalendarContract.Events.EVENT_LOCATION)

                while (it.moveToNext()) {
                    val id = if (idIdx != -1) it.getLong(idIdx) else 0L
                    val title = if (titleIdx != -1) it.getString(titleIdx) ?: "" else ""
                    val description = if (descIdx != -1) it.getString(descIdx) ?: "" else ""
                    val startTime = if (startIdx != -1) it.getLong(startIdx) else 0L
                    val endTime = if (endIdx != -1) it.getLong(endIdx) else 0L
                    val location = if (locIdx != -1) it.getString(locIdx) ?: "" else ""

                    events.add(
                        CalendarEvent(
                            title = title,
                            description = description,
                            location = location,
                            startTime = startTime,
                            endTime = endTime,
                            systemEventId = id
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return events
    }

    private fun insertNativeCalendarEvent(
        title: String,
        description: String,
        location: String,
        startTime: Long,
        endTime: Long
    ): Long? {
        try {
            // Retrieve default database Calendar ID
            var calendarId: Long? = null
            val calCursor = context.contentResolver.query(
                android.provider.CalendarContract.Calendars.CONTENT_URI,
                arrayOf(android.provider.CalendarContract.Calendars._ID),
                null,
                null,
                null
            )
            calCursor?.use {
                if (it.moveToFirst()) {
                    calendarId = it.getLong(0)
                }
            }

            val targetCalendarId = calendarId ?: 1L
            val values = android.content.ContentValues().apply {
                put(android.provider.CalendarContract.Events.CALENDAR_ID, targetCalendarId)
                put(android.provider.CalendarContract.Events.TITLE, title)
                put(android.provider.CalendarContract.Events.DESCRIPTION, description)
                put(android.provider.CalendarContract.Events.EVENT_LOCATION, location)
                put(android.provider.CalendarContract.Events.DTSTART, startTime)
                put(android.provider.CalendarContract.Events.DTEND, endTime)
                put(android.provider.CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
            }

            val uri = context.contentResolver.insert(android.provider.CalendarContract.Events.CONTENT_URI, values)
            return uri?.lastPathSegment?.toLongOrNull()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun deleteNativeCalendarEvent(systemEventId: Long) {
        try {
            val deleteUri = android.content.ContentUris.withAppendedId(
                android.provider.CalendarContract.Events.CONTENT_URI,
                systemEventId
            )
            context.contentResolver.delete(deleteUri, null, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun seedDatabaseIfEmpty() {
        // Seed mock emails if empty
        val list = emailDao.getAllEmails().first()
        if (list.isEmpty()) {
            val sampleEmails = listOf(
                Email(
                    sender = "Google Security Team",
                    senderEmail = "no-reply@accounts.google.com",
                    subject = "New sign-in on Android Device",
                    body = "We noticed a new login to your Google account from an Android device in New York. If this was you, no action is needed.",
                    isRead = false,
                    timestamp = System.currentTimeMillis() - 3600000 * 2
                ),
                Email(
                    sender = "M. Masum Rana",
                    senderEmail = "rana.masum@techcorp.com",
                    subject = "Project Aura Assistant Launch",
                    body = "Hey team, the background voice assistant is ready for launch testing today. Let me know if you are able to perform Gmail trigger commands!",
                    isRead = false,
                    timestamp = System.currentTimeMillis() - 3600000 * 8
                ),
                Email(
                    sender = "Jane Smith",
                    senderEmail = "jane@creativeagency.co",
                    subject = "Design Portfolio specs",
                    body = "Hi! Can we meet at 3:00 PM today to go over the new Material 3 design spec? Talk soon.",
                    isRead = true,
                    timestamp = System.currentTimeMillis() - 3600000 * 24
                )
            )
            for (email in sampleEmails) {
                emailDao.insertEmail(email)
            }
        }

        // Seed mock calendar events if empty
        val eventsList = calendarEventDao.getAllEvents().first()
        if (eventsList.isEmpty()) {
            val nowTime = System.currentTimeMillis()
            val sampleEvents = listOf(
                CalendarEvent(
                    title = "✨ Project Aura Assistant Kickoff",
                    description = "Sync with Masum Rana and development team about Room and Gemini calendar integrations.",
                    location = "Meeting Room A / Discord Portal",
                    startTime = nowTime + 3600000 * 2, // In 2 hours
                    endTime = nowTime + 3600000 * 3
                ),
                CalendarEvent(
                    title = "🍔 Lunch with Rana (Design Specs)",
                    description = "Discuss Material Design 3 and responsive layouts over food.",
                    location = "TechCorp Cafeteria",
                    startTime = nowTime + 3600000 * 22, // In 22 hours
                    endTime = nowTime + 3600000 * 23
                ),
                CalendarEvent(
                    title = "💡 Technical Architecture Alignment",
                    description = "Review Room database migrations, Android Calendar permissions, and fallback models.",
                    location = "Conference Hall B",
                    startTime = nowTime + 3600000 * 48, // In 2 days
                    endTime = nowTime + 3600000 * 50
                )
            )
            for (event in sampleEvents) {
                calendarEventDao.insertEvent(event)
            }
        }
    }

    suspend fun getGeminiResponse(userCommand: String): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return "Error: Gemini API Key is missing. Please configure GEMINI_API_KEY in the Secrets panel in AI Studio."
        }

        val request = GenerateContentRequest(
            contents = listOf(
                Content(parts = listOf(Part(text = userCommand)))
            ),
            systemInstruction = Content(
                parts = listOf(
                    Part(
                        text = """
                        You are a smart background voice/text assistant for an Android phone.
                        You will receive short verbal or typed commands from the user (such as checking emails, checking calendar events, adding appointments, general questions, or conversational chat).
                        Analyze the command and return a structured, natural voice assistant response.
                        
                        GUIDELINES:
                        1. Always reply in a natural, friendly, and helpful tone.
                        2. If the user speaks or types in Bengali (e.g., using Bengali characters or romanticized Bengali words like 'email check koro' or 'appointment dekho'), you MUST respond in Bengali. If they speak or type in English, respond in English.
                        3. Keep your spoken responses short, concise, and conversational, as you are a voice assistant (maximum 1-2 sentences, easy to read aloud).
                        
                        INTENT-BASED TRIGGERS:
                        4. Detect user intent for Gmail. If the user wants to check, read, see, search or browse emails or inbox, you MUST append the exact tag '[ACTION: GMAIL_CHECK]' at the very end of your response (after a space, e.g. "I'm checking your inbox right now. [ACTION: GMAIL_CHECK]").
                        5. If the user wants to write, send, compose, draft or dispatch an email, you MUST append the exact tag '[ACTION: GMAIL_SEND]' at the very end of your response (after a space, e.g. "Sure, preparing to send that email. [ACTION: GMAIL_SEND]").
                        
                        6. Detect user intent for Calendar:
                           a. If the user wants to check, list, view, find, query, or see upcoming appointments, schedule, or meetings, you MUST append the exact tag '[ACTION: CALENDAR_CHECK]' at the very end of your response (e.g., "Reviewing your calendar right now. [ACTION: CALENDAR_CHECK]").
                           b. If the user wants to add, create, schedule, make, book, or schedule a new event or reminder, you MUST append the exact tag '[ACTION: CALENDAR_ADD]' at the very end of your response (e.g., "Let's create that appointment for you. [ACTION: CALENDAR_ADD]").
                           c. If the user wants to view details or retrieve information about a specific event or meeting, you MUST append the exact tag '[ACTION: CALENDAR_DETAIL]' at the very end of your response (e.g., "Opening event details. [ACTION: CALENDAR_DETAIL]").
                        
                        Ensure you only append these exact tags, and do not use other formats. Keep response concise, elegant, and direct.
                        """.trimIndent()
                    )
                )
            )
        )

        return try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text 
                ?: "I couldn't generate a response. Please try again."
        } catch (e: Exception) {
            "Error contacting assistant service: ${e.localizedMessage ?: e.message}"
        }
    }
}
