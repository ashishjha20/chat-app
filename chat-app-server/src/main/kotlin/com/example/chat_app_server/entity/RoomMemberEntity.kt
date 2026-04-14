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
@Table(name = "room_memberships")
data class RoomMemberEntity(
    @Id
    @Column(name = "membership_id", nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    val room: RoomEntity,

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    val user: UserEntity,

    @Enumerated(EnumType.STRING)
    @Column(name = "membership_status", nullable = false)
    val status: MembershipStatus = MembershipStatus.ACTIVE,

    @Column(name = "joined_at", nullable = false)
    val joinedAt: Instant = Instant.now(),

    @Column(name = "last_seen_at")
    val lastSeenAt: Instant? = null
)
