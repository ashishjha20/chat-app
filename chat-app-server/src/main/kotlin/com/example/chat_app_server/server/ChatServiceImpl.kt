package com.example.chat_app_server.server

import com.example.chat.proto.*
import com.example.chat_app_server.entity.MessageEntity
import com.example.chat_app_server.repository.MessageRepository
import io.grpc.stub.StreamObserver

import net.devh.boot.grpc.server.service.GrpcService
import java.time.Instant
import java.util.UUID



@GrpcService
class ChatServiceImpl(
    private val repository: MessageRepository
) : ChatServiceGrpc.ChatServiceImplBase() {

    override fun sendMessage(request: SendMessageRequest, responseObserver: StreamObserver<SendMessageResponse>) {
        super.sendMessage(request, responseObserver)

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

    override fun getMessages(request: GetMessagesRequest, responseObserver: StreamObserver<GetMessagesResponse>) {
        super.getMessages(request, responseObserver)

        val messages =repository.findByRoomId(request.roomId)

        val protoList = messages.map {
            ChatMessage.newBuilder()
                .setMessageId(it.messageId.toString())
                .setRoomId(it.roomId)
                .setSenderId(it.senderId)
                .setContent(it.content)
                .build()
        }

        responseObserver.onNext(GetMessagesResponse.
                        newBuilder().
                        addAllMessages(protoList).
                        build())
        responseObserver.onCompleted()

    }

    override fun chatStream(responseObserver: StreamObserver<ChatEvent>): StreamObserver<ChatEvent> {
        return super.chatStream(responseObserver)
    }
    override fun joinRoom(request: RoomRequest?, responseObserver: StreamObserver<Empty?>?) {
        super.joinRoom(request, responseObserver)
    }

    override fun leaveRoom(request: RoomRequest?, responseObserver: StreamObserver<Empty?>?) {
        super.leaveRoom(request, responseObserver)
    }


}