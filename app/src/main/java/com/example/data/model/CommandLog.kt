package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "command_logs")
data class CommandLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val commandText: String,
    val responseSpeech: String,
    val timestamp: Long = System.currentTimeMillis(),
    val detectedAction: String = "NONE", // GMAIL_CHECK, GMAIL_SEND, NONE
    val actionStatus: String = "NONE"   // PENDING, EXECUTED, FAILED, NONE
)
