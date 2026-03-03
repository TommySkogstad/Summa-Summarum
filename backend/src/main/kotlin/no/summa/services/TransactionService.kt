package no.summa.services

import no.summa.database.*
import no.summa.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class TransactionService {
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    private val uploadDir = File("/app/uploads/attachments")

    init {
        uploadDir.mkdirs()
    }

    fun getAll(
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

        type?.let { t ->
            val transType = TransactionType.valueOf(t)
            query = query.andWhere { Transactions.type eq transType }
        }

        categoryId?.let { cid ->
            query = query.andWhere { Transactions.categoryId eq cid }
        }

        search?.let { s ->
            if (s.isNotBlank()) {
                query = query.andWhere {
                    Transactions.description.lowerCase() like "%${s.lowercase()}%"
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

        val transactions = query
            .orderBy(Transactions.date, SortOrder.DESC)
            .limit(pageSize)
            .offset(((page - 1) * pageSize).toLong())
            .map { row ->
                val txId = row[Transactions.id].value
                val attachments = Attachments.selectAll()
                    .where { Attachments.transactionId eq txId }
                    .map { toAttachmentDTO(it) }

                toDTO(row, attachments)
            }

        TransactionListResponse(
            transactions = transactions,
            total = total,
            page = page,
            pageSize = pageSize
        )
    }

    fun getById(id: Int): TransactionDTO? = transaction {
        val row = Transactions.innerJoin(Categories)
            .selectAll()
            .where { Transactions.id eq id }
            .singleOrNull() ?: return@transaction null

        val attachments = Attachments.selectAll()
            .where { Attachments.transactionId eq id }
            .map { toAttachmentDTO(it) }

        toDTO(row, attachments)
    }

    fun create(request: CreateTransactionRequest, userId: Int?): TransactionDTO = transaction {
        val type = TransactionType.valueOf(request.type)
        val amount = BigDecimal(request.amount)
        val date = LocalDateTime.parse(request.date + "T00:00:00")

        val id = Transactions.insertAndGetId {
            it[Transactions.date] = date
            it[Transactions.type] = type
            it[Transactions.amount] = amount
            it[description] = request.description
            it[categoryId] = request.categoryId
            it[createdBy] = userId
            it[createdAt] = TimeUtils.nowOslo()
            it[updatedAt] = TimeUtils.nowOslo()
        }.value

        getById(id)!!
    }

    fun update(id: Int, request: UpdateTransactionRequest): TransactionDTO? = transaction {
        val existing = Transactions.selectAll()
            .where { Transactions.id eq id }
            .singleOrNull() ?: return@transaction null

        Transactions.update({ Transactions.id eq id }) {
            request.date?.let { d -> it[date] = LocalDateTime.parse(d + "T00:00:00") }
            request.type?.let { t -> it[type] = TransactionType.valueOf(t) }
            request.amount?.let { a -> it[amount] = BigDecimal(a) }
            request.description?.let { d -> it[description] = d }
            request.categoryId?.let { c -> it[categoryId] = c }
            it[updatedAt] = TimeUtils.nowOslo()
        }

        getById(id)
    }

    fun delete(id: Int): Boolean = transaction {
        val existing = Transactions.selectAll()
            .where { Transactions.id eq id }
            .singleOrNull() ?: return@transaction false

        // Slett vedlegg
        val attachments = Attachments.selectAll()
            .where { Attachments.transactionId eq id }
            .toList()

        for (att in attachments) {
            val file = File(uploadDir, att[Attachments.filename])
            file.delete()
        }

        Attachments.deleteWhere { Attachments.transactionId eq id }
        Transactions.deleteWhere { Transactions.id eq id }
        true
    }

    fun addAttachment(
        transactionId: Int,
        originalName: String,
        mimeType: String,
        fileBytes: ByteArray
    ): AttachmentDTO? = transaction {
        // Sjekk at transaksjonen finnes
        Transactions.selectAll()
            .where { Transactions.id eq transactionId }
            .singleOrNull() ?: return@transaction null

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

        Attachments.selectAll()
            .where { Attachments.id eq id }
            .singleOrNull()
            ?.let { toAttachmentDTO(it) }
    }

    fun getAttachment(attachmentId: Int): Pair<AttachmentDTO, File>? = transaction {
        val row = Attachments.selectAll()
            .where { Attachments.id eq attachmentId }
            .singleOrNull() ?: return@transaction null

        val dto = toAttachmentDTO(row)
        val file = File(uploadDir, dto.filename)
        if (!file.exists()) return@transaction null

        Pair(dto, file)
    }

    fun deleteAttachment(attachmentId: Int): Boolean = transaction {
        val row = Attachments.selectAll()
            .where { Attachments.id eq attachmentId }
            .singleOrNull() ?: return@transaction false

        val filename = row[Attachments.filename]
        val file = File(uploadDir, filename)
        file.delete()

        Attachments.deleteWhere { Attachments.id eq attachmentId }
        true
    }

    private fun toDTO(row: ResultRow, attachments: List<AttachmentDTO> = emptyList()): TransactionDTO {
        return TransactionDTO(
            id = row[Transactions.id].value,
            date = row[Transactions.date].format(DateTimeFormatter.ISO_LOCAL_DATE),
            type = row[Transactions.type].name,
            amount = row[Transactions.amount].toPlainString(),
            description = row[Transactions.description],
            categoryId = row[Transactions.categoryId].value,
            categoryCode = row[Categories.code],
            categoryName = row[Categories.name],
            createdBy = row[Transactions.createdBy]?.value,
            createdAt = row[Transactions.createdAt].format(dateFormatter),
            updatedAt = row[Transactions.updatedAt].format(dateFormatter),
            attachments = attachments
        )
    }

    private fun toAttachmentDTO(row: ResultRow): AttachmentDTO {
        return AttachmentDTO(
            id = row[Attachments.id].value,
            transactionId = row[Attachments.transactionId].value,
            filename = row[Attachments.filename],
            originalName = row[Attachments.originalName],
            mimeType = row[Attachments.mimeType],
            createdAt = row[Attachments.createdAt].format(dateFormatter)
        )
    }
}
