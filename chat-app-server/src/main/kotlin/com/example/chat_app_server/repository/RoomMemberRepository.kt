package com.example.chat_app_server.repository

import com.example.chat_app_server.entity.MembershipStatus
import com.example.chat_app_server.entity.RoomMemberEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface RoomMemberRepository : JpaRepository<RoomMemberEntity, UUID> {
    fun findByRoomRoomKeyAndUserUsername(roomKey: String, username: String): RoomMemberEntity?
    fun findAllByUserUsernameAndStatus(username: String, status: MembershipStatus): List<RoomMemberEntity>
    fun findAllByRoomRoomKeyAndStatus(roomKey: String, status: MembershipStatus): List<RoomMemberEntity>
}
