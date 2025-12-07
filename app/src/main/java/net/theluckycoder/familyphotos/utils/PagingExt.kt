package net.theluckycoder.familyphotos.utils

import androidx.paging.PagingData
import androidx.paging.insertSeparators
import androidx.paging.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toJavaMonth
import kotlinx.datetime.toLocalDateTime
import net.theluckycoder.familyphotos.data.model.DataOrSeparator
import net.theluckycoder.familyphotos.data.model.db.Photo
import java.time.format.TextStyle
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

fun <T : Photo> Flow<PagingData<T>>.mapPagingPhotos(): Flow<PagingData<DataOrSeparator<T>>> =
    map { pagingData ->
        pagingData
            .map { photo -> DataOrSeparator.Data(photo) }
            .insertSeparators { before, after ->
                after ?: return@insertSeparators null
                computeSeparatorText(before?.data, after.data)?.let {
                    DataOrSeparator.Separator(it)
                }
            }
    }

@OptIn(ExperimentalTime::class)
private fun computeSeparatorText(before: Photo?, after: Photo): String? {
    val timeZone = TimeZone.currentSystemDefault()
    val beforeDate = before?.let {
        val instant = Instant.fromEpochSeconds(it.timeCreated)
        instant.toLocalDateTime(timeZone)
    }
    val afterDate =
        Instant.fromEpochSeconds(after.timeCreated)
            .toLocalDateTime(timeZone)

    if (beforeDate != null && beforeDate.month.number == afterDate.month.number && beforeDate.year == afterDate.year)
        return null

    val currentDate =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    return buildDateString(currentDate, afterDate)
}

private fun buildDateString(currentDate: LocalDateTime, afterDate: LocalDateTime) =
    buildString {
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
