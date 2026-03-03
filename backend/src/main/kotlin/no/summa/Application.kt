package no.summa

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.summa.plugins.*
import no.summa.services.*

fun main() {
    embeddedServer(Netty, port = 8083, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    val emailService = EmailService()

    // Konfigurer e-post (SMTP)
    val smtpHost = System.getenv("SMTP_HOST") ?: ""
    val smtpPort = System.getenv("SMTP_PORT")?.toIntOrNull() ?: 25
    val smtpUser = System.getenv("SMTP_USER") ?: ""
    val smtpPassword = System.getenv("SMTP_PASSWORD") ?: ""
    val smtpFrom = System.getenv("SMTP_FROM") ?: "noreply@summa.tommytv.no"
    val appDomain = System.getenv("APP_DOMAIN") ?: "summa.tommytv.no"
    val smtpAuthRequired = System.getenv("SMTP_AUTH_REQUIRED")?.toBoolean() ?: false
    if (smtpHost.isNotBlank()) {
        emailService.configure(
            host = smtpHost,
            port = smtpPort,
            user = smtpUser,
            password = smtpPassword,
            from = smtpFrom,
            domain = appDomain,
            requireAuth = smtpAuthRequired
        )
    }

    val authService = AuthService(emailService)
    val categoryService = CategoryService()
    val transactionService = TransactionService()
    val reportService = ReportService()

    // Plugins
    configureSerialization()
    configureSecurity()
    configureDatabase()
    configureAuth()
    install(CsrfProtection)
    configureRouting(
        authService = authService,
        categoryService = categoryService,
        transactionService = transactionService,
        reportService = reportService
    )

    log.info("Summa Summarum backend startet pa port 8083")
}
