package com.example.chat_app_server.server

import com.example.chat.proto.ChatEvent
import kotlinx.coroutines.channels.Channel
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class PresenceRegistry {

    private val activeStreams = ConcurrentHashMap<String, ConcurrentHashMap<String, Channel<ChatEvent>>>()
    private val userStreams = ConcurrentHashMap<String, Channel<ChatEvent>>()

    fun bind(roomKey: String, username: String, channel: Channel<ChatEvent>) {
        activeStreams.computeIfAbsent(roomKey) { ConcurrentHashMap() }[username] = channel
        userStreams[username] = channel
    }

    fun unbind(roomKey: String, username: String) {
        activeStreams[roomKey]?.remove(username)
        if (activeStreams[roomKey]?.isEmpty() == true) {
            activeStreams.remove(roomKey)
        }
        userStreams.remove(username)
    }

    suspend fun broadcast(roomKey: String, event: ChatEvent) {
        activeStreams[roomKey]?.values?.forEach { channel ->
            channel.send(event)
        }
    }

    suspend fun sendToUser(username: String, event: ChatEvent) {
        userStreams[username]?.send(event)
    }
}
