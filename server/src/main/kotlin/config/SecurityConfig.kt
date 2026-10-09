package config

import AppException
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.*

object SecurityConfigs {
    private val config = ConfigManager.config.security

    val algorithm = Algorithm.HMAC256(config.jwtSecret) ?: throw AppException("Algorithm initialization failed")

    fun generateToken(userId: UUID, userType: String): String = JWT.create()
        .withSubject(userId.toString())
        .withIssuer(config.jwtIssuer)
        .withAudience(config.jwtAudience)
        .withClaim("userType", userType)
        .withExpiresAt(Date(System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000)) // 7 days
        .sign(algorithm)

}
