import config.SecurityConfigs.algorithm
import com.auth0.jwt.JWT
import config.ConfigManager
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.response.respond

fun Application.configureAth() {
    val config = ConfigManager.config.security

    install(Authentication) {
        jwt("auth-jwt") {
            realm = config.jwtRealm
            verifier(
                JWT.require(algorithm)
                    .withIssuer(config.jwtIssuer)
                    .withAudience(config.jwtAudience)
                    .build()
            )
            validate { credential ->
                if (credential.payload.getClaim("userId").asString() != null) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, "Invalid or expired token")
            }
        }
    }

}