package no.summa.database

import no.grunnmur.TimeUtils
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.datetime

// Transaksjonstyper
enum class TransactionType { INNTEKT, UTGIFT }

// Kategorityper (matcher TransactionType)
enum class CategoryType { INNTEKT, UTGIFT }

// Brukerroller
enum class UserRole { ADMIN, SUPERADMIN }

// E-poststatus
enum class EmailStatus { PENDING, SENT, FAILED }

// ===== TABELLER =====

object Users : IntIdTable("users") {
    val email = varchar("email", 255).uniqueIndex()
    val name = varchar("name", 255)
    val role = enumerationByName("role", 20, UserRole::class)
    val otpCode = varchar("otp_code", 10).nullable()
    val otpExpiresAt = datetime("otp_expires_at").nullable()
    val createdAt = datetime("created_at").clientDefault { TimeUtils.nowOslo() }
    val lastLoginAt = datetime("last_login_at").nullable()
    val active = bool("active").default(true)
}

object Organizations : IntIdTable("organizations") {
    val name = varchar("name", 255)
    val orgNumber = varchar("org_number", 20).nullable().uniqueIndex()
    val mvaRegistered = bool("mva_registered").default(false)
    val active = bool("active").default(true)
    val createdAt = datetime("created_at").clientDefault { TimeUtils.nowOslo() }
}

object UserOrganizations : IntIdTable("user_organizations") {
    val userId = reference("user_id", Users)
    val organizationId = reference("organization_id", Organizations)
    val createdAt = datetime("created_at").clientDefault { TimeUtils.nowOslo() }

    init {
        uniqueIndex(userId, organizationId)
    }
}

object Categories : IntIdTable("categories") {
    val code = varchar("code", 10).uniqueIndex()
    val name = varchar("name", 255)
    val type = enumerationByName("type", 20, CategoryType::class)
    val active = bool("active").default(true)
    val isDefault = bool("is_default").default(false)
    val createdAt = datetime("created_at").clientDefault { TimeUtils.nowOslo() }
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
    val createdAt = datetime("created_at").clientDefault { TimeUtils.nowOslo() }
    val updatedAt = datetime("updated_at").clientDefault { TimeUtils.nowOslo() }

    init {
        index(isUnique = false, organizationId, date)
        index(isUnique = false, organizationId, type, date)
    }
}

object Attachments : IntIdTable("attachments") {
    val transactionId = reference("transaction_id", Transactions)
    val filename = varchar("filename", 255)
    val originalName = varchar("original_name", 255)
    val mimeType = varchar("mime_type", 100)
    val createdAt = datetime("created_at").clientDefault { TimeUtils.nowOslo() }
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
    val createdAt = datetime("created_at").clientDefault { TimeUtils.nowOslo() }
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

object EmailLog : IntIdTable("email_log") {
    val toEmail = varchar("to_email", 255)
    val subject = varchar("subject", 500)
    val body = text("body")
    val status = enumerationByName("status", 20, EmailStatus::class)
    val sentAt = datetime("sent_at").clientDefault { TimeUtils.nowOslo() }
    val errorMessage = text("error_message").nullable()
}

val allTables = arrayOf(
    Users, Organizations, UserOrganizations,
    Categories, Transactions, Attachments,
    ParsedDocuments, ParsedLineItems,
    no.grunnmur.AuditLogs, EmailLog
)
