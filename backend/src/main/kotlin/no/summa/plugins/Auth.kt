package no.summa.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import no.summa.database.UserRole
import no.summa.models.ErrorResponse
import java.util.*

data class UserPrincipal(
    val userId: Int,
    val email: String,
    val name: String,
    val role: UserRole
) : Principal

object JwtConfig {
    private val secret = System.getenv("JWT_SECRET") ?: "dev-secret-must-be-at-least-32-characters-long"
    private val issuer = System.getenv("JWT_ISSUER") ?: "summa"
    private val audience = System.getenv("JWT_AUDIENCE") ?: "summa-users"
    private val validityInMs = 24 * 60 * 60 * 1000L // 24 timer

    val algorithm: Algorithm = Algorithm.HMAC256(secret)

    fun generateToken(userId: Int, email: String, role: String): String {
        return JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("userId", userId)
            .withClaim("email", email)
            .withClaim("role", role)
            .withExpiresAt(Date(System.currentTimeMillis() + validityInMs))
            .sign(algorithm)
    }

    fun configureJwt(): JWTAuthenticationProvider.Config.() -> Unit = {
        verifier(
            JWT.require(algorithm)
                .withIssuer(issuer)
                .withAudience(audience)
                .build()
        )

        validate { credential ->
            val userId = credential.payload.getClaim("userId").asInt()
            val email = credential.payload.getClaim("email").asString()
            val roleStr = credential.payload.getClaim("role").asString()

            if (userId != null && email != null && roleStr != null) {
                val role = try {
                    UserRole.valueOf(roleStr)
                } catch (e: IllegalArgumentException) {
                    return@validate null
                }

                UserPrincipal(
                    userId = userId,
                    email = email,
                    name = "",
                    role = role
                )
            } else {
                null
            }
        }

        challenge { _, _ ->
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Ugyldig eller utlopt token"))
        }
    }
}

fun Application.configureAuth() {
    install(Authentication) {
        jwt("auth-jwt") {
            authHeader { call ->
                val token = call.request.cookies["auth_token"]
                if (token != null) {
                    HttpAuthHeader.Single("Bearer", token)
                } else {
                    null
                }
            }
            JwtConfig.configureJwt()()
        }
    }
}

fun ApplicationCall.userPrincipal(): UserPrincipal? = principal<UserPrincipal>()

suspend fun ApplicationCall.requireAdmin(): UserPrincipal? {
    val principal = userPrincipal()
    if (principal == null || principal.role != UserRole.ADMIN) {
        respond(HttpStatusCode.Forbidden, ErrorResponse("Krever admin-tilgang"))
        return null
    }
    return principal
}
