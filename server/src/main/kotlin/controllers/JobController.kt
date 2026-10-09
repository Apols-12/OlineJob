package controllers

import ApiResponse
import AppException
import ApplyJobRequest
import CreateJobRequest
import JobApplicationResponse
import JobNotFound
import JobResponse
import LocationDto
import Unauthorized
import UpdateJobStatusRequest
import ValidationError
import models.Job
import models.JobApplication
import models.JobCategory
import models.Location
import models.NearbyJobsRequest
import services.JobService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.slf4j.LoggerFactory
import java.util.UUID

fun Route.jobController(
    jobService: JobService
) {
    val logger = LoggerFactory.getLogger(this::class.java)
    route("/api/jobs") {

        authenticate("auth-jwt") {
            post {
                try {
                    val userId = getUserIdFromCall(call)
                    val request = call.receive<CreateJobRequest>()
                    val job = jobService.createJob(userId.toString(), request)
                    call.respond(HttpStatusCode.Created,
                        ApiResponse(success = true, data = job.toResponse(), message = "Job created")
                    )
                } catch (e: AppException) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(success = false, error = e.message))
                } catch (e: Exception) {
                    logger.error("Create job error", e)
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse<Unit>(success = false, error = "Internal error"))
                }
            }

            get("/owners/jobs") {
                try {
                    val userId = getUserIdFromCall(call)
                    val page = call.parameters["page"]?.toIntOrNull() ?: 1
                    val limit = call.parameters["limit"]?.toIntOrNull() ?: 10

                    val result = jobService.getUserJobs(userId, page, limit)
                    call.respond(HttpStatusCode.OK, result)
                } catch (e: Unauthorized) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(success = false, error = e.message))
                } catch (e: Exception) {
                    logger.error("Failed to process the request", e)
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse<Unit>(success = false, error = "Internal error"))
                }
            }

            get("/job") {
                try {
                    getUserIdFromCall(call)

                    val jobId = call.parameters["jobId"]?.let { UUID.fromString(it) } ?: throw ValidationError("Missing jobId")
                    val job = jobService.getJobDetails(jobId)

                    call.respond(HttpStatusCode.OK, job)
                } catch (e: JobNotFound) {
                    logger.error("Job not found", e)
                    call.respond(HttpStatusCode.NotFound)
                } catch (e: ValidationError) {
                    logger.error("No provided job id", e)
                    call.respond(HttpStatusCode.BadRequest)
                } catch (e: Unauthorized) {
                    logger.error("Unauthorize access", e)
                    call.respond(HttpStatusCode.Unauthorized)
                } catch (e: Exception) {
                    logger.error("Error processing the request", e)
                    call.respond(HttpStatusCode.InternalServerError)
                }
            }

            put("/job/complete") {
                try {
                    val userId = getUserIdFromCall(call)
                    val jobId = call.parameters["jobId"]?.let { UUID.fromString(it) } ?: throw ValidationError("Missing jobId")

                    val completed = jobService.completeJob(jobId, userId)

                    if (completed){
                        call.respond(HttpStatusCode.OK)
                    } else {
                        call.respond(HttpStatusCode.BadRequest)
                    }
                } catch (e: JobNotFound) {
                    logger.error("Job not found", e)
                    call.respond(HttpStatusCode.NotFound)
                } catch (e: ValidationError) {
                    logger.error("No provided job id", e)
                    call.respond(HttpStatusCode.BadRequest)
                } catch (e: Unauthorized) {
                    logger.error("Unauthorize access", e)
                    call.respond(HttpStatusCode.Unauthorized)
                } catch (e: Exception) {
                    logger.error("Error processing the request", e)
                    call.respond(HttpStatusCode.InternalServerError)
                }
            }

            get("/jobs/nearby") {
                try {
                    val userId = getUserIdFromCall(call)
                    val lat = call.request.queryParameters["lat"]?.toDouble() ?: throw ValidationError("Latitude required")
                    val lng = call.request.queryParameters["lng"]?.toDouble() ?: throw ValidationError("Longitude required")
                    val radius = call.request.queryParameters["radius"]?.toDouble() ?: 50.0
                    val categories = call.request.queryParameters["categories"]?.split(",")
                        ?.map { JobCategory.valueOf(it) }
                    val page = call.request.queryParameters["page"]?.toInt() ?: 1
                    val limit = call.request.queryParameters["limit"]?.toInt() ?: 20

                    val request = NearbyJobsRequest(
                        location = Location(lat, lng),
                        radiusKm = radius,
                        categories = categories,
                        page = page,
                        limit = limit
                    )
                    val result = jobService.getNearbyJobs(userId, request)
                    call.respond(HttpStatusCode.OK, ApiResponse(success = true, data = result))
                } catch (e: AppException) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(success = false, error = e.message))
                } catch (e: Exception) {
                    logger.error("Nearby jobs error", e)
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse<Unit>(success = false, error = "Internal error"))
                }
            }

            post("/jobs/{id}/apply") {
                try {
                    val userId = getUserIdFromCall(call)
                    val jobId = UUID.fromString(call.parameters["id"])
                    val request = call.receive<ApplyJobRequest>()
                    val application = jobService.applyForJob(userId, jobId, request)
                    call.respond(HttpStatusCode.Created, ApiResponse(success = true, data = application.toResponse(), message = "Applied successfully"))
                } catch (e: AppException) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(success = false, error = e.message))
                } catch (e: Exception) {
                    logger.error("Apply job error", e)
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse<Unit>(success = false, error = "Internal error"))
                }
            }

            get("/jobs/{id}/applications") {
                try {
                    val userId = getUserIdFromCall(call)
                    val jobId = UUID.fromString(call.parameters["id"])
                    val page = call.request.queryParameters["page"]?.toInt() ?: 1
                    val limit = call.request.queryParameters["limit"]?.toInt() ?: 20
                    val result = jobService.getJobApplications(jobId, userId, page, limit)
                    call.respond(HttpStatusCode.OK, ApiResponse(success = true, data = result))
                } catch (e: AppException) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(success = false, error = e.message))
                } catch (e: Exception) {
                    logger.error("Get applications error", e)
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse<Unit>(success = false, error = "Internal error"))
                }
            }

            put("/applications/{id}/status") {
                try {
                    val userId = getUserIdFromCall(call)
                    val applicationId = UUID.fromString(call.parameters["id"])
                    val request = call.receive<UpdateJobStatusRequest>()
                    val updated = jobService.updateApplicationStatus(applicationId, userId, request.status)
                    call.respond(HttpStatusCode.OK, ApiResponse<Unit>(success = updated, message = "Status updated"))
                } catch (e: AppException) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(success = false, error = e.message))
                } catch (e: Exception) {
                    logger.error("Update application status error", e)
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse<Unit>(success = false, error = "Internal error"))
                }
            }
        }
    }
}

private fun getUserIdFromCall(call: ApplicationCall): UUID {
    val principal = call.principal<JWTPrincipal>() ?: throw Unauthorized()
    return UUID.fromString(principal["user_id"])
}

// Extension functions
private fun Job.toResponse(): JobResponse = JobResponse(
    id = id.toString(),
    title = title,
    description = description,
    jobType = jobType.name,
    category = category.name,
    budget = budget,
    budgetType = budgetType.name,
    duration = duration,
    location = LocationDto(location.latitude, location.longitude),
    address = address,
    radiusKm = radiusKm,
    status = status.name,
    createdBy = createdBy,
    createdAt = createdAt,
    updatedAt = updatedAt,
    distance = distance
)

private fun JobApplication.toResponse(): JobApplicationResponse = JobApplicationResponse(
    id = id.toString(),
    jobId = jobId,
    userId = userId,
    proposal = proposal,
    bidAmount = bidAmount,
    status = status.name,
    appliedAt = appliedAt
)
