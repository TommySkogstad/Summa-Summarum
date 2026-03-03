package no.summa.routes

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.summa.models.ErrorResponse
import no.summa.plugins.requireAdmin
import no.summa.services.ReportService

fun Route.reportRoutes(reportService: ReportService) {
    authenticate("auth-jwt") {
        route("/reports") {
            get("/overview") {
                call.requireAdmin() ?: return@get
                call.respond(reportService.getOverview())
            }

            get("/monthly") {
                call.requireAdmin() ?: return@get
                val year = call.parameters["year"]?.toIntOrNull()
                    ?: java.time.LocalDate.now().year

                call.respond(reportService.getMonthlyReport(year))
            }

            get("/categories") {
                call.requireAdmin() ?: return@get
                val year = call.parameters["year"]?.toIntOrNull()
                    ?: java.time.LocalDate.now().year
                val month = call.parameters["month"]?.toIntOrNull()

                call.respond(reportService.getCategoryReport(year, month))
            }

            get("/yearly") {
                call.requireAdmin() ?: return@get
                call.respond(reportService.getYearlyReport())
            }
        }
    }
}
