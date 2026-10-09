package services

import WebSocketHandler
import WebSocketMessage
import database.DatabaseFactory
import database.NotificationType
import database.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.exposed.v1.jdbc.insert
import org.slf4j.LoggerFactory
import java.util.UUID

class NotificationService {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val webSocketHandler = WebSocketHandler()
    private val scope = CoroutineScope(Dispatchers.IO)

    fun notifyJobApplication(jobCreatorId: UUID, jobId: UUID, applicantId: UUID) {
        scope.launch {
            try {
                DatabaseFactory.dbQuery {
                    Notifications.insert {
                        it[Notifications.userId] = jobCreatorId
                        it[title] = "New Job Application"
                        it[message] = "Someone applied to your job"
                        it[type] = NotificationType.JOB_APPLICATION
                    }
                }
                webSocketHandler.send(
                    jobCreatorId,
                    WebSocketMessage(
                        type = "job_application",
                        data = """{"jobId": "$jobId", "applicantId": "$applicantId"}"""
                    )
                )
            } catch (e: Exception) {
                logger.error("Failed to send job application notification", e)
            }
        }
    }

    fun notifyApplicationAccepted(applicantId: UUID, jobId: UUID) {
        scope.launch {
            try {
                DatabaseFactory.dbQuery {
                    Notifications.insert {
                        it[id] = UUID.randomUUID()
                        it[Notifications.userId] = applicantId
                        it[title] = "Application Accepted"
                        it[message] = "Your job application was accepted"
                        it[type] = NotificationType.JOB_OFFER
                    }
                }
                webSocketHandler.send(
                    applicantId,
                    WebSocketMessage(
                        type = "application_accepted",
                        data = """{"jobId": "$jobId"}"""
                    )
                )
            } catch (e: Exception) {
                logger.error("Failed to send acceptance notification", e)
            }
        }
    }

    fun notifyPaymentSuccess(userId: UUID, amount: Double) {
        scope.launch {
            try {
                DatabaseFactory.dbQuery {
                    Notifications.insert {
                        it[id] = UUID.randomUUID()
                        it[Notifications.userId] = userId
                        it[title] = "Payment Successful"
                        it[message] = "Your payment of $amount XAF was successful"
                        it[type] = NotificationType.PAYMENT
                    }
                }
                webSocketHandler.send(
                    userId,
                    WebSocketMessage(
                        type = "payment_success",
                        data = """{"amount": $amount, "currency": "XAF"}"""
                    )
                )
            } catch (e: Exception) {
                logger.error("Failed to send payment notification", e)
            }
        }
    }
}
