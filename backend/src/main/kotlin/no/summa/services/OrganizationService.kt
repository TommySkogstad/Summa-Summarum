package no.summa.services

import no.summa.database.*
import no.summa.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.format.DateTimeFormatter

class OrganizationService {
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    fun getAll(): List<OrganizationDTO> = transaction {
        Organizations.selectAll()
            .orderBy(Organizations.name, SortOrder.ASC)
            .map { toDTO(it) }
    }

    fun getById(id: Int): OrganizationDTO? = transaction {
        Organizations.selectAll()
            .where { Organizations.id eq id }
            .singleOrNull()
            ?.let { toDTO(it) }
    }

    fun create(request: CreateOrganizationRequest): OrganizationDTO = transaction {
        val id = Organizations.insertAndGetId {
            it[name] = request.name
            it[orgNumber] = request.orgNumber
            it[mvaRegistered] = request.mvaRegistered
            it[active] = true
        }.value

        getById(id)!!
    }

    fun update(id: Int, request: UpdateOrganizationRequest): OrganizationDTO? = transaction {
        Organizations.selectAll()
            .where { Organizations.id eq id }
            .singleOrNull() ?: return@transaction null

        Organizations.update({ Organizations.id eq id }) {
            request.name?.let { n -> it[name] = n }
            request.orgNumber?.let { o -> it[orgNumber] = o }
            request.mvaRegistered?.let { m -> it[mvaRegistered] = m }
            request.active?.let { a -> it[active] = a }
        }

        getById(id)
    }

    private fun toDTO(row: ResultRow): OrganizationDTO {
        return OrganizationDTO(
            id = row[Organizations.id].value,
            name = row[Organizations.name],
            orgNumber = row[Organizations.orgNumber],
            mvaRegistered = row[Organizations.mvaRegistered],
            active = row[Organizations.active],
            createdAt = row[Organizations.createdAt].format(dateFormatter)
        )
    }
}
