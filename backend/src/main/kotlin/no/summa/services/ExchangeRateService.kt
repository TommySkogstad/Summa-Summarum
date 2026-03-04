package no.summa.services

import no.summa.models.ExchangeRateResponse
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.*

class ExchangeRateService {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val httpClient = HttpClient.newHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = mutableMapOf<String, BigDecimal>()

    fun getRate(currency: String, date: LocalDate): ExchangeRateResponse? {
        if (currency.uppercase() == "NOK") return ExchangeRateResponse(
            base = "NOK", target = "NOK", rate = "1.000000", date = date.toString()
        )

        val cacheKey = "${currency.uppercase()}_$date"
        cache[cacheKey]?.let { rate ->
            return ExchangeRateResponse(
                base = currency.uppercase(),
                target = "NOK",
                rate = rate.toPlainString(),
                date = date.toString()
            )
        }

        // Try fetching from Norges Bank, going back up to 7 days for weekends/holidays
        for (daysBack in 0..7) {
            val queryDate = date.minusDays(daysBack.toLong())
            val rate = fetchFromNorgesBank(currency.uppercase(), queryDate)
            if (rate != null) {
                cache[cacheKey] = rate
                return ExchangeRateResponse(
                    base = currency.uppercase(),
                    target = "NOK",
                    rate = rate.toPlainString(),
                    date = queryDate.toString()
                )
            }
        }

        logger.warn("Kunne ikke hente kurs for $currency pa dato $date")
        return null
    }

    private fun fetchFromNorgesBank(currency: String, date: LocalDate): BigDecimal? {
        return try {
            val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val url = "https://data.norges-bank.no/api/data/EXR/B.$currency.NOK.SP?format=sdmx-json&startPeriod=$dateStr&endPeriod=$dateStr"

            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() != 200) return null

            val jsonResponse = json.parseToJsonElement(response.body()).jsonObject
            val data = jsonResponse["data"]?.jsonObject ?: return null
            val dataSets = data["dataSets"]?.jsonArray ?: return null
            if (dataSets.isEmpty()) return null

            val series = dataSets[0].jsonObject["series"]?.jsonObject ?: return null
            if (series.isEmpty()) return null

            val firstSeries = series.values.firstOrNull()?.jsonObject ?: return null
            val observations = firstSeries["observations"]?.jsonObject ?: return null
            if (observations.isEmpty()) return null

            val firstObs = observations.values.firstOrNull()?.jsonArray ?: return null
            if (firstObs.isEmpty()) return null

            val rateStr = firstObs[0].jsonPrimitive.content
            BigDecimal(rateStr)
        } catch (e: Exception) {
            logger.error("Feil ved henting av kurs fra Norges Bank for $currency/$date: ${e.message}", e)
            null
        }
    }
}
