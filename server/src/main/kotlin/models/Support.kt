package models

import kotlinx.serialization.Serializable

@Serializable
data class SupportTicket(
    val id: String? = null,
    val userId: String?,
    val title: String,
    val description: String,
    val status: String = "open",
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val userName: String? = null,
    val replies: List<TicketReply> = emptyList()
)

@Serializable
data class SupportTicketCreate(
    val title: String,
    val description: String,
    val status: String = "open"
)

@Serializable
data class TicketReply(
    val id: String? = null,
    val ticketId: String,
    val userId: String,
    val message: String,
    val isAdminReply: Boolean = false,
    val createdAt: String? = null,
    val userName: String? = null
)
@Serializable
data class TicketReplyCreate(
    val ticketId: String,
    val userId: String,
    val message: String,
    val isAdminReply: Boolean = false
)
