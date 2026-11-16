
package com.example.models

import kotlinx.serialization.Serializable

@Serializable
data class CreatePrivateConversationRequest(
    val userId: String
)

@Serializable
data class CreateGroupConversationRequest(
    val title: String,
    val participantIds: List<String>
)

@Serializable
data class SendMessageRequest(
    val text: String,
    val originalLanguage: String? = null
)

@Serializable
data class ConversationResponse(
    val id: String,
    val title: String?,
    val isGroup: Boolean,
    val createdBy: String?,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class ConversationParticipantResponse(
    val userId: String,
    val username: String,
    val fullName: String,
    val preferredLanguage: String,
    val isOnline: Boolean
)

@Serializable
data class ChatMessageResponse(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val originalText: String,
    val originalLanguage: String?,
    val createdAt: String
)
