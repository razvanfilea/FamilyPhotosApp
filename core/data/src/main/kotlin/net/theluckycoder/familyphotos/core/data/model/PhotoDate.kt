package net.theluckycoder.familyphotos.core.data.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

object PhotoDate {

    fun toLocalDateTime(epochSeconds: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), zone)

    fun toEpochSeconds(
        date: LocalDate,
        hour: Int,
        minute: Int,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long = date.atTime(hour, minute).atZone(zone).toEpochSecond()
}

fun NetworkPhoto.canChangeDate(currentUserId: String?): Boolean =
    trashedOn == null && (userId == null || userId == currentUserId)
