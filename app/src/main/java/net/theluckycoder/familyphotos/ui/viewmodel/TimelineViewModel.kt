package net.theluckycoder.familyphotos.ui.viewmodel

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.insertSeparators
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toJavaMonth
import kotlinx.datetime.toLocalDateTime
import net.theluckycoder.familyphotos.data.local.datastore.SettingsDataStore
import net.theluckycoder.familyphotos.data.model.PhotoType
import net.theluckycoder.familyphotos.data.model.db.Photo
import net.theluckycoder.familyphotos.data.model.db.isPublic
import net.theluckycoder.familyphotos.data.repository.PhotosRepository
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel.Companion.PAGING_CONFIG
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val photosRepository: PhotosRepository,
    private val settingsStore: SettingsDataStore,
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
                    PhotoType.Personal -> !it.isPublic
                    PhotoType.Family -> it.isPublic
                }
            }
        }

    val memories = selectedPhotoType
        .flatMapLatest { photoType -> photosRepository.getMemories(photoType) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val memoriesListState = LazyListState()
    val timelineGridState = LazyGridState()

    fun setSelectedPhotoType(photoType: PhotoType) {
        viewModelScope.launch(Dispatchers.Default) {
            settingsStore.setSelectedPhotoType(photoType)
        }
    }

    companion object {
        private fun Flow<PagingData<Photo>>.mapPagingPhotos() = map { pagingData ->
            pagingData
                .insertSeparators { before, after ->
                    after ?: return@insertSeparators null

                    computeSeparatorText(before, after)
                }
        }

        @OptIn(ExperimentalTime::class)
        fun computeSeparatorText(before: Photo?, after: Photo): String? {
            val timeZone = TimeZone.currentSystemDefault()
            val beforeDate = before?.let {
                val instant = Instant.fromEpochSeconds(it.timeCreated)
                instant.toLocalDateTime(timeZone)
            }
            val afterDate =
                Instant.fromEpochSeconds(after.timeCreated)
                    .toLocalDateTime(timeZone)

            if (beforeDate != null && beforeDate.month.number == afterDate.month.number && beforeDate.year == afterDate.year)
                return null

            val currentDate =
                Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            return buildDateString(currentDate, afterDate)
        }

        private fun buildDateString(currentDate: LocalDateTime, afterDate: LocalDateTime) =
            buildString {
                append(
                    afterDate.month.toJavaMonth().getDisplayName(
                        TextStyle.FULL,
                        Locale.forLanguageTag("ro-RO")
                    )
                )

                val year = afterDate.year
                if (year != currentDate.year)
                    append(' ').append(year)
            }.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }
}