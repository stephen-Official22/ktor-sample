
package com.example.models

import kotlinx.serialization.Serializable

@Serializable
data class UpdateProfileRequest(
    val fullName: String,
    val bio: String? = null,
    val profilePicture: String? = null,
    val preferredLanguage: String
)

@Serializable
data class UserProfileResponse(
    val id: String,
    val username: String,
    val fullName: String,
    val email: String,
    val bio: String?,
    val profilePicture: String?,
    val preferredLanguage: String,
    val isOnline: Boolean
)

@Serializable
data class UserSearchResponse(
    val id: String,
    val username: String,
    val fullName: String,
    val profilePicture: String?,
    val isOnline: Boolean
)

@Serializable
data class ConnectionRequestResponse(
    val id: String,
    val senderId: String,
    val receiverId: String,
    val status: String
)

@Serializable
data class MessageResponse(
    val message: String
)
