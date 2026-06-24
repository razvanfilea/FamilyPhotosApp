package net.theluckycoder.familyphotos.core.data.model.db

import androidx.room.Embedded
import net.theluckycoder.familyphotos.core.data.model.NetworkPhoto

internal data class NetworkPhotoWithYearOffset(
    @Embedded val photo: NetworkPhoto,
    val yearOffset: Int
)