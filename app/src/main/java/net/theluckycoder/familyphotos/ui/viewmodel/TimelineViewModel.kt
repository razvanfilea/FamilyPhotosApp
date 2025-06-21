package net.theluckycoder.familyphotos.ui.viewmodel

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.cachedIn
import androidx.paging.filter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.theluckycoder.familyphotos.data.local.datastore.SettingsDataStore
import net.theluckycoder.familyphotos.data.local.datastore.UserDataStore
import net.theluckycoder.familyphotos.data.model.PUBLIC_USER_ID
import net.theluckycoder.familyphotos.data.model.PhotoType
import net.theluckycoder.familyphotos.data.model.isPublic
import net.theluckycoder.familyphotos.data.repository.PhotosRepository
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel.Companion.PAGING_CONFIG
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val photosRepository: PhotosRepository,
    private val settingsStore: SettingsDataStore,
    userDataStore: UserDataStore,
) : ViewModel() {

    val selectedPhotoType =
        settingsStore.photoType.stateIn(viewModelScope, SharingStarted.Lazily, PhotoType.All)

    val timelinePager = Pager(PAGING_CONFIG) {
        photosRepository.getAllPhotosPaged()
    }.flow
        .cachedIn(viewModelScope)
        .combine(selectedPhotoType) { photos, photoType ->
            if (photoType == PhotoType.All) {
                return@combine photos
            }
            photos.filter {
                when (photoType) {
                    PhotoType.All -> true
                    PhotoType.Personal -> !it.isPublic()
                    PhotoType.Family -> it.isPublic()
                }
            }
        }

    val memories = selectedPhotoType
        .combine(userDataStore.userIdFlow) { photoType, userName ->
            when (photoType) {
                PhotoType.All -> null
                PhotoType.Personal -> userName
                PhotoType.Family -> PUBLIC_USER_ID
            }
        }
        .flatMapLatest { userName -> photosRepository.getMemories(userName) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyMap())

    val timelineGridState = LazyGridState()

    fun setSelectedPhotoType(photoType: PhotoType) {
        viewModelScope.launch(Dispatchers.Default) {
            settingsStore.setSelectedPhotoType(photoType)
        }
    }
}