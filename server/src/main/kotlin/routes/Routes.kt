package routes

import controllers.authController
import controllers.jobController
import controllers.messageController
import controllers.paymentController
import controllers.supportController
import io.ktor.openapi.OpenApiDoc
import io.ktor.openapi.OpenApiInfo
import io.ktor.server.application.Application
import io.ktor.server.http.content.staticResources
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.hide
import io.ktor.server.routing.openapi.plus
import io.ktor.server.routing.routing
import io.ktor.server.routing.routingRoot
import io.ktor.utils.io.ExperimentalKtorApi
import org.koin.ktor.ext.inject
import repos.MessageRepository
import repos.SupportRepository
import repos.UserRepository
import services.AuthService
import services.ImageService
import services.JobService
import services.PaymentService
import services.SubscriptionService
import kotlin.getValue

@OptIn(ExperimentalKtorApi::class)
fun Application.configureRoutes() {

    val authService: AuthService by inject()
    val userRepo: UserRepository by inject()
    val imageService: ImageService by inject()
    val subscriptionService: SubscriptionService by inject()
    val jobService: JobService by inject()
    val messageRepository: MessageRepository by inject()
    val paymentService: PaymentService by inject()
    val supportRepository: SupportRepository by inject()

    routing {

        authController(authService, userRepo, imageService, subscriptionService)

        supportController(supportRepository, userRepo)

        jobController(jobService)

        paymentController(paymentService, subscriptionService)

        messageController(userRepo, messageRepository)

        get("/health") {
            call.respondText("OK")
        }

        staticResources("/files", "uploads")

        get("/") {
            call.respondText("MotoTaxi Booking API is running!")
        }

        get("/doc.json") {
            val doc = OpenApiDoc(info = OpenApiInfo("My api", "1.0")) + call.application.routingRoot.descendants()
            call.respond(doc)
        }.hide()
    }
}
