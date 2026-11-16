
package com.example.repositories

import com.example.database.DatabaseFactory
import com.example.models.TranslationResponse
import java.util.UUID

data class TranslationRecipient(
    val userId: UUID,
    val preferredLanguage: String
)

class TranslationRepository {

    fun saveDetectedSourceLanguage(
        messageId: UUID,
        detectedLanguage: String
    ) {
        val sql = """
        UPDATE messages
        SET original_language = ?
        WHERE id = ?
          AND original_language IS NULL
    """.trimIndent()

        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, detectedLanguage)
                statement.setObject(2, messageId)
                statement.executeUpdate()
            }
        }
    }

    private val dataSource
        get() = DatabaseFactory.dataSource

    fun getRecipients(
        conversationId: UUID,
        senderId: UUID
    ): List<TranslationRecipient> {

        val sql = """
            SELECT u.id, u.preferred_language
            FROM conversation_participants cp
            JOIN app_users u ON u.id = cp.user_id
            WHERE cp.conversation_id = ?
              AND cp.user_id <> ?
        """.trimIndent()

        val recipients = mutableListOf<TranslationRecipient>()

        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setObject(1, conversationId)
                statement.setObject(2, senderId)

                statement.executeQuery().use { result ->
                    while (result.next()) {
                        recipients.add(
                            TranslationRecipient(
                                userId = result.getObject(
                                    "id",
                                    UUID::class.java
                                ),
                                preferredLanguage = result.getString(
                                    "preferred_language"
                                )
                            )
                        )
                    }
                }
            }
        }

        return recipients
    }

    fun saveTranslation(
        messageId: UUID,
        recipientId: UUID,
        translatedText: String,
        targetLanguage: String
    ) {
        val sql = """
            INSERT INTO message_translations (
                message_id,
                recipient_id,
                translated_text,
                target_language
            )
            VALUES (?, ?, ?, ?)
            ON CONFLICT (message_id, recipient_id)
            DO UPDATE SET
                translated_text = EXCLUDED.translated_text,
                target_language = EXCLUDED.target_language
        """.trimIndent()

        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setObject(1, messageId)
                statement.setObject(2, recipientId)
                statement.setString(3, translatedText)
                statement.setString(4, targetLanguage)

                statement.executeUpdate()
            }
        }
    }

    fun getTranslation(
        messageId: UUID,
        recipientId: UUID
    ): TranslationResponse? {
        AND m.conversation_id = ?

        val sql = """
            SELECT m.id AS message_id,
                   m.original_text,
                   m.original_language,
                   mt.translated_text,
                   mt.target_language
            FROM message_translations mt
            JOIN messages m ON m.id = mt.message_id
            WHERE mt.message_id = ?
              AND mt.recipient_id = ?
              AND m.is_deleted = FALSE
        """.trimIndent()

        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setObject(1, messageId)
                statement.setObject(2, recipientId)

                statement.executeQuery().use { result ->
                    if (!result.next()) return null

                    return TranslationResponse(
                        messageId = result.getObject(
                            "message_id",
                            UUID::class.java
                        ).toString(),
                        originalText = result.getString(
                            "original_text"
                        ),
                        originalLanguage = result.getString(
                            "original_language"
                        ),
                        translatedText = result.getString(
                            "translated_text"
                        ),
                        targetLanguage = result.getString(
                            "target_language"
                        )
                    )
                }
            }
        }
    }
}
