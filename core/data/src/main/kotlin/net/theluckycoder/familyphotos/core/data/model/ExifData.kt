package net.theluckycoder.familyphotos.core.data.model

import androidx.compose.runtime.Immutable
import net.theluckycoder.familyphotos.core.data.model.network.ExifFieldDto

@Immutable
class ExifData internal constructor(exifData: List<ExifFieldDto>) {
    constructor() : this(emptyList())

    private val map = mapOf(*exifData.map { it.tag to it.value }.toTypedArray())

    operator fun get(tag: String) = map[tag]

    val isNotEmpty = map.isNotEmpty()
}
