package com.example.chat_app_server.server

import com.example.chat.proto.ChatEvent
import kotlinx.coroutines.channels.Channel

object RoomManager {

    //map [room] has [user] which has list of chatEvent
    private val rooms = mutableMapOf<String, MutableMap<String, Channel<ChatEvent>>>()

    // user joins room
    fun join(roomId: String, userId: String, channel: Channel<ChatEvent>) {
        val room = rooms.computeIfAbsent(roomId) { mutableMapOf() }
        room[userId] = channel
    }

    // User leaves room
    fun leave(roomId: String, userId: String) {
        rooms[roomId]?.remove(userId)

        if (rooms[roomId]?.isEmpty() == true) {
            rooms.remove(roomId)
        }
    }

    // broadcast to all users in room
    suspend fun broadcast(roomId: String, event: ChatEvent) {
        rooms[roomId]?.values?.forEach { channel ->
            channel.send(event)
        }
    }

    // send to specific user
    suspend fun sendToUser(roomId: String, userId: String, event: ChatEvent) {
        rooms[roomId]?.get(userId)?.send(event)
    }
}