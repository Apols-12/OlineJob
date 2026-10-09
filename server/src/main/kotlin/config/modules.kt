package config

import repos.JobRepository
import repos.JobRepositoryImpl
import repos.SupportRepository
import repos.SupportRepositoryImpl
import repos.UserRepository
import repos.UserRepositoryImpl
import services.AirtelMoneyService
import services.AuthService
import services.EmailService
import services.JobService
import services.NotificationService
import services.PaymentService
import services.SubscriptionService
import org.koin.dsl.module
import repos.MessageRepository
import repos.MessageRepositoryImpl
import services.ImageService

val appModule = module {
    // Repositories
    single<UserRepository> { UserRepositoryImpl() }
    single<JobRepository> { JobRepositoryImpl() }
    single<SupportRepository> { SupportRepositoryImpl() }
    single<MessageRepository> { MessageRepositoryImpl() }

    // External services
    single { AirtelMoneyService() }
    single { EmailService() }

    // Application services
    single { AuthService() }
    single { JobService() }
    single { ImageService() }
    single { PaymentService() }
    single { SubscriptionService() }
    single { NotificationService() }

}
