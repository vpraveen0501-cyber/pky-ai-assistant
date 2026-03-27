package com.pkyai.android.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_history")
data class ChatHistoryItem(
    @PrimaryKey
    val id: String,
    val text: String,
    val type: String, // "chat" or "document"
    val timestamp: Long
)
