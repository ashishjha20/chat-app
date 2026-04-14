package com.example.chat_app_server.repository

import com.example.chat_app_server.entity.RoomEntity
import com.example.chat_app_server.entity.RoomType
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface RoomRepository : JpaRepository<RoomEntity, UUID> {
    fun findByRoomKey(roomKey: String): RoomEntity?
    fun findByDirectKey(directKey: String): RoomEntity?
    fun findAllByTypeOrderByNameAsc(type: RoomType): List<RoomEntity>
}
