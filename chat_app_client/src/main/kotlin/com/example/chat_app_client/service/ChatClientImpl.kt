package com.example.chat_app_client.service

import com.example.chat.proto.ChatEvent
import com.example.chat.proto.ChatMessage
import com.example.chat.proto.ChatServiceGrpc
import com.example.chat.proto.GetMessagesRequest
import com.example.chat.proto.RoomRequest
import com.google.protobuf.Empty
import io.grpc.stub.StreamObserver
import net.devh.boot.grpc.client.inject.GrpcClient
import org.springframework.stereotype.Component
import java.util.Scanner
import java.util.concurrent.CountDownLatch

@Component
class ChatClientImpl {

    @GrpcClient("chatService")
    private lateinit var chatServiceStub: ChatServiceGrpc.ChatServiceStub

    @GrpcClient("chatService")
    private lateinit var blockingChatServiceStub: ChatServiceGrpc.ChatServiceBlockingStub

    fun start() {
        val scanner = Scanner(System.`in`)

        print("Enter your username: ")
        val userId = scanner.nextLine().trim()

        print("Enter room ID: ")
        val roomId = scanner.nextLine().trim()

        val joinLatch = CountDownLatch(1)
        chatServiceStub.joinRoom(
            RoomRequest.newBuilder()
                .setUserId(userId)
                .setRoomId(roomId)
                .build(),
            object : StreamObserver<Empty> {
                override fun onNext(value: Empty) {}

                override fun onError(t: Throwable) {
                    println("Join failed: ${t.message}")
                    joinLatch.countDown()
                }

                override fun onCompleted() {
                    joinLatch.countDown()
                }
            }
        )
        joinLatch.await()

        val responseObserver = object : StreamObserver<ChatEvent> {
            override fun onNext(event: ChatEvent) {
                if (event.hasNewMessage()) {
                    val msg = event.newMessage
                    if (msg.content == PRESENCE_MARKER) {
                        return
                    }
                    if (msg.senderId != userId) {
                        // Use \r to move to the beginning of the line and clear it
                        print("\r" + " ".repeat(100) + "\r") 
                        println("📩 ${msg.senderId}: ${msg.content}")
                        print("> ")
                    }
                }
            }

            override fun onError(t: Throwable) {
                println("\nStream error: ${t.message}")
            }

            override fun onCompleted() {
                println("\nChat stream closed.")
            }
        }

        val requestObserver = chatServiceStub.chatStream(responseObserver)

        if (!sendPresencePing(requestObserver, roomId, userId)) {
            println("Failed to register with the room. Please restart the client.")
            return
        }

        println("\n✅ Joined room: $roomId")
        println("Type 'getMessages' to see history, or 'exit' to quit.\n")

        while (true) {
            print("> ")
            val text = scanner.nextLine()

            if (text.equals("exit", ignoreCase = true)) {
                break
            }

            if (text.equals("getMessages", ignoreCase = true)) {
                try {
                    val request = GetMessagesRequest.newBuilder().setRoomId(roomId).build()
                    val response = blockingChatServiceStub.getMessages(request)
                    println("\n--- Message History for room '$roomId' ---")
                    response.messagesList.takeLast(15).forEach { msg ->
                        println("[${msg.senderId}]: ${msg.content}")
                    }
                    println("--- End of History ---\n")
                } catch (e: Exception) {
                    println("Error fetching messages: ${e.message}")
                }
                continue
            }

            val event = ChatEvent.newBuilder()
                .setNewMessage(
                    ChatMessage.newBuilder()
                        .setRoomId(roomId)
                        .setSenderId(userId)
                        .setContent(text)
                        .build()
                )
                .build()

            requestObserver.onNext(event)
        }

        requestObserver.onCompleted()

        val leaveLatch = CountDownLatch(1)
        chatServiceStub.leaveRoom(
            RoomRequest.newBuilder()
                .setUserId(userId)
                .setRoomId(roomId)
                .build(),
            object : StreamObserver<Empty> {
                override fun onNext(value: Empty) {}

                override fun onError(t: Throwable) {
                    leaveLatch.countDown()
                }

                override fun onCompleted() {
                    leaveLatch.countDown()
                }
            }
        )
        leaveLatch.await()
    }

    private fun sendPresencePing(
        requestObserver: StreamObserver<ChatEvent>,
        roomId: String,
        userId: String
    ): Boolean {
        return try {
            val presenceEvent = ChatEvent.newBuilder()
                .setNewMessage(
                    ChatMessage.newBuilder()
                        .setRoomId(roomId)
                        .setSenderId(userId)
                        .setContent(PRESENCE_MARKER)
                        .build()
                )
                .build()

            // Server only registers a user once it receives an event, so emit a silent one to
            // wire up the bi-directional stream before real messages start flowing.
            requestObserver.onNext(presenceEvent)
            true
        } catch (ex: Exception) {
            println("Stream setup failed: ${ex.message}")
            false
        }
    }

    companion object {
        private const val PRESENCE_MARKER = "__presence_ping__"
    }
}