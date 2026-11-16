
package com.example.repositories

import com.example.database.DatabaseFactory
import com.example.models.AuthUser
import java.sql.ResultSet
import java.util.UUID

data class StoredUser(
    val user: AuthUser,
    val passwordHash: String
)

class UserRepository {

    private val dataSource
        get() = DatabaseFactory.dataSource

    fun createUser(
        username: String,
        fullName: String,
        email: String,
        passwordHash: String,
        preferredLanguage: String
    ): AuthUser {

        val sql = """
            INSERT INTO app_users (
                username,
                full_name,
                email,
                password_hash,
                preferred_language
            )
            VALUES (?, ?, ?, ?, ?)
            RETURNING id, username, full_name, email,
                      preferred_language
        """.trimIndent()

        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->

                statement.setString(1, username)
                statement.setString(2, fullName)
                statement.setString(3, email)
                statement.setString(4, passwordHash)
                statement.setString(5, preferredLanguage)

                statement.executeQuery().use { result ->
                    if (!result.next()) {
                        error("User registration failed")
                    }

                    return result.toAuthUser()
                }
            }
        }
    }

    fun findByIdentifier(identifier: String): StoredUser? {

        val sql = """
            SELECT id, username, full_name, email,
                   preferred_language, password_hash
            FROM app_users
            WHERE LOWER(email) = LOWER(?)
               OR LOWER(username) = LOWER(?)
            LIMIT 1
        """.trimIndent()

        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->

                statement.setString(1, identifier)
                statement.setString(2, identifier)

                statement.executeQuery().use { result ->
                    if (!result.next()) return null

                    val user = result.toAuthUser()

                    return StoredUser(
                        user = user,
                        passwordHash = result.getString("password_hash")
                    )
                }
            }
        }
    }

    fun findById(id: UUID): AuthUser? {

        val sql = """
            SELECT id, username, full_name, email,
                   preferred_language
            FROM app_users
            WHERE id = ?
        """.trimIndent()

        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->

                statement.setObject(1, id)

                statement.executeQuery().use { result ->
                    return if (result.next()) {
                        result.toAuthUser()
                    } else {
                        null
                    }
                }
            }
        }
    }

    private fun ResultSet.toAuthUser(): AuthUser {
        return AuthUser(
            id = getObject("id", UUID::class.java).toString(),
            username = getString("username"),
            fullName = getString("full_name"),
            email = getString("email"),
            preferredLanguage = getString("preferred_language")
        )
    }
}
