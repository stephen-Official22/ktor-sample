package com.example.services

import com.example.models.SocketEvent
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.send
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object WebSocketHub {

    private data class Connection(
        val userId: UUID,
        val session: WebSocketSession
    )

    private val connections =
        ConcurrentHashMap<UUID, MutableSet<Connection>>()

    private val userConnections =
        ConcurrentHashMap<UUID, MutableSet<Connection>>()

    private val lock = Any()

    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun connect(
        conversationId: UUID,
        userId: UUID,
        session: WebSocketSession
    ): Boolean {
        val connection = Connection(userId, session)

        synchronized(lock) {
            val userSessions = userConnections.getOrPut(userId) {
                ConcurrentHashMap.newKeySet()
            }

            val isFirstConnection = userSessions.isEmpty()
            userSessions.add(connection)

            connections.getOrPut(conversationId) {
                ConcurrentHashMap.newKeySet()
            }.add(connection)

            return isFirstConnection
        }
    }

    fun disconnect(
        conversationId: UUID,
        userId: UUID,
        session: WebSocketSession
    ): Boolean {
        val connection = Connection(userId, session)

        synchronized(lock) {
            connections[conversationId]?.let {
                it.remove(connection)

                if (it.isEmpty()) {
                    connections.remove(conversationId)
                }
            }

            userConnections[userId]?.let {
                it.remove(connection)

                if (it.isEmpty()) {
                    userConnections.remove(userId)
                    return true
                }
            }
        }

        return false
    }

    suspend fun broadcast(
        conversationId: UUID,
        event: SocketEvent
    ) {
        val message = json.encodeToString(event)

        val sessions = connections[conversationId]?.toList()
            ?: return

        sessions.forEach { connection ->
            try {
                connection.session.send(Frame.Text(message))
            } catch (_: Exception) {
                // A disconnected session will be removed on cleanup.
            }
        }
    }

    suspend fun sendToUser(
        userId: UUID,
        event: SocketEvent
    ) {
        val message = json.encodeToString(event)

        val sessions = userConnections[userId]?.toList()
            ?: return

        sessions.forEach { connection ->
            try {
                connection.session.send(Frame.Text(message))
            } catch (_: Exception) {
                // Ignore disconnected sessions.
            }
        }
    }

    fun isOnline(userId: UUID): Boolean {
        return !userConnections[userId].isNullOrEmpty()
    }
}