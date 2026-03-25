package com.example.chat_app_server.server

import com.example.chat.proto.ChatEvent
import kotlinx.coroutines.channels.Channel

object RoomManager {

    private val rooms = mutableMapOf<String, MutableList<Channel<ChatEvent>>>()

    fun join(roomId: String, channel: Channel<ChatEvent>) {
        rooms.computeIfAbsent(roomId) { mutableListOf() }.add(channel)
    }

    fun leave(roomId: String, channel: Channel<ChatEvent>) {
        rooms[roomId]?.remove(channel)
    }

    suspend fun broadcast(roomId: String, event: ChatEvent) {
        rooms[roomId]?.forEach {
            it.send(event)
        }
    }
}