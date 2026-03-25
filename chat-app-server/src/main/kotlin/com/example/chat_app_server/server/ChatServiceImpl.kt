package com.example.chat_app_server.server

import com.example.chat.proto.*
import com.example.chat_app_server.entity.MessageEntity
import com.example.chat_app_server.repository.MessageRepository
import io.grpc.stub.StreamObserver

import org.springframework.grpc.server.service.GrpcService;
import java.time.Instant
import java.util.UUID



@GrpcService
class ChatServiceImpl(
    private val repository: MessageRepository
) : ChatServiceGrpc.ChatServiceImplBase() {

    override fun sendMessage(request: SendMessageRequest, responseObserver: StreamObserver<SendMessageResponse>) {

    }

    override fun getMessages(request: GetMessagesRequest, responseObserver: StreamObserver<GetMessagesResponse>) {
        super.getMessages(request, responseObserver)
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