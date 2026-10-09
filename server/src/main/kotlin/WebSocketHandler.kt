import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.routing.routing
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.UUID

@Serializable
data class WebSocketMessage(
    val type: String,
    val data: String,
    val timestamp: Long = System.currentTimeMillis()
)

class WebSocketHandler {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val connections = mutableMapOf<UUID, WebSocketSession>()

    suspend fun connect(userId: UUID, session: DefaultWebSocketSession) {
        connections[userId] = session
        logger.info("User $userId connected")

        try {
            send(userId, WebSocketMessage("connected", "You are now connected"))
            for (frame in session.incoming) {
                frame as? Frame.Text ?: continue
                val text = frame.readText()
                handleMessage(userId, text)
            }
        } catch (e: ClosedReceiveChannelException) {
            logger.info("User $userId disconnected", e)
        } catch (e: Exception) {
            logger.error("WebSocket error for user $userId", e)
        } finally {
            connections.remove(userId)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun send(userId: UUID, message: WebSocketMessage) {
        val session = connections[userId]
        if (session != null && !session.outgoing.isClosedForSend) {
            session.outgoing.send(Frame.Text(Json.encodeToString(message)))
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun broadcast(message: WebSocketMessage, excludeUserId: UUID? = null) {
        connections.forEach { (userId, session) ->
            if (userId != excludeUserId && !session.outgoing.isClosedForSend) {
                session.outgoing.send(Frame.Text(Json.encodeToString(message)))
            }
        }
    }

    private suspend fun handleMessage(userId: UUID, text: String) {
        try {
            val message = Json.decodeFromString<WebSocketMessage>(text)
            when (message.type) {
                "ping" -> send(userId, WebSocketMessage("pong", ""))
                else -> logger.warn("Unknown message type: ${message.type}")
            }
        } catch (e: Exception) {
            logger.error("Error handling message", e)
            send(userId, WebSocketMessage("error", "Invalid message"))
        }
    }
}

fun Application.configureWebSocket() {

    val handler = WebSocketHandler()

    routing {
        authenticate("auth-jwt") {
            webSocket("/ws") {
                val principal = call.principal<JWTPrincipal>() ?: throw Unauthorized()
                val userId = UUID.fromString(principal.subject)
                handler.connect(userId, this)
            }
        }
    }
}
