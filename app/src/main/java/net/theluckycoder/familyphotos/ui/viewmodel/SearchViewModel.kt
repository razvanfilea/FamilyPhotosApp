package net.theluckycoder.familyphotos.ui.viewmodel

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import net.theluckycoder.familyphotos.core.data.model.PhotoSearchQuery
import net.theluckycoder.familyphotos.core.data.model.TimelineLayout
import net.theluckycoder.familyphotos.core.data.repository.SearchRepository
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel.Companion.PAGING_CONFIG
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val text: StateFlow<String> = savedStateHandle.getStateFlow(KEY_TEXT, "")
    val videosOnly: StateFlow<Boolean> = savedStateHandle.getStateFlow(KEY_VIDEOS, false)
    val favoritesOnly: StateFlow<Boolean> = savedStateHandle.getStateFlow(KEY_FAVORITES, false)

    private val _gridState = MutableStateFlow(LazyGridState())
    val gridState: StateFlow<LazyGridState> = _gridState.asStateFlow()

    /** Waits for a pause in typing, but applies the filter chips at once. */
    val query: StateFlow<PhotoSearchQuery> =
        combine(text.debounce { if (it.isEmpty()) 0 else TYPING_DEBOUNCE_MS }, videosOnly, favoritesOnly) { text, videos, favorites ->
            PhotoSearchQuery.parse(text, videosOnly = videos, favoritesOnly = favorites)
        }
            .distinctUntilChanged()
            // A new search starts at the top
            .onEach { _gridState.value = LazyGridState() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, PhotoSearchQuery())

    val photosPager = query
        .flatMapLatest { query ->
            Pager(PAGING_CONFIG) { searchRepository.searchPhotosPaged(query) }.flow
        }
        .cachedIn(viewModelScope)

    val timelineLayout: StateFlow<TimelineLayout> = query
        .flatMapLatest { searchRepository.searchMonthSummaries(it) }
        .map { TimelineLayout.build(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimelineLayout.EMPTY)

    val folders = query
        .flatMapLatest { searchRepository.searchFolders(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val years = searchRepository.getYearSummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setText(text: String) {
        savedStateHandle[KEY_TEXT] = text
    }

    fun setVideosOnly(value: Boolean) {
        savedStateHandle[KEY_VIDEOS] = value
    }

    fun setFavoritesOnly(value: Boolean) {
        savedStateHandle[KEY_FAVORITES] = value
    }

    private companion object {
        const val KEY_TEXT = "text"
        const val KEY_VIDEOS = "videos_only"
        const val KEY_FAVORITES = "favorites_only"
        const val TYPING_DEBOUNCE_MS = 300L
    }
}
