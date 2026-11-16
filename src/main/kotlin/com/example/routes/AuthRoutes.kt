
package com.example.services

import com.example.models.AuthResponse
import com.example.models.RegisterRequest
import com.example.repositories.UserRepository
import com.example.security.JwtConfig
import com.example.security.PasswordHasher
import java.sql.SQLException

class AuthService(
    private val userRepository: UserRepository = UserRepository()
) {

    fun register(request: RegisterRequest): AuthResponse {
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


        val username = request.username.trim()
        val fullName = request.fullName.trim()
        val email = request.email.trim().lowercase()
        val language = request.preferredLanguage.trim().lowercase()

        require(username.matches(Regex("^[a-zA-Z0-9_]{3,30}$"))) {
            "Username must be 3–30 characters and contain only letters, numbers or underscores."
        }

        require(fullName.length in 2..100) {
            "Full name must be between 2 and 100 characters."
        }

        require(email.matches(Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"))) {
            "Enter a valid email address."
        }

        require(request.password.length >= 8) {
            "Password must contain at least 8 characters."
        }

        require(language.matches(Regex("^[a-z]{2,3}(-[a-zA-Z0-9]{2,8})?$"))) {
            "Invalid preferred language code."
        }

        val passwordHash = PasswordHasher.hashPassword(request.password)

        val user = try {
            userRepository.createUser(
                username = username,
                fullName = fullName,
                email = email,
                passwordHash = passwordHash,
                preferredLanguage = language
            )
        } catch (e: SQLException) {
            if (e.sqlState == "23505") {
                throw IllegalArgumentException(
                    "Username or email is already registered."
                )
            }
            throw e
        }

        return AuthResponse(
            message = "Registration successful.",
            token = JwtConfig.generateToken(
                java.util.UUID.fromString(user.id)
            ),
            user = user
        )
    }

    fun login(
        identifier: String,
        password: String
    ): AuthResponse {

        require(identifier.isNotBlank()) {
            "Enter your email or username."
        }

        require(password.isNotBlank()) {
            "Password is required."
        }

        val storedUser = userRepository.findByIdentifier(
            identifier.trim()
        ) ?: throw IllegalArgumentException(
            "Invalid username/email or password."
        )

        if (!PasswordHasher.verifyPassword(
                password,
                storedUser.passwordHash
            )
        ) {
            throw IllegalArgumentException(
                "Invalid username/email or password."
            )
        }

        return AuthResponse(
            message = "Login successful.",
            token = JwtConfig.generateToken(
                java.util.UUID.fromString(storedUser.user.id)
            ),
            user = storedUser.user
        )
    }
}
