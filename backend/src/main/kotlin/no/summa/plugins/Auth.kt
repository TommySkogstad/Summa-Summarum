package no.summa.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import no.summa.models.ErrorResponse

suspend fun ApplicationCall.requireOrgId(): Int? {
    val orgId = request.header("X-Organization-Id")?.toIntOrNull()
    if (orgId == null) {
        respond(HttpStatusCode.BadRequest, ErrorResponse("Manglende X-Organization-Id header"))
        return null
    }
    return orgId
}
