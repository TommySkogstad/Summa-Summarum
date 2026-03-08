package no.summa.services

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.*
import no.grunnmur.TimeUtils
import no.summa.database.*
import no.summa.models.ParsedDocumentDTO
import no.summa.models.ParsedLineItemDTO
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.util.Base64

@Serializable
data class ParsedDocumentJson(
    val totalAmount: Double? = null,
    val currency: String? = null,
    val vatAmount: Double? = null,
    val vatRate: Double? = null,
    val invoiceDate: String? = null,
    val paymentDueDate: String? = null,
    val paymentReference: String? = null,
    val vendorName: String? = null,
    val vendorOrgNumber: String? = null,
    val invoiceNumber: String? = null,
    val confidence: Double? = null,
    val lineItems: List<ParsedLineItemJson> = emptyList()
)

@Serializable
data class ParsedLineItemJson(
    val description: String? = null,
    val quantity: Double? = null,
    val unitPrice: Double? = null,
    val amount: Double? = null,
    val vatRate: Double? = null,
    val vatAmount: Double? = null
)

sealed class ParseResult {
    data class Success(val document: ParsedDocumentJson, val rawJson: String) : ParseResult()
    data class Failed(val error: String) : ParseResult()
    object Disabled : ParseResult()
    object Unsupported : ParseResult()
}

