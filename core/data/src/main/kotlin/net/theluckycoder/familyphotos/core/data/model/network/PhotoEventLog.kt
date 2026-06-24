package net.theluckycoder.familyphotos.core.data.model.network

import android.util.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.theluckycoder.familyphotos.core.data.model.NetworkPhoto

@Serializable
internal class PhotoEventLog(
    @SerialName("photo_id")
    val photoId: Long,
    @SerialName("data")
    val data: String?
) {
    fun decodePhoto(): NetworkPhoto? = data?.let {
        val json = Base64.decode(it, Base64.DEFAULT).toString(Charsets.UTF_8)
        Json.decodeFromString<NetworkPhotoDto>(json).toEntity()
    }
}

@Serializable
internal class FullPhotoListDto(
    @SerialName("event_log_id")
    val eventLogId: Long,
    @SerialName("photos")
    val photos: List<NetworkPhotoDto>
)

@Serializable
internal class PartialPhotoListDto(
    @SerialName("event_log_id")
    val eventLogId: Long,
    @SerialName("events")
    val events: List<PhotoEventLog>
)
