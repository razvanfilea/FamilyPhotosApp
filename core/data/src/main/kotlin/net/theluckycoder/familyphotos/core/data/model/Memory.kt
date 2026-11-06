package net.theluckycoder.familyphotos.core.data.model

import java.time.LocalDate

/**
 * Photos taken around today's date, [yearsAgo] years ago.
 *
 * @property date the day most of the photos were taken on
 * @property highlights the curated story: [cover] first, then the rest in chronological order
 * @property allPhotos every photo of the memory, in chronological order
 */
data class Memory(
    val yearsAgo: Int,
    val date: LocalDate,
    val cover: NetworkPhotoThumbnail,
    val highlights: List<NetworkPhotoThumbnail>,
    val allPhotos: List<NetworkPhotoThumbnail>,
)
