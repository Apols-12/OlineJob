package models

import kotlinx.serialization.*

@Serializable
data class Portfolio(
    val id: String? = null,
    val userId: String,
    val bio: String?,
    val skills: List<String>,
    val experienceYears: Int,
    val rating: Double,
    val completedJob: Int,
    val imageUrl: String?,
    val location: Location?,
    val address: String?,
    val thumbnailUrl: String?,
    val filename: String?,
    val distance: Double? = null,
    val createdAt: String = "",
    val updatedAt: String = ""
)
@Serializable
data class CreatePortfolio(
    val userId: String,
    val bio: String?,
    val skills: List<String>,
    val location: Location?,
    val address: String?,
    val experienceYears: Int
)
@Serializable
data class SearchPortfolioFilters(
    val page: Int = 1,
    val limit: Int = 10,
    val kmRad: Double = 10.0,
    val skills: String?,
    val location: Location?,
    val rating: Int?,
    val experiencesYears: Int?
)