package no.summa.services

import no.summa.database.*
import no.summa.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

class CategoryService {

    fun getAll(): List<CategoryDTO> = transaction {
        Categories.selectAll()
            .orderBy(Categories.code, SortOrder.ASC)
            .map { toDTO(it) }
    }

    fun getById(id: Int): CategoryDTO? = transaction {
        Categories.selectAll()
            .where { Categories.id eq id }
            .singleOrNull()
            ?.let { toDTO(it) }
    }

    fun create(request: CreateCategoryRequest): CategoryDTO = transaction {
        val type = CategoryType.valueOf(request.type)

        val id = Categories.insertAndGetId {
            it[code] = request.code
            it[name] = request.name
            it[Categories.type] = type
            it[active] = true
            it[isDefault] = false
        }.value

        getById(id)!!
    }

    fun update(id: Int, request: UpdateCategoryRequest): CategoryDTO? = transaction {
        val existing = Categories.selectAll()
            .where { Categories.id eq id }
            .singleOrNull() ?: return@transaction null

        Categories.update({ Categories.id eq id }) {
            request.code?.let { c -> it[code] = c }
            request.name?.let { n -> it[name] = n }
            request.type?.let { t -> it[type] = CategoryType.valueOf(t) }
            request.active?.let { a -> it[active] = a }
        }

        getById(id)
    }

    fun delete(id: Int): Boolean = transaction {
        val existing = Categories.selectAll()
            .where { Categories.id eq id }
            .singleOrNull() ?: return@transaction false

        // Sjekk om kategorien er i bruk
        val inUse = Transactions.selectAll()
            .where { Transactions.categoryId eq id }
            .count() > 0

        if (inUse) {
            // Deaktiver i stedet for a slette
            Categories.update({ Categories.id eq id }) {
                it[active] = false
            }
        } else {
            Categories.deleteWhere { Categories.id eq id }
        }

        true
    }

    private fun toDTO(row: ResultRow): CategoryDTO {
        return CategoryDTO(
            id = row[Categories.id].value,
            code = row[Categories.code],
            name = row[Categories.name],
            type = row[Categories.type].name,
            active = row[Categories.active],
            isDefault = row[Categories.isDefault]
        )
    }
}
