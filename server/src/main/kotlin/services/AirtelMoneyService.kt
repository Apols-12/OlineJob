package services

import PaymentFailed
import config.ConfigManager
import models.PaymentStatus
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.Base64


class AirtelMoneyService {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val config = ConfigManager.config.airtel

    private val client = HttpClient{
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
            })
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 30000
        }
        defaultRequest {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
        }
    }

    suspend fun initiatePayment(
        amount: Double,
        phoneNumber: String,
        transactionId: String
//        metadata: Map<String, String> = emptyMap()
    ): Map<String, Any> = withContext(Dispatchers.IO) {
        try {
            val token = getAccessToken()
            val formattedPhone = if (phoneNumber.startsWith("+")) phoneNumber else "+235$phoneNumber"

            val requestBody = mapOf(
                "reference" to transactionId,
                "subscriber" to mapOf(
                    "country" to config.country,
                    "currency" to config.currency,
                    "msisdn" to formattedPhone
                ),
                "transaction" to mapOf(
                    "amount" to amount,
                    "country" to config.country,
                    "currency" to config.currency,
                    "id" to transactionId
                )
            )

            logger.info("Initiating Airtel payment: $requestBody")

            val response: HttpResponse = client.post("${config.apiUrl}/merchant/v1/payments/") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $token")
                    append("X-Country", config.country)
                    append("X-Currency", config.currency)
                }
                setBody(requestBody)
            }

            if (response.status.isSuccess()) {
                val responseBody = response.body<Map<String, Any>>()
                logger.info("Airtel payment initiated: $responseBody")
                responseBody
            } else {
                val errorBody = response.body<String>()
                logger.error("Airtel payment failed: ${response.status} - $errorBody")
                throw PaymentFailed("Failed to initiate payment: ${response.status}")
            }
        } catch (e: Exception) {
            logger.error("Error initiating Airtel payment", e)
            throw PaymentFailed("Payment initiation failed: ${e.message}")
        }
    }

    suspend fun checkPaymentStatus(transactionId: String): Map<String, Any> = withContext(Dispatchers.IO) {
        try {
            val token = getAccessToken()
            val response: HttpResponse = client.get("${config.apiUrl}/standard/v1/payments/$transactionId") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $token")
                    append("X-Country", config.country)
                }
            }
            if (response.status.isSuccess()) {
                response.body()
            } else {
                throw PaymentFailed("Failed to check payment status")
            }
        } catch (e: Exception) {
            logger.error("Error checking payment status", e)
            throw PaymentFailed("Failed to check payment status")
        }
    }

    private suspend fun getAccessToken(): String = withContext(Dispatchers.IO) {
        try {
            val auth = Base64.getEncoder().encodeToString("${config.clientId}:${config.clientSecret}".toByteArray())
            val response: HttpResponse = client.post("${config.apiUrl}/auth/oauth2/token") {
                headers {
                    append(HttpHeaders.ContentType, "application/x-www-form-urlencoded")
                    append(HttpHeaders.Authorization, "Basic $auth")
                }
                setBody("grant_type=client_credentials")
            }
            if (response.status.isSuccess()) {
                val body = response.body<Map<String, Any>>()
                body["access_token"] as String
            } else {
                throw PaymentFailed("Failed to get access token")
            }
        } catch (e: Exception) {
            logger.error("Error getting access token", e)
            throw PaymentFailed("Failed to get access token")
        }
    }

    fun validateWebhookSignature(payload: String, signature: String): Boolean {
        // Implement signature validation as per Airtel documentation
        return true
    }

    fun processWebhook(data: Map<String, Any>): PaymentStatus {
        val status = data["status"] as? String ?: return PaymentStatus.PENDING
        return when (status.uppercase()) {
            "SUCCESSFUL", "TS" -> PaymentStatus.SUCCESSFUL
            "FAILED", "TF" -> PaymentStatus.FAILED
            else -> PaymentStatus.PENDING
        }
    }
}
