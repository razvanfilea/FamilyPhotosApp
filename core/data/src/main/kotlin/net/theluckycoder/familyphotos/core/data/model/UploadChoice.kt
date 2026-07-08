package net.theluckycoder.familyphotos.core.data.model

import androidx.compose.runtime.Immutable

@Immutable
sealed class UploadChoice(open val isPublic: Boolean?) {
    data class NoFolder(override val isPublic: Boolean) : UploadChoice(isPublic)
    data class NewFolder(val name: String, override val isPublic: Boolean) : UploadChoice(isPublic)
    data class Folder(val folderId: Long) : UploadChoice(null)
}
