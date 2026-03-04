package no.summa.database

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.LocalDateTime

object Seed {
    private val logger = LoggerFactory.getLogger(Seed::class.java)

    fun seedIfEmpty() {
        transaction {
            // Opprett default organisasjon hvis ingen finnes
            if (Organizations.selectAll().empty()) {
                val orgName = System.getenv("INITIAL_ORG_NAME") ?: "Standard"

                val orgId = Organizations.insertAndGetId {
                    it[name] = orgName
                    it[active] = true
                }.value

                // Migrer eksisterende transaksjoner uten organizationId
                Transactions.update({ Transactions.organizationId.isNull() }) {
                    it[organizationId] = orgId
                }

                logger.info("Opprettet default organisasjon: $orgName (id=$orgId)")
            }

            // Opprett standard kontoplan hvis ingen kategorier finnes
            if (Categories.selectAll().empty()) {
                val defaultCategories = listOf(
                    // Inntekter
                    Triple("3000", "Salgsinntekt", CategoryType.INNTEKT),
                    Triple("3100", "Medlemskontingent", CategoryType.INNTEKT),
                    Triple("3200", "Offentlig tilskudd", CategoryType.INNTEKT),
                    Triple("3400", "Sponsorinntekt", CategoryType.INNTEKT),
                    Triple("3600", "Leieinntekt", CategoryType.INNTEKT),
                    Triple("3900", "Annen driftsinntekt", CategoryType.INNTEKT),
                    // Utgifter
                    Triple("4000", "Varekostnad", CategoryType.UTGIFT),
                    Triple("5000", "Lonn og godtgjorelse", CategoryType.UTGIFT),
                    Triple("5400", "Arbeidsgiveravgift", CategoryType.UTGIFT),
                    Triple("6300", "Leie lokaler", CategoryType.UTGIFT),
                    Triple("6400", "Lys og varme", CategoryType.UTGIFT),
                    Triple("6500", "Verktoy og inventar", CategoryType.UTGIFT),
                    Triple("6700", "Kontorrekvisita", CategoryType.UTGIFT),
                    Triple("6800", "Telefon og internett", CategoryType.UTGIFT),
                    Triple("7000", "Reisekostnad", CategoryType.UTGIFT),
                    Triple("7300", "Forsikring", CategoryType.UTGIFT),
                    Triple("7700", "Bank- og kortgebyr", CategoryType.UTGIFT),
                    Triple("7900", "Annen driftskostnad", CategoryType.UTGIFT),
                )

                for ((code, name, type) in defaultCategories) {
                    Categories.insert {
                        it[Categories.code] = code
                        it[Categories.name] = name
                        it[Categories.type] = type
                        it[active] = true
                        it[isDefault] = true
                    }
                }
                logger.info("Opprettet ${defaultCategories.size} standard kategorier")
            }

            // Seed testbedrift med eksempeldata i dev-modus
            if (System.getenv("SEED_TEST_DATA") == "true") {
                seedTestData()
            }
        }
    }

