package controllers

import ApiResponse
import AppException
import Unauthorized
import models.PaymentRequest
import services.PaymentService
import services.SubscriptionService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.slf4j.LoggerFactory
import java.util.UUID

fun Route.paymentController(
    paymentService: PaymentService,
    subscriptionService: SubscriptionService
) {
    val logger = LoggerFactory.getLogger(this::class.java)
    route("/api/payments") {

        authenticate("auth-jwt") {
            post("/subscribe") {
                try {
                    val userId = getUserIdFromCall(call)
                    val request = call.receive<PaymentRequest>()
                    val payment = subscriptionService.initiateSubscription(userId, request.phoneNumber)
                    call.respond(
                        HttpStatusCode.OK,
                        ApiResponse(
                            success = true,
                            message = "Payment initiated. Check your phone.",
                            data = payment
                        )
                    )
                } catch (e: AppException) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse<Unit>(success = false, error = e.message))
                } catch (e: Exception) {
                    logger.error("Subscription error", e)
                    call.respond(HttpStatusCode.InternalServerError, ApiResponse<Unit>(success = false, error = "Internal error"))
                }
            }
        }

        post("/webhooks/airtel") {
            try {
                val signature = call.request.headers["X-Callback-Signature"]
                val payload = call.receiveText()
                if (signature == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }
                paymentService.handleWebhook(payload, signature)
                call.respond(HttpStatusCode.OK)
            } catch (e: Exception) {
                logger.error("Webhook error", e)
                call.respond(HttpStatusCode.InternalServerError)
            }
        }
    }
}

private fun getUserIdFromCall(call: ApplicationCall): UUID {
    val principal = call.principal<JWTPrincipal>() ?: throw Unauthorized()
    return UUID.fromString(principal["user_id"])
}
