package net.theluckycoder.familyphotos.data.model

import androidx.room.Embedded

data class NetworkPhotoWithYearOffset(
    @Embedded val photo: NetworkPhoto,
    val yearOffset: Int
)