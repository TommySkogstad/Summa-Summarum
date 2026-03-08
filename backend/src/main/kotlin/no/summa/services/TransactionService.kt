package no.summa.services

import no.grunnmur.TimeUtils
import no.summa.database.*
import no.summa.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class TransactionService(private val documentParserService: DocumentParserService? = null) {
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    private val uploadDir = File("/app/uploads/attachments")
    private val allowedMimeTypes = setOf(
        "image/jpeg", "image/png", "image/gif",
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    )

    init {
        uploadDir.mkdirs()
    }

    fun getAll(
        organizationId: Int,
        page: Int = 1,
        pageSize: Int = 20,
        type: String? = null,
        categoryId: Int? = null,
        search: String? = null,
        dateFrom: String? = null,
        dateTo: String? = null
    ): TransactionListResponse = transaction {
        var query = Transactions.innerJoin(Categories)
            .selectAll()
            .andWhere { Transactions.organizationId eq organizationId }

        type?.let { t ->
            val transType = TransactionType.valueOf(t)
            query = query.andWhere { Transactions.type eq transType }
        }

        categoryId?.let { cid ->
            query = query.andWhere { Transactions.categoryId eq cid }
        }

        search?.let { s ->
            if (s.isNotBlank()) {
                val escaped = s.lowercase()
                    .replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_")
                query = query.andWhere {
                    Transactions.description.lowerCase() like "%${escaped}%"
                }
            }
        }

        dateFrom?.let { df ->
            val from = LocalDateTime.parse(df + "T00:00:00")
            query = query.andWhere { Transactions.date greaterEq from }
        }

        dateTo?.let { dt ->
            val to = LocalDateTime.parse(dt + "T23:59:59")
            query = query.andWhere { Transactions.date lessEq to }
        }

        val total = query.count().toInt()

        val rows = query
            .orderBy(Transactions.date, SortOrder.DESC)
            .limit(pageSize)
            .offset(((page - 1) * pageSize).toLong())
            .toList()

        val txIds = rows.map { it[Transactions.id].value }

        // Batch-hent alle vedlegg for disse transaksjonene
        val attachmentsByTx = if (txIds.isNotEmpty()) {
            val allAttachments = Attachments.selectAll()
                .where { Attachments.transactionId inList txIds }
                .toList()

            // Batch-hent parsed documents for alle vedlegg
            val attachmentIds = allAttachments.map { it[Attachments.id].value }
            val parsedDocs = if (attachmentIds.isNotEmpty()) {
                ParsedDocuments.selectAll()
                    .where { ParsedDocuments.attachmentId inList attachmentIds }
                    .associateBy { it[ParsedDocuments.attachmentId].value }
            } else emptyMap()

            val parsedDocIds = parsedDocs.values.map { it[ParsedDocuments.id].value }
            val lineItemsByDoc = if (parsedDocIds.isNotEmpty()) {
                ParsedLineItems.selectAll()
                    .where { ParsedLineItems.parsedDocumentId inList parsedDocIds }
                    .groupBy { it[ParsedLineItems.parsedDocumentId].value }
            } else emptyMap()

            allAttachments.groupBy { it[Attachments.transactionId].value }
                .mapValues { (_, attRows) ->
                    attRows.map { attRow -> toAttachmentDTOBatch(attRow, parsedDocs, lineItemsByDoc) }
                }
        } else emptyMap()

        val transactions = rows.map { row ->
            toDTO(row, attachmentsByTx[row[Transactions.id].value] ?: emptyList())
        }

        TransactionListResponse(
            transactions = transactions,
            total = total,
            page = page,
            pageSize = pageSize
        )
    }

    fun getById(id: Int, organizationId: Int): TransactionDTO? = transaction {
        val row = Transactions.innerJoin(Categories)
            .selectAll()
            .where { (Transactions.id eq id) and (Transactions.organizationId eq organizationId) }
            .singleOrNull() ?: return@transaction null

        val attachments = Attachments.selectAll()
            .where { Attachments.transactionId eq id }
            .map { toAttachmentDTO(it) }

        toDTO(row, attachments)
    }

    fun create(request: CreateTransactionRequest, organizationId: Int): TransactionDTO = transaction {
        val type = TransactionType.valueOf(request.type)
        val amount = BigDecimal(request.amount)
        val date = LocalDateTime.parse(request.date + "T00:00:00")

        val id = Transactions.insertAndGetId {
            it[Transactions.date] = date
            it[Transactions.type] = type
            it[Transactions.amount] = amount
            it[currency] = request.currency
            it[vatRate] = request.vatRate?.let { v -> BigDecimal(v) }
            it[vatAmount] = request.vatAmount?.let { v -> BigDecimal(v) }
            it[exchangeRate] = request.exchangeRate?.let { v -> BigDecimal(v) }
            it[amountNok] = request.amountNok?.let { v -> BigDecimal(v) }
            it[description] = request.description
            it[vendorName] = request.vendorName
            it[categoryId] = request.categoryId
            it[Transactions.organizationId] = organizationId
            it[createdAt] = TimeUtils.nowOslo()
            it[updatedAt] = TimeUtils.nowOslo()
        }.value

        getById(id, organizationId)!!
    }

    fun update(id: Int, request: UpdateTransactionRequest, organizationId: Int): TransactionDTO? = transaction {
        Transactions.selectAll()
            .where { (Transactions.id eq id) and (Transactions.organizationId eq organizationId) }
            .singleOrNull() ?: return@transaction null

        Transactions.update({ (Transactions.id eq id) and (Transactions.organizationId eq organizationId) }) {
            request.date?.let { d -> it[date] = LocalDateTime.parse(d + "T00:00:00") }
            request.type?.let { t -> it[type] = TransactionType.valueOf(t) }
            request.amount?.let { a -> it[amount] = BigDecimal(a) }
            request.currency?.let { c -> it[currency] = c }
            request.vatRate?.let { v -> it[vatRate] = BigDecimal(v) }
            request.vatAmount?.let { v -> it[vatAmount] = BigDecimal(v) }
            request.exchangeRate?.let { v -> it[exchangeRate] = BigDecimal(v) }
            request.amountNok?.let { v -> it[amountNok] = BigDecimal(v) }
            request.description?.let { d -> it[description] = d }
            request.vendorName?.let { v -> it[vendorName] = v }
            request.categoryId?.let { c -> it[categoryId] = c }
            it[updatedAt] = TimeUtils.nowOslo()
        }

        getById(id, organizationId)
    }

    fun delete(id: Int, organizationId: Int): Boolean = transaction {
        Transactions.selectAll()
            .where { (Transactions.id eq id) and (Transactions.organizationId eq organizationId) }
            .singleOrNull() ?: return@transaction false

        val attachments = Attachments.selectAll()
            .where { Attachments.transactionId eq id }
            .toList()

        for (att in attachments) {
            val file = File(uploadDir, att[Attachments.filename])
            file.delete()
        }

        // Slett parsed documents og line items for vedlegg
        val attachmentIds = attachments.map { it[Attachments.id].value }
        if (attachmentIds.isNotEmpty()) {
            val parsedDocIds = ParsedDocuments.selectAll()
                .where { ParsedDocuments.attachmentId inList attachmentIds }
                .map { it[ParsedDocuments.id].value }
            if (parsedDocIds.isNotEmpty()) {
                ParsedLineItems.deleteWhere { ParsedLineItems.parsedDocumentId inList parsedDocIds }
            }
            ParsedDocuments.deleteWhere { ParsedDocuments.attachmentId inList attachmentIds }
        }

        Attachments.deleteWhere { Attachments.transactionId eq id }
        Transactions.deleteWhere { (Transactions.id eq id) and (Transactions.organizationId eq organizationId) }
        true
    }

    fun addAttachment(
        transactionId: Int,
        organizationId: Int,
        originalName: String,
        mimeType: String,
        fileBytes: ByteArray
    ): AttachmentDTO? = transaction {
        Transactions.selectAll()
            .where { (Transactions.id eq transactionId) and (Transactions.organizationId eq organizationId) }
            .singleOrNull() ?: return@transaction null

        if (mimeType !in allowedMimeTypes) {
            return@transaction null
        }

        val extension = originalName.substringAfterLast('.', "bin")
        val filename = "${UUID.randomUUID()}.$extension"
        val file = File(uploadDir, filename)
        file.writeBytes(fileBytes)

        val id = Attachments.insertAndGetId {
            it[Attachments.transactionId] = transactionId
            it[Attachments.filename] = filename
            it[Attachments.originalName] = originalName
            it[Attachments.mimeType] = mimeType
            it[createdAt] = TimeUtils.nowOslo()
        }.value

        // Parse dokument med Claude Vision hvis tilgjengelig
        if (documentParserService != null && documentParserService.isEnabled && documentParserService.canParse(mimeType)) {
            try {
                documentParserService.parseAndStore(id, fileBytes, mimeType)
            } catch (e: Exception) {
                // Parsing-feil skal aldri blokkere opplasting
                org.slf4j.LoggerFactory.getLogger(javaClass).warn("Dokumentparsing feilet for vedlegg $id: ${e.message}")
            }
        }

        Attachments.selectAll()
            .where { Attachments.id eq id }
            .singleOrNull()
            ?.let { toAttachmentDTO(it) }
    }

    fun getAttachment(attachmentId: Int, organizationId: Int): Pair<AttachmentDTO, File>? = transaction {
        val row = Attachments.selectAll()
            .where { Attachments.id eq attachmentId }
            .singleOrNull() ?: return@transaction null

        val txId = row[Attachments.transactionId].value
        Transactions.selectAll()
            .where { (Transactions.id eq txId) and (Transactions.organizationId eq organizationId) }
            .singleOrNull() ?: return@transaction null

        val dto = toAttachmentDTO(row)
        val file = File(uploadDir, dto.filename)
        if (!file.exists()) return@transaction null

        Pair(dto, file)
    }

    fun deleteAttachment(attachmentId: Int, organizationId: Int): Boolean = transaction {
        val row = Attachments.selectAll()
            .where { Attachments.id eq attachmentId }
            .singleOrNull() ?: return@transaction false

        val txId = row[Attachments.transactionId].value
        Transactions.selectAll()
            .where { (Transactions.id eq txId) and (Transactions.organizationId eq organizationId) }
            .singleOrNull() ?: return@transaction false

        val filename = row[Attachments.filename]
        val file = File(uploadDir, filename)
        file.delete()

        // Slett parsed document og line items
        val parsedDoc = ParsedDocuments.selectAll()
            .where { ParsedDocuments.attachmentId eq attachmentId }
            .singleOrNull()
        if (parsedDoc != null) {
            val parsedDocId = parsedDoc[ParsedDocuments.id].value
            ParsedLineItems.deleteWhere { ParsedLineItems.parsedDocumentId eq parsedDocId }
            ParsedDocuments.deleteWhere { ParsedDocuments.id eq parsedDocId }
        }

        Attachments.deleteWhere { Attachments.id eq attachmentId }
        true
    }

    fun getAllAttachments(organizationId: Int): AttachmentListResponse = transaction {
        val rows = Attachments
            .innerJoin(Transactions)
            .selectAll()
            .where { Transactions.organizationId eq organizationId }
            .orderBy(Attachments.createdAt, SortOrder.DESC)
            .map { row ->
                AttachmentWithTransactionDTO(
                    id = row[Attachments.id].value,
                    transactionId = row[Attachments.transactionId].value,
                    filename = row[Attachments.filename],
                    originalName = row[Attachments.originalName],
                    mimeType = row[Attachments.mimeType],
                    createdAt = row[Attachments.createdAt].format(dateFormatter),
                    transactionDate = row[Transactions.date].format(DateTimeFormatter.ISO_LOCAL_DATE),
                    transactionDescription = row[Transactions.description]
                )
            }

        AttachmentListResponse(attachments = rows, total = rows.size)
    }

    private fun toDTO(row: ResultRow, attachments: List<AttachmentDTO> = emptyList()): TransactionDTO {
        return TransactionDTO(
            id = row[Transactions.id].value,
            date = row[Transactions.date].format(DateTimeFormatter.ISO_LOCAL_DATE),
            type = row[Transactions.type].name,
            amount = row[Transactions.amount].toPlainString(),
            currency = row[Transactions.currency],
            vatRate = row[Transactions.vatRate]?.toPlainString(),
            vatAmount = row[Transactions.vatAmount]?.toPlainString(),
            exchangeRate = row[Transactions.exchangeRate]?.toPlainString(),
            amountNok = row[Transactions.amountNok]?.toPlainString(),
            description = row[Transactions.description],
            vendorName = row[Transactions.vendorName],
            categoryId = row[Transactions.categoryId].value,
            categoryCode = row[Categories.code],
            categoryName = row[Categories.name],
            createdAt = row[Transactions.createdAt].format(dateFormatter),
            updatedAt = row[Transactions.updatedAt].format(dateFormatter),
            attachments = attachments
        )
    }

    private fun toAttachmentDTO(row: ResultRow): AttachmentDTO {
        val attachmentId = row[Attachments.id].value
        val parsedDoc = documentParserService?.getParsedDocument(attachmentId)

        return AttachmentDTO(
            id = attachmentId,
            transactionId = row[Attachments.transactionId].value,
            filename = row[Attachments.filename],
            originalName = row[Attachments.originalName],
            mimeType = row[Attachments.mimeType],
            createdAt = row[Attachments.createdAt].format(dateFormatter),
            parsedDocument = parsedDoc
        )
    }

    private fun toAttachmentDTOBatch(
        row: ResultRow,
        parsedDocs: Map<Int, ResultRow>,
        lineItemsByDoc: Map<Int, List<ResultRow>>
    ): AttachmentDTO {
        val attachmentId = row[Attachments.id].value
        val parsedDocRow = parsedDocs[attachmentId]
        val parsedDoc = parsedDocRow?.let { pdRow ->
            val docId = pdRow[ParsedDocuments.id].value
            val lineItems = lineItemsByDoc[docId]?.map { li ->
                ParsedLineItemDTO(
                    description = li[ParsedLineItems.description],
                    quantity = li[ParsedLineItems.quantity]?.toPlainString(),
                    unitPrice = li[ParsedLineItems.unitPrice]?.toPlainString(),
                    amount = li[ParsedLineItems.amount]?.toPlainString(),
                    vatRate = li[ParsedLineItems.vatRate]?.toPlainString(),
                    vatAmount = li[ParsedLineItems.vatAmount]?.toPlainString()
                )
            } ?: emptyList()

            ParsedDocumentDTO(
                id = docId,
                totalAmount = pdRow[ParsedDocuments.totalAmount]?.toPlainString(),
                currency = pdRow[ParsedDocuments.currency],
                vatAmount = pdRow[ParsedDocuments.vatAmount]?.toPlainString(),
                vatRate = pdRow[ParsedDocuments.vatRate]?.toPlainString(),
                invoiceDate = pdRow[ParsedDocuments.invoiceDate],
                paymentDueDate = pdRow[ParsedDocuments.paymentDueDate],
                paymentReference = pdRow[ParsedDocuments.paymentReference],
                vendorName = pdRow[ParsedDocuments.vendorName],
                vendorOrgNumber = pdRow[ParsedDocuments.vendorOrgNumber],
                invoiceNumber = pdRow[ParsedDocuments.invoiceNumber],
                confidence = pdRow[ParsedDocuments.confidence]?.toPlainString(),
                status = pdRow[ParsedDocuments.status].name,
                errorMessage = pdRow[ParsedDocuments.errorMessage],
                lineItems = lineItems
            )
        }

        return AttachmentDTO(
            id = attachmentId,
            transactionId = row[Attachments.transactionId].value,
            filename = row[Attachments.filename],
            originalName = row[Attachments.originalName],
            mimeType = row[Attachments.mimeType],
            createdAt = row[Attachments.createdAt].format(dateFormatter),
            parsedDocument = parsedDoc
        )
    }
}
