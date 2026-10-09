package repos

import database.DatabaseFactory.dbQuery
import database.Portfolios
import database.Users
import models.CreatePortfolio
import models.Location
import models.PaginatedResult
import models.Portfolio
import models.SearchPortfolioFilters
import models.User
import services.ImageSaveResult
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

interface UserRepository {
    suspend fun createUser(user: User, passwordHash: String): User
    suspend fun findUserById(id: UUID): User?
    suspend fun findUserByEmail(email: String): User?
    suspend fun updateUser(user: User): User
    suspend fun saveVerificationToken(userId: UUID, token: String, expiresAt: Instant)
    suspend fun verifyUser(token: String): Boolean
    suspend fun saveResetToken(email: String, token: String, expiresAt: Instant): Boolean
    suspend fun resetPassword(token: String, newPasswordHash: String): Boolean
    suspend fun updateLocation(userId: UUID, location: Location): Boolean
    suspend fun getPasswordHash(userId: UUID): String?
    suspend fun getUserPortfolio(userId: UUID): Portfolio?
    suspend fun searchHandyMans(request: SearchPortfolioFilters): PaginatedResult<Portfolio>
    suspend fun addPortfolio(portfolio: CreatePortfolio): CreatePortfolio
    suspend fun addPortfolioImage(userId: UUID, image: ImageSaveResult): Boolean
    suspend fun updatePortfolio(portfolio: Portfolio): Boolean
    suspend fun ratePortfolio(portfolioId: UUID, rating: Int): Boolean
}

