package net.theluckycoder.familyphotos.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ExifField(
    val tag: String,
    val value: String,
)

class ExifData(exifData: List<ExifField>) {
    constructor() : this(emptyList())

    private val map = mapOf(*exifData.map { it.tag to it.value }.toTypedArray())

    operator fun get(tag: String) = map[tag]

    val isEmpty = map.isEmpty()

    val isNotEmpty = map.isNotEmpty()
}
