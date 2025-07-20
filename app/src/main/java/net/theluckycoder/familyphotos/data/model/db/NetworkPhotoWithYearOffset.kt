package net.theluckycoder.familyphotos.data.model.db

import androidx.room.Embedded
import net.theluckycoder.familyphotos.data.model.db.NetworkPhoto

data class NetworkPhotoWithYearOffset(
    @Embedded val photo: NetworkPhoto,
    val yearOffset: Int
)