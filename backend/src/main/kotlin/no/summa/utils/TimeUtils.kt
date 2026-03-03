package no.summa.utils

import java.time.LocalDateTime
import java.time.ZoneId

object TimeUtils {
    private val osloZone = ZoneId.of("Europe/Oslo")
    fun nowOslo(): LocalDateTime = LocalDateTime.now(osloZone)
}
