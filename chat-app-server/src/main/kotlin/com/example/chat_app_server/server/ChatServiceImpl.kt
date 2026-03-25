package com.example.chat_app_server.server

import com.example.chat.proto.*
import com.example.chat_app_server.entity.MessageEntity
import com.example.chat_app_server.repository.MessageRepository
import com.google.protobuf.Empty
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import net.devh.boot.grpc.server.service.GrpcService
import java.time.Instant
import java.util.UUID

@GrpcService
class ChatServiceImpl(
    private val repository: MessageRepository
) : ChatServiceGrpc.ChatServiceImplBase() {


    override fun sendMessage(
        request: SendMessageRequest,
        responseObserver: StreamObserver<SendMessageResponse>
    ) {

        val entity = MessageEntity(
            messageId = UUID.randomUUID(),
            roomId = request.roomId,
            senderId = request.senderId,
            content = request.content,
            timestamp = Instant.now()
        )

        repository.save(entity)

        val msg = ChatMessage.newBuilder()
            .setMessageId(entity.messageId.toString())
            .setRoomId(entity.roomId)
            .setSenderId(entity.senderId)
            .setContent(entity.content)
            .build()

        val response = SendMessageResponse.newBuilder()
            .setMessage(msg)
            .build()

        responseObserver.onNext(response)
        responseObserver.onCompleted()
    }


    override fun getMessages(
        request: GetMessagesRequest,
        responseObserver: StreamObserver<GetMessagesResponse>
    ) {

        val messages = repository.findByRoomId(request.roomId)

        val protoList = messages.map {
            ChatMessage.newBuilder()
                .setMessageId(it.messageId.toString())
                .setRoomId(it.roomId)
                .setSenderId(it.senderId)
                .setContent(it.content)
                .build()
        }

        val response = GetMessagesResponse.newBuilder()
            .addAllMessages(protoList)
            .build()

        responseObserver.onNext(response)
        responseObserver.onCompleted()
    }


    override fun chatStream(
        responseObserver: StreamObserver<ChatEvent>
    ): StreamObserver<ChatEvent> {

        val channel = Channel<ChatEvent>()
        var userId = ""
        var roomId = ""


        val senderThread = Thread {
            try {
                kotlinx.coroutines.runBlocking {
                    while (true) {
                        val event = channel.receive()
                        responseObserver.onNext(event)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        senderThread.start()

        return object : StreamObserver<ChatEvent> {

            override fun onNext(event: ChatEvent) {

                if (event.hasNewMessage()) {
                    val msg = event.newMessage
                    userId = msg.senderId
                    roomId = msg.roomId

                   
                    val entity = MessageEntity(
                        messageId = UUID.randomUUID(),
                        roomId = msg.roomId,
                        senderId = msg.senderId,
                        content = msg.content,
                        timestamp = Instant.now()
                    )
                    repository.save(entity)

             
                    RoomManager.join(roomId, userId, channel)

                    // Broadcast the message to everyone in the room
                    runBlocking {
                        RoomManager.broadcast(roomId, event)
                    }
                }
            }

            override fun onError(t: Throwable) {
                RoomManager.leave(roomId, userId)
                channel.close()
                responseObserver.onError(t)
            }

            override fun onCompleted() {
                RoomManager.leave(roomId, userId)
                channel.close()
                responseObserver.onCompleted()
            }
        }
    }


    override fun joinRoom(
        request: RoomRequest,
        responseObserver: StreamObserver<Empty>
    ) {
        println("User ${request.userId} joined room ${request.roomId}")

        responseObserver.onNext(Empty.getDefaultInstance())
        responseObserver.onCompleted()
    }



    override fun leaveRoom(
        request: RoomRequest,
        responseObserver: StreamObserver<Empty>
    ) {
        RoomManager.leave(request.roomId, request.userId)
        println("User ${request.userId} left room ${request.roomId}")

        responseObserver.onNext(Empty.getDefaultInstance())
        responseObserver.onCompleted()
    }

}