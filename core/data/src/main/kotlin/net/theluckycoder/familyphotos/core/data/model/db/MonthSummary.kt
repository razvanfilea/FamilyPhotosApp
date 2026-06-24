package net.theluckycoder.familyphotos.core.data.model.db

import androidx.compose.runtime.Immutable

@Immutable
data class MonthSummary(
    val timeCreated: Long,
    val coverPhotoId: Long,
    val photoCount: Int,
)
