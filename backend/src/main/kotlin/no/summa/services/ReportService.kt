package no.summa.services

import no.grunnmur.TimeUtils
import no.summa.database.*
import no.summa.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ReportService {
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    private val monthNames = listOf(
        "", "Januar", "Februar", "Mars", "April", "Mai", "Juni",
        "Juli", "August", "September", "Oktober", "November", "Desember"
    )

    fun getOverview(organizationId: Int): OverviewResponse = transaction {
        val now = TimeUtils.nowOslo()
        val startOfYear = LocalDateTime.of(now.year, 1, 1, 0, 0)

        val orgFilter: Op<Boolean> = Transactions.organizationId eq organizationId

        // YTD-totaler (bruker amountNok for utenlandsk valuta)
        val ytdInntekter = Transactions.selectAll()
            .where { orgFilter and (Transactions.type eq TransactionType.INNTEKT) and (Transactions.date greaterEq startOfYear) }
            .sumOfEffective()

        val ytdUtgifter = Transactions.selectAll()
            .where { orgFilter and (Transactions.type eq TransactionType.UTGIFT) and (Transactions.date greaterEq startOfYear) }
            .sumOfEffective()

        val antallTransaksjoner = Transactions.selectAll()
            .where { orgFilter and (Transactions.date greaterEq startOfYear) }
            .count().toInt()

        // Siste 5 transaksjoner
        val sisteTransaksjoner = Transactions.innerJoin(Categories)
            .selectAll()
            .where { orgFilter }
            .orderBy(Transactions.date, SortOrder.DESC)
            .limit(5)
            .map { row ->
                TransactionDTO(
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
                    updatedAt = row[Transactions.updatedAt].format(dateFormatter)
                )
            }

        OverviewResponse(
            totalInntekter = ytdInntekter.toPlainString(),
            totalUtgifter = ytdUtgifter.toPlainString(),
            resultat = (ytdInntekter - ytdUtgifter).toPlainString(),
            antallTransaksjoner = antallTransaksjoner,
            sisteTransaksjoner = sisteTransaksjoner
        )
    }

    fun getMonthlyReport(year: Int, organizationId: Int): MonthlyReportResponse = transaction {
        val orgFilter: Op<Boolean> = Transactions.organizationId eq organizationId

        val months = (1..12).map { month ->
            val startOfMonth = LocalDateTime.of(year, month, 1, 0, 0)
            val endOfMonth = startOfMonth.plusMonths(1)

            val inntekter = Transactions.selectAll()
                .where {
                    orgFilter and
                    (Transactions.type eq TransactionType.INNTEKT) and
                    (Transactions.date greaterEq startOfMonth) and
                    (Transactions.date less endOfMonth)
                }
                .sumOfEffective()

            val utgifter = Transactions.selectAll()
                .where {
                    orgFilter and
                    (Transactions.type eq TransactionType.UTGIFT) and
                    (Transactions.date greaterEq startOfMonth) and
                    (Transactions.date less endOfMonth)
                }
                .sumOfEffective()

            MonthlyReportRow(
                month = month,
                monthName = monthNames[month],
                inntekter = inntekter.toPlainString(),
                utgifter = utgifter.toPlainString(),
                resultat = (inntekter - utgifter).toPlainString()
            )
        }

        MonthlyReportResponse(year = year, months = months)
    }

    fun getCategoryReport(year: Int, month: Int?, organizationId: Int): CategoryReportResponse = transaction {
        val startDate: LocalDateTime
        val endDate: LocalDateTime

        if (month != null) {
            startDate = LocalDateTime.of(year, month, 1, 0, 0)
            endDate = startDate.plusMonths(1)
        } else {
            startDate = LocalDateTime.of(year, 1, 1, 0, 0)
            endDate = startDate.plusYears(1)
        }

        val orgFilter: Op<Boolean> = Transactions.organizationId eq organizationId

        val rows = Transactions.innerJoin(Categories)
            .selectAll()
            .where {
                orgFilter and
                (Transactions.date greaterEq startDate) and (Transactions.date less endDate)
            }
            .toList()

        val categories = rows
            .groupBy { Triple(it[Categories.id].value, it[Categories.code], it[Categories.name]) }
            .map { (key, groupRows) ->
                CategoryReportRow(
                    categoryId = key.first,
                    categoryCode = key.second,
                    categoryName = key.third,
                    type = groupRows.first()[Categories.type].name,
                    total = groupRows.fold(BigDecimal.ZERO) { acc, row -> acc + effectiveAmount(row) }.toPlainString(),
                    count = groupRows.size
                )
            }
            .sortedBy { it.categoryCode }

        CategoryReportResponse(year = year, month = month, categories = categories)
    }

    fun getYearlyReport(organizationId: Int): YearlyReportResponse = transaction {
        val orgFilter: Op<Boolean> = Transactions.organizationId eq organizationId

        // Finn alle ar med transaksjoner for denne org
        val years = Transactions.selectAll()
            .where { orgFilter }
            .map { it[Transactions.date].year }
            .distinct()
            .sorted()

        if (years.isEmpty()) {
            val currentYear = TimeUtils.nowOslo().year
            return@transaction YearlyReportResponse(
                years = listOf(YearlyReportRow(currentYear, "0", "0", "0"))
            )
        }

        val yearlyData = years.map { year ->
            val startOfYear = LocalDateTime.of(year, 1, 1, 0, 0)
            val endOfYear = startOfYear.plusYears(1)

            val inntekter = Transactions.selectAll()
                .where {
                    orgFilter and
                    (Transactions.type eq TransactionType.INNTEKT) and
                    (Transactions.date greaterEq startOfYear) and
                    (Transactions.date less endOfYear)
                }
                .sumOfEffective()

            val utgifter = Transactions.selectAll()
                .where {
                    orgFilter and
                    (Transactions.type eq TransactionType.UTGIFT) and
                    (Transactions.date greaterEq startOfYear) and
                    (Transactions.date less endOfYear)
                }
                .sumOfEffective()

            YearlyReportRow(
                year = year,
                inntekter = inntekter.toPlainString(),
                utgifter = utgifter.toPlainString(),
                resultat = (inntekter - utgifter).toPlainString()
            )
        }

        YearlyReportResponse(years = yearlyData)
    }

    fun getMvaReport(year: Int, month: Int?, organizationId: Int): MvaReportResponse = transaction {
        val orgFilter: Op<Boolean> = Transactions.organizationId eq organizationId

        val startDate: LocalDateTime
        val endDate: LocalDateTime
        if (month != null) {
            startDate = LocalDateTime.of(year, month, 1, 0, 0)
            endDate = startDate.plusMonths(1)
        } else {
            startDate = LocalDateTime.of(year, 1, 1, 0, 0)
            endDate = startDate.plusYears(1)
        }

        // Utgaaende MVA = MVA pa inntekter (salg)
        val inntektRows = Transactions.selectAll()
            .where {
                orgFilter and
                (Transactions.date greaterEq startDate) and (Transactions.date less endDate) and
                (Transactions.type eq TransactionType.INNTEKT) and Transactions.vatAmount.isNotNull()
            }
            .toList()

        val utgaaendeMva = inntektRows.fold(BigDecimal.ZERO) { acc, row ->
            acc + (row[Transactions.vatAmount] ?: BigDecimal.ZERO)
        }
        val mvaGrunnlagUtgaaende = inntektRows.fold(BigDecimal.ZERO) { acc, row ->
            acc + effectiveAmount(row)
        }

        // Inngaaende MVA = MVA pa utgifter (kjop)
        val utgiftRows = Transactions.selectAll()
            .where {
                orgFilter and
                (Transactions.date greaterEq startDate) and (Transactions.date less endDate) and
                (Transactions.type eq TransactionType.UTGIFT) and Transactions.vatAmount.isNotNull()
            }
            .toList()

        val inngaaendeMva = utgiftRows.fold(BigDecimal.ZERO) { acc, row ->
            acc + (row[Transactions.vatAmount] ?: BigDecimal.ZERO)
        }
        val mvaGrunnlagInngaaende = utgiftRows.fold(BigDecimal.ZERO) { acc, row ->
            acc + effectiveAmount(row)
        }

        val nettoBetaling = utgaaendeMva - inngaaendeMva

        MvaReportResponse(
            year = year,
            month = month,
            utgaaendeMva = utgaaendeMva.setScale(2, RoundingMode.HALF_UP).toPlainString(),
            inngaaendeMva = inngaaendeMva.setScale(2, RoundingMode.HALF_UP).toPlainString(),
            mvaGrunnlagUtgaaende = mvaGrunnlagUtgaaende.setScale(2, RoundingMode.HALF_UP).toPlainString(),
            mvaGrunnlagInngaaende = mvaGrunnlagInngaaende.setScale(2, RoundingMode.HALF_UP).toPlainString(),
            nettoBetaling = nettoBetaling.setScale(2, RoundingMode.HALF_UP).toPlainString()
        )
    }

    /** Use amountNok when available (foreign currency), otherwise use amount */
    private fun effectiveAmount(row: ResultRow): BigDecimal {
        return row[Transactions.amountNok] ?: row[Transactions.amount]
    }

    private fun Iterable<ResultRow>.sumOfEffective(): BigDecimal {
        return this.fold(BigDecimal.ZERO) { acc, row -> acc + effectiveAmount(row) }
    }

    private fun Iterable<ResultRow>.sumOf(selector: (ResultRow) -> BigDecimal): BigDecimal {
        return this.fold(BigDecimal.ZERO) { acc, row -> acc + selector(row) }
    }
}
