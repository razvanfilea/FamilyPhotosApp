package net.theluckycoder.familyphotos.utils

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import net.theluckycoder.familyphotos.model.Photo
import java.text.Normalizer
import java.time.format.TextStyle
import java.util.Locale

private val normalizationRegex = "\\p{InCombiningDiacriticalMarks}+".toRegex()

fun String.normalize(): String {
    val normalized = Normalizer.normalize(this, Normalizer.Form.NFD)
    return normalized.replace(normalizationRegex, "")
}

fun String.normalizeLowerCase(): String =
    this.normalize().lowercase(Locale.getDefault())

private val currentDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

fun computeSeparatorText(before: Photo?, after: Photo): String? {
    val beforeDate = before?.let {
        val instant = Instant.fromEpochSeconds(it.timeCreated)
        instant.toLocalDateTime(TimeZone.currentSystemDefault())
    }
    val afterDate =
        Instant.fromEpochSeconds(after.timeCreated)
            .toLocalDateTime(TimeZone.currentSystemDefault())

    return if (beforeDate == null || beforeDate.monthNumber != afterDate.monthNumber || beforeDate.year != afterDate.year
    ) {
        buildDateString(afterDate)
    } else null
}

private fun buildDateString(afterDate: LocalDateTime) = buildString {
    append(
        afterDate.month.getDisplayName(
            TextStyle.FULL,
            Locale.forLanguageTag("ro-RO")
        )
    )

    val year = afterDate.year
    if (year != currentDate.year)
        append(' ').append(year)
}.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
