package no.summa.models

import kotlinx.serialization.Serializable

// ===== Generiske responser =====

@Serializable
data class ErrorResponse(val error: String)

@Serializable
data class MessageResponse(val message: String)

// ===== Auth =====

@Serializable
data class RequestCodeRequest(val email: String)

@Serializable
data class RequestCodeResponse(
    val message: String,
    val devOtp: String? = null,
    val cooldownSeconds: Int? = null
)

@Serializable
data class VerifyCodeRequest(val email: String, val code: String)

@Serializable
data class VerifyCodeResponse(val success: Boolean, val role: String? = null)

@Serializable
data class CurrentUserResponse(
    val id: Int,
    val email: String,
    val name: String,
    val role: String
)

// ===== Kategorier =====

@Serializable
data class CategoryDTO(
    val id: Int,
    val code: String,
    val name: String,
    val type: String,
    val active: Boolean,
    val isDefault: Boolean
)

@Serializable
data class CreateCategoryRequest(
    val code: String,
    val name: String,
    val type: String
)

@Serializable
data class UpdateCategoryRequest(
    val code: String? = null,
    val name: String? = null,
    val type: String? = null,
    val active: Boolean? = null
)

// ===== Transaksjoner =====

@Serializable
data class TransactionDTO(
    val id: Int,
    val date: String,
    val type: String,
    val amount: String,
    val description: String,
    val categoryId: Int,
    val categoryCode: String? = null,
    val categoryName: String? = null,
    val createdBy: Int? = null,
    val createdAt: String,
    val updatedAt: String,
    val attachments: List<AttachmentDTO> = emptyList()
)

@Serializable
data class CreateTransactionRequest(
    val date: String,
    val type: String,
    val amount: String,
    val description: String,
    val categoryId: Int
)

@Serializable
data class UpdateTransactionRequest(
    val date: String? = null,
    val type: String? = null,
    val amount: String? = null,
    val description: String? = null,
    val categoryId: Int? = null
)

@Serializable
data class TransactionListResponse(
    val transactions: List<TransactionDTO>,
    val total: Int,
    val page: Int,
    val pageSize: Int
)

// ===== Vedlegg =====

@Serializable
data class AttachmentDTO(
    val id: Int,
    val transactionId: Int,
    val filename: String,
    val originalName: String,
    val mimeType: String,
    val createdAt: String
)

// ===== Rapporter =====

@Serializable
data class OverviewResponse(
    val totalInntekter: String,
    val totalUtgifter: String,
    val resultat: String,
    val antallTransaksjoner: Int,
    val sisteTransaksjoner: List<TransactionDTO>
)

@Serializable
data class MonthlyReportRow(
    val month: Int,
    val monthName: String,
    val inntekter: String,
    val utgifter: String,
    val resultat: String
)

@Serializable
data class MonthlyReportResponse(
    val year: Int,
    val months: List<MonthlyReportRow>
)

@Serializable
data class CategoryReportRow(
    val categoryId: Int,
    val categoryCode: String,
    val categoryName: String,
    val type: String,
    val total: String,
    val count: Int
)

@Serializable
data class CategoryReportResponse(
    val year: Int,
    val month: Int? = null,
    val categories: List<CategoryReportRow>
)

@Serializable
data class YearlyReportRow(
    val year: Int,
    val inntekter: String,
    val utgifter: String,
    val resultat: String
)

@Serializable
data class YearlyReportResponse(
    val years: List<YearlyReportRow>
)
