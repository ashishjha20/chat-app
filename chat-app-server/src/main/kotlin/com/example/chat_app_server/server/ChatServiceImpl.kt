package com.example.chat_app_server.server

import com.example.chat.proto.AuthResponse
import com.example.chat.proto.ChatEvent
import com.example.chat.proto.ChatMessage
import com.example.chat.proto.ChatServiceGrpc
import com.example.chat.proto.CreateRoomRequest
import com.example.chat.proto.GetMessagesRequest
import com.example.chat.proto.GetMessagesResponse
import com.example.chat.proto.InviteNotification
import com.example.chat.proto.InviteStatus as ProtoInviteStatus
import com.example.chat.proto.InviteSummary
import com.example.chat.proto.ListPendingInvitesResponse
import com.example.chat.proto.ListRoomsResponse
import com.example.chat.proto.LoginRequest
import com.example.chat.proto.MessageStatus
import com.example.chat.proto.PrivateInviteRequest
import com.example.chat.proto.PrivateInviteResponse
import com.example.chat.proto.RegisterRequest
import com.example.chat.proto.RespondToPrivateInviteRequest
import com.example.chat.proto.RoomRequest
import com.example.chat.proto.RoomResponse
import com.example.chat.proto.RoomSummary
import com.example.chat.proto.RoomType as ProtoRoomType
import com.example.chat.proto.SendMessageRequest
import com.example.chat.proto.SendMessageResponse
import com.example.chat.proto.UserRequest
import com.example.chat_app_server.entity.InviteStatus
import com.example.chat_app_server.entity.MembershipStatus
import com.example.chat_app_server.entity.MessageEntity
import com.example.chat_app_server.entity.PrivateChatInviteEntity
import com.example.chat_app_server.entity.RoomEntity
import com.example.chat_app_server.entity.RoomMemberEntity
import com.example.chat_app_server.entity.RoomType
import com.example.chat_app_server.entity.UserEntity
import com.example.chat_app_server.repository.MessageRepository
import com.example.chat_app_server.repository.PrivateChatInviteRepository
import com.example.chat_app_server.repository.RoomMemberRepository
import com.example.chat_app_server.repository.RoomRepository
import com.example.chat_app_server.repository.UserRepository
import com.google.protobuf.Empty
import com.google.protobuf.Timestamp
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import net.devh.boot.grpc.server.service.GrpcService
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.time.Instant
import java.util.UUID

