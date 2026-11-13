package net.theluckycoder.familyphotos.core.data.model

import androidx.compose.runtime.Immutable
import java.text.Normalizer
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/**
 * A search over the synced photos. The words of [text] match a photo's file name or its folder's
 * name; [year] and [month] (1-12) match when the photo was taken.
 */
@Immutable
data class PhotoSearchQuery(
    val text: String = "",
    val year: Int? = null,
    val month: Int? = null,
    val videosOnly: Boolean = false,
    val favoritesOnly: Boolean = false,
) {
    val isEmpty: Boolean
        get() = text.isEmpty() && year == null && month == null && !videosOnly && !favoritesOnly

    /** [text] as a LIKE pattern (`ESCAPE '\'`), so `%` and `_` in it are matched literally. */
    val likeText: String
        get() = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    companion object {
        private const val MIN_YEAR = 1900
        private const val MAX_YEAR = 2100

        /** Month names in English and Romanian (the app's languages), and in the device's. */
        private val monthNames: Map<String, Int> by lazy {
            val locales = setOf(Locale.ENGLISH, Locale.forLanguageTag("ro"), Locale.getDefault())
            buildMap {
                for (locale in locales) for (month in Month.entries) {
                    for (style in listOf(TextStyle.FULL, TextStyle.FULL_STANDALONE, TextStyle.SHORT)) {
                        val name = normalize(month.getDisplayName(style, locale), locale).trimEnd('.')
                        // A standalone digit would be a weird month name: skip what isn't a word
                        if (name.length >= 3 && name.all { it.isLetter() }) put(name, month.value)
                    }
                }
            }
        }

        /** Lowercase and without diacritics, so "Mai", "mai" and "octombrie" all match. */
        private fun normalize(word: String, locale: Locale = Locale.ROOT): String =
            Normalizer.normalize(word.lowercase(locale), Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "")

        /**
         * Picks a year (4 digits) and a month name out of [input]: "octombrie 2024", "2023",
         * "May vacation". The rest stays as text. Only the first year and month count; the ones
         * after are kept as text.
         */
        fun parse(
            input: String,
            videosOnly: Boolean = false,
            favoritesOnly: Boolean = false,
        ): PhotoSearchQuery {
            var year: Int? = null
            var month: Int? = null
            val rest = mutableListOf<String>()

            for (word in input.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }) {
                val asYear = word.takeIf { it.length == 4 }?.toIntOrNull()
                val asMonth = monthNames[normalize(word).trimEnd('.', ',')]
                when {
                    year == null && asYear != null && asYear in MIN_YEAR..MAX_YEAR -> year = asYear
                    month == null && asMonth != null -> month = asMonth
                    else -> rest += word
                }
            }

            return PhotoSearchQuery(
                text = rest.joinToString(" "),
                year = year,
                month = month,
                videosOnly = videosOnly,
                favoritesOnly = favoritesOnly,
            )
        }
    }
}
