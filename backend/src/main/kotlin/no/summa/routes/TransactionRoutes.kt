package no.summa.routes

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.summa.models.*
import no.summa.plugins.requireAdmin
import no.summa.plugins.userPrincipal
import no.summa.services.RateLimiter
import no.summa.services.TransactionService

fun Route.transactionRoutes(transactionService: TransactionService) {
    authenticate("auth-jwt") {
        route("/transactions") {
            get {
                call.requireAdmin() ?: return@get

                val page = call.parameters["page"]?.toIntOrNull() ?: 1
                val pageSize = call.parameters["pageSize"]?.toIntOrNull() ?: 20
                val type = call.parameters["type"]
                val categoryId = call.parameters["categoryId"]?.toIntOrNull()
                val search = call.parameters["search"]
                val dateFrom = call.parameters["dateFrom"]
                val dateTo = call.parameters["dateTo"]

                call.respond(transactionService.getAll(
                    page = page,
                    pageSize = pageSize,
                    type = type,
                    categoryId = categoryId,
                    search = search,
                    dateFrom = dateFrom,
                    dateTo = dateTo
                ))
            }

            post {
                val principal = call.requireAdmin() ?: return@post
                val request = call.receive<CreateTransactionRequest>()

                if (request.description.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Beskrivelse er pakrevd"))
                    return@post
                }

                try {
                    val transaction = transactionService.create(request, principal.userId)
                    call.respond(HttpStatusCode.Created, transaction)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Kunne ikke opprette transaksjon: ${e.message}"))
                }
            }

            get("/{id}") {
                call.requireAdmin() ?: return@get
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Ugyldig ID"))

                val transaction = transactionService.getById(id)
                if (transaction != null) {
                    call.respond(transaction)
                } else {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Transaksjon ikke funnet"))
                }
            }

            put("/{id}") {
                call.requireAdmin() ?: return@put
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("Ugyldig ID"))

                val request = call.receive<UpdateTransactionRequest>()
                val updated = transactionService.update(id, request)

                if (updated != null) {
                    call.respond(updated)
                } else {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Transaksjon ikke funnet"))
                }
            }

            delete("/{id}") {
                call.requireAdmin() ?: return@delete
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("Ugyldig ID"))

                if (transactionService.delete(id)) {
                    call.respond(MessageResponse("Transaksjon slettet"))
                } else {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Transaksjon ikke funnet"))
                }
            }

            // Vedlegg
            post("/{id}/attachments") {
                call.requireAdmin() ?: return@post
                val transactionId = call.parameters["id"]?.toIntOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Ugyldig transaksjons-ID"))

                val ip = call.request.local.remoteHost
                if (!RateLimiter.canUpload(ip)) {
                    call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("For mange opplastinger. Prov igjen om litt."))
                    return@post
                }

                val multipart = call.receiveMultipart()
                var attachment: AttachmentDTO? = null

                multipart.forEachPart { part ->
                    if (part is PartData.FileItem) {
                        val originalName = part.originalFileName ?: "unknown"
                        val mimeType = part.contentType?.toString() ?: "application/octet-stream"
                        val bytes = part.streamProvider().readBytes()

                        // Valider filstorrelse (maks 10MB)
                        if (bytes.size > 10 * 1024 * 1024) {
                            part.dispose()
                            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Filen er for stor (maks 10MB)"))
                            return@forEachPart
                        }

                        attachment = transactionService.addAttachment(
                            transactionId = transactionId,
                            originalName = originalName,
                            mimeType = mimeType,
                            fileBytes = bytes
                        )
                        RateLimiter.recordUpload(ip)
                    }
                    part.dispose()
                }

                if (attachment != null) {
                    call.respond(HttpStatusCode.Created, attachment!!)
                } else {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Ingen fil mottatt eller transaksjon ikke funnet"))
                }
            }

            get("/{id}/attachments/{aid}") {
                call.requireAdmin() ?: return@get
                val attachmentId = call.parameters["aid"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Ugyldig vedleggs-ID"))

                val result = transactionService.getAttachment(attachmentId)
                if (result != null) {
                    val (dto, file) = result
                    call.response.header(
                        HttpHeaders.ContentDisposition,
                        ContentDisposition.Attachment.withParameter(
                            ContentDisposition.Parameters.FileName, dto.originalName
                        ).toString()
                    )
                    call.respondFile(file)
                } else {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Vedlegg ikke funnet"))
                }
            }

            delete("/{id}/attachments/{aid}") {
                call.requireAdmin() ?: return@delete
                val attachmentId = call.parameters["aid"]?.toIntOrNull()
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("Ugyldig vedleggs-ID"))

                if (transactionService.deleteAttachment(attachmentId)) {
                    call.respond(MessageResponse("Vedlegg slettet"))
                } else {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Vedlegg ikke funnet"))
                }
            }
        }
    }
}
