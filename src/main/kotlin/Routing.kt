
package com.example

import com.example.database.DatabaseFactory
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("Hello, World!")
        }

        get("/db-test") {
            try {
                withContext(Dispatchers.IO) {
                    DatabaseFactory.testConnection()
                }

                call.respondText("Multivo database connected successfully!")
            } catch (e: Exception) {
                call.respondText(
                    "Database connection failed: ${e.message}",
                    status = HttpStatusCode.InternalServerError
                )
            }
        }
    }
}
