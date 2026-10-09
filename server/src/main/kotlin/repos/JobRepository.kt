package repos

import database.DatabaseFactory
import database.JobApplications
import database.Jobs
import models.ApplicationStatus
import models.Job
import models.JobApplication
import models.JobStatus
import models.Location
import models.NearbyJobsRequest
import models.PaginatedResult
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

interface JobRepository {
    suspend fun createJob(job: Job): Job
    suspend fun findJobById(id: UUID): Job?
    suspend fun findJobsByCreator(userId: UUID, page: Int, limit: Int): PaginatedResult<Job>
    suspend fun findNearbyJobs(request: NearbyJobsRequest, userId: UUID): PaginatedResult<Job>
    suspend fun updateJobStatus(jobId: UUID, status: JobStatus): Boolean
    suspend fun deleteJob(jobId: UUID, userId: UUID): Boolean

    suspend fun createApplication(application: JobApplication): JobApplication
    suspend fun findApplicationById(id: UUID): JobApplication?
    suspend fun findApplicationsByJob(jobId: UUID, page: Int, limit: Int): PaginatedResult<JobApplication>
    suspend fun findApplicationsByUser(userId: UUID, page: Int, limit: Int): PaginatedResult<JobApplication>
    suspend fun updateApplicationStatus(applicationId: UUID, status: ApplicationStatus): Boolean
    suspend fun findApplicationByJobAndUser(jobId: UUID, userId: UUID): JobApplication?
}

class JobRepositoryImpl : JobRepository {
    override suspend fun createJob(job: Job): Job = DatabaseFactory.dbQuery {
        Jobs.insert {
            it[title] = job.title
            it[description] = job.description
            it[jobType] = job.jobType
            it[category] = job.category
            it[budget] = job.budget
            it[budgetType] = job.budgetType
            it[duration] = job.duration
            it[latitude] = job.location.latitude
            it[longitude] = job.location.longitude
            it[address] = job.address
            it[radiusKm] = job.radiusKm
            it[status] = job.status
            it[createdBy] = UUID.fromString(job.createdBy)
            it[createdAt] = Instant.parse(job.createdAt)
            it[updatedAt] = Instant.parse(job.updatedAt)
        }
        job
    }

    override suspend fun findJobById(id: UUID): Job? = DatabaseFactory.dbQuery {
        Jobs.selectAll().where { Jobs.id eq id }
            .map { rowToJob(it) }
            .singleOrNull()
    }

    override suspend fun findJobsByCreator(userId: UUID, page: Int, limit: Int): PaginatedResult<Job> =
        DatabaseFactory.dbQuery {
            val query = Jobs.selectAll().where { Jobs.createdBy eq userId }
            val total = query.count()
            val jobs = query
                .orderBy(Jobs.createdAt, SortOrder.DESC)
                .limit(limit)
                .map { rowToJob(it) }
            PaginatedResult(
                data = jobs,
                page = page,
                limit = limit,
                total = total,
                totalPages = ceil(total.toDouble() / limit).toLong()
            )
        }

    override suspend fun findNearbyJobs(request: NearbyJobsRequest, userId: UUID): PaginatedResult<Job> =
        DatabaseFactory.dbQuery {
            // Get all open jobs
            val allJobs = Jobs.selectAll().where { Jobs.status eq JobStatus.OPEN }
                .map { rowToJob(it) }

            // Filter by distance and other criteria
            val nearbyJobs = allJobs.mapNotNull { job ->
                val distance = calculateDistance(
                    request.location.latitude,
                    request.location.longitude,
                    job.location.latitude,
                    job.location.longitude
                )
                if (distance <= request.radiusKm &&
                    (request.categories == null || job.category in request.categories) &&
                    (request.minBudget == null || job.budget >= request.minBudget) &&
                    (request.maxBudget == null || job.budget <= request.maxBudget)
                ) {
                    job.copy(distance = distance)
                } else null
            }.sortedBy { it.distance }

            val total = nearbyJobs.size.toLong()
            val start = ((request.page - 1) * request.limit).coerceAtMost(nearbyJobs.size)
            val end = (start + request.limit).coerceAtMost(nearbyJobs.size)
            val paginated = nearbyJobs.subList(start, end)

            PaginatedResult(
                data = paginated,
                page = request.page,
                limit = request.limit,
                total = total,
                totalPages = ceil(total.toDouble() / request.limit).toLong()
            )
        }

