package services

import models.InitializeTransactionRequest
import models.InitializeTransactionResponse
import models.PaystackConfig
import models.VerifyTransactionResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json

class PaystackService(
    private val httpClient: HttpClient,
    private val config: PaystackConfig
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun initializeTransaction(
        request: InitializeTransactionRequest
    ): InitializeTransactionResponse {
        val response: HttpResponse = httpClient.post("${config.baseUrl}/transaction/initialize") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer ${config.secretKey}")
            setBody(request)
        }

        return json.decodeFromString(response.bodyAsText())
    }

    suspend fun verifyTransaction(reference: String): VerifyTransactionResponse {
        val response: HttpResponse = httpClient.get("${config.baseUrl}/transaction/verify/$reference") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer ${config.secretKey}")
        }

        return json.decodeFromString(response.bodyAsText())
    }
}
