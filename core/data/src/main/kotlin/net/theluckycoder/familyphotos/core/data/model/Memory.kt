package net.theluckycoder.familyphotos.core.data.model

import java.time.LocalDate

/**
 * Photos taken around today's date, [yearsAgo] years ago.
 *
 * @property date the day most of the photos were taken on
 * @property highlights the curated story: [cover] first, then the rest in chronological order
 * @property allPhotos every photo of the memory, in chronological order
 * @property moments how many distinct moments [allPhotos] capture: a burst of photos counts once
 */
data class Memory(
    val yearsAgo: Int,
    val date: LocalDate,
    val cover: NetworkPhotoThumbnail,
    val highlights: List<NetworkPhotoThumbnail>,
    val allPhotos: List<NetworkPhotoThumbnail>,
    val moments: Int,
)

/**
 * The memories worth showing: the [MemoriesBuilder.MAX_MEMORIES] with the most moments, still
 * sorted by years ago. On ties, the more recent year wins.
 */
fun List<Memory>.mostMemorable(): List<Memory> =
    sortedByDescending { it.moments }.take(MemoriesBuilder.MAX_MEMORIES).sortedBy { it.yearsAgo }
