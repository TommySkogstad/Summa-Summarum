package no.summa.services

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import no.summa.database.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.time.ZoneOffset

class AuthService(
    private val jwtSecret: String,
    private val jwtIssuer: String = "summa-summarum",
    private val jwtAudience: String = "summa-summarum"
) {
    private val isDev = System.getenv("DEV_MODE")?.lowercase() == "true"
    private val algorithm = Algorithm.HMAC256(jwtSecret)

    fun requestOtp(email: String): Boolean = transaction {
        val user = Users.selectAll()
            .where { Users.email eq email.lowercase().trim() }
            .singleOrNull() ?: return@transaction false

        if (!user[Users.active]) return@transaction false

        val otp = if (isDev) "123456" else (100000..999999).random().toString()
        val expiresAt = TimeUtils.nowOslo().plusMinutes(5)

        Users.update({ Users.id eq user[Users.id] }) {
            it[otpCode] = otp
            it[otpExpiresAt] = expiresAt
        }

        // I produksjon: send OTP via e-post her
        if (isDev) {
            org.slf4j.LoggerFactory.getLogger(javaClass).info("DEV OTP for $email: $otp")
        }

        true
    }

    fun verifyOtp(email: String, code: String, orgId: Int? = null): Pair<String, Cookie>? = transaction {
        val user = Users.selectAll()
            .where { Users.email eq email.lowercase().trim() }
            .singleOrNull() ?: return@transaction null

        if (!user[Users.active]) return@transaction null

        val storedOtp = user[Users.otpCode] ?: return@transaction null
        val expiresAt = user[Users.otpExpiresAt] ?: return@transaction null

        // Sjekk OTP - i dev-modus fungerer "123456" alltid
        val otpValid = (isDev && code == "123456") || (storedOtp == code && TimeUtils.nowOslo().isBefore(expiresAt))
        if (!otpValid) return@transaction null

        // Finn brukerens organisasjoner
        val userOrgs = UserOrganizations.selectAll()
            .where { UserOrganizations.userId eq user[Users.id] }
            .map { it[UserOrganizations.organizationId].value }

        val activeOrgId = when {
            orgId != null && orgId in userOrgs -> orgId
            userOrgs.isNotEmpty() -> userOrgs.first()
            user[Users.role] == UserRole.SUPERADMIN -> {
                Organizations.selectAll().firstOrNull()?.get(Organizations.id)?.value
            }
            else -> null
        }

        // Nullstill OTP og oppdater siste innlogging
        Users.update({ Users.id eq user[Users.id] }) {
            it[otpCode] = null
            it[otpExpiresAt] = null
            it[lastLoginAt] = TimeUtils.nowOslo()
        }

        val token = generateToken(user[Users.id].value, user[Users.email], user[Users.role].name, activeOrgId)
        val cookie = Cookie(
            name = "auth_token",
            value = token,
            httpOnly = true,
            secure = !isDev,
            path = "/",
            maxAge = 86400, // 24 timer
            extensions = mapOf("SameSite" to if (isDev) "Lax" else "Strict")
        )

        Pair(token, cookie)
    }

    fun switchOrganization(userId: Int, role: String, newOrgId: Int): Pair<String, Cookie>? = transaction {
        val user = Users.selectAll()
            .where { Users.id eq userId }
            .singleOrNull() ?: return@transaction null

        // SUPERADMIN kan bytte til alle orgs, andre må være medlem
        val hasAccess = if (role == "SUPERADMIN") {
            Organizations.selectAll().where { Organizations.id eq newOrgId }.count() > 0
        } else {
            UserOrganizations.selectAll()
                .where { (UserOrganizations.userId eq userId) and (UserOrganizations.organizationId eq newOrgId) }
                .count() > 0
        }

        if (!hasAccess) return@transaction null

        val token = generateToken(userId, user[Users.email], user[Users.role].name, newOrgId)
        val cookie = Cookie(
            name = "auth_token",
            value = token,
            httpOnly = true,
            secure = !isDev,
            path = "/",
            maxAge = 86400,
            extensions = mapOf("SameSite" to if (isDev) "Lax" else "Strict")
        )

        Pair(token, cookie)
    }

    fun getUserInfo(userId: Int): UserInfo? = transaction {
        val user = Users.selectAll()
            .where { Users.id eq userId }
            .singleOrNull() ?: return@transaction null

        val orgs = UserOrganizations
            .innerJoin(Organizations)
            .selectAll()
            .where { UserOrganizations.userId eq userId }
            .map { row ->
                UserOrgInfo(
                    id = row[Organizations.id].value,
                    name = row[Organizations.name],
                    mvaRegistered = row[Organizations.mvaRegistered]
                )
            }

        // SUPERADMIN ser alle organisasjoner
        val allOrgs = if (user[Users.role] == UserRole.SUPERADMIN) {
            Organizations.selectAll().where { Organizations.active eq true }.map { row ->
                UserOrgInfo(
                    id = row[Organizations.id].value,
                    name = row[Organizations.name],
                    mvaRegistered = row[Organizations.mvaRegistered]
                )
            }
        } else orgs

        UserInfo(
            id = user[Users.id].value,
            email = user[Users.email],
            name = user[Users.name],
            role = user[Users.role].name,
            organizations = allOrgs
        )
    }

    fun generateCsrfToken(userId: Int): String {
        return JWT.create()
            .withClaim("userId", userId)
            .withClaim("type", "csrf")
            .withExpiresAt(java.util.Date(System.currentTimeMillis() + 86400000))
            .sign(algorithm)
    }

    fun verifyCsrfToken(token: String, userId: Int): Boolean {
        return try {
            val decoded = JWT.require(algorithm).build().verify(token)
            decoded.getClaim("userId").asInt() == userId && decoded.getClaim("type").asString() == "csrf"
        } catch (e: Exception) {
            false
        }
    }

    private fun generateToken(userId: Int, email: String, role: String, orgId: Int?): String {
        return JWT.create()
            .withIssuer(jwtIssuer)
            .withAudience(jwtAudience)
            .withClaim("userId", userId)
            .withClaim("email", email)
            .withClaim("role", role)
            .withClaim("orgId", orgId)
            .withExpiresAt(java.util.Date(System.currentTimeMillis() + 86400000)) // 24 timer
            .sign(algorithm)
    }

    fun getAlgorithm() = algorithm
    fun getIssuer() = jwtIssuer
    fun getAudience() = jwtAudience
}

data class UserInfo(
    val id: Int,
    val email: String,
    val name: String,
    val role: String,
    val organizations: List<UserOrgInfo>
)

data class UserOrgInfo(
    val id: Int,
    val name: String,
    val mvaRegistered: Boolean
)
