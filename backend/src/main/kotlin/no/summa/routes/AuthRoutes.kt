package no.summa.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.auth.*
import no.summa.models.*
import no.summa.plugins.JwtConfig
import no.summa.plugins.generateCsrfToken
import no.summa.plugins.setCsrfCookie
import no.summa.plugins.userPrincipal
import no.summa.services.AuthService
import no.summa.services.RateLimiter

fun Route.authRoutes(authService: AuthService) {
    route("/auth") {
        post("/request-code") {
            val request = call.receive<RequestCodeRequest>()

            if (request.email.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("E-post er pakrevd"))
                return@post
            }

            val ip = call.request.local.remoteHost
            if (RateLimiter.isBlocked(ip)) {
                call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("For mange forsok. Prov igjen senere."))
                return@post
            }

            if (!RateLimiter.canRequestOtp(request.email)) {
                call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("For mange kodeforesporsler. Prov igjen senere."))
                return@post
            }

            val cooldown = RateLimiter.getOtpCooldownSeconds(request.email)
            if (cooldown > 0) {
                call.respond(RequestCodeResponse(
                    message = "Vent litt for du ber om ny kode",
                    cooldownSeconds = cooldown.toInt()
                ))
                return@post
            }

            val (success, devOtp) = authService.requestOtp(request.email)

            RateLimiter.recordOtpRequest(request.email)
            RateLimiter.recordOtpRequestTime(request.email)

            if (!success) {
                call.respond(RequestCodeResponse("Hvis e-postadressen er registrert, vil du motta en kode"))
                return@post
            }

            call.respond(RequestCodeResponse(
                message = "Kode sendt til ${request.email}",
                devOtp = devOtp,
                cooldownSeconds = 60
            ))
        }

        post("/verify-code") {
            val request = call.receive<VerifyCodeRequest>()

            if (request.email.isBlank() || request.code.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("E-post og kode er pakrevd"))
                return@post
            }

            val ip = call.request.local.remoteHost
            if (RateLimiter.isBlocked(ip)) {
                call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("For mange forsok. Prov igjen senere."))
                return@post
            }

            if (!RateLimiter.canVerifyOtp(request.email)) {
                call.respond(HttpStatusCode.TooManyRequests, ErrorResponse("For mange verifiseringsforsok."))
                return@post
            }

            RateLimiter.recordOtpVerification(request.email)

            val user = authService.verifyOtp(request.email, request.code)

            if (user == null) {
                RateLimiter.recordFailedAttempt(ip)
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Ugyldig eller utlopt kode"))
                return@post
            }

            RateLimiter.recordSuccessfulLogin(ip)
            RateLimiter.recordSuccessfulOtpLogin(request.email)

            val token = JwtConfig.generateToken(user.id, user.email, user.role.name)

            call.response.cookies.append(
                Cookie(
                    name = "auth_token",
                    value = token,
                    httpOnly = true,
                    secure = System.getenv("SECURE_COOKIES")?.toBoolean() ?: false,
                    path = "/",
                    maxAge = 24 * 60 * 60,
                    extensions = mapOf("SameSite" to "Lax")
                )
            )

            val csrfToken = generateCsrfToken()
            call.setCsrfCookie(csrfToken)

            call.respond(VerifyCodeResponse(
                success = true,
                role = user.role.name
            ))
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
                    path = "/",
                    maxAge = 0
                )
            )
            call.respond(MessageResponse("Logget ut"))
        }

        authenticate("auth-jwt") {
            get("/me") {
                val principal = call.userPrincipal()
                if (principal == null) {
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Ikke autentisert"))
                    return@get
                }

                val user = authService.getUserById(principal.userId)
                if (user == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Bruker ikke funnet"))
                    return@get
                }

                call.respond(CurrentUserResponse(
                    id = user.id,
                    email = user.email,
                    name = user.name,
                    role = user.role.name
                ))
            }
        }
    }
}
