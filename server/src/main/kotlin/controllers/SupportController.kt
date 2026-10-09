package controllers

import Unauthorized
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
import io.ktor.server.routing.route
import models.SupportTicketCreate
import models.TicketReplyCreate
import models.UserType
import org.slf4j.LoggerFactory
import repos.SupportRepository
import repos.UserRepository
import java.util.UUID

fun Route.supportController(
    supportRepo: SupportRepository,
    userService: UserRepository
) {
    val logger = LoggerFactory.getLogger(this::class.java)

    route("/api/support") {

        authenticate("auth-jwt") {
            // Create Support Ticket
            post("/create") {
                try {
                    val userId = getUserIdFromCall(call).toString()
                    val supportTicket = call.receive<SupportTicketCreate>()

                    supportRepo.createSupportTicket(supportTicket, userId)

                    call.respond(HttpStatusCode.Created, "Created successfully")
                } catch (e: Unauthorized) {
                    logger.error("Error: ${e.localizedMessage}")
                    call.respond(HttpStatusCode.Unauthorized)
                } catch (e: Exception) {
                    logger.error("Error - High: ${e.cause?.message}")
                    call.respond(HttpStatusCode.InternalServerError, "Failed to process the request")
                }

            }

            get {
                try {
                    val userId = getUserIdFromCall(call).toString()

                    val supports = supportRepo.getSupportTicketsByUser(userId)
                    call.respond(HttpStatusCode.OK, supports)
                } catch (e: Unauthorized) {
                    logger.error("Illegal State: ${e.cause}")
                    call.respond(HttpStatusCode.Unauthorized)
                } catch (e: Exception) {
                    logger.error("An Exception: ${e.localizedMessage}")
                    call.respond(HttpStatusCode.InternalServerError, "Failed to process the request")
                }
            }

            // View Ticket Details
            get("/{id}") {
                try {
                    val userId = getUserIdFromCall(call)
                    val ticketId = call.parameters["id"]

                    val user = userService.findUserById(userId)

                    if (ticketId == null) {
                        call.respond(HttpStatusCode.BadRequest, "Missing ticket Id")
                        return@get
                    }

                    val ticket = supportRepo.getSupportTicketById(ticketId)

                    if (ticket == null || (ticket.userId != user?.id) || (user?.userType != UserType.ADMIN)) {
                        call.respond(HttpStatusCode.Unauthorized, "Have no access")
                        return@get
                    }

                    call.respond(HttpStatusCode.OK, ticket)
                }  catch (e: Unauthorized) {
                    logger.error("Exception happen: ${e.localizedMessage}")
                    call.respond(HttpStatusCode.Unauthorized, "UnAuthorize access")
                } catch (e: Exception) {
                    logger.error("Excpt: ${e.localizedMessage}")
                    call.respond(HttpStatusCode.InternalServerError)
                }
            }

            // Add Ticket Reply
            post("/replies") {

                try {
                    val userId = getUserIdFromCall(call)
                    val ticket = call.receive<TicketReplyCreate>()


                    val user = userService.findUserById(userId)

                    if (user?.userType != UserType.ADMIN || UUID.fromString(ticket.userId) != userId) {
                        call.respond(HttpStatusCode.Unauthorized, "Have no access")
                    }
                    if (user?.userType == UserType.ADMIN) {
                        // If admin is replying, mark as admin and update ticket status to in_progress
                        //We can optimice it by checking if the status is in_progress before updating it
                        val newTicket = ticket.copy(isAdminReply = true)
                        supportRepo.addTicketReply(newTicket)
                        supportRepo.updateTicketStatus(ticket.ticketId, "in_progress")
                    }

                    supportRepo.addTicketReply(ticket)

                    call.respond(HttpStatusCode.OK, "Reply successful")
                } catch (e: Unauthorized) {
                    e.printStackTrace()
                    call.respond(HttpStatusCode.Unauthorized)
                } catch (e: Exception) {
                    e.printStackTrace()
                    call.respond(HttpStatusCode.InternalServerError)
                }

            }

            // Resolve Ticket (Admin only)
            post("admin/{id}/resolve") {
                try {
                    val userId = getUserIdFromCall(call)

                    val user = userService.findUserById(userId)
                    if (user?.userType != UserType.ADMIN) {
                        call.respond(HttpStatusCode.Unauthorized, "Not allowed to access")
                        return@post
                    }

                    val ticketId = call.parameters["id"] ?: throw IllegalArgumentException("missing param")

                    supportRepo.updateTicketStatus(ticketId, "resolved")

                    call.respond(HttpStatusCode.OK, "Update successfully")

                } catch (e: Unauthorized) {
                    e.printStackTrace()
                    call.respond(HttpStatusCode.Unauthorized)
                } catch (e: Exception) {
                    e.printStackTrace()
                    call.respond(HttpStatusCode.InternalServerError, "Failed to process the request")
                }
            }

            // Admin Support Management
            get("/admin") {
                try {
                    val userId = getUserIdFromCall(call)

                    val user = userService.findUserById(userId)
                    if (user?.userType != UserType.ADMIN) {
                        call.respond(HttpStatusCode.Unauthorized, "Not allowed to access")
                        return@get
                    }

                    val allTickets = supportRepo.getAllSupportTickets()
                    call.respond(HttpStatusCode.OK, allTickets)

                } catch (e: Unauthorized) {
                    e.printStackTrace()
                    call.respond(HttpStatusCode.Unauthorized, "Unauthorize access")
                } catch (e: Exception) {
                    e.printStackTrace()
                    call.respond(HttpStatusCode.InternalServerError, "Failed to perform the operation")
                }
            }
        }
    }
}

private fun getUserIdFromCall(call: ApplicationCall): UUID {
    val principal = call.principal<JWTPrincipal>() ?: throw Unauthorized()
    return UUID.fromString(principal["user_id"])
}
