package no.summa.services

import java.util.concurrent.ConcurrentHashMap
import java.time.Instant

object RateLimiter {

    private data class AttemptInfo(
        val attempts: Int,
        val firstAttemptTime: Instant,
        val blockedUntil: Instant? = null
    )

    private val attemptsByIp = ConcurrentHashMap<String, AttemptInfo>()
    private val otpRequestsByEmail = ConcurrentHashMap<String, AttemptInfo>()
    private val otpVerificationsByEmail = ConcurrentHashMap<String, AttemptInfo>()
    private val otpLastRequestByEmail = ConcurrentHashMap<String, Instant>()
    private val uploadByIp = ConcurrentHashMap<String, AttemptInfo>()

    private const val MAX_ATTEMPTS = 5
    private const val WINDOW_MINUTES = 15L
    private const val BLOCK_DURATION_MINUTES = 15L

    private const val OTP_REQUEST_MAX = 5
    private const val OTP_REQUEST_WINDOW_MINUTES = 60L
    private const val OTP_VERIFY_MAX = 10
    private const val OTP_VERIFY_WINDOW_MINUTES = 60L
    private const val OTP_COOLDOWN_SECONDS = 60L

    private const val UPLOAD_MAX = 10
    private const val UPLOAD_WINDOW_MINUTES = 1L

    fun isBlocked(ip: String): Boolean {
        cleanupExpired()
        val info = attemptsByIp[ip] ?: return false

        info.blockedUntil?.let { blockedUntil ->
            if (Instant.now().isBefore(blockedUntil)) {
                return true
            }
            attemptsByIp.remove(ip)
            return false
        }

        return false
    }

    fun recordFailedAttempt(ip: String): Boolean {
        cleanupExpired()
        val now = Instant.now()
        val currentInfo = attemptsByIp[ip]

        val newInfo = if (currentInfo == null) {
            AttemptInfo(attempts = 1, firstAttemptTime = now)
        } else {
            val windowExpired = currentInfo.firstAttemptTime
                .plusSeconds(WINDOW_MINUTES * 60)
                .isBefore(now)

            if (windowExpired) {
                AttemptInfo(attempts = 1, firstAttemptTime = now)
            } else {
                val newAttempts = currentInfo.attempts + 1
                if (newAttempts >= MAX_ATTEMPTS) {
                    AttemptInfo(
                        attempts = newAttempts,
                        firstAttemptTime = currentInfo.firstAttemptTime,
                        blockedUntil = now.plusSeconds(BLOCK_DURATION_MINUTES * 60)
                    )
                } else {
                    currentInfo.copy(attempts = newAttempts)
                }
            }
        }

        attemptsByIp[ip] = newInfo
        return newInfo.blockedUntil != null
    }

    fun recordSuccessfulLogin(ip: String) {
        attemptsByIp.remove(ip)
    }

    fun getOtpCooldownSeconds(email: String): Long {
        val normalizedEmail = email.lowercase()
        val lastRequest = otpLastRequestByEmail[normalizedEmail] ?: return 0

        val cooldownEnds = lastRequest.plusSeconds(OTP_COOLDOWN_SECONDS)
        val now = Instant.now()

        return if (now.isBefore(cooldownEnds)) {
            cooldownEnds.epochSecond - now.epochSecond
        } else {
            otpLastRequestByEmail.remove(normalizedEmail)
            0
        }
    }

    fun recordOtpRequestTime(email: String) {
        otpLastRequestByEmail[email.lowercase()] = Instant.now()
    }

    fun canRequestOtp(email: String): Boolean {
        val normalizedEmail = email.lowercase()
        val info = otpRequestsByEmail[normalizedEmail] ?: return true

        val windowExpired = info.firstAttemptTime
            .plusSeconds(OTP_REQUEST_WINDOW_MINUTES * 60)
            .isBefore(Instant.now())

        if (windowExpired) {
            otpRequestsByEmail.remove(normalizedEmail)
            return true
        }

        return info.attempts < OTP_REQUEST_MAX
    }

