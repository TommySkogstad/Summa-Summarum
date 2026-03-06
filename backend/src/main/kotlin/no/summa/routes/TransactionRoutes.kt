package no.summa.routes

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.summa.models.*
import no.summa.plugins.getUserId
import no.summa.plugins.requireOrgId
import no.summa.plugins.verifyCsrf
import no.summa.services.AuditLogService
import no.summa.services.AuthService
import no.summa.services.DocumentParserService
import no.summa.services.ParseResult
import no.summa.services.TransactionService
import no.summa.utils.Validators

fun Route.transactionRoutes(
    transactionService: TransactionService,
    documentParserService: DocumentParserService?,
    authService: AuthService,
    auditLogService: AuditLogService
) {
    get("/attachments") {
        val orgId = call.requireOrgId() ?: return@get
        call.respond(transactionService.getAllAttachments(orgId))
    }

    post("/parse-document") {
        if (!call.verifyCsrf(authService)) return@post

        if (documentParserService == null || !documentParserService.isEnabled) {
            call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("Dokumentparsing er ikke aktivert"))
            return@post
        }

        val multipart = call.receiveMultipart()
        var result: ParseResult? = null

        multipart.forEachPart { part ->
            if (part is PartData.FileItem && result == null) {
                val mimeType = part.contentType?.toString() ?: "application/octet-stream"
                val bytes = part.streamProvider().readBytes()

                if (bytes.size > 10 * 1024 * 1024) {
                    part.dispose()
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Filen er for stor (maks 10MB)"))
                    return@forEachPart
                }

                result = documentParserService.parse(bytes, mimeType)
            }
            part.dispose()
        }

        when (val r = result) {
            is ParseResult.Success -> call.respond(ParsedDocumentDTO(
                id = 0,
                totalAmount = r.document.totalAmount?.toString(),
                currency = r.document.currency,
                vatAmount = r.document.vatAmount?.toString(),
                vatRate = r.document.vatRate?.toString(),
                invoiceDate = r.document.invoiceDate,
                paymentDueDate = r.document.paymentDueDate,
                paymentReference = r.document.paymentReference,
                vendorName = r.document.vendorName,
                vendorOrgNumber = r.document.vendorOrgNumber,
                invoiceNumber = r.document.invoiceNumber,
                confidence = r.document.confidence?.toString(),
                status = "SUCCESS",
                lineItems = r.document.lineItems.map { li ->
                    ParsedLineItemDTO(
                        description = li.description,
                        quantity = li.quantity?.toString(),
                        unitPrice = li.unitPrice?.toString(),
                        amount = li.amount?.toString(),
                        vatRate = li.vatRate?.toString(),
                        vatAmount = li.vatAmount?.toString()
                    )
                }
            ))
            is ParseResult.Failed -> call.respond(ParsedDocumentDTO(
                id = 0, status = "FAILED", errorMessage = r.error
            ))
            is ParseResult.Unsupported -> call.respond(HttpStatusCode.BadRequest, ErrorResponse("Filtypen stettes ikke for parsing"))
            is ParseResult.Disabled -> call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("Parsing er deaktivert"))
            null -> call.respond(HttpStatusCode.BadRequest, ErrorResponse("Ingen fil mottatt"))
        }
    }

    route("/transactions") {
        get {
            val orgId = call.requireOrgId() ?: return@get

            val page = call.parameters["page"]?.toIntOrNull() ?: 1
            val pageSize = minOf(call.parameters["pageSize"]?.toIntOrNull() ?: 20, 100)
            val type = call.parameters["type"]
            val categoryId = call.parameters["categoryId"]?.toIntOrNull()
            val search = call.parameters["search"]
            val dateFrom = call.parameters["dateFrom"]
            val dateTo = call.parameters["dateTo"]

            call.respond(transactionService.getAll(
                organizationId = orgId,
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
            if (!call.verifyCsrf(authService)) return@post
            val orgId = call.requireOrgId() ?: return@post
            val request = call.receive<CreateTransactionRequest>()

            if (request.description.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Beskrivelse er pakrevd"))
                return@post
            }

            if (!Validators.isValidAmount(request.amount)) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Ugyldig beløp"))
                return@post
            }

            val validTypes = listOf("INNTEKT", "UTGIFT")
            if (request.type !in validTypes) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Ugyldig transaksjonstype. Må være INNTEKT eller UTGIFT"))
                return@post
            }

            try {
                val transaction = transactionService.create(request, orgId)
                auditLogService.log(
                    userId = call.getUserId(),
                    action = "CREATE",
                    entityType = "Transaction",
                    entityId = transaction.id,
                    details = "Opprettet transaksjon: ${transaction.description} (${transaction.amount} ${transaction.currency})",
                    ipAddress = call.request.header("X-Real-IP") ?: call.request.local.remoteAddress
                )
                call.respond(HttpStatusCode.Created, transaction)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Kunne ikke opprette transaksjon: ${e.message}"))
            }
        }

        get("/{id}") {
            val orgId = call.requireOrgId() ?: return@get
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Ugyldig ID"))

            val transaction = transactionService.getById(id, orgId)
            if (transaction != null) {
                call.respond(transaction)
            } else {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("Transaksjon ikke funnet"))
            }
        }

        put("/{id}") {
            if (!call.verifyCsrf(authService)) return@put
            val orgId = call.requireOrgId() ?: return@put
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse("Ugyldig ID"))

            val request = call.receive<UpdateTransactionRequest>()
            val updated = transactionService.update(id, request, orgId)

            if (updated != null) {
                auditLogService.log(
                    userId = call.getUserId(),
                    action = "UPDATE",
                    entityType = "Transaction",
                    entityId = id,
                    details = "Oppdatert transaksjon: ${updated.description}",
                    ipAddress = call.request.header("X-Real-IP") ?: call.request.local.remoteAddress
                )
                call.respond(updated)
            } else {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("Transaksjon ikke funnet"))
            }
        }

        delete("/{id}") {
            if (!call.verifyCsrf(authService)) return@delete
            val orgId = call.requireOrgId() ?: return@delete
            val id = call.parameters["id"]?.toIntOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("Ugyldig ID"))

            if (transactionService.delete(id, orgId)) {
                auditLogService.log(
                    userId = call.getUserId(),
                    action = "DELETE",
                    entityType = "Transaction",
                    entityId = id,
                    details = "Slettet transaksjon",
                    ipAddress = call.request.header("X-Real-IP") ?: call.request.local.remoteAddress
                )
                call.respond(MessageResponse("Transaksjon slettet"))
            } else {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("Transaksjon ikke funnet"))
            }
        }

        // Vedlegg
        post("/{id}/attachments") {
            if (!call.verifyCsrf(authService)) return@post
            val orgId = call.requireOrgId() ?: return@post
            val transactionId = call.parameters["id"]?.toIntOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Ugyldig transaksjons-ID"))

            val multipart = call.receiveMultipart()
            var attachment: AttachmentDTO? = null

            multipart.forEachPart { part ->
                if (part is PartData.FileItem) {
                    val originalName = part.originalFileName ?: "unknown"
                    val mimeType = part.contentType?.toString() ?: "application/octet-stream"
                    val bytes = part.streamProvider().readBytes()

                    if (bytes.size > 10 * 1024 * 1024) {
                        part.dispose()
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Filen er for stor (maks 10MB)"))
                        return@forEachPart
                    }

                    attachment = transactionService.addAttachment(
                        transactionId = transactionId,
                        organizationId = orgId,
                        originalName = originalName,
                        mimeType = mimeType,
                        fileBytes = bytes
                    )
                }
                part.dispose()
            }

            if (attachment != null) {
                auditLogService.log(
                    userId = call.getUserId(),
                    action = "CREATE",
                    entityType = "Attachment",
                    entityId = attachment!!.id,
                    details = "Lastet opp vedlegg: ${attachment!!.originalName}",
                    ipAddress = call.request.header("X-Real-IP") ?: call.request.local.remoteAddress
                )
                call.respond(HttpStatusCode.Created, attachment!!)
            } else {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Ingen fil mottatt eller transaksjon ikke funnet"))
            }
        }

        get("/{id}/attachments/{aid}") {
            val orgId = call.requireOrgId() ?: return@get
            val attachmentId = call.parameters["aid"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("Ugyldig vedleggs-ID"))

            val result = transactionService.getAttachment(attachmentId, orgId)
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
            if (!call.verifyCsrf(authService)) return@delete
            val orgId = call.requireOrgId() ?: return@delete
            val attachmentId = call.parameters["aid"]?.toIntOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("Ugyldig vedleggs-ID"))

            if (transactionService.deleteAttachment(attachmentId, orgId)) {
                auditLogService.log(
                    userId = call.getUserId(),
                    action = "DELETE",
                    entityType = "Attachment",
                    entityId = attachmentId,
                    details = "Slettet vedlegg",
                    ipAddress = call.request.header("X-Real-IP") ?: call.request.local.remoteAddress
                )
                call.respond(MessageResponse("Vedlegg slettet"))
            } else {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("Vedlegg ikke funnet"))
            }
        }
    }
}
