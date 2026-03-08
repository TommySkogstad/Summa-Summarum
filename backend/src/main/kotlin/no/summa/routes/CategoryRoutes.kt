package no.summa.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.summa.models.*
import no.grunnmur.AuditLogService
import no.summa.plugins.getUserId
import no.summa.plugins.requireSuperAdmin
import no.summa.services.CategoryService

fun Route.categoryRoutes(categoryService: CategoryService, auditLogService: AuditLogService) {
    route("/categories") {
        get {
            call.respond(categoryService.getAll())
        }

        post {
            if (!call.requireSuperAdmin()) return@post

            val request = call.receive<CreateCategoryRequest>()

            if (request.code.isBlank() || request.name.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Kode og navn er pakrevd"))
                return@post
            }

            try {
                val category = categoryService.create(request)
                auditLogService.log(
                    userId = call.getUserId(),
                    action = "CREATE",
                    entityType = "Category",
                    entityId = category.id,
                    details = "Opprettet kategori ${category.code} - ${category.name}",
                    ipAddress = call.request.header("X-Real-IP") ?: call.request.local.remoteAddress
                )
                call.respond(HttpStatusCode.Created, category)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.Conflict, ErrorResponse("Kategori med kode ${request.code} finnes allerede"))
            }
        }

        put("/{id}") {
            if (!call.requireSuperAdmin()) return@put

            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("Ugyldig ID"))

            val request = call.receive<UpdateCategoryRequest>()
            val updated = categoryService.update(id, request)

            if (updated != null) {
                auditLogService.log(
                    userId = call.getUserId(),
                    action = "UPDATE",
                    entityType = "Category",
                    entityId = id,
                    details = "Oppdatert kategori ${updated.code}",
                    ipAddress = call.request.header("X-Real-IP") ?: call.request.local.remoteAddress
                )
                call.respond(updated)
            } else {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("Kategori ikke funnet"))
            }
        }

        delete("/{id}") {
            if (!call.requireSuperAdmin()) return@delete

            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("Ugyldig ID"))

            if (categoryService.delete(id)) {
                auditLogService.log(
                    userId = call.getUserId(),
                    action = "DELETE",
                    entityType = "Category",
                    entityId = id,
                    details = "Slettet/deaktivert kategori",
                    ipAddress = call.request.header("X-Real-IP") ?: call.request.local.remoteAddress
                )
                call.respond(MessageResponse("Kategori slettet"))
            } else {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("Kategori ikke funnet"))
            }
        }
    }
}