class UserRepositoryImpl : UserRepository {
    private val json = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }
    override suspend fun createUser(user: User, passwordHash: String): User = dbQuery {
        Users.insert {
            it[email] = user.email
            it[fullName] = user.fullName
            it[phone] = user.phone
            it[userType] = user.userType
            it[Users.passwordHash] = passwordHash
            it[latitude] = user.location?.latitude
            it[longitude] = user.location?.longitude
            it[address] = user.address
        }.let { _ ->
            user
        }
    }

    override suspend fun addPortfolio(portfolio: CreatePortfolio): CreatePortfolio = dbQuery {
        val user = findUserById(UUID.fromString(portfolio.userId))
        Portfolios.insert {
            it[userId] = UUID.fromString(portfolio.userId)
            it[bio] = portfolio.bio
            it[longitude] = user?.location?.longitude
            it[latitude] = user?.location?.latitude
            it[address] = user?.address
            it[skills] = json.encodeToString(portfolio.skills)
            it[experienceYears] = portfolio.experienceYears
        }
        portfolio
    }

    override suspend fun updatePortfolio(portfolio: Portfolio): Boolean = dbQuery {
        Portfolios.update({ Portfolios.id eq UUID.fromString(portfolio.userId)}) {
            it[bio] = portfolio.bio
            it[longitude] = portfolio.location?.longitude
            it[latitude] = portfolio.location?.latitude
            it[address] = portfolio.address
            it[skills] = json.encodeToString(portfolio.skills)
            it[experienceYears] = portfolio.experienceYears
        } > 0
    }

    override suspend fun ratePortfolio(portfolioId: UUID, rating: Int) = dbQuery {
        val rating = Portfolios.selectAll().where { Portfolios.id eq portfolioId }.map { it[Portfolios.rating] }.singleOrNull() ?: 0.0
        val newRating = (rating + rating) / 2.0
        newRating.let {
            Portfolios.update({ Portfolios.id eq portfolioId}) {
                it[Portfolios.rating] = newRating
            } > 0
        }
    }

    override suspend fun addPortfolioImage(userId: UUID, image: ImageSaveResult): Boolean = dbQuery {
        Portfolios.update({ Portfolios.userId eq userId} ) {
            it[imageUrl] = image.fileUrl
            it[imageThumbnail] = image.thumbnailUrl
            it[fileName] = image.fileName
        } > 0
    }


    override suspend fun getUserPortfolio(userId: UUID): Portfolio? = dbQuery {
        Portfolios.selectAll().where { Portfolios.userId eq userId }
            .map { rowToPortfolio(it) }
            .singleOrNull()
    }

    override suspend fun searchHandyMans(request: SearchPortfolioFilters): PaginatedResult<Portfolio> = dbQuery {

        val portfolios = Portfolios.selectAll().map { rowToPortfolio(it) }
        val nearbyHandyMans = portfolios.mapNotNull { handy ->
            val distance = request.location?.latitude?.let { lat1 ->
                request.location.longitude.let { long1 ->
                    handy.location?.latitude?.let { lat2 ->
                        handy.location.longitude.let { long2 ->
                            calculateDistance(
                                lat1,
                                long1,
                                lat2,
                                long2
                            )
                        }
                    }
                }
            }
            if (distance != null && distance <= request.kmRad &&
                (handy.skills.contains(request.skills)) &&
                (request.experiencesYears != null && handy.experienceYears >= request.experiencesYears) &&
                (request.rating != null && handy.rating >= request.rating )
            ) {
                handy.copy(distance = distance)
            } else null
        }.sortedBy { it.distance }

        val total = nearbyHandyMans.size
        val start = ((request.page - 1) * request.limit).coerceAtMost(total)
        val end = (start + request.limit).coerceAtMost(total)
        val paginated = nearbyHandyMans.subList(start, end)

        PaginatedResult(
            data = paginated,
            page = request.page,
            limit = request.limit,
            total = total.toLong(),
            totalPages = ceil(total.toDouble() / request.limit).toLong()
        )
    }

    override suspend fun findUserById(id: UUID): User? = dbQuery {
        Users.selectAll().where { Users.id eq id }
            .map { rowToUser(it) }
            .singleOrNull()
    }

    override suspend fun findUserByEmail(email: String): User? = dbQuery {
        Users.selectAll().where { Users.email eq email }
            .map { rowToUser(it) }
            .singleOrNull()
    }

    override suspend fun updateUser(user: User): User = dbQuery {
        Users.update({ Users.id eq UUID.fromString(user.id) }) {
            it[fullName] = user.fullName
            it[phone] = user.phone
            it[latitude] = user.location?.latitude
            it[longitude] = user.location?.longitude
            it[address] = user.address
            it[updatedAt] = Instant.now()
        }
        user.copy(updatedAt = Instant.now().toString())
    }

    override suspend fun saveVerificationToken(userId: UUID, token: String, expiresAt: Instant) {
        dbQuery {
            Users.update({ Users.id eq userId }) {
                it[verificationToken] = token
                it[verificationExpires] = expiresAt
            }
        }
    }

    override suspend fun verifyUser(token: String): Boolean = dbQuery {
        val user = Users.selectAll().where { Users.verificationToken eq token }
            .singleOrNull()
        if (user != null && user[Users.verificationExpires]?.isAfter(Instant.now()) == true) {
            Users.update({ Users.id eq user[Users.id] }) {
                it[isEmailVerified] = true
                it[verificationToken] = null
                it[verificationExpires] = null
            }
            true
        } else {
            false
        }
    }

    override suspend fun saveResetToken(email: String, token: String, expiresAt: Instant): Boolean =
        dbQuery {
            Users.update({ Users.email eq email }) {
                it[resetToken] = token
                it[resetExpires] = expiresAt
            } > 0
        }

    override suspend fun resetPassword(token: String, newPasswordHash: String): Boolean = dbQuery {
        val user = Users.selectAll().where { Users.resetToken eq token }
            .singleOrNull()
        if (user != null && user[Users.resetExpires]?.isAfter(Instant.now()) == true) {
            Users.update({ Users.id eq user[Users.id] }) {
                it[passwordHash] = newPasswordHash
                it[resetToken] = null
                it[resetExpires] = null
            }
            true
        } else {
            false
        }
    }

    override suspend fun updateLocation(userId: UUID, location: Location): Boolean = dbQuery {
        Users.update({ Users.id eq userId }) {
            it[latitude] = location.latitude
            it[longitude] = location.longitude
            it[updatedAt] = Instant.now()
        } > 0
    }

    override suspend fun getPasswordHash(userId: UUID): String? = dbQuery {
        Users.selectAll().where { Users.id eq userId }
            .singleOrNull()
            ?.let { it[Users.passwordHash] }
    }

    private fun rowToPortfolio(row: ResultRow): Portfolio {

        return Portfolio (
            id = row[Portfolios.id].toString(),
            userId = row[Portfolios.userId].toString(),
            bio = row[Portfolios.bio],
            skills = json.decodeFromString(row[Portfolios.skills]),
            experienceYears = row[Portfolios.experienceYears],
            rating = row[Portfolios.rating],
            completedJob = row[Portfolios.completedJobs],
            imageUrl = row[Portfolios.imageUrl],
            thumbnailUrl = row[Portfolios.imageThumbnail],
            location = row[Portfolios.latitude]?.let { lat ->
                row[Portfolios.longitude]?.let { long ->
                    Location(lat, long)
                }
            },
            address = row[Portfolios.address],
            filename = row[Portfolios.fileName],
            createdAt = row[Portfolios.createdAt].toString(),
            updatedAt = row[Portfolios.updatedAt].toString()
        )
    }
    private fun rowToUser(row: ResultRow): User = User(
        id = row[Users.id].toString(),
        email = row[Users.email],
        fullName = row[Users.fullName],
        phone = row[Users.phone],
        userType = row[Users.userType],
        isEmailVerified = row[Users.isEmailVerified],
        location = row[Users.latitude]?.let { lat ->
            row[Users.longitude]?.let { lon -> Location(lat, lon) }
        },
        address = row[Users.address],
        createdAt = row[Users.createdAt].toString(),
        updatedAt = row[Users.updatedAt].toString()
    )

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }
}

