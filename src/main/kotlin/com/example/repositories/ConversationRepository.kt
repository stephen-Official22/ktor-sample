
package com.example.repositories

import com.example.database.DatabaseFactory
import com.example.models.ChatMessageResponse
import com.example.models.ConversationParticipantResponse
import com.example.models.ConversationResponse
import java.util.UUID

class ConversationRepository {

    private val dataSource
        get() = DatabaseFactory.dataSource

    fun createPrivateConversation(
        currentUserId: UUID,
        otherUserId: UUID
    ): ConversationResponse {
        require(currentUserId != otherUserId) {
            "You cannot create a conversation with yourself."
        }

        dataSource.connection.use { connection ->
            connection.autoCommit = false

            try {
                // Check that the other user exists.
                val userExists = connection.prepareStatement(
                    "SELECT 1 FROM app_users WHERE id = ?"
                ).use { statement ->
                    statement.setObject(1, otherUserId)
                    statement.executeQuery().use { it.next() }
                }

                require(userExists) { "The selected user does not exist." }

                // Reuse an existing private conversation if one exists.
                val existingSql = """
                    SELECT c.id
                    FROM conversations c
                    JOIN conversation_participants p
                      ON p.conversation_id = c.id
                    WHERE c.is_group = FALSE
                      AND p.user_id IN (?, ?)
                    GROUP BY c.id
                    HAVING COUNT(DISTINCT p.user_id) = 2
                       AND COUNT(*) = 2
                    LIMIT 1
                """.trimIndent()

                val existingId = connection.prepareStatement(existingSql).use {
                    it.setObject(1, currentUserId)
                    it.setObject(2, otherUserId)

                    it.executeQuery().use { result ->
                        if (result.next()) {
                            result.getObject("id", UUID::class.java)
                        } else {
                            null
                        }
                    }
                }

                if (existingId != null) {
                    connection.commit()
                    return getConversation(connection, existingId)
                        ?: error("Existing conversation could not be loaded.")
                }

                val conversationId = connection.prepareStatement(
                    """
                    INSERT INTO conversations (is_group, created_by)
                    VALUES (FALSE, ?)
                    RETURNING id
                    """.trimIndent()
                ).use {
                    it.setObject(1, currentUserId)
                    it.executeQuery().use { result ->
                        check(result.next())
                        result.getObject("id", UUID::class.java)
                    }
                }

                addParticipant(connection, conversationId, currentUserId)
                addParticipant(connection, conversationId, otherUserId)

                connection.commit()

                return getConversation(dataSource.connection, conversationId)
                    ?: error("Conversation could not be loaded.")

            } catch (e: Exception) {
                connection.rollback()
                throw e
            } finally {
                connection.autoCommit = true
            }
        }
    }

    fun createGroupConversation(
        currentUserId: UUID,
        title: String,
        participantIds: List<UUID>
    ): ConversationResponse {
        require(title.isNotBlank() && title.length <= 100) {
            "Group title must be between 1 and 100 characters."
        }

        val allParticipants = (participantIds + currentUserId).distinct()

        require(allParticipants.size >= 2) {
            "A group must have at least two participants."
        }

        dataSource.connection.use { connection ->
            connection.autoCommit = false

            try {
                // Confirm every participant exists.
                val existingCount = connection.prepareStatement(
                    """
                    SELECT COUNT(*)
                    FROM app_users
                    WHERE id = ANY (?)
                    """.trimIndent()
                ).use { statement ->
                    val array = connection.createArrayOf(
                        "uuid",
                        allParticipants.toTypedArray()
                    )

                    statement.setArray(1, array)

                    statement.executeQuery().use {
                        it.next()
                        it.getInt(1)
                    }
                }

                require(existingCount == allParticipants.size) {
                    "One or more selected users do not exist."
                }

                val conversationId = connection.prepareStatement(
                    """
                    INSERT INTO conversations (title, is_group, created_by)
                    VALUES (?, TRUE, ?)
                    RETURNING id
                    """.trimIndent()
                ).use {
                    it.setString(1, title.trim())
                    it.setObject(2, currentUserId)

                    it.executeQuery().use { result ->
                        check(result.next())
                        result.getObject("id", UUID::class.java)
                    }
                }

                allParticipants.forEach { participantId ->
                    addParticipant(
                        connection,
                        conversationId,
                        participantId
                    )
                }

                connection.commit()

                dataSource.connection.use { readConnection ->
                    return getConversation(readConnection, conversationId)
                        ?: error("Group conversation could not be loaded.")
                }

            } catch (e: Exception) {
                connection.rollback()
                throw e
            } finally {
                connection.autoCommit = true
            }
        }
    }

    fun getUserConversations(
        userId: UUID
    ): List<ConversationResponse> {
        val sql = """
            SELECT c.id, c.title, c.is_group, c.created_by,
                   c.created_at, c.updated_at
            FROM conversations c
            JOIN conversation_participants p
              ON p.conversation_id = c.id
            WHERE p.user_id = ?
            ORDER BY c.updated_at DESC
        """.trimIndent()

        val conversations = mutableListOf<ConversationResponse>()

        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use {
                it.setObject(1, userId)

                it.executeQuery().use { result ->
                    while (result.next()) {
                        conversations.add(result.toConversation())
                    }
                }
            }
        }

