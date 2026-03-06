package no.summa.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.summa.models.*
import no.summa.plugins.getUserId
import no.summa.plugins.requireSuperAdmin
import no.summa.plugins.verifyCsrf
import no.summa.services.AuditLogService
import no.summa.services.AuthService
import no.summa.services.OrganizationService

fun Route.organizationRoutes(organizationService: OrganizationService, authService: AuthService, auditLogService: AuditLogService) {
    route("/organizations") {
        get {
            call.respond(organizationService.getAll())
        }

        post {
            if (!call.requireSuperAdmin()) return@post
            if (!call.verifyCsrf(authService)) return@post

            val request = call.receive<CreateOrganizationRequest>()

            if (request.name.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Navn er pakrevd"))
                return@post
            }

            try {
                val org = organizationService.create(request)
                auditLogService.log(
                    userId = call.getUserId(),
                    action = "CREATE",
                    entityType = "Organization",
                    entityId = org.id,
                    details = "Opprettet organisasjon ${org.name}",
                    ipAddress = call.request.header("X-Real-IP") ?: call.request.local.remoteAddress
                )
                call.respond(HttpStatusCode.Created, org)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.Conflict, ErrorResponse("Kunne ikke opprette organisasjon: ${e.message}"))
            }
        }

        put("/{id}") {
            if (!call.requireSuperAdmin()) return@put
            if (!call.verifyCsrf(authService)) return@put

            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("Ugyldig ID"))

            val request = call.receive<UpdateOrganizationRequest>()
            val updated = organizationService.update(id, request)

            if (updated != null) {
                auditLogService.log(
                    userId = call.getUserId(),
                    action = "UPDATE",
                    entityType = "Organization",
                    entityId = id,
                    details = "Oppdatert organisasjon ${updated.name}",
                    ipAddress = call.request.header("X-Real-IP") ?: call.request.local.remoteAddress
                )
                call.respond(updated)
            } else {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("Organisasjon ikke funnet"))
            }
        }
    }
}
