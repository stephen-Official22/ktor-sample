package com.example.models

import kotlinx.serialization.Serializable

@Serializable
data class SocketEvent(
    val type: String,
    val conversationId: String,
    val senderId: String? = null,
    val messageId: String? = null,
    val originalText: String? = null,
    val originalLanguage: String? = null,
    val translatedText: String? = null,
    val targetLanguage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class ClientSocketEvent(
    val type: String
)