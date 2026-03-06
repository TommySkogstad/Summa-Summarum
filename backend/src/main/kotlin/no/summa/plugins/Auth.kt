package no.summa.plugins

import com.auth0.jwt.JWT
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import no.summa.models.ErrorResponse
import no.summa.services.AuthService

fun Application.configureAuth(authService: AuthService) {
    install(Authentication) {
        jwt("auth-jwt") {
            verifier(
                JWT.require(authService.getAlgorithm())
                    .withIssuer(authService.getIssuer())
                    .withAudience(authService.getAudience())
                    .build()
            )
            validate { credential ->
                val userId = credential.payload.getClaim("userId")?.asInt()
                if (userId != null) {
                    JWTPrincipal(credential.payload)
                } else null
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Ikke autentisert"))
            }
            // Les token fra HttpOnly cookie
            authHeader { call ->
                val token = call.request.cookies["auth_token"]
                if (token != null) {
                    io.ktor.http.auth.HttpAuthHeader.Single("Bearer", token)
                } else null
            }
        }
    }
}

/** Hent orgId fra JWT-token (erstatter X-Organization-Id header) */
suspend fun ApplicationCall.requireOrgId(): Int? {
    val principal = principal<JWTPrincipal>()
    val orgId = principal?.getClaim("orgId", Int::class)
    if (orgId == null) {
        respond(HttpStatusCode.BadRequest, ErrorResponse("Ingen aktiv organisasjon. Bytt organisasjon forst."))
        return null
    }
    return orgId
}

/** Hent userId fra JWT-token */
fun ApplicationCall.getUserId(): Int? {
    return principal<JWTPrincipal>()?.getClaim("userId", Int::class)
}

/** Hent rolle fra JWT-token */
fun ApplicationCall.getUserRole(): String? {
    return principal<JWTPrincipal>()?.getClaim("role", String::class)
}

/** Krev ADMIN eller SUPERADMIN */
suspend fun ApplicationCall.requireAdmin(): Boolean {
    val role = getUserRole()
    if (role != "ADMIN" && role != "SUPERADMIN") {
        respond(HttpStatusCode.Forbidden, ErrorResponse("Krever admin-tilgang"))
        return false
    }
    return true
}

/** Krev SUPERADMIN */
suspend fun ApplicationCall.requireSuperAdmin(): Boolean {
    val role = getUserRole()
    if (role != "SUPERADMIN") {
        respond(HttpStatusCode.Forbidden, ErrorResponse("Krever superadmin-tilgang"))
        return false
    }
    return true
}
