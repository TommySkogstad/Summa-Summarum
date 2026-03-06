package no.summa.services

import no.summa.database.AuditLogs
import no.summa.database.TimeUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction

class AuditLogService {

    fun log(
        userId: Int?,
        action: String,
        entityType: String,
        entityId: Int?,
        details: String? = null,
        ipAddress: String = "system"
    ) {
        transaction {
            AuditLogs.insert {
                it[AuditLogs.userId] = userId
                it[AuditLogs.action] = action
                it[AuditLogs.entityType] = entityType
                it[AuditLogs.entityId] = entityId
                it[AuditLogs.details] = details
                it[AuditLogs.ipAddress] = ipAddress
                it[createdAt] = TimeUtils.nowOslo()
            }
        }
    }
}