    override suspend fun updateJobStatus(jobId: UUID, status: JobStatus): Boolean = DatabaseFactory.dbQuery {
        Jobs.update({ Jobs.id eq jobId }) {
            it[Jobs.status] = status
            it[updatedAt] = Instant.now()
        } > 0
    }

    override suspend fun deleteJob(jobId: UUID, userId: UUID): Boolean = DatabaseFactory.dbQuery {
        Jobs.deleteWhere { (Jobs.id eq jobId) and (Jobs.createdBy eq userId) } > 0
    }

    override suspend fun createApplication(application: JobApplication): JobApplication = DatabaseFactory.dbQuery {
        JobApplications.insert {
            it[jobId] = UUID.fromString(application.jobId)
            it[userId] = UUID.fromString(application.userId)
            it[proposal] = application.proposal
            it[bidAmount] = application.bidAmount
            it[status] = application.status
            it[appliedAt] = Instant.parse(application.appliedAt)
        }
        application
    }

    override suspend fun findApplicationById(id: UUID): JobApplication? = DatabaseFactory.dbQuery {
        JobApplications.selectAll().where { JobApplications.id eq id }
            .map { rowToApplication(it) }
            .singleOrNull()
    }

    override suspend fun findApplicationsByJob(jobId: UUID, page: Int, limit: Int): PaginatedResult<JobApplication> =
        DatabaseFactory.dbQuery {
            val query = JobApplications.selectAll().where { JobApplications.jobId eq jobId }
            val total = query.count()
            val apps = query
                .orderBy(JobApplications.appliedAt, SortOrder.DESC)
                .limit(limit)
                .map { rowToApplication(it) }
            PaginatedResult(
                data = apps,
                page = page,
                limit = limit,
                total = total,
                totalPages = ceil(total.toDouble() / limit).toLong()
            )
        }

    override suspend fun findApplicationsByUser(userId: UUID, page: Int, limit: Int): PaginatedResult<JobApplication> =
        DatabaseFactory.dbQuery {
            val query = JobApplications.selectAll().where { JobApplications.userId eq userId }
            val total = query.count()
            val apps = query
                .orderBy(JobApplications.appliedAt, SortOrder.DESC)
                .limit(limit)
                .map { rowToApplication(it) }
            PaginatedResult(
                data = apps,
                page = page,
                limit = limit,
                total = total,
                totalPages = ceil(total.toDouble() / limit).toLong()
            )
        }

    override suspend fun updateApplicationStatus(applicationId: UUID, status: ApplicationStatus): Boolean =
        DatabaseFactory.dbQuery {
            JobApplications.update({ JobApplications.id eq applicationId }) {
                it[JobApplications.status] = status
            } > 0
        }

    override suspend fun findApplicationByJobAndUser(jobId: UUID, userId: UUID): JobApplication? =
        DatabaseFactory.dbQuery {
            JobApplications.selectAll().where {
                (JobApplications.jobId eq jobId) and (JobApplications.userId eq userId)
            }.map { rowToApplication(it) }.singleOrNull()
        }

    private fun rowToJob(row: ResultRow): Job = Job(
        id = row[Jobs.id].toString(),
        title = row[Jobs.title],
        description = row[Jobs.description],
        jobType = row[Jobs.jobType],
        category = row[Jobs.category],
        budget = row[Jobs.budget],
        budgetType = row[Jobs.budgetType],
        duration = row[Jobs.duration],
        location = Location(row[Jobs.latitude], row[Jobs.longitude]),
        address = row[Jobs.address],
        radiusKm = row[Jobs.radiusKm],
        status = row[Jobs.status],
        createdBy = row[Jobs.createdBy].toString(),
        createdAt = row[Jobs.createdAt].toString(),
        updatedAt = row[Jobs.updatedAt].toString()
    )

    private fun rowToApplication(row: ResultRow): JobApplication = JobApplication(
        id = row[JobApplications.id].toString(),
        jobId = row[JobApplications.jobId].toString(),
        userId = row[JobApplications.userId].toString(),
        proposal = row[JobApplications.proposal],
        bidAmount = row[JobApplications.bidAmount],
        status = row[JobApplications.status],
        appliedAt = row[JobApplications.appliedAt].toString()
    )

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }
}

