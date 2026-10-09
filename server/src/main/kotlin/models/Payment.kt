package models

import kotlinx.serialization.*

@Serializable
data class Subscription(
    val id: String? = null,
    val userId: String,
    val planType: PlanType = PlanType.BASIC,
    val amount: Double,
    val startDate: String,
    val endDate: String,
    val status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
    val autoRenew: Boolean = true,
    val createdAt: String
)

@Serializable
data class Payment(
    val id: String? = null,
    val userId: String,
    val amount: Double,
    val currency: String = "XAF",
    val transactionId: String,
    val externalTransactionId: String? = null,
    val status: PaymentStatus,
    val paymentType: PaymentType,
    val phoneNumber: String,
    val metadata: Map<String, String> = emptyMap(),
    val createdAt: String
)

data class PaymentRequest(
    val phoneNumber: String,
    val amount: Double? = null
)


@Serializable
data class PaystackConfig(
    val secretKey: String,
    val baseUrl: String = "https://api.paystack.co"
)

@Serializable
data class InitializeTransactionRequest(
    val email: String,
    val amount: String,
    val reference: String? = null,
    val callback_url: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class InitializeTransactionResponse(
    val status: Boolean,
    val message: String,
    val data: TransactionData?
)

@Serializable
data class TransactionData(
    val authorization_url: String,
    val access_code: String,
    val reference: String
)

@Serializable
data class VerifyTransactionResponse(
    val status: Boolean,
    val message: String,
    val data: TransactionDetails?
)

@Serializable
data class TransactionDetails(
    val id: Long,
    val reference: String,
    val amount: Int,
    val channel: String,
    val currency: String,
    val status: String,
    val paid_at: String? = null,
    val metadata: TransactionMetadata? = null
)

@Serializable
data class TransactionMetadata(
    val property_id: String? = null,
    val user_id: String? = null
)

@Serializable
data class PaymentTransaction(
    val id: String,
    val userId: String,
    val propertyId: String? = null,
    val reference: String,
    val amount: Double,
    val currency: String = "NGN",
    val status: String,
    val paymentMethod: String? = null,
    val paystackTransactionId: Long? = null,
    val paidAt: String? = null,
    val createdAt: String
)

@Serializable
data class InitializePaymentRequest(
    val propertyId: String? = null,
    val amount: Double,
    val email: String
)

@Serializable
data class InitializePaymentResponse(
    val authorizationUrl: String,
    val reference: String,
    val accessCode: String
)


enum class PlanType { BASIC }
enum class SubscriptionStatus { ACTIVE, EXPIRED, CANCELLED, TRIAL }
enum class PaymentStatus { PENDING, SUCCESSFUL, FAILED, CANCELLED }
enum class PaymentType { SUBSCRIPTION, JOB_PAYMENT, WITHDRAWAL }
