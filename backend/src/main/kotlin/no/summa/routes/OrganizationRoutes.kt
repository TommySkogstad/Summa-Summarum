package no.summa.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.summa.models.*
import no.summa.services.OrganizationService

fun Route.organizationRoutes(organizationService: OrganizationService) {
    route("/organizations") {
        get {
            call.respond(organizationService.getAll())
        }

        post {
            val request = call.receive<CreateOrganizationRequest>()

            if (request.name.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Navn er pakrevd"))
                return@post
            }

            try {
                val org = organizationService.create(request)
                call.respond(HttpStatusCode.Created, org)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.Conflict, ErrorResponse("Kunne ikke opprette organisasjon: ${e.message}"))
            }
        }

        put("/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("Ugyldig ID"))

            val request = call.receive<UpdateOrganizationRequest>()
            val updated = organizationService.update(id, request)

            if (updated != null) {
                call.respond(updated)
            } else {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("Organisasjon ikke funnet"))
            }
        }
    }
}
