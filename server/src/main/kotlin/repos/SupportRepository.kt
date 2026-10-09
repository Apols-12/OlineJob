package repos

import database.DatabaseFactory.dbQuery
import database.SupportTickets
import database.TicketReplies
import database.Users
import models.SupportTicket
import models.SupportTicketCreate
import models.TicketReply
import models.TicketReplyCreate
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.LocalDateTime
import java.util.UUID

interface SupportRepository {
    suspend fun createSupportTicket(ticket: SupportTicketCreate, uid: String): String
    suspend fun getSupportTicketsByUser(userId: String): List<SupportTicket>
    suspend fun getAllSupportTickets(): List<SupportTicket>
    suspend fun getSupportTicketById(ticketId: String): SupportTicket?
    suspend fun addTicketReply(reply: TicketReplyCreate): String
    suspend fun updateTicketStatus(ticketId: String, status: String): Int
}

class SupportRepositoryImpl: SupportRepository {
    // Support ticket methods

    override suspend fun createSupportTicket(ticket: SupportTicketCreate, uid: String): String = dbQuery {
        SupportTickets.insert {
            it[userId] = UUID.fromString(uid)
            it[title] = ticket.title
            it[description] = ticket.description
            it[status] = ticket.status
        }[SupportTickets.id].toString()
    }

    override suspend fun getSupportTicketsByUser(userId: String): List<SupportTicket> = dbQuery {
        (SupportTickets innerJoin Users)
            .selectAll().where { SupportTickets.userId eq UUID.fromString(userId) }
            .orderBy(SupportTickets.createdAt, SortOrder.DESC)
            .map { rowToSupportTicket(it) }
    }

    override suspend fun getAllSupportTickets(): List<SupportTicket> = dbQuery {
        try {
            (SupportTickets innerJoin Users)
                .selectAll()
                .orderBy(SupportTickets.createdAt, SortOrder.DESC)
                .map { rowToSupportTicket(it) }

        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun getSupportTicketById(ticketId: String): SupportTicket? = dbQuery {
        (SupportTickets innerJoin Users)
            .selectAll().where { SupportTickets.id eq UUID.fromString(ticketId) }
            .singleOrNull()?.let { row ->
                val replies = (TicketReplies innerJoin Users)
                    .selectAll().where { TicketReplies.ticketId eq UUID.fromString(ticketId) }
                    .orderBy(TicketReplies.createdAt)
                    .map { rowToTicketReply(it) }

                rowToSupportTicket(row).copy(replies = replies)
            }
    }

    override suspend fun addTicketReply(reply: TicketReplyCreate): String = dbQuery {
        TicketReplies.insert {
            it[ticketId] = UUID.fromString(reply.ticketId)
            it[userId] = UUID.fromString(reply.userId)
            it[message] = reply.message
            it[isAdminReply] = reply.isAdminReply
        }[TicketReplies.id].toString()
    }

    override suspend fun updateTicketStatus(ticketId: String, status: String) = dbQuery {
        SupportTickets.update({ SupportTickets.id eq UUID.fromString(ticketId) }) {
            it[SupportTickets.status] = status
            it[SupportTickets.updatedAt] = LocalDateTime.now()
        }
    }

    private fun rowToSupportTicket(row: ResultRow): SupportTicket = SupportTicket(
        id = row[SupportTickets.id].toString(),
        userId = row[SupportTickets.userId].toString(),
        title = row[SupportTickets.title],
        description = row[SupportTickets.description],
        status = row[SupportTickets.status],
        createdAt = row[SupportTickets.createdAt].toString(),
        updatedAt = row[SupportTickets.updatedAt].toString(),
        userName = row[Users.fullName]
    )

    private fun rowToTicketReply(row: ResultRow): TicketReply = TicketReply(
        id = row[TicketReplies.id].toString(),
        ticketId = row[TicketReplies.ticketId].value.toString(),
        userId = row[TicketReplies.userId].toString(),
        message = row[TicketReplies.message],
        isAdminReply = row[TicketReplies.isAdminReply],
        createdAt = row[TicketReplies.createdAt].toString(),
        userName = row[Users.fullName]
    )
}