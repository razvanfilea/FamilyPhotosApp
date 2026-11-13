package net.theluckycoder.familyphotos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.theluckycoder.familyphotos.core.data.model.Photo
import net.theluckycoder.familyphotos.core.data.repository.FoldersRepository
import net.theluckycoder.familyphotos.core.data.repository.PhotosRepository
import net.theluckycoder.familyphotos.ui.FolderNav
import net.theluckycoder.familyphotos.ui.SlideshowNav
import javax.inject.Inject

@HiltViewModel
class SlideshowViewModel @Inject constructor(
    private val photosRepository: PhotosRepository,
    private val foldersRepository: FoldersRepository,
    private val selections: SlideshowSelections,
) : ViewModel() {

    private var source: SlideshowNav.Source? = null

    private val _photos = MutableStateFlow<List<Photo>?>(null)

    /** Null while loading. Loaded once: the slideshow mustn't change while playing. */
    val photos: StateFlow<List<Photo>?> = _photos.asStateFlow()

    fun load(source: SlideshowNav.Source) {
        if (this.source == source) return
        this.source = source

        viewModelScope.launch(Dispatchers.IO) {
            _photos.value = when (source) {
                is SlideshowNav.Source.Folder -> when (val folder = source.folder) {
                    is FolderNav.Source.Network -> foldersRepository.allNetworkPhotosFromFolder(folder.folderId)
                    is FolderNav.Source.Local -> foldersRepository.allLocalPhotosFromFolder(folder.name)
                    FolderNav.Source.Favorites -> photosRepository.getFavoritePhotos()
                }

                // Newest first, like the timeline and the folders
                is SlideshowNav.Source.Selection -> {
                    val photoIds = selections[source.selectionId].orEmpty()
                    val photos = if (source.isLocal) {
                        photosRepository.getLocalPhotos(photoIds)
                    } else {
                        photosRepository.getNetworkPhotoThumbnails(photoIds)
                    }
                    photos.sortedByDescending { it.timeCreated }
                }
            }
        }
    }

    /** The slideshow was closed: its selection isn't needed anymore (the photos are loaded). */
    override fun onCleared() {
        (source as? SlideshowNav.Source.Selection)?.let { selections.remove(it.selectionId) }
    }
}
