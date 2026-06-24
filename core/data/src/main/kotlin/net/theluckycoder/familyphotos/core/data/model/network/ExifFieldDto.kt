package net.theluckycoder.familyphotos.core.data.model.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ExifFieldDto(
    @SerialName("tag") val tag: String,
    @SerialName("value") val value: String,
)
