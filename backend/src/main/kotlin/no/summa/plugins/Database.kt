package no.summa.plugins

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import no.summa.database.Seed
import no.summa.database.allTables
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

fun Application.configureDatabase() {
    val dbUrl = environment.config.propertyOrNull("database.url")?.getString()
        ?: System.getenv("DATABASE_URL")
        ?: "jdbc:postgresql://localhost:5432/summa"

    val dbUser = environment.config.propertyOrNull("database.user")?.getString()
        ?: System.getenv("DATABASE_USER")
        ?: "summa"

    val dbPassword = environment.config.propertyOrNull("database.password")?.getString()
        ?: System.getenv("DATABASE_PASSWORD")
        ?: "summa"

    val config = HikariConfig().apply {
        jdbcUrl = dbUrl
        username = dbUser
        password = dbPassword
        driverClassName = "org.postgresql.Driver"
        maximumPoolSize = 10
        isAutoCommit = false
        transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        validate()
    }

    val dataSource = HikariDataSource(config)
    Database.connect(dataSource)

    transaction {
        SchemaUtils.createMissingTablesAndColumns(*allTables)
        log.info("Database tilkoblet og tabeller opprettet: $dbUrl")
    }

    Seed.seedIfEmpty()
}
