
package com.example.config

data class DatabaseConfig(
    val jdbcUrl: String,
    val username: String,
    val password: String
) {
    companion object {
        fun load(): DatabaseConfig {
            return DatabaseConfig(
                jdbcUrl = System.getenv("DB_URL")
                    ?: "jdbc:postgresql://localhost:5432/multivo",
                username = System.getenv("DB_USER")
                    ?: "postgres",
                password = System.getenv("DB_PASSWORD")
                    ?: throw IllegalStateException(
                        "DB_PASSWORD environment variable is required"
                    )
            )
        }
    }
}
