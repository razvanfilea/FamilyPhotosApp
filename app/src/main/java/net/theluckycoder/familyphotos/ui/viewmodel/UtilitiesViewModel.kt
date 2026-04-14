package net.theluckycoder.familyphotos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import net.theluckycoder.familyphotos.data.model.db.PhotoStatistics
import net.theluckycoder.familyphotos.data.repository.PhotosRepository
import net.theluckycoder.familyphotos.data.repository.ServerRepository
import javax.inject.Inject

@HiltViewModel
class UtilitiesViewModel @Inject constructor(
    private val photosRepository: PhotosRepository,
    private val serverRepository: ServerRepository,
) : ViewModel() {

    val photoStatistics = photosRepository.getPhotoStatistics()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), PhotoStatistics.Empty)

    val largePhotos = photosRepository.getLargePhotos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getDuplicatesAsync() = viewModelScope.async(Dispatchers.IO) {
        serverRepository.getDuplicates()
    }
}
