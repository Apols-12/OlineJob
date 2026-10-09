package services

import UserNotFound
import config.ConfigManager
import database.DatabaseFactory
import database.Subscriptions
import models.Payment
import models.PaymentType
import models.PlanType
import models.Subscription
import models.SubscriptionStatus
import repos.UserRepository
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class SubscriptionService : KoinComponent {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val userRepository: UserRepository by inject()
    private val paymentService: PaymentService by inject()

    private val config = ConfigManager.config.subscription

    suspend fun hasActiveSubscription(userId: UUID): Boolean {
        val user = userRepository.findUserById(userId) ?: throw UserNotFound()
        // trial period
        val daysSinceCreation = ChronoUnit.DAYS.between(Instant.parse(user.createdAt), Instant.now())
        if (daysSinceCreation <= config.trialDays) return true

        // check active subscription
        val subscription = DatabaseFactory.dbQuery {
            Subscriptions.selectAll().where {
                (Subscriptions.userId eq userId) and
                        (Subscriptions.status eq SubscriptionStatus.ACTIVE) and
                        (Subscriptions.endDate greater Instant.now())
            }.singleOrNull()
        }
        return subscription != null
    }

    suspend fun initiateSubscription(userId: UUID, phoneNumber: String): Payment {
        return paymentService.initiatePayment(
            userId = userId,
            amount = config.price,
            paymentType = PaymentType.SUBSCRIPTION,
            phoneNumber = phoneNumber,
            metadata = mapOf(
                "plan_type" to "BASIC",
                "duration_days" to config.durationDays.toString()
            )
        )
    }

    suspend fun activateSubscription(userId: UUID, transactionId: String) {
        val now = Instant.now()
        val endAte = now.plus(config.durationDays.toLong(), ChronoUnit.DAYS)

        DatabaseFactory.dbQuery {
            Subscriptions.insert {
                it[Subscriptions.userId] = userId
                it[planType] = PlanType.BASIC
                it[amount] = config.price
                it[startDate] = now
                it[endDate] = endAte
                it[status] = SubscriptionStatus.ACTIVE
                it[autoRenew] = true
                it[createdAt] = now
            }
        }

        logger.info("Subscription activated for user $userId, transaction: $transactionId")
    }

    suspend fun getSubscriptionStatus(userId: UUID): Subscription? {
        return DatabaseFactory.dbQuery {
            Subscriptions.selectAll().where {
                (Subscriptions.userId eq userId) and
                        (Subscriptions.status eq SubscriptionStatus.ACTIVE) and
                        (Subscriptions.endDate greater Instant.now())
            }.singleOrNull()?.let { row ->
                Subscription(
                    id = row[Subscriptions.id].toString(),
                    userId = row[Subscriptions.userId].toString(),
                    planType = row[Subscriptions.planType],
                    amount = row[Subscriptions.amount],
                    startDate = row[Subscriptions.startDate].toString(),
                    endDate = row[Subscriptions.endDate].toString(),
                    status = row[Subscriptions.status],
                    autoRenew = row[Subscriptions.autoRenew],
                    createdAt = row[Subscriptions.createdAt].toString()
                )
            }
        }
    }
}
