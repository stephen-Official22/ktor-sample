

package com.example.models

import kotlinx.serialization.Serializable

@Serializable
data class TranslationResponse(
    val messageId: String,
    val originalText: String,
    val originalLanguage: String?,
    val translatedText: String,
    val targetLanguage: String
)

@Serializable
data class TranslationResult(
    val translatedText: String,
    val detectedSourceLanguage: String?
)

@Serializable
data class TranslationError(
    val error: String
)
