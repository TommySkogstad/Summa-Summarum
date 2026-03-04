package no.summa.routes

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.summa.models.ErrorResponse
import no.summa.plugins.requireOrgId
import no.summa.services.ExchangeRateService
import no.summa.services.ReportService
import java.time.LocalDate

fun Route.reportRoutes(reportService: ReportService, exchangeRateService: ExchangeRateService) {
    route("/reports") {
        get("/overview") {
            val orgId = call.requireOrgId() ?: return@get
            call.respond(reportService.getOverview(orgId))
        }

        get("/monthly") {
            val orgId = call.requireOrgId() ?: return@get
            val year = call.parameters["year"]?.toIntOrNull()
                ?: java.time.LocalDate.now().year
            call.respond(reportService.getMonthlyReport(year, orgId))
        }

        get("/categories") {
            val orgId = call.requireOrgId() ?: return@get
            val year = call.parameters["year"]?.toIntOrNull()
                ?: java.time.LocalDate.now().year
            val month = call.parameters["month"]?.toIntOrNull()
            call.respond(reportService.getCategoryReport(year, month, orgId))
        }

        get("/yearly") {
            val orgId = call.requireOrgId() ?: return@get
            call.respond(reportService.getYearlyReport(orgId))
        }

        get("/mva") {
            val orgId = call.requireOrgId() ?: return@get
            val year = call.parameters["year"]?.toIntOrNull()
                ?: java.time.LocalDate.now().year
            val month = call.parameters["month"]?.toIntOrNull()
            call.respond(reportService.getMvaReport(year, month, orgId))
        }
    }

    get("/exchange-rate") {
        val currency = call.parameters["currency"]
            ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("currency er pakrevd"))
        val dateStr = call.parameters["date"]
        val date = try {
            if (dateStr != null) LocalDate.parse(dateStr) else LocalDate.now()
        } catch (_: Exception) {
            return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Ugyldig datoformat (bruk YYYY-MM-DD)"))
        }

        val rate = exchangeRateService.getRate(currency, date)
        if (rate != null) {
            call.respond(rate)
        } else {
            call.respond(HttpStatusCode.NotFound, ErrorResponse("Kunne ikke finne kurs for $currency"))
        }
    }
}
