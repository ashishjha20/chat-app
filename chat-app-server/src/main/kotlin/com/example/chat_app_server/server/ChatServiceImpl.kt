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

        //creating request to entity
        val entity = MessageEntity(
            messageId = UUID.randomUUID(),
            roomId = request.roomId,
            senderId = request.senderId,
            content = request.content,
            timestamp = Instant.now()
        )

        //save to db
        repository.save(entity)

        //creating proto response from entity
        val msg = ChatMessage.newBuilder()
            .setMessageId(entity.messageId.toString())
            .setRoomId(entity.roomId)
            .setSenderId(entity.senderId)
            .setContent(entity.content)
            .build()


        //creating response
        val response = SendMessageResponse.newBuilder()
            .setMessage(msg)
            .build()


        //sending the response
        responseObserver.onNext(response)
        responseObserver.onCompleted()
    }


    override fun getMessages(
        request: GetMessagesRequest,
        responseObserver: StreamObserver<GetMessagesResponse>
    ) {

        //fetching all  entities for the room from db
        val entities = repository.findByRoomId(request.roomId)
        

        // converting entities to proto messages
        val messages = entities.map {
            ChatMessage.newBuilder()
                .setMessageId(it.messageId.toString())
                .setRoomId(it.roomId)
                .setSenderId(it.senderId)
                .setContent(it.content)
                .build()
        }

        // creating response with list of messages
        val response = GetMessagesResponse.newBuilder()
            .addAllMessages(messages)
            .build()


        // sending the response
        responseObserver.onNext(response)
        responseObserver.onCompleted()
    }


    override fun chatStream(
        responseObserver: StreamObserver<ChatEvent>
    ): StreamObserver<ChatEvent> {


        // Create a channel for sending events to this client
        val channel = Channel<ChatEvent>()
        var userId = ""
        var roomId = ""


        // Start a coroutine to send events from the channel to the client
        //what is coroutine? Coroutine is a lightweight thread that can be suspended 
        //and resumed without blocking the main thread. It allows us to write asynchronous code in a 
        //sequential manner. In this case, we are using a separate thread to continuously 
        //receive events from the channel and send them to the client without blocking the main
        //thread which is handling incoming events from the client.


        //using separate thread to send events to client to avoid blocking the main thread 
        ///which is handling incoming events from client
        val senderThread = Thread {
            try {
                // Continuously receive events from the channel and send to client
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

                //if event has new message, save to db and broadcast to room
                if (event.hasNewMessage()) {
                    val msg = event.newMessage
                    userId = msg.senderId
                    roomId = msg.roomId

                   //creating entity from message
                    val entity = MessageEntity(
                        messageId = UUID.randomUUID(),
                        roomId = msg.roomId,
                        senderId = msg.senderId,
                        content = msg.content,
                        timestamp = Instant.now()
                    )

                    //saving message to db
                    repository.save(entity)

                    //join the user to the room in RoomManager and associate the channel 
                    //with the user for broadcasting messages
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
        RoomManager.join(request.roomId, request.userId, Channel())
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