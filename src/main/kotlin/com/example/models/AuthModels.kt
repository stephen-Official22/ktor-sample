
package com.example.models

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val username: String,
    val fullName: String,
    val email: String,
    val password: String,
    val preferredLanguage: String = "en"
)

@Serializable
data class LoginRequest(
    val identifier: String,
    val password: String
)

@Serializable
data class AuthUser(
    val id: String,
    val username: String,
    val fullName: String,
    val email: String,
    val preferredLanguage: String
)

@Serializable
data class AuthResponse(
    val message: String,
    val token: String,
    val user: AuthUser
)

@Serializable
data class ApiError(
    val error: String
)
