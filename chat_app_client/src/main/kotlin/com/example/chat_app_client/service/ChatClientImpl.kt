package com.example.chat_app_client.service

import com.example.chat.proto.AuthResponse
import com.example.chat.proto.ChatEvent
import com.example.chat.proto.ChatMessage
import com.example.chat.proto.ChatServiceGrpc
import com.example.chat.proto.CreateRoomRequest
import com.example.chat.proto.GetMessagesRequest
import com.example.chat.proto.InviteSummary
import com.example.chat.proto.ListRoomsResponse
import com.example.chat.proto.LoginRequest
import com.example.chat.proto.PrivateInviteRequest
import com.example.chat.proto.RegisterRequest
import com.example.chat.proto.RespondToPrivateInviteRequest
import com.example.chat.proto.RoomRequest
import com.example.chat.proto.RoomSummary
import com.example.chat.proto.UserRequest
import io.grpc.stub.StreamObserver
import net.devh.boot.grpc.client.inject.GrpcClient
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Scanner

@Component
class ChatClientImpl {

    @GrpcClient("chatService")
    private lateinit var asyncStub: ChatServiceGrpc.ChatServiceStub

    @GrpcClient("chatService")
    private lateinit var blockingStub: ChatServiceGrpc.ChatServiceBlockingStub

    private val scanner = Scanner(System.`in`)
    private val timestampFormatter = DateTimeFormatter.ofPattern("dd MMM HH:mm")
        .withZone(ZoneId.systemDefault())

    fun start() {
        while (true) {
            val auth = authenticate() ?: return
            runMainMenu(auth.username)
        }
    }

    private fun authenticate(): AuthResponse? {
        while (true) {
            println("\n1. Login")
            println("2. Register")
            println("3. Exit")
            print("Choose an option: ")

            when (scanner.nextLine().trim()) {
                "1" -> {
                    val response = blockingStub.login(
                        LoginRequest.newBuilder()
                            .setUsername(prompt("Username"))
                            .setPassword(prompt("Password"))
                            .build()
                    )
                    println(response.message)
                    if (response.success) return response
                }

                "2" -> {
                    val response = blockingStub.register(
                        RegisterRequest.newBuilder()
                            .setUsername(prompt("Choose username"))
                            .setPassword(prompt("Choose password"))
                            .build()
                    )
                    println(response.message)
                    if (response.success) return response
                }

                "3" -> return null
                else -> println("Invalid option.")
            }
        }
    }

    private fun runMainMenu(username: String) {
        while (true) {
            println("\nLogged in as $username")
            println("1. Open joined room")
            println("2. Join public room")
            println("3. Create public room")
            println("4. Send private chat invite")
            println("5. View pending invites")
            println("6. Refresh room summary")
            println("7. Logout")
            print("Choose an option: ")

            when (scanner.nextLine().trim()) {
                "1" -> openJoinedRoom(username)
                "2" -> joinPublicRoom(username)
                "3" -> createPublicRoom(username)
                "4" -> sendPrivateInvite(username)
                "5" -> handlePendingInvites(username)
                "6" -> printRoomSummary(username)
                "7" -> return
                else -> println("Invalid option.")
            }
        }
    }

    private fun openJoinedRoom(username: String) {
        val rooms = blockingStub.listMyRooms(UserRequest.newBuilder().setUsername(username).build())
        val selected = chooseRoom(rooms, "joined")
        if (selected != null) {
            enterRoom(username, selected)
        }
    }

    private fun joinPublicRoom(username: String) {
        val rooms = blockingStub.listAvailableRooms(UserRequest.newBuilder().setUsername(username).build())
        val selected = chooseRoom(rooms, "available")
        if (selected == null) return

        val response = blockingStub.joinRoom(
            RoomRequest.newBuilder()
                .setUserId(username)
                .setRoomId(selected.roomId)
                .build()
        )
        println(response.message)
        if (response.hasRoom()) {
            enterRoom(username, response.room)
        }
    }

    private fun createPublicRoom(username: String) {
        val roomName = prompt("Enter room name")
        if (roomName.isBlank()) {
            println("Room name cannot be empty.")
            return
        }

        val response = blockingStub.createRoom(
            CreateRoomRequest.newBuilder()
                .setUsername(username)
                .setRoomName(roomName)
                .build()
        )
        println(response.message)
        if (response.hasRoom()) {
            enterRoom(username, response.room)
        }
    }

    private fun sendPrivateInvite(username: String) {
        val targetUser = prompt("Invite which user")
        if (targetUser.isBlank()) {
            println("Target username is required.")
            return
        }

        val response = blockingStub.sendPrivateInvite(
            PrivateInviteRequest.newBuilder()
                .setFromUser(username)
                .setToUser(targetUser)
                .build()
        )
        println(response.message)
        if (response.hasRoom()) {
            println("Direct room ready: ${response.room.roomName} (${response.room.roomId})")
        }
    }

    private fun handlePendingInvites(username: String) {
        val response = blockingStub.listPendingInvites(
            UserRequest.newBuilder().setUsername(username).build()
        )

        if (response.invitesCount == 0) {
            println("No pending invites.")
            return
        }

        println("\nPending private invites:")
        response.invitesList.forEachIndexed { index, invite ->
            println("${index + 1}. ${invite.fromUser} at ${formatTimestamp(invite.createdAt.seconds, invite.createdAt.nanos)}")
        }

        print("Choose invite number to respond, or press Enter to go back: ")
        val raw = scanner.nextLine().trim()
        if (raw.isBlank()) return

        val selected = raw.toIntOrNull()?.let { response.invitesList.getOrNull(it - 1) }
        if (selected == null) {
            println("Invalid selection.")
            return
        }

        print("Accept invite from ${selected.fromUser}? (y/n): ")
        val accept = scanner.nextLine().trim().equals("y", ignoreCase = true)
        val inviteResponse = blockingStub.respondToPrivateInvite(
            RespondToPrivateInviteRequest.newBuilder()
                .setInviteId(selected.inviteId)
                .setUsername(username)
                .setAccept(accept)
                .build()
        )

        println(inviteResponse.message)
        if (accept && inviteResponse.hasRoom()) {
            enterRoom(username, inviteResponse.room)
        }
    }

