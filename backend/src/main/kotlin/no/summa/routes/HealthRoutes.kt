package no.summa.routes

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.transactions.transaction

@Serializable
data class HealthResponse(
    val status: String,
    val database: String,
    val version: String = "0.0.1"
)

fun Route.healthRoutes() {
    get("/api/health") {
        val dbStatus = try {
            transaction {
                exec("SELECT 1") { it.next() }
            }
            "connected"
        } catch (e: Exception) {
            "error: ${e.message}"
        }

        call.respond(
            if (dbStatus == "connected") HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable,
            HealthResponse(
                status = if (dbStatus == "connected") "ok" else "degraded",
                database = dbStatus
            )
        )
    }
}
