import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.callid.*

fun Application.configureMonitoring() {

    install(CallId) {
        header(HttpHeaders.XRequestId)
        verify { callId: String ->
            callId.isNotEmpty()
        }
    }
}
