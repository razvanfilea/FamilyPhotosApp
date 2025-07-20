package net.theluckycoder.familyphotos.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class PhotoEventLog(
    @SerialName("photo_id")
    val photoId: Long,
    val data: List<Byte>?
)

@Serializable
class FullPhotoList(
    @SerialName("event_log_id")
    val eventLogId: Long,
    val photos: List<BasicNetworkPhoto>
)

@Serializable
class PartialPhotoList(
    @SerialName("event_log_id")
    val eventLogId: Long,
    val events: List<PhotoEventLog>
)
