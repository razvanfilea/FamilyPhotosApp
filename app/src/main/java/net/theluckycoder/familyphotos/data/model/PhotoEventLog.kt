package net.theluckycoder.familyphotos.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.theluckycoder.familyphotos.data.model.db.NetworkPhoto

@Serializable
class PhotoEventLog(
    @SerialName("photo_id")
    val photoId: Long,
    @SerialName("data")
    val data: List<Byte>?
)

@Serializable
class FullPhotoList(
    @SerialName("event_log_id")
    val eventLogId: Long,
    @SerialName("photos")
    val photos: List<NetworkPhoto>
)

@Serializable
class PartialPhotoList(
    @SerialName("event_log_id")
    val eventLogId: Long,
    @SerialName("events")
    val events: List<PhotoEventLog>
)
