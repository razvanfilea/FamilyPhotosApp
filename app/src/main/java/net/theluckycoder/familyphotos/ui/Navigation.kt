package net.theluckycoder.familyphotos.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.model.NetworkFolder
import net.theluckycoder.familyphotos.model.Photo

sealed interface TopLevelRouteNav : NavKey {
    @get:DrawableRes
    val icon: Int

    @get:DrawableRes
    val selectedIcon: Int

    @get:StringRes
    val name: Int
}

@Serializable
data object TimelineNav : TopLevelRouteNav {
    override val icon = R.drawable.ic_photo_outline
    override val selectedIcon: Int = R.drawable.ic_photo_filled
    override val name: Int = R.string.section_photos
}

@Serializable
data object NetworkFolderNav : TopLevelRouteNav {
    override val icon = R.drawable.tab_network_folder_outlined
    override val selectedIcon: Int = R.drawable.tab_network_folder_filled
    override val name: Int = R.string.section_folders
}

@Serializable
data object DeviceNav : TopLevelRouteNav {
    override val icon = R.drawable.ic_storage_outline
    override val selectedIcon: Int = R.drawable.ic_storage_filled
    override val name: Int = R.string.section_device
}

@Serializable
data class FolderNav(
    val source: Source
) : NavKey {

    @Serializable
    sealed class Source {
        data object Favorites : Source()
        data class Network(val folder: NetworkFolder) : Source()
        data class Local(val name: String) : Source()
    }
}

@Serializable
data class PhotoViewerNav(
    val photos: List<Photo>,
) : NavKey

@Serializable
data class MovePhotosNav(
    val photoIds: List<Long>,
) : NavKey

@Serializable
data class UploadPhotosNav(
    val photoIds: List<Long>,
) : NavKey

@Serializable
data class RenameFolderNav(
    val folder: NetworkFolder
) : NavKey