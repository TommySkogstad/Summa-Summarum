package no.summa.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import no.summa.models.ErrorResponse
import no.summa.services.AuthService

/** Verifiser CSRF-token på muterende operasjoner */
suspend fun ApplicationCall.verifyCsrf(authService: AuthService): Boolean {
    val method = request.httpMethod
    if (method == HttpMethod.Get || method == HttpMethod.Head || method == HttpMethod.Options) {
        return true
    }

    val csrfToken = request.header("X-CSRF-Token")
    if (csrfToken == null) {
        respond(HttpStatusCode.Forbidden, ErrorResponse("Manglende CSRF-token"))
        return false
    }

    val userId = getUserId()
    if (userId == null || !authService.verifyCsrfToken(csrfToken, userId)) {
        respond(HttpStatusCode.Forbidden, ErrorResponse("Ugyldig CSRF-token"))
        return false
    }

    return true
}