    private fun printRoomSummary(username: String) {
        val response = blockingStub.listMyRooms(UserRequest.newBuilder().setUsername(username).build())
        if (response.roomsCount == 0) {
            println("You have not joined any rooms yet.")
            return
        }

        println("\nMy rooms:")
        response.roomsList.forEachIndexed { index, room ->
            val lastSeen = if (room.hasLastSeenAt()) {
                formatTimestamp(room.lastSeenAt.seconds, room.lastSeenAt.nanos)
            } else {
                "never"
            }
            println("${index + 1}. ${room.roomName} [${room.roomId}] unread=${room.unreadCount}, lastSeen=$lastSeen")
        }
    }

    private fun enterRoom(username: String, room: RoomSummary) {
        println("\nEntering ${room.roomName} (${room.roomId})")
        printUnreadMessages(username, room.roomId)

        val responseObserver = object : StreamObserver<ChatEvent> {
            override fun onNext(event: ChatEvent) {
                when {
                    event.hasNewMessage() -> renderIncomingMessage(username, event.newMessage)
                    event.hasInviteNotification() -> {
                        print("\r" + " ".repeat(120) + "\r")
                        println("[Invite] ${event.inviteNotification.message}")
                        print("> ")
                    }
                }
            }

            override fun onError(t: Throwable) {
                println("\nStream error: ${t.message}")
            }

            override fun onCompleted() {
                println("\nRoom stream closed.")
            }
        }

        val requestObserver = asyncStub.chatStream(responseObserver)
        sendPresencePing(requestObserver, room.roomId, username)

        println("Commands: `/history`, `/unread`, `/exit`")
        while (true) {
            print("> ")
            val text = scanner.nextLine()

            when {
                text.equals("/exit", ignoreCase = true) -> break
                text.equals("/history", ignoreCase = true) -> printRecentMessages(username, room.roomId)
                text.equals("/unread", ignoreCase = true) -> printUnreadMessages(username, room.roomId)
                text.isBlank() -> Unit
                else -> {
                    requestObserver.onNext(
                        ChatEvent.newBuilder()
                            .setNewMessage(
                                ChatMessage.newBuilder()
                                    .setRoomId(room.roomId)
                                    .setSenderId(username)
                                    .setContent(text)
                                    .build()
                            )
                            .build()
                    )
                }
            }
        }

        requestObserver.onCompleted()
    }

    private fun printUnreadMessages(username: String, roomId: String) {
        val response = blockingStub.getMessages(
            GetMessagesRequest.newBuilder()
                .setUsername(username)
                .setRoomId(roomId)
                .setUnreadOnly(true)
                .setLimit(50)
                .build()
        )

        if (response.messagesCount == 0) {
            println("No unread messages.")
            return
        }

        println("\nUnread messages:")
        response.messagesList.forEach { printMessage(it) }
    }

    private fun printRecentMessages(username: String, roomId: String) {
        val response = blockingStub.getMessages(
            GetMessagesRequest.newBuilder()
                .setUsername(username)
                .setRoomId(roomId)
                .setUnreadOnly(false)
                .setLimit(20)
                .build()
        )

        if (response.messagesCount == 0) {
            println("No messages yet.")
            return
        }

        println("\nRecent messages:")
        response.messagesList.forEach { printMessage(it) }
    }

    private fun renderIncomingMessage(username: String, msg: ChatMessage) {
        if (msg.content == PRESENCE_MARKER) return
        if (msg.senderId == username) return

        print("\r" + " ".repeat(120) + "\r")
        printMessage(msg)
        print("> ")
    }

    private fun printMessage(message: ChatMessage) {
        val prefix = formatTimestamp(message.timestamp.seconds, message.timestamp.nanos)
        println("[$prefix] ${message.senderId}: ${message.content}")
    }

    private fun chooseRoom(response: ListRoomsResponse, label: String): RoomSummary? {
        if (response.roomsCount == 0) {
            println("No $label rooms found.")
            return null
        }

        println("\n$label rooms:")
        response.roomsList.forEachIndexed { index, room ->
            println("${index + 1}. ${room.roomName} [${room.roomId}] unread=${room.unreadCount}")
        }

        print("Choose room number, or press Enter to go back: ")
        val raw = scanner.nextLine().trim()
        if (raw.isBlank()) return null
        return raw.toIntOrNull()?.let { response.roomsList.getOrNull(it - 1) }.also {
            if (it == null) println("Invalid selection.")
        }
    }

    private fun sendPresencePing(
        requestObserver: StreamObserver<ChatEvent>,
        roomId: String,
        username: String
    ) {
        requestObserver.onNext(
            ChatEvent.newBuilder()
                .setNewMessage(
                    ChatMessage.newBuilder()
                        .setRoomId(roomId)
                        .setSenderId(username)
                        .setContent(PRESENCE_MARKER)
                        .build()
                )
                .build()
        )
    }

    private fun prompt(label: String): String {
        print("$label: ")
        return scanner.nextLine().trim()
    }

    private fun formatTimestamp(seconds: Long, nanos: Int): String {
        return timestampFormatter.format(Instant.ofEpochSecond(seconds, nanos.toLong()))
    }

    companion object {
        private const val PRESENCE_MARKER = "__presence_ping__"
    }
}
