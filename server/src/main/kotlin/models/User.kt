package models

import kotlinx.serialization.*

@Serializable
data class User(
    val id: String? = null,
    val email: String,
    val fullName: String,
    val phone: String? = null,
    val userType: UserType,
    val isEmailVerified: Boolean = false,
    val location: Location? = null,
    val address: String? = null,
    val createdAt: String = "",
    val updatedAt: String = ""
)

@Serializable
data class Location(
    val latitude: Double,
    val longitude: Double
)

enum class UserType {
    HANDYMAN,
    CLIENT,
    ADMIN
}

