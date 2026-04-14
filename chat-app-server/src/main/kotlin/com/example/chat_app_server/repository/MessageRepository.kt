package com.example.chat_app_server.repository

import com.example.chat_app_server.entity.MessageEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

interface MessageRepository : JpaRepository<MessageEntity, UUID> {
    fun findByRoomRoomKeyOrderByCreatedAtAsc(roomKey: String): List<MessageEntity>
    fun findByRoomRoomKeyAndCreatedAtAfterOrderByCreatedAtAsc(roomKey: String, createdAt: Instant): List<MessageEntity>
    fun countByRoomRoomKeyAndCreatedAtAfter(roomKey: String, createdAt: Instant): Long
    fun findTopByRoomRoomKeyOrderByCreatedAtDesc(roomKey: String): MessageEntity?
}
