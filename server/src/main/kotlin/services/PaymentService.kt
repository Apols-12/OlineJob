package services

import PaymentFailed
import database.DatabaseFactory
import database.Payments
import models.Payment
import models.PaymentStatus
import models.PaymentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

class PaymentService : KoinComponent {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val airtelService: AirtelMoneyService by inject()
    private val notificationService: NotificationService by inject()
    private val subscriptionService: SubscriptionService by inject()

    suspend fun initiatePayment(
        userId: UUID,
        amount: Double,
        paymentType: PaymentType,
        phoneNumber: String,
        metadata: Map<String, String> = emptyMap()
    ): Payment = withContext(Dispatchers.IO) {
        val transactionId = UUID.randomUUID().toString()
        val airtelResponse = airtelService.initiatePayment(
            amount = amount,
            phoneNumber = phoneNumber,
            transactionId = transactionId,
        )
        logger.debug("Payment initiated $phoneNumber")
        val externalId = airtelResponse["transaction_id"] as? String

        val payment = Payment(
            userId = userId.toString(),
            amount = amount,
            transactionId = transactionId,
            externalTransactionId = externalId,
            status = PaymentStatus.PENDING,
            paymentType = paymentType,
            phoneNumber = phoneNumber,
            metadata = metadata,
            createdAt = Instant.now().toString()
        )


        DatabaseFactory.dbQuery {
            Payments.insert {
                it[Payments.userId] = UUID.fromString(payment.userId)
                it[Payments.amount] = payment.amount.toBigDecimal()
                it[Payments.currency] = payment.currency
                it[Payments.transactionId] = payment.transactionId
                it[Payments.externalTransactionId] = payment.externalTransactionId
                it[Payments.status] = payment.status
                it[Payments.paymentType] = payment.paymentType
                it[Payments.phoneNumber] = payment.phoneNumber
                it[Payments.metadata] = Json.encodeToString(payment.metadata)
            }
        }

        logger.debug("Payment saved: ${payment.transactionId}")
        payment
    }

    suspend fun handleWebhook(payload: String, signature: String) {
        if (!airtelService.validateWebhookSignature(payload, signature)) {
            throw PaymentFailed("Invalid webhook signature")
        }

        val data = Json.decodeFromString<Map<String, Any>>(payload)
        val transactionId = data["transactionId"] as? String ?: return
        val status = airtelService.processWebhook(data)

        DatabaseFactory.dbQuery {
            val payment = Payments.selectAll().where { Payments.transactionId eq transactionId }.singleOrNull()
            if (payment != null) {
                Payments.update({ Payments.transactionId eq transactionId }) {
                    it[Payments.status] = status
                }

                if (status == PaymentStatus.SUCCESSFUL) {
                    val userId = payment[Payments.userId]
                    val paymentType = payment[Payments.paymentType]

                    when (paymentType) {
                        PaymentType.SUBSCRIPTION -> {
                            subscriptionService.activateSubscription(userId, transactionId)
                        }
                        else -> {
                            // handle other payment types
                        }
                    }
                    notificationService.notifyPaymentSuccess(userId, payment[Payments.amount].toDouble())
                }
            }
        }
    }
}
