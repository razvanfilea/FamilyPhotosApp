package net.theluckycoder.familyphotos.core.data.model.db

import androidx.room.Embedded
import net.theluckycoder.familyphotos.core.data.model.NetworkPhotoThumbnail

/** A photo the memories query found near today's date, with what curation ranks it by. */
internal data class MemoryCandidate(
    @Embedded val photo: NetworkPhotoThumbnail,
    val fileSize: Long,
    val isFavorite: Boolean,
)
