package no.summa.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.summa.models.*
import no.summa.services.CategoryService

fun Route.categoryRoutes(categoryService: CategoryService) {
    route("/categories") {
        get {
            call.respond(categoryService.getAll())
        }

        post {
            val request = call.receive<CreateCategoryRequest>()

            if (request.code.isBlank() || request.name.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Kode og navn er pakrevd"))
                return@post
            }

            try {
                val category = categoryService.create(request)
                call.respond(HttpStatusCode.Created, category)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.Conflict, ErrorResponse("Kategori med kode ${request.code} finnes allerede"))
            }
        }

        put("/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("Ugyldig ID"))

            val request = call.receive<UpdateCategoryRequest>()
            val updated = categoryService.update(id, request)

            if (updated != null) {
                call.respond(updated)
            } else {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("Kategori ikke funnet"))
            }
        }

        delete("/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("Ugyldig ID"))

            if (categoryService.delete(id)) {
                call.respond(MessageResponse("Kategori slettet"))
            } else {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("Kategori ikke funnet"))
            }
        }
    }
}
