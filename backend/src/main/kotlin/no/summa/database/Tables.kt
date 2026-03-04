package no.summa.database

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime
import java.time.ZoneId

object TimeUtils {
    private val osloZone = ZoneId.of("Europe/Oslo")
    fun nowOslo(): LocalDateTime = LocalDateTime.now(osloZone)
}

// Transaksjonstyper
enum class TransactionType { INNTEKT, UTGIFT }

// Kategorityper (matcher TransactionType)
enum class CategoryType { INNTEKT, UTGIFT }

// ===== TABELLER =====

object Organizations : IntIdTable("organizations") {
    val name = varchar("name", 255)
    val orgNumber = varchar("org_number", 20).nullable().uniqueIndex()
    val mvaRegistered = bool("mva_registered").default(false)
    val active = bool("active").default(true)
    val createdAt = datetime("created_at").default(TimeUtils.nowOslo())
}

object Categories : IntIdTable("categories") {
    val code = varchar("code", 10).uniqueIndex()
    val name = varchar("name", 255)
    val type = enumerationByName("type", 20, CategoryType::class)
    val active = bool("active").default(true)
    val isDefault = bool("is_default").default(false)
    val createdAt = datetime("created_at").default(TimeUtils.nowOslo())
}

object Transactions : IntIdTable("transactions") {
    val date = datetime("date")
    val type = enumerationByName("type", 20, TransactionType::class)
    val amount = decimal("amount", 12, 2)
    val currency = varchar("currency", 10).default("NOK")
    val vatRate = decimal("vat_rate", 5, 2).nullable()
    val vatAmount = decimal("vat_amount", 12, 2).nullable()
    val exchangeRate = decimal("exchange_rate", 12, 6).nullable()
    val amountNok = decimal("amount_nok", 12, 2).nullable()
    val description = varchar("description", 500)
    val vendorName = varchar("vendor_name", 255).nullable()
    val categoryId = reference("category_id", Categories)
    val organizationId = reference("organization_id", Organizations)
    val createdAt = datetime("created_at").default(TimeUtils.nowOslo())
    val updatedAt = datetime("updated_at").default(TimeUtils.nowOslo())
}

object Attachments : IntIdTable("attachments") {
    val transactionId = reference("transaction_id", Transactions)
    val filename = varchar("filename", 255)
    val originalName = varchar("original_name", 255)
    val mimeType = varchar("mime_type", 100)
    val createdAt = datetime("created_at").default(TimeUtils.nowOslo())
}

// Parsing-status for dokumenter
enum class ParseStatus { SUCCESS, FAILED, UNSUPPORTED }

object ParsedDocuments : IntIdTable("parsed_documents") {
    val attachmentId = reference("attachment_id", Attachments).uniqueIndex()
    val totalAmount = decimal("total_amount", 12, 2).nullable()
    val currency = varchar("currency", 10).nullable()
    val vatAmount = decimal("vat_amount", 12, 2).nullable()
    val vatRate = decimal("vat_rate", 5, 2).nullable()
    val invoiceDate = varchar("invoice_date", 20).nullable()
    val paymentDueDate = varchar("payment_due_date", 20).nullable()
    val paymentReference = varchar("payment_reference", 100).nullable()
    val vendorName = varchar("vendor_name", 255).nullable()
    val vendorOrgNumber = varchar("vendor_org_number", 20).nullable()
    val invoiceNumber = varchar("invoice_number", 100).nullable()
    val rawJson = text("raw_json")
    val confidence = decimal("confidence", 3, 2).nullable()
    val status = enumerationByName("status", 20, ParseStatus::class)
    val errorMessage = text("error_message").nullable()
    val createdAt = datetime("created_at").default(TimeUtils.nowOslo())
}

object ParsedLineItems : IntIdTable("parsed_line_items") {
    val parsedDocumentId = reference("parsed_document_id", ParsedDocuments)
    val description = varchar("description", 500).nullable()
    val quantity = decimal("quantity", 10, 2).nullable()
    val unitPrice = decimal("unit_price", 12, 2).nullable()
    val amount = decimal("amount", 12, 2).nullable()
    val vatRate = decimal("vat_rate", 5, 2).nullable()
    val vatAmount = decimal("vat_amount", 12, 2).nullable()
}

val allTables = arrayOf(Organizations, Categories, Transactions, Attachments, ParsedDocuments, ParsedLineItems)
