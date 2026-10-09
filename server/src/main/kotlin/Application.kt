import database.DatabaseFactory
import config.configureHttp
import config.configureKoin
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import org.koin.ktor.ext.inject
import repos.MessageRepository
import repos.SupportRepository
import repos.UserRepository
import routes.configureRoutes
import services.AuthService
import services.ImageService
import services.JobService
import services.PaymentService
import services.SubscriptionService
import kotlin.getValue


fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        module()
    }.start(wait = true)
}

fun Application.module() {
    DatabaseFactory.init()
    configureKoin()
    configureHttp()
    configureAth()
    configureMonitoring()
    configureWebSocket()
    configureRoutes()
}

