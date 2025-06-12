package net.theluckycoder.familyphotos.model

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey

@Keep
@Entity(
    tableName = "temp_photo_ids",
)
data class TempPhotoId(
    @PrimaryKey
    val id: Long
)