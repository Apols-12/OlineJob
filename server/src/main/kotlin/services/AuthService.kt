package services

import at.favre.lib.crypto.bcrypt.BCrypt
import EmailNotVerified
import InvalidCredentials
import LoginRequest
import RegisterRequest
import UserAlreadyExists
import UserNotFound
import config.SecurityConfigs
import models.Location
import models.User
import repos.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class AuthService : KoinComponent {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val userRepository: UserRepository by inject()
    private val emailService: EmailService by inject()

    suspend fun register(request: RegisterRequest): User {
        val existing = userRepository.findUserByEmail(request.email)
        if (existing != null) throw UserAlreadyExists()

        val passwordHash = BCrypt.withDefaults().hashToString(12, request.password.toCharArray())

        val user = User(
            email = request.email,
            fullName = request.fullName,
            phone = request.phone,
            userType = request.userType,
            location = request.latitude?.let { lat ->
                request.longitude?.let { lon -> Location(lat, lon) }
            },
            address = request.address,
            createdAt = Instant.now().toString(),
            updatedAt = Instant.now().toString()
        )

        val created = userRepository.createUser(user, passwordHash)

        val token = UUID.randomUUID().toString()
        val expiresAt = Instant.now().plus(24, ChronoUnit.HOURS)
        userRepository.saveVerificationToken(UUID.fromString(created.id), token, expiresAt)

        withContext(Dispatchers.IO) {
            try {
                emailService.sendVerificationEmail(created.email, token, UUID.fromString(created.id))
            } catch (e: Exception) {
                logger.error("Failed to send verification email", e)
            }
        }

        return created
    }

    suspend fun login(request: LoginRequest): Pair<User, String> {
        val user = userRepository.findUserByEmail(request.email) ?: throw InvalidCredentials()
        if (!user.isEmailVerified) throw EmailNotVerified()

        val hash = userRepository.getPasswordHash(UUID.fromString(user.id)) ?: throw InvalidCredentials()
        val valid = BCrypt.verifyer().verify(request.password.toCharArray(), hash).verified
        if (!valid) throw InvalidCredentials()

        val token = SecurityConfigs.generateToken(UUID.fromString(user.id), user.userType.name)
        return Pair(user, token)
    }

    suspend fun verifyEmail(token: String): Boolean = userRepository.verifyUser(token)

    suspend fun requestPasswordReset(email: String): Boolean {
        val user = userRepository.findUserByEmail(email) ?: return true // don't reveal existence
        val token = UUID.randomUUID().toString().substring(0, 5)
        val expiresAt = Instant.now().plus(1, ChronoUnit.HOURS)
        val saved = userRepository.saveResetToken(email, token, expiresAt)
        if (saved) {
            withContext(Dispatchers.IO) {
                try {
                    emailService.sendPasswordResetEmail(user.email, token)
                } catch (e: Exception) {
                    logger.error("Failed to send reset email", e)
                    return@withContext false
                }
            }
        }
        return saved
    }

    suspend fun resetPassword(token: String, newPassword: String): Boolean {
        val hash = BCrypt.withDefaults().hashToString(12, newPassword.toCharArray())
        return userRepository.resetPassword(token, hash)
    }

    suspend fun getCurrentUser(userId: UUID): User =
        userRepository.findUserById(userId) ?: throw UserNotFound()

    suspend fun updateLocation(userId: UUID, location: Location): Boolean =
        userRepository.updateLocation(userId, location)
}
