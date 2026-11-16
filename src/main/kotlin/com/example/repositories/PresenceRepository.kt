package com.example.repositories

import com.example.database.DatabaseFactory
import java.util.UUID

class PresenceRepository {

    fun updatePresence(userId: UUID, online: Boolean) {
        val sql = """
            UPDATE app_users
            SET is_online = ?,
                last_seen = CASE
                    WHEN ? = FALSE THEN CURRENT_TIMESTAMP
                    ELSE last_seen
                END
            WHERE id = ?
        """.trimIndent()

        DatabaseFactory.dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setBoolean(1, online)
                statement.setBoolean(2, online)
                statement.setObject(3, userId)
                statement.executeUpdate()
            }
        }
    }
}