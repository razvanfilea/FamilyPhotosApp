package net.theluckycoder.familyphotos.core.data.model

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Pure helpers for files shared from other apps. Times are epoch seconds unless noted.
 */
internal object SharedUploadMetadata {

    private val EXIF_DATE_TIME = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")
    private val VIDEO_DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss[.SSS]'Z'")

    /**
     * Picks the first plausible candidate. Apps that strip metadata (WhatsApp) end up at [nowSeconds].
     *
     * @param embeddedSeconds from EXIF (images) or the container (videos)
     * @param dateTakenMillis `MediaStore.MediaColumns.DATE_TAKEN`, exposed by MediaStore URIs
     * @param lastModifiedMillis `DocumentsContract.Document.COLUMN_LAST_MODIFIED`
     */
    fun selectCaptureTime(
        embeddedSeconds: Long?,
        dateTakenMillis: Long?,
        lastModifiedMillis: Long?,
        nowSeconds: Long,
    ): Long = embeddedSeconds?.takeIf { it.isPlausible(nowSeconds) }
        ?: dateTakenMillis?.div(1000)?.takeIf { it.isPlausible(nowSeconds) }
        ?: lastModifiedMillis?.div(1000)?.takeIf { it.isPlausible(nowSeconds) }
        ?: nowSeconds

    // Zero dates (e.g. 1904 in empty MP4 headers) and clocks that are far ahead aren't capture times
    private fun Long.isPlausible(nowSeconds: Long) = this > 0 && this <= nowSeconds + 86_400

    /**
     * @param dateTime EXIF `DateTimeOriginal`, `yyyy:MM:dd HH:mm:ss`
     * @param offset EXIF `OffsetTimeOriginal`, e.g. `+02:00`; without it the time is taken as [zone]'s
     */
    fun parseExifDateTime(dateTime: String?, offset: String?, zone: ZoneId): Long? {
        if (dateTime.isNullOrBlank()) return null
        val local = try {
            LocalDateTime.parse(dateTime.trim(), EXIF_DATE_TIME)
        } catch (_: DateTimeParseException) {
            return null
        }
        val zoneOffset = offset?.trim()?.takeIf { it.isNotEmpty() }?.let {
            try {
                ZoneOffset.of(it)
            } catch (_: Exception) {
                null
            }
        }
        return if (zoneOffset != null) {
            OffsetDateTime.of(local, zoneOffset).toEpochSecond()
        } else {
            local.atZone(zone).toEpochSecond()
        }
    }

    /** @param date `MediaMetadataRetriever.METADATA_KEY_DATE`, e.g. `20230615T143000.000Z` */
    fun parseVideoDate(date: String?): Long? {
        if (date.isNullOrBlank()) return null
        return try {
            LocalDateTime.parse(date.trim(), VIDEO_DATE_TIME).toEpochSecond(ZoneOffset.UTC)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    /** The server tells videos apart by extension, so a shared name must keep one */
    fun ensureExtension(name: String, extension: String?): String {
        if (extension.isNullOrEmpty()) return name
        val current = name.substringAfterLast('.', missingDelimiterValue = "")
        return if (current.isNotEmpty() && name.length > current.length + 1) name else "$name.$extension"
    }

    // The share intent's type, if it names exactly one supported type: "image/jpeg" does,
    // while wildcards (several image types, or a mixed share) don't
    fun concreteMimeType(intentType: String?): String? =
        intentType?.lowercase()?.takeIf { '*' !in it && isSupportedMimeType(it) }

    fun isSupportedMimeType(mimeType: String?): Boolean =
        mimeType != null && (mimeType.startsWith("image/") || mimeType.startsWith("video/"))
}
