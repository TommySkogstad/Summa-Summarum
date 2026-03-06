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
    val jwtSecret = System.getenv("JWT_SECRET")
        ?: throw IllegalStateException("JWT_SECRET miljøvariabel er påkrevd")

    val authService = AuthService(jwtSecret)
    val rateLimiter = RateLimiter()
    val auditLogService = AuditLogService()
    val documentParserService = DocumentParserService(System.getenv("ANTHROPIC_API_KEY"))
    val categoryService = CategoryService()
    val transactionService = TransactionService(documentParserService)
    val reportService = ReportService()
    val organizationService = OrganizationService()
    val exchangeRateService = ExchangeRateService()

    // Plugins
    configureSerialization()
    configureSecurity()
    configureAuth(authService)
    configureDatabase()
    configureRouting(
        authService = authService,
        rateLimiter = rateLimiter,
        auditLogService = auditLogService,
        categoryService = categoryService,
        transactionService = transactionService,
        reportService = reportService,
        organizationService = organizationService,
        exchangeRateService = exchangeRateService,
        documentParserService = documentParserService
    )

    log.info("Summa Summarum backend startet pa port 8083")
}
