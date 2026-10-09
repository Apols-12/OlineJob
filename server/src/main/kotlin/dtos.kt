import models.ApplicationStatus
import models.BudgetType
import models.JobCategory
import models.JobType
import models.UserType
import kotlinx.serialization.*
import org.valiktor.functions.hasSize
import org.valiktor.functions.isBetween
import org.valiktor.functions.isEmail
import org.valiktor.functions.isGreaterThan
import org.valiktor.functions.isNotBlank
import org.valiktor.validate

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val fullName: String,
    val phone: String? = null,
    val userType: UserType,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null
) {
    init {
        validate(this) {
            validate(RegisterRequest::email).isEmail().isNotBlank()
            validate(RegisterRequest::password).hasSize(min = 8)
            validate(RegisterRequest::fullName).isNotBlank()
        }
    }
}

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
) {
    init {
        validate(this) {
            validate(LoginRequest::email).isEmail().isNotBlank()
            validate(LoginRequest::password).isNotBlank()
        }
    }
}

@Serializable
data class AuthResponse(
    val token: String,
    val user: UserResponse,
    val subscription: SubscriptionResponse? = null
)

@Serializable
data class UserResponse(
    val id: String,
    val email: String,
    val fullName: String,
    val phone: String?,
    val userType: UserType,
    val isEmailVerified: Boolean,
    val location: LocationDto?,
    val address: String?,
    val subscriptionResponse: SubscriptionResponse?
)

@Serializable
data class LocationDto(
    val latitude: Double,
    val longitude: Double
)

@Serializable
data class SubscriptionResponse(
    val id: String,
    val planType: String,
    val amount: Double,
    val startDate: String,
    val endDate: String,
    val status: String,
    val isActive: Boolean
)

@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val message: String? = null,
    val error: String? = null
)

@Serializable
data class CreateJobRequest(
    val title: String,
    val description: String,
    val jobType: JobType,
    val category: JobCategory,
    val budget: Double,
    val budgetType: BudgetType,
    val duration: String? = null,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val radiusKm: Double = 10.0
) {
    init {
        validate(this) {
            validate(CreateJobRequest::title).isNotBlank().hasSize(min = 3, max = 255)
            validate(CreateJobRequest::description).isNotBlank().hasSize(min = 10)
            validate(CreateJobRequest::budget).isGreaterThan(0.0)
            validate(CreateJobRequest::latitude).isBetween(-90.0, 90.0)
            validate(CreateJobRequest::longitude).isBetween(-180.0, 180.0)
            validate(CreateJobRequest::address).isNotBlank()
        }
    }
}

@Serializable
data class JobResponse(
    val id: String,
    val title: String,
    val description: String,
    val jobType: String,
    val category: String,
    val budget: Double,
    val budgetType: String,
    val duration: String?,
    val location: LocationDto,
    val address: String,
    val radiusKm: Double,
    val status: String,
    val createdBy: String,
    val createdAt: String,
    val updatedAt: String,
    val distance: Double? = null
)

@Serializable
data class ApplyJobRequest(
    val proposal: String,
    val bidAmount: Double? = null
) {
    init {
        validate(this) {
            validate(ApplyJobRequest::proposal).isNotBlank().hasSize(min = 10)
        }
    }
}

@Serializable
data class JobApplicationResponse(
    val id: String,
    val jobId: String,
    val userId: String,
    val proposal: String,
    val bidAmount: Double?,
    val status: String,
    val appliedAt: String
)

@Serializable
data class UpdateJobStatusRequest(
    val status: ApplicationStatus
)

@Serializable
data class LocationUpdateRequest(
    val latitude: Double,
    val longitude: Double
) {
    init {
        validate(this) {
            validate(LocationUpdateRequest::latitude).isBetween(-90.0, 90.0)
            validate(LocationUpdateRequest::longitude).isBetween(-180.0, 180.0)
        }
    }
}
