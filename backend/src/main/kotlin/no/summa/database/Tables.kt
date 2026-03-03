package no.summa.database

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime
import java.time.ZoneId

object TimeUtils {
    private val osloZone = ZoneId.of("Europe/Oslo")
    fun nowOslo(): LocalDateTime = LocalDateTime.now(osloZone)
}

// Brukerroller
enum class UserRole { ADMIN }

// Transaksjonstyper
enum class TransactionType { INNTEKT, UTGIFT }

// Kategorityper (matcher TransactionType)
enum class CategoryType { INNTEKT, UTGIFT }

// E-posttyper
enum class EmailType { OTP, GENERELL }
enum class EmailStatus { PENDING, SENT, FAILED, SKIPPED_DEV }

// ===== TABELLER =====

object Users : IntIdTable("users") {
    val email = varchar("email", 255).uniqueIndex()
    val name = varchar("name", 255)
    val role = enumerationByName("role", 20, UserRole::class).default(UserRole.ADMIN)
    val otpHash = varchar("otp_hash", 64).nullable()
    val otpExpiry = datetime("otp_expiry").nullable()
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
    val description = varchar("description", 500)
    val categoryId = reference("category_id", Categories)
    val createdBy = reference("created_by", Users).nullable()
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

object AuditLogs : IntIdTable("audit_logs") {
    val userId = reference("user_id", Users).nullable()
    val action = varchar("action", 50)
    val entity = varchar("entity", 50)
    val entityId = integer("entity_id").nullable()
    val details = text("details").nullable()
    val createdAt = datetime("created_at").default(TimeUtils.nowOslo())
}

object EmailLog : IntIdTable("email_log") {
    val toEmail = varchar("to_email", 255)
    val fromEmail = varchar("from_email", 255)
    val subject = varchar("subject", 500)
    val body = text("body")
    val type = enumerationByName("type", 20, EmailType::class)
    val status = enumerationByName("status", 20, EmailStatus::class)
    val errorMessage = text("error_message").nullable()
    val sentAt = datetime("sent_at").nullable()
    val createdAt = datetime("created_at").default(TimeUtils.nowOslo())
}

val allTables = arrayOf(Users, Categories, Transactions, Attachments, AuditLogs, EmailLog)
