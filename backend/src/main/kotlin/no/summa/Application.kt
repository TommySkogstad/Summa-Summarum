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
    val documentParserService = DocumentParserService(System.getenv("ANTHROPIC_API_KEY"))
    val categoryService = CategoryService()
    val transactionService = TransactionService(documentParserService)
    val reportService = ReportService()
    val organizationService = OrganizationService()
    val exchangeRateService = ExchangeRateService()

    // Plugins
    configureSerialization()
    configureSecurity()
    configureDatabase()
    configureRouting(
        categoryService = categoryService,
        transactionService = transactionService,
        reportService = reportService,
        organizationService = organizationService,
        exchangeRateService = exchangeRateService,
        documentParserService = documentParserService
    )

    log.info("Summa Summarum backend startet pa port 8083")
}
