package repos

import database.DatabaseFactory
import database.Messages
import kotlinx.serialization.json.Json
import models.ChatConversation
import models.Message
import models.MessageCreate
import models.MessageType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.LocalDateTime
import java.util.UUID


interface MessageRepository {
    suspend fun createMessage(messageCreate: MessageCreate, senderId: String): Message?
    suspend fun getMessageById(id: String): Message?
    suspend fun getConversation(user1Id: String, user2Id: String, portfolioId: String? = null): List<Message>
    suspend fun getUserConversations(userId: String): List<ChatConversation>
    suspend fun markAsRead(messageId: String): Boolean
    suspend fun markAsDelivered(messageId: String): Boolean
    suspend fun markAllAsRead(userId: String, senderId: String): Boolean
    suspend fun getUnreadCount(userId: String): Int

}


class MessageRepositoryImpl: MessageRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    override suspend fun createMessage(messageCreate: MessageCreate, senderId: String): Message? = DatabaseFactory.dbQuery {
        val attachmentsJson = json.encodeToString(messageCreate.attachments)

        val id = Messages.insertAndGetId {
            it[Messages.senderId] = UUID.fromString(senderId)
            it[receiverId] = UUID.fromString(messageCreate.receiverId)
            it[portfolioId] = UUID.fromString(messageCreate.portfolioId)
            it[content] = messageCreate.content
            it[messageType] = messageCreate.messageType.name
            it[attachments] = attachmentsJson
            it[isRead] = false
            it[isDelivered] = false
        }.value

        getMessageById(id.toString())
    }

    override suspend fun getMessageById(id: String): Message? = DatabaseFactory.dbQuery {
        Messages.selectAll().where { Messages.id eq UUID.fromString(id) }.map { rowToMessage(it) }.singleOrNull()
    }

    override suspend fun getConversation(user1Id: String, user2Id: String, portfolioId: String?): List<Message> = DatabaseFactory.dbQuery {
        var query = ((Messages.senderId eq UUID.fromString(user1Id)) and (Messages.receiverId eq UUID.fromString(user2Id))) or
                ((Messages.senderId eq UUID.fromString(user2Id)) and (Messages.receiverId eq UUID.fromString(user1Id)))

        portfolioId?.let { query = query and (Messages.portfolioId eq UUID.fromString(it)) }

        Messages.selectAll().where { query }
            .orderBy(Messages.createdAt to SortOrder.ASC)
            .map { rowToMessage(it) }
    }

    override suspend fun getUserConversations(userId: String): List<ChatConversation> = DatabaseFactory.dbQuery {
        // Get distinct users who have conversations with this user

        val id = userId.let { UUID.fromString(it) }
        val senderMessages = Messages
            .selectAll().where { Messages.senderId eq id }
            .distinctBy { it[Messages.receiverId] }

        val receiverMessages = Messages
            .selectAll().where { Messages.receiverId eq id }
            .distinctBy { it[Messages.senderId] }

        val userIds = (senderMessages.map { it[Messages.receiverId] } +
                receiverMessages.map { it[Messages.senderId] }).distinct()

        userIds.mapNotNull { otherUserId ->
            val userDAO = UserRepositoryImpl()
            val user = userDAO.findUserById(otherUserId) ?: return@mapNotNull null

            val lastMessage = Messages.selectAll().where {
                ((Messages.senderId eq UUID.fromString(userId)) and (Messages.receiverId eq otherUserId)) or
                        ((Messages.senderId eq otherUserId) and (Messages.receiverId eq UUID.fromString(userId)))
            }.orderBy(Messages.createdAt to SortOrder.DESC)
                .limit(1)
                .map { rowToMessage(it) }
                .firstOrNull()

            val unreadCount = Messages.selectAll().where {
                (Messages.receiverId eq UUID.fromString(userId)) and
                        (Messages.senderId eq otherUserId) and
                        (Messages.isRead eq false)
            }.count()

            ChatConversation(
                userId = otherUserId.toString(),
                userName = user.fullName,
                lastMessage = lastMessage?.content ?: "",
                lastMessageTime = (lastMessage?.createdAt ?: LocalDateTime.now()).toString(),
                unreadCount = unreadCount.toInt(),
                portfolioId = lastMessage?.portfolioId
            )
        }.sortedByDescending { it.lastMessageTime }
    }

    override suspend fun markAsRead(messageId: String): Boolean = DatabaseFactory.dbQuery {
        Messages.update({ Messages.id eq UUID.fromString(messageId) }) {
            it[isRead] = true
            it[readAt] = LocalDateTime.now()
        } > 0
    }

    override suspend fun markAsDelivered(messageId: String): Boolean = DatabaseFactory.dbQuery {
        Messages.update({ Messages.id eq UUID.fromString(messageId) }) {
            it[isDelivered] = true
        } > 0
    }

    override suspend fun markAllAsRead(userId: String, senderId: String): Boolean = DatabaseFactory.dbQuery {
        Messages.update({
            (Messages.receiverId eq UUID.fromString(userId)) and
                    (Messages.senderId eq UUID.fromString(senderId)) and
                    (Messages.isRead eq false)
        }) {
            it[isRead] = true
            it[readAt] = LocalDateTime.now()
        } > 0
    }

    override suspend fun getUnreadCount(userId: String): Int = DatabaseFactory.dbQuery {
        Messages.selectAll().where { (Messages.receiverId eq UUID.fromString(userId)) and (Messages.isRead eq false) }
            .count()
            .toInt()
    }

    private fun rowToMessage(row: ResultRow): Message {
        return Message(
            id = row[Messages.id].value.toString(),
            senderId = row[Messages.senderId].toString(),
            receiverId = row[Messages.receiverId].toString(),
            portfolioId = row[Messages.portfolioId]?.toString(),
            content = row[Messages.content],
            messageType = MessageType.valueOf(row[Messages.messageType]),
            attachments = json.decodeFromString(row[Messages.attachments]),
            isRead = row[Messages.isRead],
            isDelivered = row[Messages.isDelivered],
            createdAt = row[Messages.createdAt].toString(),
            readAt = row[Messages.readAt]?.toString()
        )
    }
}
