package no.summa.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import no.summa.models.ErrorResponse
import no.summa.services.AuthService
import no.grunnmur.RateLimiter
import java.util.UUID

@Serializable
data class RequestCodeRequest(val email: String)

@Serializable
data class VerifyCodeRequest(val email: String, val code: String)

@Serializable
data class AuthResponse(val message: String, val csrfToken: String? = null)

@Serializable
data class UserResponse(
    val id: Int,
    val email: String,
    val name: String,
    val role: String,
    val activeOrgId: Int? = null,
    val organizations: List<OrgResponse> = emptyList(),
    val csrfToken: String? = null
)

@Serializable
data class OrgResponse(
    val id: Int,
    val name: String,
    val mvaRegistered: Boolean
)

private val isDev = System.getenv("DEV_MODE")?.lowercase() == "true"

private fun setCsrfCookie(call: ApplicationCall): String {
    val csrfToken = UUID.randomUUID().toString()
    call.response.cookies.append(
        Cookie(
            name = "csrf_token",
            value = csrfToken,
            httpOnly = false,
            secure = !isDev,
            path = "/",
            maxAge = 86400,
            extensions = mapOf("SameSite" to if (isDev) "Lax" else "Strict")
        )
    )
    return csrfToken
}

fun Route.authRoutes(authService: AuthService, rateLimiter: RateLimiter) {
    route("/auth") {
        post("/request-code") {
            val request = call.receive<RequestCodeRequest>()
            val ip = call.request.header("X-Real-IP") ?: call.request.local.remoteAddress

            if (!rateLimiter.isAllowed(ip)) {
                call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("For mange forsok. Prov igjen senere."))
                return@post
            }

            // Returnerer alltid suksess for å ikke lekke om e-posten finnes
            authService.requestOtp(request.email)
            call.respond(AuthResponse(message = "Engangskode sendt til ${request.email}"))
        }

        post("/verify-code") {
            val request = call.receive<VerifyCodeRequest>()
            val ip = call.request.header("X-Real-IP") ?: call.request.local.remoteAddress

            if (!rateLimiter.isAllowed("verify:$ip")) {
                call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("For mange forsok. Prov igjen senere."))
                return@post
            }

            val result = authService.verifyOtp(request.email, request.code)
            if (result == null) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Ugyldig eller utlopt engangskode"))
                return@post
            }

            val (_, cookie) = result
            call.response.cookies.append(cookie)

            val csrfToken = setCsrfCookie(call)

            rateLimiter.reset(ip)
            rateLimiter.reset("verify:$ip")

            call.respond(AuthResponse(message = "Innlogget", csrfToken = csrfToken))
        }

        post("/logout") {
            call.response.cookies.append(
                Cookie(
                    name = "auth_token",
                    value = "",
                    httpOnly = true,
                    path = "/",
                    maxAge = 0
                )
            )
            call.response.cookies.append(
                Cookie(
                    name = "csrf_token",
                    value = "",
                    httpOnly = false,
                    path = "/",
                    maxAge = 0
                )
            )
            call.respond(AuthResponse(message = "Logget ut"))
        }

        authenticate("auth-jwt") {
            get("/me") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.getClaim("userId", Int::class)!!
                val orgId = principal.getClaim("orgId", Int::class)

                val userInfo = authService.getUserInfo(userId)
                if (userInfo == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Bruker ikke funnet"))
                    return@get
                }

                val csrfToken = setCsrfCookie(call)

                call.respond(UserResponse(
                    id = userInfo.id,
                    email = userInfo.email,
                    name = userInfo.name,
                    role = userInfo.role,
                    activeOrgId = orgId,
                    organizations = userInfo.organizations.map { org ->
                        OrgResponse(id = org.id, name = org.name, mvaRegistered = org.mvaRegistered)
                    },
                    csrfToken = csrfToken
                ))
            }

            post("/switch-org/{orgId}") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.getClaim("userId", Int::class)!!
                val role = principal.getClaim("role", String::class)!!
                val newOrgId = call.parameters["orgId"]?.toIntOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("Ugyldig org-ID"))

                val result = authService.switchOrganization(userId, role, newOrgId)
                if (result == null) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("Ingen tilgang til denne organisasjonen"))
                    return@post
                }

                val (_, cookie) = result
                call.response.cookies.append(cookie)

                val csrfToken = setCsrfCookie(call)
                call.respond(AuthResponse(message = "Byttet organisasjon", csrfToken = csrfToken))
            }
        }
    }
}
