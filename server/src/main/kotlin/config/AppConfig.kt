package config

import kotlinx.serialization.*

@Serializable
data class AppConfig(
    val server: ServerConfig,
    val database: DatabaseConfig,
    val security: SecurityConfig,
    val airtel: AirtelConfig,
    val email: EmailConfig,
    val subscription: SubscriptionConfig
)

@Serializable
data class ServerConfig(
    val port: Int = 8080,
    val host: String = "0.0.0.0"
)

@Serializable
data class DatabaseConfig(
    val url: String = "",
    val driver: String = "",
    val user: String = "",
    val password: String = "",
    val poolSize: Int = 10
)

@Serializable
data class SecurityConfig(
    val jwtSecret: String = "ijfeopkp3or3jor20ri23-ir=320or=-3o2",
    val jwtIssuer: String = "job-platform",
    val jwtAudience: String = "job-platform-users",
    val jwtRealm: String = "Job Platform"
)

@Serializable
data class AirtelConfig(
    val clientId: String = "",
    val clientSecret: String = "",
    val apiUrl: String = "https://openapiuat.airtel.africa",
    val currency: String = "XAF",
    val country: String = "TD"
)

@Serializable
data class EmailConfig(
    val host: String = "localhost",
    val port: Int = 1025,
    val from: String = "noreply@jobplatform.td",
    val verificationUrl: String = "http://localhost:8080/api/auth/verify"
)

@Serializable
data class SubscriptionConfig(
    val price: Double = 3000.0,
    val durationDays: Int = 30,
    val trialDays: Int = 7
)

object ConfigManager {
    val config: AppConfig by lazy {
        AppConfig(
            ServerConfig(),
            DatabaseConfig(),
            SecurityConfig(),
            AirtelConfig(),
            EmailConfig(),
            SubscriptionConfig()
        )
    }
}
