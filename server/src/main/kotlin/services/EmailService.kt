package services

import config.ConfigManager
import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.util.Properties
import java.util.UUID

class EmailService {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val config = ConfigManager.config.email

    private val props = Properties().apply {
        put("mail.smtp.host", config.host)
        put("mail.smtp.port", config.port.toString())
        put("mail.smtp.auth", "false")
        put("mail.smtp.starttls.enable", "false")
    }

    private val session = Session.getInstance(props)

    suspend fun sendVerificationEmail(to: String, token: String, userId: UUID) = withContext(Dispatchers.IO) {
        try {
            val verificationUrl = "${config.verificationUrl}?token=$token&userId=$userId"
            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(config.from, "Job Platform"))
                setRecipient(Message.RecipientType.TO, InternetAddress(to))
                subject = "Verify Your Email - Job Platform"
                val html = """
                    <html>
                    <body>
                        <h2>Email Verification</h2>
                        <p>Click the link below to verify your email:</p>
                        <a href="$verificationUrl">$verificationUrl</a>
                        <p>This link expires in 24 hours.</p>
                    </body>
                    </html>
                """.trimIndent()
                setContent(html, "text/html; charset=utf-8")
            }
            Transport.send(message)
            logger.info("Verification email sent to $to")
        } catch (e: Exception) {
            logger.error("Failed to send verification email", e)
            throw e
        }
    }

    suspend fun sendPasswordResetEmail(to: String, token: String) = withContext(Dispatchers.IO) {
        try {
            val resetUrl = "${config.verificationUrl}/reset?token=$token"
            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(config.from, "Job Platform"))
                setRecipient(Message.RecipientType.TO, InternetAddress(to))
                subject = "Password Reset - Job Platform"
                val html = """
                    <html>
                    <body>
                        <h2>Password Reset</h2>
                        <p>Click the link below to reset your password:</p>
                        <a href="$resetUrl">$resetUrl</a>
                        <p>This link expires in 1 hour.</p>
                    </body>
                    </html>
                """.trimIndent()
                setContent(html, "text/html; charset=utf-8")
            }
            Transport.send(message)
            logger.info("Password reset email sent to $to")
        } catch (e: Exception) {
            logger.error("Failed to send password reset email", e)
            throw e
        }
    }
}
