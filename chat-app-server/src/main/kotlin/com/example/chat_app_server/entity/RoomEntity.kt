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
@Table(name = "rooms")
data class RoomEntity(
    @Id
    @Column(name = "room_id", nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "room_key", nullable = false, unique = true)
    val roomKey: String,

    @Column(name = "name", nullable = false)
    val name: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "room_type", nullable = false)
    val type: RoomType = RoomType.PUBLIC,

    @ManyToOne
    @JoinColumn(name = "created_by")
    val createdBy: UserEntity? = null,

    @Column(name = "direct_key", unique = true)
    val directKey: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
