
package com.example.services

import com.example.repositories.TranslationRepository
import java.util.UUID

class MessageTranslationService(
    private val provider: GoogleTranslationProvider =
        GoogleTranslationProvider(),
    private val repository: TranslationRepository =
        TranslationRepository()
) {

    fun translateForRecipients(
        messageId: UUID,
        conversationId: UUID,
        senderId: UUID,
        originalText: String,
        originalLanguage: String?
    ) {
        val recipients = repository.getRecipients(
            conversationId,
            senderId
        )

        recipients.forEach { recipient ->

            // No translation is needed when the recipient
            // already uses the original language.
            if (
                originalLanguage != null &&
                originalLanguage.equals(
                    recipient.preferredLanguage,
                    ignoreCase = true
                )
            ) {
                return@forEach
            }

            val result = provider.translate(
                result.detectedSourceLanguage?.let { detected ->
                    repository.saveDetectedSourceLanguage(
                        messageId,
                        detected
                    )
                }
                text = originalText,
                targetLanguage = recipient.preferredLanguage,
                sourceLanguage = originalLanguage
            )

            repository.saveTranslation(
                messageId = messageId,
                recipientId = recipient.userId,
                translatedText = result.translatedText,
                targetLanguage = recipient.preferredLanguage
            )
        }
    }
}
