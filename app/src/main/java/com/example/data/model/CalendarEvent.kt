package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calendar_events")
data class CalendarEvent(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String,
    val location: String = "",
    val startTime: Long, // timestamp in ms
    val endTime: Long, // timestamp in ms
    val systemEventId: Long? = null // Ref to Android's internal DB if synced
)
