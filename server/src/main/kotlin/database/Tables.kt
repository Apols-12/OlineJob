package database

import models.ApplicationStatus
import models.BudgetType
import models.JobCategory
import models.JobStatus
import models.JobType
import models.PaymentStatus
import models.PaymentType
import models.PlanType
import models.SubscriptionStatus
import models.UserType
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.javatime.datetime
import org.jetbrains.exposed.v1.javatime.timestamp
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

object Users : Table("users") {
    val id = uuid("id").autoGenerate()
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val fullName = varchar("full_name", 255)
    val phone = varchar("phone", 20).nullable()
    val userType = enumerationByName("user_type", 20, UserType::class)
    val isEmailVerified = bool("is_email_verified").default(false)
    val verificationToken = varchar("verification_token", 100).nullable()
    val verificationExpires = timestamp("verification_expires").nullable()
    val resetToken = varchar("reset_token", 100).nullable()
    val resetExpires = timestamp("reset_expires").nullable()
    val latitude = double("latitude").nullable()
    val longitude = double("longitude").nullable()
    val address = text("address").nullable()
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }

    override val primaryKey = PrimaryKey(id)
}

object Portfolios : Table("portfolios") {
    val id = uuid("id").autoGenerate()
    val userId = reference("user_id", Users.id, onDelete = ReferenceOption.CASCADE).uniqueIndex()
    val bio = text("bio").nullable()
    val skills = text("skills").default("[]")
    val experienceYears = integer("experience_years").default(0)
    val rating = double("rating").default(0.0)
    val totalJobs = integer("total_jobs").default(0).nullable()
    val completedJobs = integer("completed_jobs").default(0)
    val imageUrl = text("portfolio_images").nullable()
    val longitude = double("longitude").nullable()
    val latitude = double("latitude").nullable()
    val address = text("address").nullable()
    val imageThumbnail = text("portfolio_image_thumbnail").nullable()
    val fileName = text("image_file_name").nullable()
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }

    override val primaryKey = PrimaryKey(id)
}


object Jobs : Table("jobs") {
    val id = uuid("id").autoGenerate()
    val title = varchar("title", 255)
    val description = text("description")
    val jobType = enumerationByName("job_type", 50, JobType::class)
    val category = enumerationByName("category", 50, JobCategory::class)
    val budget = double("budget")
    val budgetType = enumerationByName("budget_type", 20, BudgetType::class)
    val duration = varchar("duration", 100).nullable()
    val latitude = double("latitude")
    val longitude = double("longitude")
    val address = text("address")
    val radiusKm = double("radius_km").default(10.0)
    val status = enumerationByName("status", 20, JobStatus::class).default(JobStatus.OPEN)
    val createdBy = reference("created_by", Users.id, onDelete = ReferenceOption.CASCADE)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Instant.now() }

    override val primaryKey = PrimaryKey(id)
}

object JobApplications : Table("job_applications") {
    val id = uuid("id").clientDefault { UUID.randomUUID() }
    val jobId = reference("job_id", Jobs.id, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", Users.id, onDelete = ReferenceOption.CASCADE)
    val proposal = text("proposal")
    val bidAmount = double("bid_amount").nullable()
    val status = enumerationByName("status", 20, ApplicationStatus::class).default(ApplicationStatus.PENDING)
    val appliedAt = timestamp("applied_at").clientDefault { Instant.now() }

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("unique_application_per_job", jobId, userId)
    }
}

object Subscriptions : Table("subscriptions") {
    val id = uuid("id").autoGenerate()
    val userId = reference("user_id", Users.id, onDelete = ReferenceOption.CASCADE).uniqueIndex()
    val planType = enumerationByName("plan_type", 20, PlanType::class)
    val amount = double("amount")
    val startDate = timestamp("start_date")
    val endDate = timestamp("end_date")
    val status = enumerationByName("status", 20, SubscriptionStatus::class)
    val autoRenew = bool("auto_renew").default(true)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }

    override val primaryKey = PrimaryKey(id)
}

object Payments : Table("payments") {
    val id = uuid("id").autoGenerate()
    val userId = reference("user_id", Users.id, onDelete = ReferenceOption.CASCADE)
    val amount = decimal("amount", 10, 2)
    val currency = varchar("currency", 3).default("XAF")
    val transactionId = varchar("transaction_id", 100).uniqueIndex()
    val externalTransactionId = varchar("external_transaction_id", 100).nullable()
    val status = enumerationByName("status", 20, PaymentStatus::class)
    val paymentType = enumerationByName("payment_type", 20, PaymentType::class)
    val phoneNumber = varchar("phone_number", 20)
    val metadata = text("metadata").nullable()
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }

    override val primaryKey = PrimaryKey(id)
}


object Notifications : Table("notifications") {
    val id = uuid("id").autoGenerate()
    val userId = uuid("user_id").references(Users.id, onDelete = ReferenceOption.CASCADE)
    val title = varchar("title", 255)
    val message = text("message")
    val type = enumerationByName("type", 50, NotificationType::class)
    val isRead = bool("is_read").default(false)
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }

    override val primaryKey = PrimaryKey(id)
}

enum class NotificationType {
    JOB_APPLICATION, JOB_OFFER, MESSAGE, PAYMENT, SYSTEM
}

object SupportTickets : UUIDTable("support_tickets") {
    val userId = reference("user_id", Users.id, ReferenceOption.CASCADE)
    val title = varchar("title", 255)
    val description = text("description")
    val status = varchar("status", 50).default("open") // open, in_progress, resolved
    val createdAt = datetime("created_at").default(LocalDateTime.now())
    val updatedAt = datetime("updated_at").default(LocalDateTime.now())
}

object TicketReplies : UUIDTable("ticket_replies") {
    val ticketId = reference("ticket_id", SupportTickets.id, ReferenceOption.CASCADE)
    val userId = reference("user_id", Users.id, ReferenceOption.CASCADE)
    val message = text("message")
    val isAdminReply = bool("is_admin_reply").default(false)
    val createdAt = datetime("created_at").default(LocalDateTime.now())
}

object Messages : UUIDTable("messages") {
    val senderId = reference("sender_id",Users.id, ReferenceOption.CASCADE)
    val receiverId = reference("receiver_id", Users.id, ReferenceOption.NO_ACTION)
    val portfolioId = reference("portfolio_id", Portfolios.id).nullable()
    val content = text("content")
    val messageType = varchar("message_type", 50)
    val attachments = text("attachments").default("[]")
    val isRead = bool("is_read").default(false)
    val isDelivered = bool("is_delivered").default(false)
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now() }
    val readAt = datetime("read_at").nullable()
}

