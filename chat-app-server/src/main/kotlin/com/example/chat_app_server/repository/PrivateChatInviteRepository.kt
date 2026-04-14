package com.example.chat_app_server.repository

import com.example.chat_app_server.entity.InviteStatus
import com.example.chat_app_server.entity.PrivateChatInviteEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PrivateChatInviteRepository : JpaRepository<PrivateChatInviteEntity, UUID> {
    fun findAllByToUserUsernameAndStatus(username: String, status: InviteStatus): List<PrivateChatInviteEntity>
    fun findTopByFromUserUsernameAndToUserUsernameAndStatusOrderByCreatedAtDesc(
        fromUsername: String,
        toUsername: String,
        status: InviteStatus
    ): PrivateChatInviteEntity?
}
