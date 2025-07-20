package net.theluckycoder.familyphotos.utils

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toJavaMonth
import kotlinx.datetime.toLocalDateTime
import net.theluckycoder.familyphotos.data.model.db.Photo
import java.text.Normalizer
import java.time.format.TextStyle
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private val normalizationRegex = "\\p{InCombiningDiacriticalMarks}+".toRegex()

fun String.normalize(): String {
    val normalized = Normalizer.normalize(this, Normalizer.Form.NFD)
    return normalized.replace(normalizationRegex, "")
}

fun String.normalizeLowerCase(): String =
    this.normalize().lowercase(Locale.getDefault())

@OptIn(ExperimentalTime::class)
private val currentDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

@OptIn(ExperimentalTime::class)
fun computeSeparatorText(before: Photo?, after: Photo): String? {
    val beforeDate = before?.let {
        val instant = Instant.fromEpochSeconds(it.timeCreated)
        instant.toLocalDateTime(TimeZone.currentSystemDefault())
    }
    val afterDate =
        Instant.fromEpochSeconds(after.timeCreated)
            .toLocalDateTime(TimeZone.currentSystemDefault())

    return if (beforeDate == null || beforeDate.month.number != afterDate.month.number || beforeDate.year != afterDate.year
    ) {
        buildDateString(afterDate)
    } else null
}

private fun buildDateString(afterDate: LocalDateTime) = buildString {
    append(
        afterDate.month.toJavaMonth().getDisplayName(
            TextStyle.FULL,
            Locale.forLanguageTag("ro-RO")
        )
    )

    val year = afterDate.year
    if (year != currentDate.year)
        append(' ').append(year)
}.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
