package no.summa.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import no.grunnmur.grunnmurExceptionHandlers
import no.summa.models.ErrorResponse

fun Application.configureSecurity() {
    install(StatusPages) {
        grunnmurExceptionHandlers()

        status(HttpStatusCode.NotFound) { call, status ->
            call.respond(status, ErrorResponse("Ressurs ikke funnet"))
        }

        status(HttpStatusCode.Unauthorized) { call, status ->
            call.respond(status, ErrorResponse("Ikke autentisert"))
        }

        status(HttpStatusCode.Forbidden) { call, status ->
            call.respond(status, ErrorResponse("Ingen tilgang"))
        }
    }
}
