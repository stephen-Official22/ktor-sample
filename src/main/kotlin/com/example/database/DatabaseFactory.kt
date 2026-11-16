
package com.example.database

import com.example.config.DatabaseConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway

object DatabaseFactory {

    lateinit var dataSource: HikariDataSource
        private set

    fun connect() {
        val db = DatabaseConfig.load()

        val config = HikariConfig().apply {
            jdbcUrl = db.jdbcUrl
            username = db.username
            password = db.password
            driverClassName = "org.postgresql.Driver"

            maximumPoolSize = 10
            minimumIdle = 2
            connectionTimeout = 30000
            idleTimeout = 600000
            maxLifetime = 1800000
            poolName = "MultivoPool"
        }

        val pool = HikariDataSource(config)

        try {
            Flyway.configure()
                .dataSource(pool)
                .locations("classpath:db/migration")
                .load()
                .migrate()

            dataSource = pool

            println("MULTIVO DATABASE CONNECTED")
            println("DATABASE MIGRATIONS COMPLETED")

        } catch (e: Exception) {
            pool.close()
            throw IllegalStateException(
                "Unable to initialize Multivo database",
                e
            )
        }
    }

    fun close() {
        if (::dataSource.isInitialized) {
            dataSource.close()
            println("MULTIVO DATABASE CLOSED")
        }
    }
}
