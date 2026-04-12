package net.theluckycoder.familyphotos.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import net.theluckycoder.familyphotos.data.model.db.PhotoStatistics
import net.theluckycoder.familyphotos.data.repository.PhotosRepository
import javax.inject.Inject

@HiltViewModel
class UtilitiesViewModel @Inject constructor(
    photosRepository: PhotosRepository,
) : ViewModel() {

    val photoStatistics = photosRepository.getPhotoStatistics()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), PhotoStatistics.Empty)
}
