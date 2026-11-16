
package com.example.routes

import com.example.models.ApiError
import com.example.models.CreateGroupConversationRequest
import com.example.models.CreatePrivateConversationRequest
import com.example.models.SendMessageRequest
import com.example.repositories.ConversationRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.util.UUID

private fun ApplicationCall.currentUserId(): UUID? {
    val subject = principal<JWTPrincipal>()
        ?.payload
        ?.subject ?: return null

    return runCatching { UUID.fromString(subject) }.getOrNull()
}

fun Route.conversationRoutes() {
    val repository = ConversationRepository()

    authenticate("auth-jwt") {
        route("/conversations") {

            // Create or retrieve a private conversation.
            post("/private") {
                val currentUserId = call.currentUserId()

                if (currentUserId == null) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ApiError("Invalid authentication token.")
                    )
                    return@post
                }

                try {
                    val request =
                        call.receive<CreatePrivateConversationRequest>()

                    val otherUserId = UUID.fromString(request.userId)

                    val conversation =
                        repository.createPrivateConversation(
                            currentUserId,
                            otherUserId
                        )

                    call.respond(HttpStatusCode.Created, conversation)

                } catch (e: IllegalArgumentException) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(e.message ?: "Invalid conversation request.")
                    )
                } catch (e: Exception) {
                    call.application.environment.log.error(
                        "Private conversation creation failed",
                        e
                    )
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiError("Could not create private conversation.")
                    )
                }
            }

            // Create a group conversation.
            post("/groups") {
                val currentUserId = call.currentUserId()

                if (currentUserId == null) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ApiError("Invalid authentication token.")
                    )
                    return@post
                }

                try {
                    val request =
                        call.receive<CreateGroupConversationRequest>()

                    val participantIds = request.participantIds.map {
                        UUID.fromString(it)
                    }

                    val conversation =
                        repository.createGroupConversation(
                            currentUserId,
                            request.title,
                            participantIds
                        )

                    call.respond(HttpStatusCode.Created, conversation)

                } catch (e: IllegalArgumentException) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(e.message ?: "Invalid group details.")
                    )
                } catch (e: Exception) {
                    call.application.environment.log.error(
                        "Group creation failed",
                        e
                    )
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiError("Could not create group.")
                    )
                }
            }

            // List conversations belonging to the logged-in user.
            get {
                val currentUserId = call.currentUserId()

                if (currentUserId == null) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ApiError("Invalid authentication token.")
                    )
                    return@get
                }

                call.respond(
                    repository.getUserConversations(currentUserId)
                )
            }

            // List participants in a conversation.
            get("/{id}/participants") {
                val currentUserId = call.currentUserId()
                val conversationId = call.parameters["id"]
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

                if (currentUserId == null || conversationId == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError("Invalid user or conversation ID.")
                    )
                    return@get
                }

                if (!repository.isParticipant(
                        conversationId,
                        currentUserId
                    )
                ) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        ApiError("You are not a participant in this conversation.")
                    )
                    return@get
                }

                call.respond(repository.getParticipants(conversationId))
            }

            // Retrieve message history.
            get("/{id}/messages") {
                val currentUserId = call.currentUserId()
                val conversationId = call.parameters["id"]
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

                if (currentUserId == null || conversationId == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError("Invalid user or conversation ID.")
                    )
                    return@get
                }

                if (!repository.isParticipant(
                        conversationId,
                        currentUserId
                    )
                ) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        ApiError("You cannot access this conversation.")
                    )
                    return@get
                }

                val limit = call.request.queryParameters["limit"]
                    ?.toIntOrNull() ?: 50

                val offset = call.request.queryParameters["offset"]
                    ?.toIntOrNull() ?: 0

                if (limit !in 1..100 || offset < 0) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError("Limit must be 1–100 and offset cannot be negative.")
                    )
                    return@get
                }

                call.respond(
                    repository.getMessageHistory(
                        conversationId,
                        limit,
                        offset
                    )
                )
            }

            // Send a message.
            post("/{id}/messages") {
                val currentUserId = call.currentUserId()
                val conversationId = call.parameters["id"]
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

                if (currentUserId == null || conversationId == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError("Invalid user or conversation ID.")
                    )
                    return@post
                }

                if (!repository.isParticipant(
                        conversationId,
                        currentUserId
                    )
                ) {
                    call.respond(
                        HttpStatusCode.Forbidden,
                        ApiError("You cannot send messages to this conversation.")
                    )
                    return@post
                }

                try {
                    val request = call.receive<SendMessageRequest>()

                    val language = request.originalLanguage?.trim()

                    if (language != null &&
                        !language.matches(
                            Regex("^[a-zA-Z]{2,3}(-[a-zA-Z0-9]{2,8})?$")
                        )
                    ) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ApiError("Invalid language code.")
                        )
                        return@post
                    }

                    val message = repository.sendMessage(
                        conversationId = conversationId,
                        senderId = currentUserId,
                        text = request.text,
                        originalLanguage = language
                    )

                    call.respond(HttpStatusCode.Created, message)

                } catch (e: IllegalArgumentException) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(e.message ?: "Invalid message.")
                    )
                } catch (e: Exception) {
                    call.application.environment.log.error(
                        "Message sending failed",
                        e
                    )
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiError("Could not send message.")
                    )
                }
            }
        }
    }
}