class DocumentParserService(apiKey: String?) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val client: AnthropicClient? = apiKey?.takeIf { it.isNotBlank() }?.let {
        logger.info("DocumentParserService: Anthropic API-noekkel konfigurert, parsing er aktivert")
        AnthropicOkHttpClient.builder().apiKey(it).build()
    }
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    val isEnabled: Boolean get() = client != null

    init {
        if (client == null) {
            logger.info("DocumentParserService: Ingen ANTHROPIC_API_KEY, parsing er deaktivert")
        }
    }

    fun canParse(mimeType: String): Boolean {
        return mimeType.startsWith("image/") || mimeType == "application/pdf"
    }

    fun parseAndStore(attachmentId: Int, fileBytes: ByteArray, mimeType: String) {
        val result = parse(fileBytes, mimeType)
        storeResult(attachmentId, result)
    }

    fun parse(fileBytes: ByteArray, mimeType: String): ParseResult {
        if (client == null) return ParseResult.Disabled
        if (!canParse(mimeType)) return ParseResult.Unsupported

        return try {
            val base64 = Base64.getEncoder().encodeToString(fileBytes)

            val contentBlock = if (mimeType == "application/pdf") {
                ContentBlockParam.ofDocument(DocumentBlockParam.builder()
                    .source(Base64PdfSource.builder()
                        .data(base64)
                        .build())
                    .build())
            } else {
                ContentBlockParam.ofImage(ImageBlockParam.builder()
                    .source(Base64ImageSource.builder()
                        .mediaType(mapMediaType(mimeType))
                        .data(base64)
                        .build())
                    .build())
            }

            val textBlock = ContentBlockParam.ofText(TextBlockParam.builder().text(PARSE_PROMPT).build())

            val response = client.messages().create(
                MessageCreateParams.builder()
                    .model(Model.CLAUDE_HAIKU_4_5_20251001)
                    .maxTokens(1024)
                    .addUserMessageOfBlockParams(listOf(contentBlock, textBlock))
                    .build()
            )

            val textContent = response.content()
                .mapNotNull { it.text().orElse(null) }
                .firstOrNull()
                ?: return ParseResult.Failed("Ingen tekstrespons fra API")

            val rawJson = extractJson(textContent.text())
            val parsed = json.decodeFromString<ParsedDocumentJson>(rawJson)
            ParseResult.Success(parsed, rawJson)
        } catch (e: Exception) {
            logger.error("Feil ved parsing av dokument: ${e.message}", e)
            ParseResult.Failed(e.message ?: "Ukjent feil")
        }
    }

    private fun storeResult(attachmentId: Int, result: ParseResult) {
        when (result) {
            is ParseResult.Disabled, is ParseResult.Unsupported -> return
            is ParseResult.Failed -> transaction {
                ParsedDocuments.insert {
                    it[ParsedDocuments.attachmentId] = attachmentId
                    it[rawJson] = ""
                    it[status] = ParseStatus.FAILED
                    it[errorMessage] = result.error
                    it[createdAt] = TimeUtils.nowOslo()
                }
            }
            is ParseResult.Success -> transaction {
                val doc = result.document
                val docId = ParsedDocuments.insertAndGetId {
                    it[ParsedDocuments.attachmentId] = attachmentId
                    it[totalAmount] = doc.totalAmount?.let { a -> BigDecimal(a.toString()) }
                    it[currency] = doc.currency
                    it[vatAmount] = doc.vatAmount?.let { a -> BigDecimal(a.toString()) }
                    it[vatRate] = doc.vatRate?.let { a -> BigDecimal(a.toString()) }
                    it[invoiceDate] = doc.invoiceDate
                    it[paymentDueDate] = doc.paymentDueDate
                    it[paymentReference] = doc.paymentReference
                    it[vendorName] = doc.vendorName
                    it[vendorOrgNumber] = doc.vendorOrgNumber
                    it[invoiceNumber] = doc.invoiceNumber
                    it[rawJson] = result.rawJson
                    it[confidence] = doc.confidence?.let { c -> BigDecimal(c.toString()) }
                    it[status] = ParseStatus.SUCCESS
                    it[createdAt] = TimeUtils.nowOslo()
                }.value

                for (item in doc.lineItems) {
                    ParsedLineItems.insert {
                        it[parsedDocumentId] = docId
                        it[description] = item.description
                        it[quantity] = item.quantity?.let { q -> BigDecimal(q.toString()) }
                        it[unitPrice] = item.unitPrice?.let { u -> BigDecimal(u.toString()) }
                        it[amount] = item.amount?.let { a -> BigDecimal(a.toString()) }
                        it[vatRate] = item.vatRate?.let { v -> BigDecimal(v.toString()) }
                        it[vatAmount] = item.vatAmount?.let { v -> BigDecimal(v.toString()) }
                    }
                }
            }
        }
    }

    fun getParsedDocument(attachmentId: Int): ParsedDocumentDTO? = transaction {
        val row = ParsedDocuments.selectAll()
            .where { ParsedDocuments.attachmentId eq attachmentId }
            .singleOrNull() ?: return@transaction null

        val docId = row[ParsedDocuments.id].value
        val lineItems = ParsedLineItems.selectAll()
            .where { ParsedLineItems.parsedDocumentId eq docId }
            .map { li ->
                ParsedLineItemDTO(
                    description = li[ParsedLineItems.description],
                    quantity = li[ParsedLineItems.quantity]?.toPlainString(),
                    unitPrice = li[ParsedLineItems.unitPrice]?.toPlainString(),
                    amount = li[ParsedLineItems.amount]?.toPlainString(),
                    vatRate = li[ParsedLineItems.vatRate]?.toPlainString(),
                    vatAmount = li[ParsedLineItems.vatAmount]?.toPlainString()
                )
            }

        ParsedDocumentDTO(
            id = docId,
            totalAmount = row[ParsedDocuments.totalAmount]?.toPlainString(),
            currency = row[ParsedDocuments.currency],
            vatAmount = row[ParsedDocuments.vatAmount]?.toPlainString(),
            vatRate = row[ParsedDocuments.vatRate]?.toPlainString(),
            invoiceDate = row[ParsedDocuments.invoiceDate],
            paymentDueDate = row[ParsedDocuments.paymentDueDate],
            paymentReference = row[ParsedDocuments.paymentReference],
            vendorName = row[ParsedDocuments.vendorName],
            vendorOrgNumber = row[ParsedDocuments.vendorOrgNumber],
            invoiceNumber = row[ParsedDocuments.invoiceNumber],
            confidence = row[ParsedDocuments.confidence]?.toPlainString(),
            status = row[ParsedDocuments.status].name,
            errorMessage = row[ParsedDocuments.errorMessage],
            lineItems = lineItems
        )
    }

    private fun mapMediaType(mimeType: String): Base64ImageSource.MediaType {
        return when (mimeType) {
            "image/jpeg" -> Base64ImageSource.MediaType.IMAGE_JPEG
            "image/png" -> Base64ImageSource.MediaType.IMAGE_PNG
            "image/gif" -> Base64ImageSource.MediaType.IMAGE_GIF
            "image/webp" -> Base64ImageSource.MediaType.IMAGE_WEBP
            else -> Base64ImageSource.MediaType.IMAGE_JPEG
        }
    }

    private fun extractJson(text: String): String {
        // Extract JSON from potential markdown code blocks
        val jsonBlockRegex = Regex("```(?:json)?\\s*\\n?(\\{.*?})\\s*\\n?```", RegexOption.DOT_MATCHES_ALL)
        jsonBlockRegex.find(text)?.let { return it.groupValues[1].trim() }

        // Try to find raw JSON object
        val jsonObjRegex = Regex("(\\{.*})", RegexOption.DOT_MATCHES_ALL)
        jsonObjRegex.find(text)?.let { return it.groupValues[1].trim() }

        return text.trim()
    }

    companion object {
        private val PARSE_PROMPT = """
Analyser dette bildet av en kvittering eller faktura. Returner BARE en JSON-objekt (ingen annen tekst) med følgende struktur:

{
  "totalAmount": 123.45,
  "currency": "NOK",
  "vatAmount": 24.69,
  "vatRate": 25.00,
  "invoiceDate": "2025-01-15",
  "paymentDueDate": "2025-02-15",
  "paymentReference": "1234567890",
  "vendorName": "Butikknavn AS",
  "vendorOrgNumber": "123456789",
  "invoiceNumber": "12345",
  "confidence": 0.95,
  "lineItems": [
    {
      "description": "Varebeskrivelse",
      "quantity": 1.0,
      "unitPrice": 100.00,
      "amount": 100.00,
      "vatRate": 25.00,
      "vatAmount": 20.00
    }
  ]
}

Regler:
- totalAmount: Totalbeløp inkl. mva, som tall (ikke streng)
- currency: Valutakode (default "NOK" hvis ikke angitt)
- vatAmount: Totalt MVA-beløp som tall
- vatRate: MVA-sats i prosent (f.eks. 25.00, 15.00, 12.00)
- invoiceDate: Fakturadato i format YYYY-MM-DD
- paymentDueDate: Forfallsdato i format YYYY-MM-DD
- paymentReference: KID-nummer eller betalingsreferanse
- vendorName: Navn på butikk/leverandør
- vendorOrgNumber: Leverandørens organisasjonsnummer (kun siffer)
- invoiceNumber: Fakturanummer hvis synlig
- confidence: Tall mellom 0 og 1 for hvor sikker du er på dataene
- lineItems: Liste over enkeltposter med MVA per linje hvis synlig
- Bruk null for felt du ikke finner
- Returner KUN JSON, ingen forklaring
""".trimIndent()
    }
}