        return conversations
    }

    fun getParticipants(
        conversationId: UUID
    ): List<ConversationParticipantResponse> {
        val sql = """
            SELECT u.id, u.username, u.full_name,
                   u.preferred_language, u.is_online
            FROM conversation_participants p
            JOIN app_users u ON u.id = p.user_id
            WHERE p.conversation_id = ?
            ORDER BY p.joined_at
        """.trimIndent()

        val participants = mutableListOf<ConversationParticipantResponse>()

        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use {
                it.setObject(1, conversationId)

                it.executeQuery().use { result ->
                    while (result.next()) {
                        participants.add(
                            ConversationParticipantResponse(
                                userId = result.getObject(
                                    "id", UUID::class.java
                                ).toString(),
                                username = result.getString("username"),
                                fullName = result.getString("full_name"),
                                preferredLanguage = result.getString(
                                    "preferred_language"
                                ),
                                isOnline = result.getBoolean("is_online")
                            )
                        )
                    }
                }
            }
        }

        return participants
    }

    fun isParticipant(
        conversationId: UUID,
        userId: UUID
    ): Boolean {
        val sql = """
            SELECT 1
            FROM conversation_participants
            WHERE conversation_id = ? AND user_id = ?
        """.trimIndent()

        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use {
                it.setObject(1, conversationId)
                it.setObject(2, userId)

                it.executeQuery().use { result ->
                    return result.next()
                }
            }
        }
    }

    fun sendMessage(
        conversationId: UUID,
        senderId: UUID,
        text: String,
        originalLanguage: String?
    ): ChatMessageResponse {
        require(text.isNotBlank() && text.length <= 10000) {
            "Message must contain between 1 and 10,000 characters."
        }

        val sql = """
            INSERT INTO messages (
                conversation_id,
                sender_id,
                original_text,
                original_language
            )
            VALUES (?, ?, ?, ?)
            RETURNING id, conversation_id, sender_id,
                      original_text, original_language, created_at
        """.trimIndent()

        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use {
                it.setObject(1, conversationId)
                it.setObject(2, senderId)
                it.setString(3, text.trim())
                it.setString(4, originalLanguage)

                it.executeQuery().use { result ->
                    check(result.next())

                    val message = result.toChatMessage()

                    connection.prepareStatement(
                        """
                        UPDATE conversations
                        SET updated_at = CURRENT_TIMESTAMP
                        WHERE id = ?
                        """.trimIndent()
                    ).use { update ->
                        update.setObject(1, conversationId)
                        update.executeUpdate()
                    }

                    return message
                }
            }
        }
    }

    fun getMessageHistory(
        conversationId: UUID,
        limit: Int,
        offset: Int
    ): List<ChatMessageResponse> {
        val sql = """
            SELECT id, conversation_id, sender_id,
                   original_text, original_language, created_at
            FROM messages
            WHERE conversation_id = ?
              AND is_deleted = FALSE
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
        """.trimIndent()

        val messages = mutableListOf<ChatMessageResponse>()

        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use {
                it.setObject(1, conversationId)
                it.setInt(2, limit)
                it.setInt(3, offset)

                it.executeQuery().use { result ->
                    while (result.next()) {
                        messages.add(result.toChatMessage())
                    }
                }
            }
        }

        return messages
    }

    private fun addParticipant(
        connection: java.sql.Connection,
        conversationId: UUID,
        userId: UUID
    ) {
        connection.prepareStatement(
            """
            INSERT INTO conversation_participants
                (conversation_id, user_id)
            VALUES (?, ?)
            ON CONFLICT (conversation_id, user_id) DO NOTHING
            """.trimIndent()
        ).use {
            it.setObject(1, conversationId)
            it.setObject(2, userId)
            it.executeUpdate()
        }
    }

    private return getConversation(connection, conversationId)
    ?: error("Conversation could not be loaded."): ConversationResponse? {
        val sql = """
            SELECT id, title, is_group, created_by,
                   created_at, updated_at
            FROM conversations
            WHERE id = ?
        """.trimIndent()

        connection.prepareStatement(sql).use {
            it.setObject(1, conversationId)

            it.executeQuery().use { result ->
                return if (result.next()) {
                    result.toConversation()
                } else {
                    null
                }
            }
        }
    }

    private fun java.sql.ResultSet.toConversation() =
        ConversationResponse(
            id = getObject("id", UUID::class.java).toString(),
            title = getString("title"),
            isGroup = getBoolean("is_group"),
            createdBy = getObject("created_by", UUID::class.java)?.toString(),
            createdAt = getTimestamp("created_at").toInstant().toString(),
            updatedAt = getTimestamp("updated_at").toInstant().toString()
        )

    private fun java.sql.ResultSet.toChatMessage() =
        ChatMessageResponse(
            id = getObject("id", UUID::class.java).toString(),
            conversationId = getObject(
                "conversation_id", UUID::class.java
            ).toString(),
            senderId = getObject("sender_id", UUID::class.java).toString(),
            originalText = getString("original_text"),
            originalLanguage = getString("original_language"),
            createdAt = getTimestamp("created_at").toInstant().toString()
        )
}
