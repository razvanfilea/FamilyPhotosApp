package net.theluckycoder.familyphotos.core.data.model

import net.theluckycoder.familyphotos.core.data.model.db.MemoryCandidate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Groups the photos found by the memories query into [Memory]s and picks their highlights.
 * The query only does a coarse day-of-year filter.
 */
internal object MemoriesBuilder {

    /** Photos up to this many days before or after today's date (in an earlier year) count. */
    const val WINDOW_DAYS = 3
    const val MIN_YEARS_AGO = 1
    const val MAX_YEARS_AGO = 10

    /** Most memories shown at once: nearly every day has photos from most of the last 10 years. */
    const val MAX_MEMORIES = 5

    /** Most photos a memory's story shows. */
    const val MAX_HIGHLIGHTS = 15

    /** Photos taken at most this many seconds apart are one burst: only the best one is shown. */
    const val BURST_GAP_SECONDS = 10L

    /** Longest a burst can last: a steady stream of photos (e.g. at a party) is several moments. */
    const val MAX_BURST_SECONDS = 30L

    fun build(candidates: List<MemoryCandidate>, today: LocalDate, zone: ZoneId): List<Memory> =
        candidates
            .mapNotNull { candidate ->
                val yearsAgo = yearsAgo(candidate.photo.localDate(zone), today)
                    ?.takeIf { it in MIN_YEARS_AGO..MAX_YEARS_AGO }
                yearsAgo?.let { it to candidate }
            }
            .groupBy({ it.first }, { it.second })
            .toSortedMap()
            .map { (yearsAgo, group) -> buildMemory(yearsAgo, group, zone) }

    /**
     * How many years ago [photoDate]'s anniversary closest to [today] was, or null if no
     * anniversary is within [WINDOW_DAYS]. Unlike comparing calendar years, this handles windows
     * crossing New Year: on Jan 2nd, a photo from Dec 31st two years earlier is 1 year ago.
     */
    fun yearsAgo(photoDate: LocalDate, today: LocalDate): Int? {
        // Feb 29th counts as Feb 28th in leap years too, like the server's memories
        val monthDay = if (photoDate.monthValue == 2 && photoDate.dayOfMonth == 29) {
            photoDate.withDayOfMonth(28)
        } else {
            photoDate
        }
        for (year in today.year - 1..today.year + 1) {
            val anniversary = monthDay.withYear(year)
            if (abs(ChronoUnit.DAYS.between(today, anniversary)) <= WINDOW_DAYS) {
                return year - photoDate.year
            }
        }
        return null
    }

    private fun buildMemory(yearsAgo: Int, group: List<MemoryCandidate>, zone: ZoneId): Memory {
        val chronological = group.sortedWith(compareBy({ it.photo.timeCreated }, { it.photo.id }))
        val selected = selectHighlights(chronological)
        val cover = selected.filterNot { it.photo.isVideo }.maxWithOrNull(coverRank)
            ?: selected.first()

        val date = chronological
            .groupingBy { it.photo.localDate(zone) }
            .eachCount()
            .maxBy { it.value }
            .key

        return Memory(
            yearsAgo = yearsAgo,
            date = date,
            cover = cover.photo,
            highlights = listOf(cover.photo) + selected.filter { it !== cover }.map { it.photo },
            allPhotos = chronological.map { it.photo },
            moments = bursts(chronological).size,
        )
    }

    /** Collapses bursts, then keeps up to [MAX_HIGHLIGHTS]: favorites first, the rest spread evenly over time. */
    fun selectHighlights(chronological: List<MemoryCandidate>): List<MemoryCandidate> {
        val representatives = bursts(chronological).map { burst -> burst.maxWith(burstRank) }
        if (representatives.size <= MAX_HIGHLIGHTS) return representatives

        val (favorites, others) = representatives.partition { it.isFavorite }
        val keptFavorites = favorites.spreadEvenly(MAX_HIGHLIGHTS)
        val keptOthers = others.spreadEvenly(MAX_HIGHLIGHTS - keptFavorites.size)
        return (keptFavorites + keptOthers).sortedWith(compareBy({ it.photo.timeCreated }, { it.photo.id }))
    }

    private fun bursts(chronological: List<MemoryCandidate>): List<List<MemoryCandidate>> {
        val bursts = mutableListOf<MutableList<MemoryCandidate>>()
        for (candidate in chronological) {
            val current = bursts.lastOrNull()
            val time = candidate.photo.timeCreated
            if (current != null &&
                time - current.last().photo.timeCreated <= BURST_GAP_SECONDS &&
                time - current.first().photo.timeCreated <= MAX_BURST_SECONDS
            ) {
                current += candidate
            } else {
                bursts += mutableListOf(candidate)
            }
        }
        return bursts
    }

    private fun <T> List<T>.spreadEvenly(count: Int): List<T> = when {
        count <= 0 -> emptyList()
        size <= count -> this
        else -> List(count) { i -> this[i * size / count] }
    }

    /** The bigger file of a burst is usually the sharper, more detailed shot. */
    private val burstRank = compareBy<MemoryCandidate>(
        { it.isFavorite },
        { !it.photo.isVideo },
        { it.fileSize },
    )

    private val coverRank = compareBy<MemoryCandidate>({ it.isFavorite }, { it.fileSize })

    private fun NetworkPhotoThumbnail.localDate(zone: ZoneId): LocalDate =
        Instant.ofEpochSecond(timeCreated).atZone(zone).toLocalDate()
}
