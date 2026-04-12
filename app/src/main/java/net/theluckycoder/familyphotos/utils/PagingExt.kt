package net.theluckycoder.familyphotos.utils

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaMonth
import kotlinx.datetime.toLocalDateTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private val displayLocale: Locale = Locale.forLanguageTag("ro-RO")
private val titleCaseLocale: Locale = Locale.getDefault()

@OptIn(ExperimentalTime::class)
internal fun buildDateString(epochSeconds: Long): String {
    val timeZone = TimeZone.currentSystemDefault()
    val currentDate = Clock.System.now().toLocalDateTime(timeZone)
    val date = Instant.fromEpochSeconds(epochSeconds).toLocalDateTime(timeZone)
    return buildDateString(currentDate, date)
}

internal fun buildDateString(currentDate: LocalDateTime, afterDate: LocalDateTime) =
    buildString {
        append(
            afterDate.month.toJavaMonth().getDisplayName(
                TextStyle.FULL,
                displayLocale
            )
        )

        val year = afterDate.year
        if (year != currentDate.year)
            append(' ').append(year)
    }.replaceFirstChar { if (it.isLowerCase()) it.titlecase(titleCaseLocale) else it.toString() }
