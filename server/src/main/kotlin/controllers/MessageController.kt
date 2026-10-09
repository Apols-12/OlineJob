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
import io.ktor.server.routing.put
import models.MessageCreate
import repos.MessageRepository
import repos.UserRepository
import java.util.UUID


fun Route.messageController(
    userRepo: UserRepository,
    messageRepo: MessageRepository
) {
    authenticate("auth-jwt") {
        // Send message
        post {
            val senderId = getUserIdFromCall(call)

            try {
                val messageCreate = call.receive<MessageCreate>()

                // Validate receiver exists
                val receiver = userRepo.findUserById(UUID.fromString(messageCreate.receiverId))
                if (receiver == null || !receiver.isEmailVerified) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Receiver not found"))
                    return@post
                }

                // Check if sender is trying to message themselves
                if (senderId == UUID.fromString(messageCreate.receiverId)) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Cannot send message to yourself"))
                    return@post
                }

                // Validate freight if provided
                messageCreate.portfolioId?.let { portfolioId ->
                    // In a real implementation, you'd check if both users are involved in this freight
                }

                val message = messageRepo.createMessage(messageCreate, senderId.toString())
                if (message == null) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to send message"))
                    return@post
                }

                call.respond(HttpStatusCode.Created, message)

            } catch (e: Exception) {
                e.printStackTrace()
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Invalid message data"))
            }
        }

        // Get conversation between two users
        get("/conversation/{userId}") {
            try {

                val currentUserId = getUserIdFromCall(call).toString()
                val otherUserId = call.parameters["userId"]

                if (otherUserId == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid request"))
                    return@get
                }

                val portfolioId = call.request.queryParameters["portfolioId"]

                val conversation = messageRepo.getConversation(currentUserId, otherUserId, portfolioId)

                // Mark messages as read
                messageRepo.markAllAsRead(currentUserId, otherUserId)
                call.respond(HttpStatusCode.OK, conversation)

            } catch (e: Exception) {
                e.printStackTrace()
                call.respond(HttpStatusCode.InternalServerError, "Failed to process the request")
            }

        }

        // Get user's conversations
        get("/conversations") {
            try {
                val userId = getUserIdFromCall(call).toString()

                val conversations = messageRepo.getUserConversations(userId)
                call.respond(HttpStatusCode.OK, conversations)
            } catch (e: Exception) {
                e.printStackTrace()
                call.respond(HttpStatusCode.InternalServerError, "Failed to process the request")
            }
        }

        // Get unread message count
        get("/unread/count") {

            try {
                val userId = getUserIdFromCall(call).toString()

                val count = messageRepo.getUnreadCount(userId)
                call.respond(mapOf("count" to count))
            } catch (e: Exception) {
                e.printStackTrace()
                call.respond(HttpStatusCode.InternalServerError, "Failed to process the request")
            }
        }

        // Mark message as read
        put("/{id}/read") {
            try {
                val userId = getUserIdFromCall(call).toString()
                val messageId = call.parameters["id"]

                if (messageId == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid request"))
                    return@put
                }

                val message = messageRepo.getMessageById(messageId)
                if (message == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Message not found"))
                    return@put
                }

                // Only receiver can mark as read
                if (message.receiverId != userId) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Not authorized"))
                    return@put
                }

                val success = messageRepo.markAsRead(messageId)
                if (!success) {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to mark message as read"))
                    return@put
                }

                call.respond(HttpStatusCode.OK, mapOf("success" to "true"))

            } catch (e: Exception) {
                e.printStackTrace()
                call.respond(HttpStatusCode.InternalServerError, "Failed to process the request")
            }
        }

        // Get message by ID
        get("/{id}") {
            try {
                val userId = getUserIdFromCall(call).toString()
                val messageId = call.parameters["id"]

                if (messageId == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid request"))
                    return@get
                }

                val message = messageRepo.getMessageById(messageId)
                if (message == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Message not found"))
                    return@get
                }

                // Only sender or receiver can view message
                if (message.senderId != userId && message.receiverId != userId) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Not authorized"))
                    return@get
                }

                call.respond(HttpStatusCode.OK, message)

            } catch (e: Exception) {
                e.printStackTrace()
                call.respond(HttpStatusCode.InternalServerError, "Failed to process the request")
            }
        }
    }
}

private fun getUserIdFromCall(call: ApplicationCall): UUID {
    val principal = call.principal<JWTPrincipal>() ?: throw Unauthorized()
    return UUID.fromString(principal["user_id"])
}
