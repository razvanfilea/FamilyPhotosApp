package net.theluckycoder.familyphotos.core.data.model.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import net.theluckycoder.familyphotos.core.data.model.NetworkPhoto

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
internal data class FavoriteNetworkPhoto(
    @PrimaryKey
    val photoId: Long,
)