package net.theluckycoder.familyphotos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import net.theluckycoder.familyphotos.data.repository.ServerRepository
import javax.inject.Inject

@HiltViewModel
class DuplicatesViewModel @Inject constructor(
    private val serverRepository: ServerRepository,
) : ViewModel() {
    fun getDuplicatesAsync() = viewModelScope.async(Dispatchers.IO) {
        serverRepository.getDuplicates()
    }
}
