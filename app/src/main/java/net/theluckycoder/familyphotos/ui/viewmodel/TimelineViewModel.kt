package net.theluckycoder.familyphotos.ui.viewmodel

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.theluckycoder.familyphotos.data.local.datastore.SettingsDataStore
import net.theluckycoder.familyphotos.data.model.PhotoType
import net.theluckycoder.familyphotos.data.model.TimelineLayout
import net.theluckycoder.familyphotos.data.repository.PhotosRepository
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel.Companion.PAGING_CONFIG
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val photosRepository: PhotosRepository,
    private val settingsStore: SettingsDataStore,
) : ViewModel() {

    val selectedPhotoType =
        settingsStore.photoType.stateIn(viewModelScope, SharingStarted.Lazily, PhotoType.All)

    val timelinePager = selectedPhotoType.flatMapLatest { photoType ->
        Pager(PAGING_CONFIG) {
            photosRepository.getAllPhotosPaged(photoType)
        }.flow
    }.cachedIn(viewModelScope)

    val memories = selectedPhotoType
        .flatMapLatest { photoType -> photosRepository.getMemories(photoType) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val timelineLayout: StateFlow<TimelineLayout> = selectedPhotoType
        .flatMapLatest { photoType -> photosRepository.getMonthSummaries(photoType) }
        .map { summaries -> TimelineLayout.build(summaries) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), TimelineLayout.EMPTY)

    val memoriesListState = LazyListState()
    val timelineGridState = LazyGridState()

    fun setSelectedPhotoType(photoType: PhotoType) {
        viewModelScope.launch(Dispatchers.Default) {
            settingsStore.setSelectedPhotoType(photoType)
        }
    }
}