package com.example.chat_app_server.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "users")
data class UserEntity(
    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "username", nullable = false, unique = true)
    val username: String,

    @Column(name = "password_hash", nullable = false)
    val passwordHash: String = "",

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
