
package com.example.repositories

import com.example.database.DatabaseFactory
import com.example.models.*
import java.util.UUID

class UserManagementRepository {

    private val dataSource
        get() = DatabaseFactory.dataSource

    fun getProfile(userId: UUID): UserProfileResponse? {
        val sql = """
            SELECT id, username, full_name, email, bio,
                   profile_picture, preferred_language, is_online
            FROM app_users
            WHERE id = ?
        """.trimIndent()

        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setObject(1, userId)

                statement.executeQuery().use { result ->
                    if (!result.next()) return null

                    return UserProfileResponse(
                        id = result.getObject("id", UUID::class.java).toString(),
                        username = result.getString("username"),
                        fullName = result.getString("full_name"),
                        email = result.getString("email"),
                        bio = result.getString("bio"),
                        profilePicture = result.getString("profile_picture"),
                        preferredLanguage = result.getString("preferred_language"),
                        isOnline = result.getBoolean("is_online")
                    )
                }
            }
        }
    }

    fun updateProfile(
        userId: UUID,
        request: UpdateProfileRequest
    ): Boolean {
        val sql = """
            UPDATE app_users
            SET full_name = ?,
                bio = ?,
                profile_picture = ?,
                preferred_language = ?,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
        """.trimIndent()

        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, request.fullName.trim())
                statement.setString(2, request.bio)
                statement.setString(3, request.profilePicture)
                statement.setString(4, request.preferredLanguage)
                statement.setObject(5, userId)

                return statement.executeUpdate() == 1
            }
        }
    }

    fun searchUsers(
        currentUserId: UUID,
        query: String
    ): List<UserSearchResponse> {
        val sql = """
            SELECT id, username, full_name, profile_picture, is_online
            FROM app_users
            WHERE id <> ?
              AND (
                username ILIKE ?
                OR full_name ILIKE ?
              )
            ORDER BY is_online DESC, username ASC
            LIMIT 30
        """.trimIndent()

        val users = mutableListOf<UserSearchResponse>()

        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                val search = "%${query.trim()}%"

                statement.setObject(1, currentUserId)
                statement.setString(2, search)
                statement.setString(3, search)

                statement.executeQuery().use { result ->
                    while (result.next()) {
                        users.add(
                            UserSearchResponse(
                                id = result.getObject("id", UUID::class.java).toString(),
                                username = result.getString("username"),
                                fullName = result.getString("full_name"),
                                profilePicture = result.getString("profile_picture"),
                                isOnline = result.getBoolean("is_online")
                            )
                        )
                    }
                }
            }
        }

        return users
    }

    fun sendConnectionRequest(
        senderId: UUID,
        receiverId: UUID
    ): UUID {
        require(senderId != receiverId) {
            "You cannot send a request to yourself."
        }

        val sql = """
            INSERT INTO connection_requests (sender_id, receiver_id)
            VALUES (?, ?)
            RETURNING id
        """.trimIndent()

        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setObject(1, senderId)
                statement.setObject(2, receiverId)

                statement.executeQuery().use { result ->
                    if (!result.next()) error("Request creation failed")
                    return result.getObject("id", UUID::class.java)
                }
            }
        }
    }

    fun respondToRequest(
        requestId: UUID,
        currentUserId: UUID,
        accepted: Boolean
    ): Boolean {
        val sql = """
            UPDATE connection_requests
            SET status = ?
            WHERE id = ?
              AND receiver_id = ?
              AND status = 'pending'
        """.trimIndent()

        dataSource.connection.use { connection ->
            connection.autoCommit = false

            try {
                val updated = connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, if (accepted) "accepted" else "rejected")
                    statement.setObject(2, requestId)
                    statement.setObject(3, currentUserId)
                    statement.executeUpdate()
                }

                if (updated == 0) {
                    connection.rollback()
                    return false
                }

                if (accepted) {
                    val insertContact = """
                        INSERT INTO contacts (user_id, contact_user_id)
                        VALUES (?, ?), (?, ?)
                        ON CONFLICT (user_id, contact_user_id) DO NOTHING
                    """.trimIndent()

                    connection.prepareStatement(insertContact).use { statement ->
                        val request = connection.prepareStatement(
                            """
                            SELECT sender_id, receiver_id
                            FROM connection_requests
                            WHERE id = ?
                            """.trimIndent()
                        )

                        val (senderId, receiverId) = request.use {
                            it.setObject(1, requestId)
                            it.executeQuery().use { result ->
                                check(result.next())
                                Pair(
                                    result.getObject("sender_id", UUID::class.java),
                                    result.getObject("receiver_id", UUID::class.java)
                                )
                            }
                        }

                        statement.setObject(1, senderId)
                        statement.setObject(2, receiverId)
                        statement.setObject(3, receiverId)
                        statement.setObject(4, senderId)
                        statement.executeUpdate()
                    }
                }

                connection.commit()
                return true

            } catch (e: Exception) {
                connection.rollback()
                throw e
            } finally {
                connection.autoCommit = true
            }
        }
    }

    fun getContacts(userId: UUID): List<UserSearchResponse> {
        val sql = """
            SELECT u.id, u.username, u.full_name,
                   u.profile_picture, u.is_online
            FROM contacts c
            JOIN app_users u ON u.id = c.contact_user_id
            WHERE c.user_id = ?
            ORDER BY u.username
        """.trimIndent()

        val contacts = mutableListOf<UserSearchResponse>()

        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setObject(1, userId)

                statement.executeQuery().use { result ->
                    while (result.next()) {
                        contacts.add(
                            UserSearchResponse(
                                id = result.getObject("id", UUID::class.java).toString(),
                                username = result.getString("username"),
                                fullName = result.getString("full_name"),
                                profilePicture = result.getString("profile_picture"),
                                isOnline = result.getBoolean("is_online")
                            )
                        )
                    }
                }
            }
        }

        return contacts
    }

    fun removeContact(
        userId: UUID,
        contactId: UUID
    ): Boolean {
        val sql = """
            DELETE FROM contacts
            WHERE user_id = ? AND contact_user_id = ?
        """.trimIndent()

        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setObject(1, userId)
                statement.setObject(2, contactId)
                return statement.executeUpdate() > 0
            }
        }
    }
}
