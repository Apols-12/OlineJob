package database

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory

object DatabaseFactory {
    private val logger = LoggerFactory.getLogger(this::class.java)
//    private lateinit var dataSource: HikariDataSource

    fun init() {
        /*val config = ConfigManager.config.database

        val hikariConfig = HikariConfig().apply {
            jdbcUrl = config.url
            driverClassName = config.driver
            username = config.user
            password = config.password
            maximumPoolSize = config.poolSize
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_READ_COMMITTED"
            validate()
        }

        dataSource = HikariDataSource(hikariConfig)*/

        // Run Flyway migrations
//        runMigrations()

        // Connect Exposed
        Database.connect("jdbc:h2:./test;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")

        // Create tables if needed (Flyway already handles, but we can still ensure)
        transaction {
            addLogger(StdOutSqlLogger)
            SchemaUtils.create(
                Users,
                Portfolios,
                Jobs,
                JobApplications,
                Subscriptions,
                Payments,
                SupportTickets,
                TicketReplies,
                Notifications
            )
        }

        logger.info("Database initialized successfully")
    }

    /*private fun runMigrations() {
        val flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
        flyway.migrate()
    }*/

    @Suppress("Deprecation")
    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

//    fun close() {
//        dataSource.close()
//    }
}
