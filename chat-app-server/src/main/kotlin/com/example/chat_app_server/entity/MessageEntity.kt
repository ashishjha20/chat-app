package com.example.chat_app_server.entity

import jakarta.persistence.Table
import jakarta.persistence.Entity
import jakarta.persistence.Id
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "chat_messages")
data class MessageEntity(
    @Id val messageId: UUID,
    val roomId: String,
    val senderId: String,
    val content: String,
    val timestamp: Instant
)