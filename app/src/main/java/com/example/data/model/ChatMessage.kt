package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class SenderRole {
    USER,
    ASSISTANT
}

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: SenderRole,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val mediaUri: String? = null,
    val isVideo: Boolean = false,
    val voiceSourceLabel: String? = null
)