    fun recordOtpRequest(email: String) {
        val normalizedEmail = email.lowercase()
        val now = Instant.now()
        val currentInfo = otpRequestsByEmail[normalizedEmail]

        val newInfo = if (currentInfo == null) {
            AttemptInfo(attempts = 1, firstAttemptTime = now)
        } else {
            val windowExpired = currentInfo.firstAttemptTime
                .plusSeconds(OTP_REQUEST_WINDOW_MINUTES * 60)
                .isBefore(now)

            if (windowExpired) {
                AttemptInfo(attempts = 1, firstAttemptTime = now)
            } else {
                currentInfo.copy(attempts = currentInfo.attempts + 1)
            }
        }

        otpRequestsByEmail[normalizedEmail] = newInfo
    }

    fun canVerifyOtp(email: String): Boolean {
        val normalizedEmail = email.lowercase()
        val info = otpVerificationsByEmail[normalizedEmail] ?: return true

        val windowExpired = info.firstAttemptTime
            .plusSeconds(OTP_VERIFY_WINDOW_MINUTES * 60)
            .isBefore(Instant.now())

        if (windowExpired) {
            otpVerificationsByEmail.remove(normalizedEmail)
            return true
        }

        return info.attempts < OTP_VERIFY_MAX
    }

    fun recordOtpVerification(email: String) {
        val normalizedEmail = email.lowercase()
        val now = Instant.now()
        val currentInfo = otpVerificationsByEmail[normalizedEmail]

        val newInfo = if (currentInfo == null) {
            AttemptInfo(attempts = 1, firstAttemptTime = now)
        } else {
            val windowExpired = currentInfo.firstAttemptTime
                .plusSeconds(OTP_VERIFY_WINDOW_MINUTES * 60)
                .isBefore(now)

            if (windowExpired) {
                AttemptInfo(attempts = 1, firstAttemptTime = now)
            } else {
                currentInfo.copy(attempts = currentInfo.attempts + 1)
            }
        }

        otpVerificationsByEmail[normalizedEmail] = newInfo
    }

    fun recordSuccessfulOtpLogin(email: String) {
        val normalizedEmail = email.lowercase()
        otpRequestsByEmail.remove(normalizedEmail)
        otpVerificationsByEmail.remove(normalizedEmail)
        otpLastRequestByEmail.remove(normalizedEmail)
    }

    fun canUpload(ip: String): Boolean {
        val info = uploadByIp[ip] ?: return true

        val windowExpired = info.firstAttemptTime
            .plusSeconds(UPLOAD_WINDOW_MINUTES * 60)
            .isBefore(Instant.now())

        if (windowExpired) {
            uploadByIp.remove(ip)
            return true
        }

        return info.attempts < UPLOAD_MAX
    }

    fun recordUpload(ip: String) {
        val now = Instant.now()
        val currentInfo = uploadByIp[ip]

        val newInfo = if (currentInfo == null) {
            AttemptInfo(attempts = 1, firstAttemptTime = now)
        } else {
            val windowExpired = currentInfo.firstAttemptTime
                .plusSeconds(UPLOAD_WINDOW_MINUTES * 60)
                .isBefore(now)

            if (windowExpired) {
                AttemptInfo(attempts = 1, firstAttemptTime = now)
            } else {
                currentInfo.copy(attempts = currentInfo.attempts + 1)
            }
        }

        uploadByIp[ip] = newInfo
    }

    private fun cleanupExpired() {
        val now = Instant.now()
        val cutoff = now.minusSeconds((WINDOW_MINUTES + BLOCK_DURATION_MINUTES) * 60)

        attemptsByIp.entries.removeIf { (_, info) ->
            val expired = info.firstAttemptTime.isBefore(cutoff)
            val unblocked = info.blockedUntil?.isBefore(now) ?: true
            expired && unblocked
        }
    }
}
