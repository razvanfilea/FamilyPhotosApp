package net.theluckycoder.familyphotos.data.model.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "favorite_network_photo",
    foreignKeys = [
        ForeignKey(
            entity = NetworkPhoto::class,
            parentColumns = ["id"],
            childColumns = ["photoId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
)
data class FavoriteNetworkPhoto(
    @PrimaryKey
    val photoId: Long,
)