package net.theluckycoder.familyphotos.core.data.model.db

import androidx.room.Embedded
import net.theluckycoder.familyphotos.core.data.model.NetworkPhotoThumbnail

internal data class NetworkPhotoWithYearOffset(
    @Embedded val photo: NetworkPhotoThumbnail,
    val yearOffset: Int
)