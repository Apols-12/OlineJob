package models

import kotlinx.serialization.*

@Serializable
data class Message(
    val id: String,
    val senderId: String,
    val receiverId: String,
    val portfolioId: String? = null,
    val content: String,
    val messageType: MessageType,
    val attachments: List<String> = emptyList(),
    val isRead: Boolean,
    val isDelivered: Boolean,
    val createdAt: String,
    val readAt: String? = null
)

@Serializable
data class MessageCreate(
    val receiverId: String,
    val portfolioId: String? = null,
    val bidId: Int? = null,
    val content: String,
    val messageType: MessageType = MessageType.TEXT,
    val attachments: List<String> = emptyList()
)

@Serializable
data class WebSocketMessage(
    val type: String,
    val data: String,
    val senderId: Int? = null,
    val receiverId: Int? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class ChatConversation(
    val userId: String,
    val userName: String,
    val lastMessage: String,
    val lastMessageTime: String,
    val unreadCount: Int,
    val portfolioId: String? = null
)

@Serializable
enum class MessageType {
    TEXT,
    IMAGE,
    DOCUMENT,
    BID_NOTIFICATION,
    STATUS_UPDATE,
    PAYMENT_NOTIFICATION,
    SYSTEM_ALERT,
    LOCATION_SHARE
}

