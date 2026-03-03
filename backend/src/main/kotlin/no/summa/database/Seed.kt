package no.summa.database

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

object Seed {
    private val logger = LoggerFactory.getLogger(Seed::class.java)

    fun seedIfEmpty() {
        transaction {
            // Opprett admin-bruker hvis ingen finnes
            if (Users.selectAll().empty()) {
                val adminEmail = System.getenv("INITIAL_ADMIN_EMAIL") ?: "admin@example.com"
                val adminName = System.getenv("INITIAL_ADMIN_NAME") ?: "Administrator"

                Users.insert {
                    it[email] = adminEmail.lowercase()
                    it[name] = adminName
                    it[role] = UserRole.ADMIN
                }
                logger.info("Opprettet admin-bruker: $adminEmail")
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
        }
    }
}
