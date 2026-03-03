package no.summa.services

import no.summa.database.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.security.MessageDigest
import java.security.SecureRandom

class AuthService(private val emailService: EmailService) {
    private val devMode = System.getenv("DEV_MODE")?.toBoolean() ?: true
    private val devOtp = "123456"
    private val secureRandom = SecureRandom()

    data class UserInfo(
        val id: Int,
        val email: String,
        val name: String,
        val role: UserRole
    )

    fun requestOtp(email: String): Pair<Boolean, String?> {
        val user = transaction {
            Users.selectAll().where { Users.email eq email.lowercase() }.singleOrNull()
        }

        if (user == null) {
            return Pair(false, null)
        }

        val realOtp = generateOtp()
        val otpHash = hashOtp(realOtp)
        val expiry = TimeUtils.nowOslo().plusMinutes(10)

        transaction {
            Users.update({ Users.email eq email.lowercase() }) {
                it[Users.otpHash] = otpHash
                it[otpExpiry] = expiry
            }
        }

        emailService.sendOtpEmail(email, realOtp)

        return Pair(true, if (devMode) devOtp else null)
    }

    fun verifyOtp(email: String, code: String): UserInfo? {
        if (devMode && code == devOtp) {
            return getUserByEmail(email)
        }

        val user = transaction {
            Users.selectAll().where { Users.email eq email.lowercase() }.singleOrNull()
        } ?: return null

        val storedHash = user[Users.otpHash] ?: return null
        val expiry = user[Users.otpExpiry] ?: return null

        if (TimeUtils.nowOslo().isAfter(expiry)) {
            return null
        }

        if (hashOtp(code) != storedHash) {
            return null
        }

        transaction {
            Users.update({ Users.email eq email.lowercase() }) {
                it[otpHash] = null
                it[otpExpiry] = null
            }
        }

        return getUserByEmail(email)
    }

    fun getUserById(userId: Int): UserInfo? = transaction {
        val user = Users.selectAll().where { Users.id eq userId }.singleOrNull() ?: return@transaction null

        UserInfo(
            id = user[Users.id].value,
            email = user[Users.email],
            name = user[Users.name],
            role = user[Users.role]
        )
    }

    fun getUserByEmail(email: String): UserInfo? = transaction {
        val user = Users.selectAll().where { Users.email eq email.lowercase() }.singleOrNull() ?: return@transaction null

        UserInfo(
            id = user[Users.id].value,
            email = user[Users.email],
            name = user[Users.name],
            role = user[Users.role]
        )
    }

    private fun generateOtp(): String {
        return (100000 + secureRandom.nextInt(900000)).toString()
    }

    private fun hashOtp(otp: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(otp.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
