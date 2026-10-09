package services

import AlreadyApplied
import ApplicationNotFound
import ApplyJobRequest
import CreateJobRequest
import InvalidUserType
import JobNotFound
import SubscriptionRequired
import Unauthorized
import UserNotFound
import models.ApplicationStatus
import models.Job
import models.JobApplication
import models.JobStatus
import models.Location
import models.NearbyJobsRequest
import models.PaginatedResult
import models.UserType
import repos.JobRepository
import repos.UserRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Instant
import java.util.UUID

class JobService : KoinComponent {
    private val jobRepository: JobRepository by inject()
    private val userRepository: UserRepository by inject()
    private val subscriptionService: SubscriptionService by inject()
    private val notificationService: NotificationService by inject()

    suspend fun createJob(userId: String, request: CreateJobRequest): Job {
        if (!subscriptionService.hasActiveSubscription(UUID.fromString(userId))) throw SubscriptionRequired()

        val job = Job(
            title = request.title,
            description = request.description,
            jobType = request.jobType,
            category = request.category,
            budget = request.budget,
            budgetType = request.budgetType,
            duration = request.duration,
            location = Location(request.latitude, request.longitude),
            address = request.address,
            radiusKm = request.radiusKm,
            createdBy = userId,
            createdAt = Instant.now().toString(),
            updatedAt = Instant.now().toString()
        )
        return jobRepository.createJob(job)
    }

    suspend fun getNearbyJobs(userId: UUID, request: NearbyJobsRequest): PaginatedResult<Job> {
        val user = userRepository.findUserById(userId) ?: throw UserNotFound()
        if (user.userType == UserType.HANDYMAN && !subscriptionService.hasActiveSubscription(userId)) {
            throw SubscriptionRequired()
        }
        return jobRepository.findNearbyJobs(request, userId)
    }

    suspend fun getJobDetails(jobId: UUID): Job =
        jobRepository.findJobById(jobId) ?: throw JobNotFound()

    suspend fun applyForJob(userId: UUID, jobId: UUID, request: ApplyJobRequest): JobApplication {
        val user = userRepository.findUserById(userId) ?: throw UserNotFound()
        if (user.userType != UserType.HANDYMAN) throw InvalidUserType()
        if (!subscriptionService.hasActiveSubscription(userId)) throw SubscriptionRequired()

        val job = jobRepository.findJobById(jobId) ?: throw JobNotFound()
        val existing = jobRepository.findApplicationByJobAndUser(jobId, userId)
        if (existing != null) throw AlreadyApplied()

        val application = JobApplication(
            jobId = jobId.toString(),
            userId = userId.toString(),
            proposal = request.proposal,
            bidAmount = request.bidAmount,
            status = ApplicationStatus.PENDING,
            appliedAt = Instant.now().toString()
        )
        val created = jobRepository.createApplication(application)
        notificationService.notifyJobApplication(UUID.fromString(job.createdBy), jobId, userId)
        return created
    }

    suspend fun getJobApplications(jobId: UUID, userId: UUID, page: Int, limit: Int): PaginatedResult<JobApplication> {
        val job = jobRepository.findJobById(jobId) ?: throw JobNotFound()
        if (UUID.fromString(job.createdBy) != userId) throw Unauthorized()
        return jobRepository.findApplicationsByJob(jobId, page, limit)
    }

    suspend fun updateApplicationStatus(applicationId: UUID, userId: UUID, status: ApplicationStatus): Boolean {
        val app = jobRepository.findApplicationById(applicationId) ?: throw ApplicationNotFound()
        val job = jobRepository.findJobById(UUID.fromString(app.jobId)) ?: throw JobNotFound()
        if (UUID.fromString(job.createdBy) != userId) throw Unauthorized()

        val updated = jobRepository.updateApplicationStatus(applicationId, status)
        if (updated && status == ApplicationStatus.ACCEPTED) {
            jobRepository.updateJobStatus(UUID.fromString(job.id), JobStatus.IN_PROGRESS)
            notificationService.notifyApplicationAccepted(UUID.fromString(app.userId), UUID.fromString(job.id))
        }
        return updated
    }

    suspend fun getUserJobs(userId: UUID, page: Int, limit: Int): PaginatedResult<Job> =
        jobRepository.findJobsByCreator(userId, page, limit)

    suspend fun completeJob(jobId: UUID, userId: UUID): Boolean {
        val job = jobRepository.findJobById(jobId) ?: throw JobNotFound()
        if (UUID.fromString(job.createdBy) != userId) throw Unauthorized()
        return jobRepository.updateJobStatus(jobId, JobStatus.COMPLETED)
    }
}
