package models

import kotlinx.serialization.*

@Serializable
data class Job(
    val id: String? = null,
    val title: String,
    val description: String,
    val jobType: JobType,
    val category: JobCategory,
    val budget: Double,
    val budgetType: BudgetType,
    val duration: String? = null,
    val location: Location,
    val address: String,
    val radiusKm: Double = 10.0,
    val status: JobStatus = JobStatus.OPEN,
    val createdBy: String,
    val createdAt: String,
    val updatedAt: String,
    val distance: Double? = null
)

@Serializable
data class JobApplication(
    val id: String? = null,
    val jobId: String,
    val userId: String,
    val proposal: String,
    val bidAmount: Double? = null,
    val status: ApplicationStatus = ApplicationStatus.PENDING,
    val appliedAt: String
)

enum class JobType { ONE_TIME, PART_TIME, FULL_TIME }
enum class JobCategory { PLUMBING, ELECTRICAL, CARPENTRY, MASONRY, PAINTING, CLEANING, GARDENING, MOVING, MECHANICAL, GENERAL_LABOR, OTHER }
enum class BudgetType { FIXED, HOURLY, DAILY, NEGOTIABLE }
enum class JobStatus { OPEN, IN_PROGRESS, COMPLETED, CANCELLED }
enum class ApplicationStatus { PENDING, ACCEPTED, REJECTED, WITHDRAWN }

data class NearbyJobsRequest(
    val location: Location,
    val radiusKm: Double = 50.0,
    val categories: List<JobCategory>? = null,
    val minBudget: Double? = null,
    val maxBudget: Double? = null,
    val page: Int = 1,
    val limit: Int = 20
)

data class PaginatedResult<T>(
    val data: List<T>,
    val page: Int,
    val limit: Int,
    val total: Long,
    val totalPages: Long
)
