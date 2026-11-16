
package com.example

import com.example.routes.webSocketRoutes
import io.ktor.server.websocket.WebSockets
import kotlin.time.Duration.Companion.seconds
import com.example.routes.conversationRoutes
import com.example.routes.userRoutes
import com.example.database.DatabaseFactory
import com.example.routes.authRoutes
import com.example.security.JwtConfig
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.routing.routing

fun Application.module() {
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 30.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    // Database connection
    DatabaseFactory.connect()

    // Close database when server stops
    environment.monitor.subscribe(ApplicationStopped) {
        DatabaseFactory.close()
    }

    // JWT authentication
    install(Authentication) {
        jwt("auth-jwt") {
            realm = "Multivo API"
            verifier(JwtConfig.verifier)

            validate { credential ->
                if (!credential.payload.subject.isNullOrBlank()) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
        }
    }

    // Register routes
    routing {
        authRoutes()
        UserRoutes
        conversationRoutes()
        webSocketRoutes()
    }
}