    private fun seedTestData() {
        // Sjekk om testbedriften allerede finnes
        val existing = Organizations.selectAll()
            .where { Organizations.name eq "Testbedrift AS" }
            .firstOrNull()
        if (existing != null) return

        val orgId = Organizations.insertAndGetId {
            it[name] = "Testbedrift AS"
            it[orgNumber] = "999888777"
            it[active] = true
        }.value

        logger.info("Opprettet testbedrift (id=$orgId), legger til seed-transaksjoner...")

        // Hent kategori-IDer
        val cats = Categories.selectAll().associate { it[Categories.code] to it[Categories.id].value }

        val year = TimeUtils.nowOslo().year
        val now = TimeUtils.nowOslo()

        data class SeedTx(val month: Int, val day: Int, val type: TransactionType, val amount: String, val desc: String, val catCode: String)

        val txs = listOf(
            // Januar
            SeedTx(1, 1, TransactionType.INNTEKT, "50000.00", "Nyttarsfaktura kunde A", "3000"),
            SeedTx(1, 5, TransactionType.UTGIFT, "12000.00", "Husleie januar", "6300"),
            SeedTx(1, 10, TransactionType.UTGIFT, "1500.00", "Internett januar", "6800"),
            SeedTx(1, 15, TransactionType.INNTEKT, "25000.00", "Konsulentoppdrag", "3000"),
            SeedTx(1, 20, TransactionType.UTGIFT, "800.00", "Kontorrekvisita", "6700"),
            // Februar
            SeedTx(2, 1, TransactionType.UTGIFT, "12000.00", "Husleie februar", "6300"),
            SeedTx(2, 5, TransactionType.INNTEKT, "35000.00", "Faktura kunde B", "3000"),
            SeedTx(2, 14, TransactionType.UTGIFT, "3500.00", "Forsikring", "7300"),
            SeedTx(2, 20, TransactionType.INNTEKT, "15000.00", "Medlemskontingenter", "3100"),
            SeedTx(2, 25, TransactionType.UTGIFT, "250.00", "Bankgebyr", "7700"),
            // Mars
            SeedTx(3, 1, TransactionType.UTGIFT, "12000.00", "Husleie mars", "6300"),
            SeedTx(3, 5, TransactionType.INNTEKT, "40000.00", "Prosjektlevering", "3000"),
            SeedTx(3, 10, TransactionType.UTGIFT, "1500.00", "Internett mars", "6800"),
            SeedTx(3, 15, TransactionType.INNTEKT, "20000.00", "Offentlig tilskudd", "3200"),
            SeedTx(3, 20, TransactionType.UTGIFT, "5000.00", "Reise konferanse", "7000"),
            // April
            SeedTx(4, 1, TransactionType.UTGIFT, "12000.00", "Husleie april", "6300"),
            SeedTx(4, 10, TransactionType.INNTEKT, "30000.00", "Faktura kunde C", "3000"),
            SeedTx(4, 15, TransactionType.UTGIFT, "2000.00", "Verktoy", "6500"),
            SeedTx(4, 20, TransactionType.INNTEKT, "10000.00", "Sponsoravtale", "3400"),
            // Mai
            SeedTx(5, 1, TransactionType.UTGIFT, "12000.00", "Husleie mai", "6300"),
            SeedTx(5, 5, TransactionType.INNTEKT, "45000.00", "Storprosjekt fase 1", "3000"),
            SeedTx(5, 15, TransactionType.UTGIFT, "1500.00", "Internett mai", "6800"),
            SeedTx(5, 20, TransactionType.UTGIFT, "4000.00", "Lys og varme", "6400"),
            // Juni
            SeedTx(6, 1, TransactionType.UTGIFT, "12000.00", "Husleie juni", "6300"),
            SeedTx(6, 10, TransactionType.INNTEKT, "55000.00", "Storprosjekt fase 2", "3000"),
            SeedTx(6, 15, TransactionType.UTGIFT, "8000.00", "Sommerfest ansatte", "7900"),
            SeedTx(6, 20, TransactionType.INNTEKT, "5000.00", "Utleie utstyr", "3600"),
            // Juli
            SeedTx(7, 1, TransactionType.UTGIFT, "12000.00", "Husleie juli", "6300"),
            SeedTx(7, 10, TransactionType.INNTEKT, "20000.00", "Vedlikehold kunde A", "3000"),
            SeedTx(7, 15, TransactionType.UTGIFT, "250.00", "Bankgebyr", "7700"),
            // August
            SeedTx(8, 1, TransactionType.UTGIFT, "12000.00", "Husleie august", "6300"),
            SeedTx(8, 5, TransactionType.INNTEKT, "60000.00", "Hoystprosjekt", "3000"),
            SeedTx(8, 15, TransactionType.UTGIFT, "1500.00", "Internett august", "6800"),
            SeedTx(8, 20, TransactionType.UTGIFT, "3000.00", "Varekjop", "4000"),
            // September
            SeedTx(9, 1, TransactionType.UTGIFT, "12000.00", "Husleie september", "6300"),
            SeedTx(9, 10, TransactionType.INNTEKT, "35000.00", "Faktura kunde D", "3000"),
            SeedTx(9, 15, TransactionType.UTGIFT, "6000.00", "Reise kundemote", "7000"),
            SeedTx(9, 20, TransactionType.INNTEKT, "15000.00", "Medlemskontingenter host", "3100"),
            // Oktober
            SeedTx(10, 1, TransactionType.UTGIFT, "12000.00", "Husleie oktober", "6300"),
            SeedTx(10, 10, TransactionType.INNTEKT, "40000.00", "Prosjekt E", "3000"),
            SeedTx(10, 15, TransactionType.UTGIFT, "3500.00", "Forsikring", "7300"),
            SeedTx(10, 20, TransactionType.UTGIFT, "1200.00", "Kontorrekvisita", "6700"),
            // November
            SeedTx(11, 1, TransactionType.UTGIFT, "12000.00", "Husleie november", "6300"),
            SeedTx(11, 5, TransactionType.INNTEKT, "50000.00", "Arsavtale kunde F", "3000"),
            SeedTx(11, 15, TransactionType.UTGIFT, "1500.00", "Internett november", "6800"),
            SeedTx(11, 20, TransactionType.UTGIFT, "4500.00", "Lys og varme", "6400"),
            // Desember
            SeedTx(12, 1, TransactionType.UTGIFT, "12000.00", "Husleie desember", "6300"),
            SeedTx(12, 10, TransactionType.INNTEKT, "30000.00", "Avsluttende faktura", "3000"),
            SeedTx(12, 15, TransactionType.UTGIFT, "12000.00", "Julebord", "7900"),
            SeedTx(12, 20, TransactionType.INNTEKT, "10000.00", "Annen inntekt", "3900"),
            SeedTx(12, 31, TransactionType.UTGIFT, "250.00", "Bankgebyr desember", "7700"),
        )

        for (tx in txs) {
            val txDate = LocalDateTime.of(year, tx.month, tx.day, 0, 0)
            // Ikke legg til fremtidige transaksjoner
            if (txDate.isAfter(now)) continue

            val catId = cats[tx.catCode] ?: continue
            Transactions.insert {
                it[date] = txDate
                it[type] = tx.type
                it[amount] = BigDecimal(tx.amount)
                it[description] = tx.desc
                it[categoryId] = catId
                it[organizationId] = orgId
            }
        }

        val count = Transactions.selectAll()
            .where { Transactions.organizationId eq orgId }
            .count()
        logger.info("Seed ferdig: $count transaksjoner for Testbedrift AS")
    }
}
