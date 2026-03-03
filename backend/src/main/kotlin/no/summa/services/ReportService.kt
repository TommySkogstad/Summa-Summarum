package no.summa.services

import no.summa.database.*
import no.summa.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ReportService {
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    private val monthNames = listOf(
        "", "Januar", "Februar", "Mars", "April", "Mai", "Juni",
        "Juli", "August", "September", "Oktober", "November", "Desember"
    )

    fun getOverview(): OverviewResponse = transaction {
        val now = TimeUtils.nowOslo()
        val startOfMonth = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0)
        val startOfYear = now.withMonth(1).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0)

        // YTD-totaler
        val ytdInntekter = Transactions.selectAll()
            .where { (Transactions.type eq TransactionType.INNTEKT) and (Transactions.date greaterEq startOfYear) }
            .sumOf { it[Transactions.amount] }

        val ytdUtgifter = Transactions.selectAll()
            .where { (Transactions.type eq TransactionType.UTGIFT) and (Transactions.date greaterEq startOfYear) }
            .sumOf { it[Transactions.amount] }

        val antallTransaksjoner = Transactions.selectAll()
            .where { Transactions.date greaterEq startOfYear }
            .count().toInt()

        // Siste 5 transaksjoner
        val sisteTransaksjoner = Transactions.innerJoin(Categories)
            .selectAll()
            .orderBy(Transactions.date, SortOrder.DESC)
            .limit(5)
            .map { row ->
                TransactionDTO(
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

    fun getMonthlyReport(year: Int): MonthlyReportResponse = transaction {
        val months = (1..12).map { month ->
            val startOfMonth = LocalDateTime.of(year, month, 1, 0, 0)
            val endOfMonth = startOfMonth.plusMonths(1)

            val inntekter = Transactions.selectAll()
                .where {
                    (Transactions.type eq TransactionType.INNTEKT) and
                    (Transactions.date greaterEq startOfMonth) and
                    (Transactions.date less endOfMonth)
                }
                .sumOf { it[Transactions.amount] }

            val utgifter = Transactions.selectAll()
                .where {
                    (Transactions.type eq TransactionType.UTGIFT) and
                    (Transactions.date greaterEq startOfMonth) and
                    (Transactions.date less endOfMonth)
                }
                .sumOf { it[Transactions.amount] }

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

    fun getCategoryReport(year: Int, month: Int?): CategoryReportResponse = transaction {
        val startDate: LocalDateTime
        val endDate: LocalDateTime

        if (month != null) {
            startDate = LocalDateTime.of(year, month, 1, 0, 0)
            endDate = startDate.plusMonths(1)
        } else {
            startDate = LocalDateTime.of(year, 1, 1, 0, 0)
            endDate = startDate.plusYears(1)
        }

        val categories = Transactions.innerJoin(Categories)
            .select(
                Categories.id,
                Categories.code,
                Categories.name,
                Categories.type,
                Transactions.amount.sum(),
                Transactions.id.count()
            )
            .where {
                (Transactions.date greaterEq startDate) and (Transactions.date less endDate)
            }
            .groupBy(Categories.id, Categories.code, Categories.name, Categories.type)
            .orderBy(Categories.code, SortOrder.ASC)
            .map { row ->
                CategoryReportRow(
                    categoryId = row[Categories.id].value,
                    categoryCode = row[Categories.code],
                    categoryName = row[Categories.name],
                    type = row[Categories.type].name,
                    total = (row[Transactions.amount.sum()] ?: BigDecimal.ZERO).toPlainString(),
                    count = row[Transactions.id.count()].toInt()
                )
            }

        CategoryReportResponse(year = year, month = month, categories = categories)
    }

    fun getYearlyReport(): YearlyReportResponse = transaction {
        // Finn alle ar med transaksjoner
        val years = Transactions.selectAll()
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
                    (Transactions.type eq TransactionType.INNTEKT) and
                    (Transactions.date greaterEq startOfYear) and
                    (Transactions.date less endOfYear)
                }
                .sumOf { it[Transactions.amount] }

            val utgifter = Transactions.selectAll()
                .where {
                    (Transactions.type eq TransactionType.UTGIFT) and
                    (Transactions.date greaterEq startOfYear) and
                    (Transactions.date less endOfYear)
                }
                .sumOf { it[Transactions.amount] }

            YearlyReportRow(
                year = year,
                inntekter = inntekter.toPlainString(),
                utgifter = utgifter.toPlainString(),
                resultat = (inntekter - utgifter).toPlainString()
            )
        }

        YearlyReportResponse(years = yearlyData)
    }

    private fun Iterable<ResultRow>.sumOf(selector: (ResultRow) -> BigDecimal): BigDecimal {
        return this.fold(BigDecimal.ZERO) { acc, row -> acc + selector(row) }
    }
}
