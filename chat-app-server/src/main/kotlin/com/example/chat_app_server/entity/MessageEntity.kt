package com.example.chat_app_server.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "messages")
data class MessageEntity(
    @Id
    @Column(name = "message_id", nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    val room: RoomEntity,

    @ManyToOne(optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    val sender: UserEntity,

    @Column(name = "content", nullable = false)
    val content: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
