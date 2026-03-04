package no.summa.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.routing.*
import no.summa.routes.*
import no.summa.services.*

fun Application.configureRouting(
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
        allowHeader("X-Organization-Id")
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
            categoryRoutes(categoryService)
            transactionRoutes(transactionService, documentParserService)
            reportRoutes(reportService, exchangeRateService)
            organizationRoutes(organizationService)
        }
    }
}