@GrpcService
class ChatServiceImpl(
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository,
    private val roomRepository: RoomRepository,
    private val roomMemberRepository: RoomMemberRepository,
    private val privateChatInviteRepository: PrivateChatInviteRepository,
    private val presenceRegistry: PresenceRegistry
) : ChatServiceGrpc.ChatServiceImplBase() {

    private val passwordEncoder = BCryptPasswordEncoder()

    override fun register(
        request: RegisterRequest,
        responseObserver: StreamObserver<AuthResponse>
    ) {
        val username = request.username.trim()
        val password = request.password.trim()

        val response = when {
            username.isBlank() || password.isBlank() -> authResponse(false, "", "Username and password are required.")
            userRepository.findByUsername(username) != null -> authResponse(false, "", "Username already exists.")
            else -> {
                val savedUser = userRepository.save(
                    UserEntity(
                        username = username,
                        passwordHash = requireNotNull(passwordEncoder.encode(password))
                    )
                )
                authResponse(true, savedUser.username, "Registration successful.")
            }
        }

        responseObserver.onNext(response)
        responseObserver.onCompleted()
    }

    override fun login(
        request: LoginRequest,
        responseObserver: StreamObserver<AuthResponse>
    ) {
        val user = userRepository.findByUsername(request.username.trim())
        val response = if (user != null && passwordEncoder.matches(request.password.trim(), user.passwordHash)) {
            authResponse(true, user.username, "Login successful.")
        } else {
            authResponse(false, "", "Invalid username or password.")
        }

        responseObserver.onNext(response)
        responseObserver.onCompleted()
    }

    override fun listMyRooms(
        request: UserRequest,
        responseObserver: StreamObserver<ListRoomsResponse>
    ) {
        val rooms = roomMemberRepository
            .findAllByUserUsernameAndStatus(request.username.trim(), MembershipStatus.ACTIVE)
            .map { toRoomSummary(it.room, it.lastSeenAt) }

        responseObserver.onNext(
            ListRoomsResponse.newBuilder().addAllRooms(rooms).build()
        )
        responseObserver.onCompleted()
    }

    override fun listAvailableRooms(
        request: UserRequest,
        responseObserver: StreamObserver<ListRoomsResponse>
    ) {
        val username = request.username.trim()
        val joinedRoomKeys = roomMemberRepository
            .findAllByUserUsernameAndStatus(username, MembershipStatus.ACTIVE)
            .map { it.room.roomKey }
            .toSet()

        val rooms = roomRepository.findAllByTypeOrderByNameAsc(RoomType.PUBLIC)
            .filterNot { joinedRoomKeys.contains(it.roomKey) }
            .map { toRoomSummary(it, null) }

        responseObserver.onNext(
            ListRoomsResponse.newBuilder().addAllRooms(rooms).build()
        )
        responseObserver.onCompleted()
    }

    override fun createRoom(
        request: CreateRoomRequest,
        responseObserver: StreamObserver<RoomResponse>
    ) {
        val user = getRequiredUser(request.username.trim())
        val roomName = request.roomName.trim()
        val response = if (user == null || roomName.isBlank()) {
            RoomResponse.newBuilder().setMessage("Username and room name are required.").build()
        } else {
            val room = roomRepository.save(
                RoomEntity(
                    roomKey = generateUniqueRoomKey(roomName),
                    name = roomName,
                    type = RoomType.PUBLIC,
                    createdBy = user
                )
            )
            ensureMembership(user, room)
            RoomResponse.newBuilder()
                .setRoom(toRoomSummary(room, null))
                .setMessage("Room created.")
                .build()
        }

        responseObserver.onNext(response)
        responseObserver.onCompleted()
    }

    override fun joinRoom(
        request: RoomRequest,
        responseObserver: StreamObserver<RoomResponse>
    ) {
        val user = getRequiredUser(request.userId.trim())
        val room = roomRepository.findByRoomKey(request.roomId.trim())
        val response = if (user == null || room == null) {
            RoomResponse.newBuilder().setMessage("User or room not found.").build()
        } else {
            ensureMembership(user, room)
            RoomResponse.newBuilder()
                .setRoom(toRoomSummary(room, currentLastSeen(room.roomKey, user.username)))
                .setMessage("Joined room.")
                .build()
        }

        responseObserver.onNext(response)
        responseObserver.onCompleted()
    }

    override fun leaveRoom(
        request: RoomRequest,
        responseObserver: StreamObserver<Empty>
    ) {
        val membership = roomMemberRepository.findByRoomRoomKeyAndUserUsername(
            request.roomId.trim(),
            request.userId.trim()
        )
        if (membership != null) {
            roomMemberRepository.save(
                membership.copy(
                    status = MembershipStatus.LEFT,
                    lastSeenAt = Instant.now()
                )
            )
        }
        unbindPresence(request.roomId, request.userId)
        responseObserver.onNext(Empty.getDefaultInstance())
        responseObserver.onCompleted()
    }

    override fun sendMessage(
        request: SendMessageRequest,
        responseObserver: StreamObserver<SendMessageResponse>
    ) {
        val username = request.senderId.trim()
        val roomKey = request.roomId.trim()
        val user = getRequiredUser(username)
        val room = roomRepository.findByRoomKey(roomKey)

        if (user == null || room == null) {
            responseObserver.onError(IllegalArgumentException("User or room not found."))
            return
        }

        ensureMembership(user, room)
        val entity = saveMessage(room, user, request.content)

        responseObserver.onNext(
            SendMessageResponse.newBuilder()
                .setMessage(entity.toProtoMessage())
                .build()
        )
        responseObserver.onCompleted()
    }

    override fun getMessages(
        request: GetMessagesRequest,
        responseObserver: StreamObserver<GetMessagesResponse>
    ) {
        val username = request.username.trim()
        val roomKey = request.roomId.trim()
        val membership = roomMemberRepository.findByRoomRoomKeyAndUserUsername(roomKey, username)
        val lastSeenAt = membership?.lastSeenAt

        val messages = when {
            membership == null -> emptyList()
            request.unreadOnly && lastSeenAt != null ->
                messageRepository.findByRoomRoomKeyAndCreatedAtAfterOrderByCreatedAtAsc(roomKey, lastSeenAt)
            else -> messageRepository.findByRoomRoomKeyOrderByCreatedAtAsc(roomKey)
        }.let { msgs ->
            if (request.limit > 0 && msgs.size > request.limit) msgs.takeLast(request.limit) else msgs
        }.map { it.toProtoMessage() }

        responseObserver.onNext(
            GetMessagesResponse.newBuilder().addAllMessages(messages).build()
        )
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
                runBlocking {
                    while (true) {
                        responseObserver.onNext(channel.receive())
                    }
                }
            } catch (_: Exception) {
            }
        }
        senderThread.start()

        return object : StreamObserver<ChatEvent> {
            override fun onNext(event: ChatEvent) {
                if (event.hasNewMessage()) {
                    val msg = event.newMessage
                    userId = msg.senderId
                    roomId = msg.roomId

                    val user = getRequiredUser(userId) ?: return
                    val room = roomRepository.findByRoomKey(roomId) ?: return
                    ensureMembership(user, room)
                    presenceRegistry.bind(roomId, userId, channel)

                    if (msg.content == PRESENCE_MARKER) {
                        return
                    }

                    val entity = saveMessage(room, user, msg.content)
                    runBlocking {
                        presenceRegistry.broadcast(
                            roomId,
                            ChatEvent.newBuilder()
                                .setNewMessage(entity.toProtoMessage())
                                .build()
                        )
                    }
                }
            }

            override fun onError(t: Throwable) {
                unbindPresence(roomId, userId)
                markLastSeen(roomId, userId)
                channel.close()
                responseObserver.onError(t)
            }

            override fun onCompleted() {
                unbindPresence(roomId, userId)
                markLastSeen(roomId, userId)
                channel.close()
                responseObserver.onCompleted()
            }
        }
    }

    override fun sendPrivateInvite(
        request: PrivateInviteRequest,
        responseObserver: StreamObserver<PrivateInviteResponse>
    ) {
        val fromUser = getRequiredUser(request.fromUser.trim())
        val toUser = getRequiredUser(request.toUser.trim())

        val response = when {
            fromUser == null || toUser == null -> PrivateInviteResponse.newBuilder().setMessage("Both users must exist.").build()
            fromUser.username == toUser.username -> PrivateInviteResponse.newBuilder().setMessage("You cannot invite yourself.").build()
            else -> {
                val directRoom = roomRepository.findByDirectKey(directKey(fromUser.username, toUser.username))
                if (directRoom != null) {
                    ensureMembership(fromUser, directRoom)
                    ensureMembership(toUser, directRoom)
                    PrivateInviteResponse.newBuilder()
                        .setRoom(toRoomSummary(directRoom, currentLastSeen(directRoom.roomKey, fromUser.username)))
                        .setMessage("Direct room already exists.")
                        .build()
                } else {
                    val pending = privateChatInviteRepository
                        .findTopByFromUserUsernameAndToUserUsernameAndStatusOrderByCreatedAtDesc(
                            fromUser.username,
                            toUser.username,
                            InviteStatus.PENDING
                        )

                    val invite = pending ?: privateChatInviteRepository.save(
                        PrivateChatInviteEntity(
                            fromUser = fromUser,
                            toUser = toUser,
                            status = InviteStatus.PENDING
                        )
                    )

                    runBlocking {
                        presenceRegistry.sendToUser(
                            toUser.username,
                            ChatEvent.newBuilder()
                                .setInviteNotification(
                                    InviteNotification.newBuilder()
                                        .setInvite(invite.toProtoInvite())
                                        .setMessage("${fromUser.username} invited you to a private chat.")
                                        .build()
                                )
                                .build()
                        )
                    }

                    PrivateInviteResponse.newBuilder()
                        .setInvite(invite.toProtoInvite())
                        .setMessage("Private invite sent.")
                        .build()
                }
            }
        }

        responseObserver.onNext(response)
        responseObserver.onCompleted()
    }

    override fun listPendingInvites(
        request: UserRequest,
        responseObserver: StreamObserver<ListPendingInvitesResponse>
    ) {
        val invites = privateChatInviteRepository
            .findAllByToUserUsernameAndStatus(request.username.trim(), InviteStatus.PENDING)
            .map { it.toProtoInvite() }

        responseObserver.onNext(
            ListPendingInvitesResponse.newBuilder().addAllInvites(invites).build()
        )
        responseObserver.onCompleted()
    }

    override fun respondToPrivateInvite(
        request: RespondToPrivateInviteRequest,
        responseObserver: StreamObserver<PrivateInviteResponse>
    ) {
        val invite = runCatching { privateChatInviteRepository.findById(UUID.fromString(request.inviteId.trim())) }
            .getOrNull()
            ?.orElse(null)

        val response = if (invite == null || invite.toUser.username != request.username.trim()) {
            PrivateInviteResponse.newBuilder().setMessage("Invite not found.").build()
        } else if (!request.accept) {
            val declined = privateChatInviteRepository.save(
                invite.copy(status = InviteStatus.DECLINED, respondedAt = Instant.now())
            )
            PrivateInviteResponse.newBuilder()
                .setInvite(declined.toProtoInvite())
                .setMessage("Invite declined.")
                .build()
        } else {
            val room = getOrCreateDirectRoom(invite.fromUser, invite.toUser)
            ensureMembership(invite.fromUser, room)
            ensureMembership(invite.toUser, room)
            val accepted = privateChatInviteRepository.save(
                invite.copy(
                    status = InviteStatus.ACCEPTED,
                    room = room,
                    respondedAt = Instant.now()
                )
            )

            runBlocking {
                presenceRegistry.sendToUser(
                    invite.fromUser.username,
                    ChatEvent.newBuilder()
                        .setInviteNotification(
                            InviteNotification.newBuilder()
                                .setInvite(accepted.toProtoInvite())
                                .setMessage("${invite.toUser.username} accepted your private chat invite.")
                                .build()
                        )
                        .build()
                )
            }

            PrivateInviteResponse.newBuilder()
                .setInvite(accepted.toProtoInvite())
                .setRoom(toRoomSummary(room, currentLastSeen(room.roomKey, invite.toUser.username)))
                .setMessage("Invite accepted.")
                .build()
        }

        responseObserver.onNext(response)
        responseObserver.onCompleted()
    }

    private fun authResponse(success: Boolean, username: String, message: String): AuthResponse {
        return AuthResponse.newBuilder()
            .setSuccess(success)
            .setUsername(username)
            .setMessage(message)
            .build()
    }

    private fun saveMessage(room: RoomEntity, user: UserEntity, content: String): MessageEntity {
        return messageRepository.save(
            MessageEntity(
                room = room,
                sender = user,
                content = content.trim(),
                createdAt = Instant.now()
            )
        )
    }

    private fun getRequiredUser(username: String): UserEntity? {
        return userRepository.findByUsername(username)
    }

    private fun getOrCreateDirectRoom(userA: UserEntity, userB: UserEntity): RoomEntity {
        val directKey = directKey(userA.username, userB.username)
        return roomRepository.findByDirectKey(directKey)
            ?: roomRepository.save(
                RoomEntity(
                    roomKey = "direct-$directKey",
                    name = "${userA.username} & ${userB.username}",
                    type = RoomType.PRIVATE_DIRECT,
                    createdBy = userA,
                    directKey = directKey
                )
            )
    }

    private fun directKey(userA: String, userB: String): String {
        return listOf(userA.lowercase(), userB.lowercase()).sorted().joinToString(":")
    }

    private fun generateUniqueRoomKey(roomName: String): String {
        val base = roomName.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "room" }

        var candidate = base
        var index = 1
        while (roomRepository.findByRoomKey(candidate) != null) {
            index += 1
            candidate = "$base-$index"
        }
        return candidate
    }

    private fun ensureMembership(user: UserEntity, room: RoomEntity) {
        val membership = roomMemberRepository.findByRoomRoomKeyAndUserUsername(room.roomKey, user.username)
        if (membership == null) {
            roomMemberRepository.save(RoomMemberEntity(room = room, user = user))
            return
        }

        if (membership.status != MembershipStatus.ACTIVE) {
            roomMemberRepository.save(
                membership.copy(status = MembershipStatus.ACTIVE)
            )
        }
    }

    private fun currentLastSeen(roomKey: String, username: String): Instant? {
        return roomMemberRepository.findByRoomRoomKeyAndUserUsername(roomKey, username)?.lastSeenAt
    }

    private fun markLastSeen(roomKey: String, username: String) {
        val membership = roomMemberRepository.findByRoomRoomKeyAndUserUsername(roomKey, username) ?: return
        if (membership.status == MembershipStatus.ACTIVE) {
            roomMemberRepository.save(membership.copy(lastSeenAt = Instant.now()))
        }
    }

    private fun unbindPresence(roomKey: String, username: String) {
        if (roomKey.isBlank() || username.isBlank()) return
        presenceRegistry.unbind(roomKey, username)
    }

    private fun toRoomSummary(room: RoomEntity, lastSeenAt: Instant?): RoomSummary {
        val unreadCount = if (lastSeenAt == null) {
            messageRepository.findByRoomRoomKeyOrderByCreatedAtAsc(room.roomKey).size
        } else {
            messageRepository.countByRoomRoomKeyAndCreatedAtAfter(room.roomKey, lastSeenAt).toInt()
        }

        val builder = RoomSummary.newBuilder()
            .setRoomId(room.roomKey)
            .setRoomName(room.name)
            .setRoomType(room.type.toProtoRoomType())
            .setUnreadCount(unreadCount)

        lastSeenAt?.let { builder.setLastSeenAt(it.toProtoTimestamp()) }
        return builder.build()
    }

    private fun MessageEntity.toProtoMessage(): ChatMessage {
        return ChatMessage.newBuilder()
            .setMessageId(id.toString())
            .setRoomId(room.roomKey)
            .setSenderId(sender.username)
            .setContent(content)
            .setTimestamp(createdAt.toProtoTimestamp())
            .setStatus(MessageStatus.SENT)
            .build()
    }

    private fun PrivateChatInviteEntity.toProtoInvite(): InviteSummary {
        val builder = InviteSummary.newBuilder()
            .setInviteId(id.toString())
            .setFromUser(fromUser.username)
            .setToUser(toUser.username)
            .setStatus(status.toProtoInviteStatus())
            .setCreatedAt(createdAt.toProtoTimestamp())

        room?.let { builder.setRoomId(it.roomKey) }
        return builder.build()
    }

    private fun RoomType.toProtoRoomType(): ProtoRoomType {
        return when (this) {
            RoomType.PUBLIC -> ProtoRoomType.PUBLIC
            RoomType.PRIVATE_DIRECT -> ProtoRoomType.PRIVATE_DIRECT
        }
    }

    private fun InviteStatus.toProtoInviteStatus(): ProtoInviteStatus {
        return when (this) {
            InviteStatus.PENDING -> ProtoInviteStatus.PENDING
            InviteStatus.ACCEPTED -> ProtoInviteStatus.ACCEPTED
            InviteStatus.DECLINED -> ProtoInviteStatus.DECLINED
        }
    }

    private fun Instant.toProtoTimestamp(): Timestamp {
        return Timestamp.newBuilder()
            .setSeconds(epochSecond)
            .setNanos(nano)
            .build()
    }

    companion object {
        private const val PRESENCE_MARKER = "__presence_ping__"
    }
}
