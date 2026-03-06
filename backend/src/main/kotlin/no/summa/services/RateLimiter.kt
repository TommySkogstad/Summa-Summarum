package no.summa.services

import java.util.concurrent.ConcurrentHashMap

class RateLimiter(
    private val maxAttempts: Int = 5,
    private val windowMs: Long = 300_000 // 5 minutter
) {
    private data class AttemptRecord(val count: Int, val windowStart: Long)
    private val attempts = ConcurrentHashMap<String, AttemptRecord>()

    fun isAllowed(key: String): Boolean {
        val now = System.currentTimeMillis()
        val record = attempts[key]

        if (record == null || now - record.windowStart > windowMs) {
            attempts[key] = AttemptRecord(1, now)
            return true
        }

        if (record.count >= maxAttempts) {
            return false
        }

        attempts[key] = record.copy(count = record.count + 1)
        return true
    }

    fun reset(key: String) {
        attempts.remove(key)
    }
}
