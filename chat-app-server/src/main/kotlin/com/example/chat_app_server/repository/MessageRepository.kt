package com.example.chat_app_server.repository

import com.example.chat_app_server.entity.MessageEntity
import org.springframework.data.repository.CrudRepository
import java.util.UUID

interface MessageRepository : CrudRepository<MessageEntity, UUID> {
    fun findByRoomId(roomId: String): List<MessageEntity>
}