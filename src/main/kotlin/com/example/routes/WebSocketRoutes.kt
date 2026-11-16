package com.example.routes

import com.example.models.SocketEvent
import com.example.repositories.ConversationRepository
import com.example.repositories.PresenceRepository
import com.example.services.WebSocketHub
import com.auth0.jwt.JWT
import com.example.security.JwtConfig
import io.ktor.server.application.Application
import io.ktor.server.routing.routing
import io.ktor.server.auth.authenticate
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.util.UUID

fun Application.webSocketRoutes() {

    val conversationRepository = ConversationRepository()
    val presenceRepository = PresenceRepository()

    val json = Json {
        ignoreUnknownKeys = true
    }

    routing {
        authenticate("auth-jwt") {

            webSocket("/ws/conversations/{id}") {

                val conversationId = call.parameters["id"]
                    ?.let {
                        runCatching { UUID.fromString(it) }.getOrNull()
                    }

                val authorization =
                    call.request.headers["Authorization"]

                val token = authorization
                    ?.removePrefix("Bearer ")
                    ?.takeIf { authorization.startsWith("Bearer ") }

                val userId = runCatching {
                    token?.let {
                        UUID.fromString(JwtConfig.verifier.verify(it).subject)
                    }
                }.getOrNull()

                if (conversationId == null || userId == null) {
                    close()
                    return@webSocket
                }

                if (!conversationRepository.isParticipant(
                        conversationId,
                        userId
                    )
                ) {
                    close()
                    return@webSocket
                }

                val firstConnection = WebSocketHub.connect(
                    conversationId,
                    userId,
                    this
                )

                try {
                    if (firstConnection) {
                        presenceRepository.updatePresence(userId, true)
                    }

                    WebSocketHub.broadcast(
                        conversationId,
                        SocketEvent(
                            type = "user_online",
                            conversationId = conversationId.toString(),
                            senderId = userId.toString()
                        )
                    )

                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue

                        val clientEvent = runCatching {
                            json.decodeFromString<
                                    com.example.models.ClientSocketEvent
                                    >(frame.readText())
                        }.getOrNull() ?: continue

                        when (clientEvent.type) {
                            "typing_start", "typing_stop" -> {
                                WebSocketHub.broadcast(
                                    conversationId,
                                    SocketEvent(
                                        type = clientEvent.type,
                                        conversationId =
                                            conversationId.toString(),
                                        senderId = userId.toString()
                                    )
                                )
                            }
                        }
                    }

                } finally {
                    val lastConnection = WebSocketHub.disconnect(
                        conversationId,
                        userId,
                        this
                    )

                    if (lastConnection) {
                        presenceRepository.updatePresence(userId, false)
                    }

                    WebSocketHub.broadcast(
                        conversationId,
                        SocketEvent(
                            type = "user_offline",
                            conversationId = conversationId.toString(),
                            senderId = userId.toString()
                        )
                    )
                }
            }
        }
    }
}