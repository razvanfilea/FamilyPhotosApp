package net.theluckycoder.familyphotos.core.data.model

import net.theluckycoder.familyphotos.core.data.model.db.MemoryCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class MemoriesBuilderTest {

    private val zone = ZoneOffset.UTC
    private var nextId = 1L

    private fun candidate(
        time: LocalDateTime,
        name: String = "photo.jpg",
        fileSize: Long = 1_000,
        isFavorite: Boolean = false,
    ) = MemoryCandidate(
        photo = NetworkPhotoThumbnail(
            id = nextId++,
            timeCreated = time.toEpochSecond(zone),
            name = name,
        ),
        fileSize = fileSize,
        isFavorite = isFavorite,
    )

    @Test
    fun `years ago is counted from the closest anniversary`() {
        val today = LocalDate.of(2026, 1, 2)
        // Two days ago is 0 years ago (build() drops it), not 1 (2026 - 2025)
        assertEquals(0, MemoriesBuilder.yearsAgo(LocalDate.of(2025, 12, 31), today))
        // Its anniversary was 2 days ago, in 2025: 1 year, not 2 (2026 - 2024)
        assertEquals(1, MemoriesBuilder.yearsAgo(LocalDate.of(2024, 12, 31), today))
        assertEquals(3, MemoriesBuilder.yearsAgo(LocalDate.of(2023, 1, 5), today))
        assertNull(MemoriesBuilder.yearsAgo(LocalDate.of(2023, 1, 6), today))
    }

    @Test
    fun `window across New Year counts forward too`() {
        val today = LocalDate.of(2025, 12, 30)
        // Jan 1st 2024's next anniversary is in 2 days: 2 years
        assertEquals(2, MemoriesBuilder.yearsAgo(LocalDate.of(2024, 1, 1), today))
    }

    @Test
    fun `leap day matches Feb 28th in other years`() {
        assertEquals(2, MemoriesBuilder.yearsAgo(LocalDate.of(2024, 2, 29), LocalDate.of(2026, 3, 3)))
        assertNull(MemoriesBuilder.yearsAgo(LocalDate.of(2024, 2, 29), LocalDate.of(2026, 3, 4)))
    }

    @Test
    fun `leap day matches Feb 28th in leap years too`() {
        assertEquals(8, MemoriesBuilder.yearsAgo(LocalDate.of(2020, 2, 29), LocalDate.of(2028, 2, 25)))
        assertEquals(8, MemoriesBuilder.yearsAgo(LocalDate.of(2020, 2, 29), LocalDate.of(2028, 3, 2)))
        assertNull(MemoriesBuilder.yearsAgo(LocalDate.of(2020, 2, 29), LocalDate.of(2028, 3, 3)))
    }

    @Test
    fun `memories are grouped and sorted by years ago, dropping out of range photos`() {
        val today = LocalDate.of(2026, 6, 15)
        val memories = MemoriesBuilder.build(
            listOf(
                candidate(LocalDateTime.of(2020, 6, 15, 12, 0)),
                candidate(LocalDateTime.of(2025, 6, 14, 12, 0)),
                candidate(LocalDateTime.of(2025, 6, 16, 12, 0)),
                candidate(LocalDateTime.of(2026, 6, 14, 12, 0)), // this year
                candidate(LocalDateTime.of(2010, 6, 15, 12, 0)), // too old
            ),
            today,
            zone,
        )

        assertEquals(listOf(1, 6), memories.map { it.yearsAgo })
        assertEquals(2, memories[0].allPhotos.size)
    }

    @Test
    fun `bursts collapse to their best photo`() {
        val base = LocalDateTime.of(2025, 6, 15, 12, 0)
        val small = candidate(base, fileSize = 100)
        val big = candidate(base.plusSeconds(3), fileSize = 900)
        val chained = candidate(base.plusSeconds(12), fileSize = 200) // 9s after the previous one
        val separate = candidate(base.plusMinutes(5))

        val selected = MemoriesBuilder.selectHighlights(listOf(small, big, chained, separate))

        assertEquals(listOf(big, separate), selected)
    }

    @Test
    fun `a steady stream of photos is split into several bursts`() {
        val base = LocalDateTime.of(2025, 6, 15, 20, 0)
        // One photo every 6 s for 2 minutes: chained, but each burst lasts at most 30 s
        val photos = List(21) { candidate(base.plusSeconds(it * 6L)) }

        val selected = MemoriesBuilder.selectHighlights(photos)

        assertEquals(4, selected.size)
    }

    @Test
    fun `favorites win a burst and are always kept`() {
        val base = LocalDateTime.of(2025, 6, 15, 8, 0)
        val photos = List(60) { candidate(base.plusMinutes(it.toLong()), isFavorite = it == 37) }

        val selected = MemoriesBuilder.selectHighlights(photos)

        assertEquals(MemoriesBuilder.MAX_HIGHLIGHTS, selected.size)
        assertTrue(photos[37] in selected)
        assertEquals(selected.sortedBy { it.photo.timeCreated }, selected)
        // Spread over the whole span, not the first 20 minutes
        assertTrue(selected.last().photo.timeCreated - selected.first().photo.timeCreated > 50 * 60)
    }

    @Test
    fun `cover prefers a favorite photo over videos and leads the highlights`() {
        val today = LocalDate.of(2026, 6, 15)
        val base = LocalDateTime.of(2024, 6, 15, 10, 0)
        val video = candidate(base, name = "clip.mp4", fileSize = 50_000, isFavorite = true)
        val plain = candidate(base.plusHours(1), fileSize = 5_000)
        val favorite = candidate(base.plusHours(2), fileSize = 1_000, isFavorite = true)
        val last = candidate(base.plusHours(3))

        val memory = MemoriesBuilder.build(listOf(last, plain, video, favorite), today, zone).single()

        assertEquals(favorite.photo, memory.cover)
        assertEquals(listOf(favorite, video, plain, last).map { it.photo }, memory.highlights)
        assertEquals(listOf(video, plain, favorite, last).map { it.photo }, memory.allPhotos)
        assertEquals(LocalDate.of(2024, 6, 15), memory.date)
    }
}
