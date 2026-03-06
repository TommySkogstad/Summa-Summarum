package no.summa.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.routing.*
import no.summa.routes.*
import no.summa.services.*

fun Application.configureRouting(
    authService: AuthService,
    rateLimiter: RateLimiter,
    auditLogService: AuditLogService,
    categoryService: CategoryService,
    transactionService: TransactionService,
    reportService: ReportService,
    organizationService: OrganizationService,
    exchangeRateService: ExchangeRateService,
    documentParserService: DocumentParserService? = null
) {
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.ContentType)
        allowHeader("X-CSRF-Token")
        allowCredentials = true

        val allowedOrigins = System.getenv("ALLOWED_ORIGINS")
        if (!allowedOrigins.isNullOrBlank()) {
            allowedOrigins.split(",").map { it.trim() }.forEach { origin ->
                allowHost(origin.removePrefix("https://").removePrefix("http://"), schemes = listOf("https"))
            }
        } else {
            anyHost()
        }
    }

    routing {
        healthRoutes()
        route("/api") {
            // Offentlige auth-endepunkter
            authRoutes(authService, rateLimiter)

            // Beskyttede endepunkter
            authenticate("auth-jwt") {
                categoryRoutes(categoryService, authService, auditLogService)
                transactionRoutes(transactionService, documentParserService, authService, auditLogService)
                reportRoutes(reportService, exchangeRateService)
                organizationRoutes(organizationService, authService, auditLogService)
            }
        }
    }
}
