package com.example.chat_app_server.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "private_chat_invites")
data class PrivateChatInviteEntity(
    @Id
    @Column(name = "invite_id", nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(optional = false)
    @JoinColumn(name = "from_user_id", nullable = false)
    val fromUser: UserEntity,

    @ManyToOne(optional = false)
    @JoinColumn(name = "to_user_id", nullable = false)
    val toUser: UserEntity,

    @ManyToOne
    @JoinColumn(name = "room_id")
    val room: RoomEntity? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "invite_status", nullable = false)
    val status: InviteStatus = InviteStatus.PENDING,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "responded_at")
    val respondedAt: Instant? = null
)
