package net.theluckycoder.familyphotos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.theluckycoder.familyphotos.core.data.model.Memory
import net.theluckycoder.familyphotos.core.data.model.mostMemorable
import net.theluckycoder.familyphotos.core.data.repository.PhotosRepository
import net.theluckycoder.familyphotos.ui.MemoryNav
import javax.inject.Inject

@HiltViewModel
class MemoryStoryViewModel @Inject constructor(
    private val photosRepository: PhotosRepository,
) : ViewModel() {

    sealed interface State {
        data object Loading : State
        data class Found(val memory: Memory) : State
        /** E.g. the day changed while the app was in the background. */
        data object Missing : State
    }

    private var key: MemoryNav? = null

    private val _state = MutableStateFlow<State>(State.Loading)

    /** Loaded once: the story mustn't reshuffle while playing (e.g. after favoriting a photo). */
    val state: StateFlow<State> = _state.asStateFlow()

    fun load(key: MemoryNav) {
        if (this.key == key) return
        this.key = key

        viewModelScope.launch(Dispatchers.Default) {
            // The ones the timeline, the widgets and the notifications show
            val memory = photosRepository.getMemoriesNow(key.photoType)
                .mostMemorable()
                .find { it.yearsAgo == key.yearsAgo }
            _state.value = if (memory != null) State.Found(memory) else State.Missing
        }
    }
}
