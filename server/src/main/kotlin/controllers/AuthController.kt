package controllers

import ApiResponse
import AppException
import AuthResponse
import LocationDto
import LocationUpdateRequest
import LoginRequest
import RegisterRequest
import SubscriptionRequired
import SubscriptionResponse
import Unauthorized
import UserNotFound
import UserResponse
import ValidationError
import database.DatabaseFactory.dbQuery
import database.Portfolios
import models.CreatePortfolio
import models.Location
import models.Portfolio
import models.Subscription
import models.SubscriptionStatus
import models.User
import services.AuthService
import services.ImageService
import services.ImageValidationException
import services.SubscriptionService
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.utils.io.ExperimentalKtorApi
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.slf4j.LoggerFactory
import repos.UserRepository
import java.util.UUID


@OptIn(ExperimentalKtorApi::class)
fun Route.authController(
    authService: AuthService,
    userRepo: UserRepository,
    imageService: ImageService,
    subscriptionService: SubscriptionService
) {
    val logger = LoggerFactory.getLogger(this::class.java)

    route("/api/auth") {

        post("/register") {
            try {
                val request = call.receive<RegisterRequest>()
                val user = authService.register(request)
                call.respond(
                    HttpStatusCode.Created,
                    ApiResponse(
                        success = true,
                        message = "Registration successful. Please check your email.",
                        data = user.toResponse()
                    )
                )
            } catch (e: AppException) {
                call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(success = false, error = e.message))
            } catch (e: Exception) {
                logger.error("Registration error", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse<Unit>(success = false, error = "Internal error")
                )
            }
        }.describe {

        }

        post("/login") {
            try {
                val request = call.receive<LoginRequest>()
                val (user, token) = authService.login(request)
                val subscription = subscriptionService.getSubscriptionStatus(UUID.fromString(user.id))
                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse(
                        success = true,
                        data = AuthResponse(
                            token = token,
                            user = user.toResponse(),
                            subscription = subscription?.toResponse()
                        )
                    )
                )
            } catch (e: AppException) {
                call.respond(HttpStatusCode.Unauthorized, ApiResponse<Unit>(success = false, error = e.message))
            } catch (e: Exception) {
                logger.error("Login error", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse<Unit>(success = false, error = "Internal error")
                )
            }
        }

        get("/verify") {
            try {
                val token = call.request.queryParameters["token"] ?: throw ValidationError("Token missing")
                val verified = authService.verifyEmail(token)
                if (verified) {
                    call.respond(HttpStatusCode.OK, ApiResponse<Unit>(success = true, message = "Email verified"))
                } else {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(success = false, error = "Invalid or expired token"))
                }
            } catch (e: AppException) {
                call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(success = false, error = e.message))
            } catch (e: Exception) {
                logger.error("Verification error", e)
                call.respond(HttpStatusCode.InternalServerError, ApiResponse<Unit>(success = false, error = "Internal error"))
            }
        }

        authenticate("auth-jwt") {
            get("/me") {
                try {
                    val userId = getUserIdFromCall(call)
                    val user = authService.getCurrentUser(userId)
                    val subscription = subscriptionService.getSubscriptionStatus(UUID.fromString(user.id))?.toResponse()
                    call.respond(HttpStatusCode.OK, ApiResponse(success = true, data = user.toResponse(subscription)))
                } catch (e: AppException) {
                    call.respond(HttpStatusCode.NotFound, ApiResponse<Unit>(success = false, error = e.message))
                } catch (e: Exception) {
                    logger.error("Get user error", e)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<Unit>(success = false, error = "Internal error")
                    )
                }
            }

            put("/me/update") {
                try {
                    getUserIdFromCall(call)
                    val user = call.receive<User>()

                    userRepo.updateUser(user)
                    call.respond(HttpStatusCode.OK)
                } catch (e: Unauthorized) {
                    logger.error("Unauthorize: ${e.cause}")
                    call.respond(HttpStatusCode.Unauthorized)
                } catch (e: Exception) {
                    logger.error("Failed: ${e.cause?.message}")
                    call.respond(HttpStatusCode.InternalServerError)
                }
            }

            get("me/password/reset-token") {
                try {
                    val userId = getUserIdFromCall(call)
                    val user = authService.getCurrentUser(userId)

                    authService.requestPasswordReset(user.email).let {
                        if (it) {
                            call.respond(HttpStatusCode.OK)
                            logger.debug("Email send to user: ${user.fullName}")
                        } else {
                            call.respond(HttpStatusCode.BadRequest, "Failed to send the email")
                            logger.debug("Failed to send the email to ${user.fullName}")
                        }
                    }
                } catch (e: Unauthorized) {
                    logger.error("Unauthorize access: ${e.localizedMessage}")
                    call.respond(HttpStatusCode.Unauthorized)
                } catch (e: Exception) {
                    logger.error("Failed to process the request: ${e.localizedMessage}")
                }
            }

            get("me/password/reset") {
                try {
                    getUserIdFromCall(call)
                    val token = call.parameters["token"] ?: throw ValidationError("Token not provided")
                    val newPassword =
                        call.parameters["newPassword"] ?: throw ValidationError("New password not provided")

                    authService.resetPassword(token, newPassword)
                } catch (e: Unauthorized) {
                    logger.error("Unauthorize request", e)
                    call.respond(HttpStatusCode.Unauthorized)
                } catch (e: Exception) {
                    logger.error("Failed to process the request", e)
                    call.respond(HttpStatusCode.InternalServerError)
                }
            }

            put("/location") {
                try {
                    val userId = getUserIdFromCall(call)
                    val request = call.receive<LocationUpdateRequest>()
                    val location = Location(request.latitude, request.longitude)
                    val updated = authService.updateLocation(userId, location)
                    call.respond(
                        HttpStatusCode.OK,
                        ApiResponse<Unit>(
                            success = updated,
                            message = if (updated) "Location updated" else "Update failed"
                        )
                    )
                } catch (e: AppException) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(success = false, error = e.message))
                } catch (e: Exception) {
                    logger.error("Update location error", e)
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse<Unit>(success = false, error = "Internal error"))
                }
            }

            post("portfolio") {
                try {
                    val userId = getUserIdFromCall(call)
                    if (!subscriptionService.hasActiveSubscription(userId)) throw SubscriptionRequired()
                    val portfolio = call.receive<CreatePortfolio>()

                    userRepo.addPortfolio(portfolio)
                    call.respond(HttpStatusCode.OK)
                } catch (e: Unauthorized) {
                    logger.error("Unauthorize request", e)
                    call.respond(HttpStatusCode.Unauthorized)
                } catch (e: SubscriptionRequired) {
                    logger.error("Subscription needed", e)
                } catch (e: Exception) {
                    logger.error("Failed to process the request", e)
                    call.respond(HttpStatusCode.InternalServerError)
                }
            }

            put("/portfolio/update") {
                try {
                    val userId = getUserIdFromCall(call)
                    if (!subscriptionService.hasActiveSubscription(userId)) throw SubscriptionRequired()
                    val portfolio = call.receive<Portfolio>()
                    val updated = userRepo.updatePortfolio(portfolio)

                    if (updated) {
                        call.respond(HttpStatusCode.OK)
                    } else {
                        call.respond(HttpStatusCode.BadRequest)
                    }
                } catch (e: Unauthorized) {
                    logger.error("Unauthorize access", e)
                    call.respond(HttpStatusCode.Unauthorized)
                } catch (e: SubscriptionRequired) {
                    logger.error("Subscription needed", e)
                } catch (e: Exception) {
                    logger.error("Failed to process the request", e)
                    call.respond(HttpStatusCode.InternalServerError)
                }
            }

            post("/portfolio/rate") {
                try {
                    getUserIdFromCall(call)
                    val portfolioId = call.parameters["portfolioId"]?.let { UUID.fromString(it) } ?: throw ValidationError("Missing portfolioId")
                    val rating = call.parameters["rating"]?.toIntOrNull() ?: throw ValidationError("Missing rating")

                    val rated = userRepo.ratePortfolio(portfolioId, rating)

                    if (rated) {
                        call.respond(HttpStatusCode.OK)
                    } else {
                        call.respond(HttpStatusCode.BadRequest)
                    }
                } catch (e: Unauthorized) {
                    logger.error("Unauthorize access", e)
                    call.respond(HttpStatusCode.Unauthorized)
                } catch (e: ValidationError) {
                    logger.error("Missing params", e)
                    call.respond(HttpStatusCode.BadRequest)
                } catch (e: Exception) {
                    logger.error("Failed to process the request", e)
                    call.respond(HttpStatusCode.InternalServerError)
                }
            }

            post("/portfolio/image") {
                try {

                    val userId = getUserIdFromCall(call)
                    if (!subscriptionService.hasActiveSubscription(userId)) throw SubscriptionRequired()
                    // Verify user owns the property
                    val portfolio = userRepo.getUserPortfolio(userId)
                    if (portfolio == null || UUID.fromString(portfolio.userId) != userId) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("error" to "You don't have permission to add images to this property"))
                        return@post
                    }

                    val multipart = call.receiveMultipart()
                    var imageAdded: Boolean? = null

                    multipart.forEachPart { part ->
                        when (part) {
                            is PartData.FileItem -> {
                                @Suppress("DEPRECATION") val fileBytes = part.streamProvider().readBytes()
                                val fileName = part.originalFileName ?: "unknown"
                                val mimeType = part.contentType?.toString() ?: "application/octet-stream"

                                // Validate image
                                imageService.validateImage(fileBytes, mimeType)

                                // Save image
                                val saveResult = imageService.saveImage(fileBytes, fileName, mimeType)

                                // Check if this is the first image (set as primary)
                                dbQuery {
                                    Portfolios.selectAll().where { Portfolios.userId eq userId }.map { it[Portfolios.fileName] }
                                }.singleOrNull()?.let { imageService.deleteImage(it) }

                                // Save to database
                                imageAdded = userRepo.addPortfolioImage(
                                    userId = userId,
                                    image = saveResult
                                )

                                part.dispose()
                            }
                            else -> part.dispose()
                        }
                    }

                    if (imageAdded != null) {
                        call.respond(HttpStatusCode.Created)
                    } else {
                        call.respond(HttpStatusCode.BadRequest)
                    }
                } catch (e: ImageValidationException) {
                    logger.error("Image validation failed: ${e.localizedMessage}")
                    call.respond(HttpStatusCode.BadRequest)
                } catch (e: SubscriptionRequired) {
                    logger.error("Subscription needed", e)
                } catch (e: Exception) {
                    logger.error("Error processing the request: ${e.cause}")
                    call.respond(HttpStatusCode.InternalServerError)
                }
            }

            get("/portfolio") {
                try {
                    val id = getUserIdFromCall(call)
                    val userId = call.parameters["userId"]?.let { UUID.fromString(it) } ?: id

                    val portfolio = userRepo.getUserPortfolio(userId) ?: throw IllegalStateException("Portfolio not found")
                    call.respond(HttpStatusCode.OK, portfolio)
                } catch (e: Unauthorized) {
                    logger.error("Unauthorize access", e)
                    call.respond(HttpStatusCode.Unauthorized)
                } catch (e: ValidationError) {
                    logger.error("Missing params", e)
                    call.respond(HttpStatusCode.BadRequest)
                } catch (e: Exception) {
                    logger.error("Failed to process the request", e)
                    call.respond(HttpStatusCode.InternalServerError)
                }
            }

            get("/portfolio/user-info") {
                try {
                    getUserIdFromCall(call)
                    val userId = call.parameters["userId"]?.let { UUID.fromString(it) } ?: throw ValidationError("Missing portfolioId")

                    val user = userRepo.findUserById(userId) ?: throw UserNotFound()
                    call.respond(HttpStatusCode.OK, user)
                } catch (e: Unauthorized) {
                    logger.error("Unauthorize access", e)
                    call.respond(HttpStatusCode.Unauthorized)
                } catch (e: ValidationError) {
                    logger.error("Missing params", e)
                    call.respond(HttpStatusCode.BadRequest)
                } catch (e: Exception) {
                    logger.error("Failed to process the request", e)
                    call.respond(HttpStatusCode.InternalServerError)
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
private fun User.toResponse(subscriptionResponse: SubscriptionResponse? = null): UserResponse = UserResponse(
    id = id.toString(),
    email = email,
    fullName = fullName,
    phone = phone,
    userType = userType,
    isEmailVerified = isEmailVerified,
    location = location?.let { LocationDto(it.latitude, it.longitude) },
    address = address,
    subscriptionResponse = subscriptionResponse
)

private fun Subscription.toResponse(): SubscriptionResponse = SubscriptionResponse(
    id = id.toString(),
    planType = planType.name,
    amount = amount,
    startDate = startDate,
    endDate = endDate,
    status = status.name,
    isActive = status == SubscriptionStatus.ACTIVE
)
