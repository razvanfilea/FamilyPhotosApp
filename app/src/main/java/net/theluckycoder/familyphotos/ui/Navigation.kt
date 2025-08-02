package net.theluckycoder.familyphotos.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.data.model.db.NetworkFolder
import net.theluckycoder.familyphotos.data.model.db.Photo

@Serializable
object TopLevelNav : NavKey

enum class TopLevelTab(
    @param:DrawableRes val icon: Int,
    @param:DrawableRes val selectedIcon: Int,
    @param:StringRes val sectionName: Int
) {
    Timeline(R.drawable.tab_photos_outline, R.drawable.tab_photos_filled, R.string.section_photos),
    NetworkFolders(
        R.drawable.tab_network_folder_outlined,
        R.drawable.tab_network_folder_filled,
        R.string.section_folders
    ),
    Device(R.drawable.tab_device_outline, R.drawable.tab_device_filled, R.string.section_device),
    Utility(R.drawable.tab_utilities_outline, R.drawable.tab_utilities_filled, R.string.section_utilities),
}

@Serializable
data class FolderNav(
    val source: Source
) : NavKey {

    @Serializable
    sealed class Source {
        @Serializable
        data object Favorites : Source()

        @Serializable
        data class Network(val folder: NetworkFolder) : Source()

        @Serializable
        data class Local(val name: String) : Source()
    }
}

@Serializable
data class PhotoViewerFlowNav(
    val initialPhotoIndex: Int,
    val source: Source,
) : NavKey {
    @Serializable
    enum class Source {
        Timeline,
        Network,
        Local,
        Favorites
    }
}

@Serializable
data class PhotoViewerListNav(
    val photos: List<Photo>,
) : NavKey

@Serializable
class MovePhotosNav(
    val photoIds: LongArray,
) : NavKey

@Serializable
class UploadPhotosNav(
    val photoIds: LongArray,
) : NavKey

@Serializable
data class RenameFolderNav(
    val folder: NetworkFolder
) : NavKey

@Serializable
object DuplicatesNav : NavKey

@Serializable
object SettingsNav : NavKey