package net.theluckycoder.familyphotos.core.data.model.db

import androidx.compose.runtime.Immutable
import androidx.room.Embedded
import net.theluckycoder.familyphotos.core.data.model.NetworkPhotoThumbnail

/** The photos taken in [year], with the latest one as the cover. */
@Immutable
data class YearSummary(
    val year: Int,
    val photoCount: Int,
    @Embedded val cover: NetworkPhotoThumbnail,
)
