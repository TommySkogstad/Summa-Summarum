package no.summa.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import java.security.SecureRandom
import java.util.*

private val CSRF_TOKEN_LENGTH = 32

fun generateCsrfToken(): String {
    val bytes = ByteArray(CSRF_TOKEN_LENGTH)
    SecureRandom().nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

private fun isExemptFromCsrf(path: String, method: HttpMethod): Boolean {
    if (method == HttpMethod.Get || method == HttpMethod.Head || method == HttpMethod.Options) {
        return true
    }

    val exemptPaths = listOf(
        "/api/auth/request-code",
        "/api/auth/verify-code",
        "/api/auth/logout",
        "/api/health"
    )

    return exemptPaths.any { path == it }
}

val CsrfProtection = createApplicationPlugin(name = "CsrfProtection") {
    val isDevMode = System.getenv("DEV_MODE")?.toBoolean() ?: false

    onCall { call ->
        val path = call.request.path()
        val method = call.request.httpMethod

        if (isExemptFromCsrf(path, method)) {
            return@onCall
        }

        val headerToken = call.request.header("X-CSRF-Token")
        val cookieToken = call.request.cookies["csrf_token"]

        if (headerToken.isNullOrBlank() || cookieToken.isNullOrBlank()) {
            if (!isDevMode) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Manglende CSRF-token"))
                return@onCall
            }
            call.application.log.warn("CSRF-token mangler for ${method.value} $path (dev-modus)")
            return@onCall
        }

        if (headerToken != cookieToken) {
            if (!isDevMode) {
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Ugyldig CSRF-token"))
                return@onCall
            }
            call.application.log.warn("CSRF-token mismatch for ${method.value} $path (dev-modus)")
        }
    }
}

fun ApplicationCall.setCsrfCookie(token: String) {
    val isDevMode = System.getenv("DEV_MODE")?.toBoolean() ?: false
    val secureCookies = System.getenv("SECURE_COOKIES")?.toBoolean() ?: !isDevMode

    response.cookies.append(
        Cookie(
            name = "csrf_token",
            value = token,
            httpOnly = false,
            secure = secureCookies,
            path = "/",
            maxAge = 24 * 60 * 60,
            extensions = mapOf("SameSite" to if (isDevMode) "Lax" else "Strict")
        )
    )
}
