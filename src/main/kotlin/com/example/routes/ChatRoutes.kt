
package com.example.routes

import com.example.models.ApiError
import com.example.models.SendMessageRequest
import com.example.models.SocketEvent
import com.example.repositories.ConversationRepository
import com.example.repositories.TranslationRepository
import com.example.services.MessageTranslationService
import com.example.services.WebSocketHub
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

private fun ApplicationCall.currentUserId(): UUID? {
    val subject = principal<JWTPrincipal>()
        ?.payload
        ?.subject
        ?: return null

    return runCatching {
        UUID.fromString(subject)
    }.getOrNull()
}

fun Application.conversationRoutes() {

    val repository = ConversationRepository()
    val translationRepository = TranslationRepository()
    val translationService = MessageTranslationService()

    routing {

        authenticate("auth-jwt") {

            route("/conversations") {

                // 1. Get all conversations
                get {
                    val userId = call.currentUserId()

                    if (userId == null) {
                        call.respond(
                            HttpStatusCode.Unauthorized,
                            ApiError("Authentication required.")
                        )
                        return@get
                    }

                    call.respond(
                        repository.getUserConversations(userId)
                    )
                }

                // 2. Get messages in a conversation
                get("/{id}/messages") {

                    val userId = call.currentUserId()

                    val conversationId = call.parameters["id"]
                        ?.let {
                            runCatching {
                                UUID.fromString(it)
                            }.getOrNull()
                        }

                    if (userId == null || conversationId == null) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiError("Invalid user or conversation ID.")
                        )
                        return@get
                    }

                    if (!repository.isParticipant(conversationId, userId)) {
                        call.respond(
                            HttpStatusCode.Forbidden,
                            ApiError("You cannot access this conversation.")
                        )
                        return@get
                    }

                    call.respond(
                        repository.getMessageHistory(conversationId)
                    )
                }

                // 3. Send a message and translate it for recipients
                post("/{id}/messages") {

                    val userId = call.currentUserId()

                    val conversationId = call.parameters["id"]
                        ?.let {
                            runCatching {
                                UUID.fromString(it)
                            }.getOrNull()
                        }

                    if (userId == null || conversationId == null) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiError("Invalid user or conversation ID.")
                        )
                        return@post
                    }

                    if (!repository.isParticipant(conversationId, userId)) {
                        call.respond(
                            HttpStatusCode.Forbidden,
                            ApiError("You cannot send messages to this conversation.")
                        )
                        return@post
                    }

                    val request = try {
                        call.receive<SendMessageRequest>()
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiError("Invalid message request.")
                        )
                        return@post
                    }

                    try {

                        // Save the original message
                        val message = repository.sendMessage(
                            conversationId = conversationId,
                            senderId = userId,
                            request = request
                        )

                        // Translate for recipients
                        try {
                            withContext(Dispatchers.IO) {

                                translationService.translateForRecipients(
                                    messageId = UUID.fromString(message.id),
                                    conversationId = conversationId,
                                    senderId = userId,
                                    originalText = message.originalText,
                                    originalLanguage = message.originalLanguage
                                )
                            }

                        } catch (e: Exception) {

                            call.application.environment.log.error(
                                "Message saved, but translation failed.",
                                e
                            )
                        }

                        // Broadcast the new message to connected users
                        WebSocketHub.broadcast(
                            conversationId,
                            SocketEvent(
                                type = "new_message",
                                conversationId = conversationId.toString(),
                                senderId = userId.toString(),
                                messageId = message.id,
                                originalText = message.originalText,
                                originalLanguage = message.originalLanguage
                            )
                        )

                        call.respond(
                            HttpStatusCode.Created,
                            message
                        )

                    } catch (e: IllegalArgumentException) {

                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiError(
                                e.message ?: "Invalid message."
                            )
                        )

                    } catch (e: Exception) {

                        call.application.environment.log.error(
                            "Failed to send message.",
                            e
                        )

                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ApiError("Unable to send message.")
                        )
                    }
                }

                // 4. Get a message translation
                get("/{conversationId}/messages/{messageId}/translation") {

                    val userId = call.currentUserId()

                    val conversationId =
                        call.parameters["conversationId"]
                            ?.let {
                                runCatching {
                                    UUID.fromString(it)
                                }.getOrNull()
                            }

                    val messageId =
                        call.parameters["messageId"]
                            ?.let {
                                runCatching {
                                    UUID.fromString(it)
                                }.getOrNull()
                            }

                    if (
                        userId == null ||
                        conversationId == null ||
                        messageId == null
                    ) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiError("Invalid IDs or authentication token.")
                        )
                        return@get
                    }

                    if (!repository.isParticipant(conversationId, userId)) {
                        call.respond(
                            HttpStatusCode.Forbidden,
                            ApiError("You cannot access this conversation.")
                        )
                        return@get
                    }

                    val translation = translationRepository.getTranslation(
                        messageId = messageId,
                        recipientId = userId,
                        conversationId = conversationId
                    )

                    if (translation == null) {

                        call.respond(
                            HttpStatusCode.NotFound,
                            ApiError("Translation is not available for this message.")
                        )

                    } else {

                        call.respond(translation)
                    }
                }
            }
        }
    }
}
