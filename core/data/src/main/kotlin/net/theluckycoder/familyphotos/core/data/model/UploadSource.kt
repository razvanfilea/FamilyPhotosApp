package net.theluckycoder.familyphotos.core.data.model

import android.net.Uri

/**
 * What [net.theluckycoder.familyphotos.core.data.repository.PhotoUploadRepository.uploadFile] sends.
 *
 * @param localPhoto set only for MediaStore photos, which get linked to the uploaded photo
 * @param timeCreated epoch seconds
 */
data class UploadSource(
    val uri: Uri,
    val name: String,
    val timeCreated: Long,
    val mimeType: String?,
    val localPhoto: LocalPhoto?,
)

fun LocalPhoto.toUploadSource() = UploadSource(
    uri = uri,
    name = name,
    timeCreated = timeCreated,
    mimeType = mimeType,
    localPhoto = this,
)
