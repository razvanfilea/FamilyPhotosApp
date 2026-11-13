package net.theluckycoder.familyphotos.core.data.repository

import androidx.paging.PagingSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import net.theluckycoder.familyphotos.core.data.local.db.NetworkFoldersDao
import net.theluckycoder.familyphotos.core.data.local.db.NetworkPhotosDao
import net.theluckycoder.familyphotos.core.data.model.NetworkFolder
import net.theluckycoder.familyphotos.core.data.model.NetworkPhotoThumbnail
import net.theluckycoder.familyphotos.core.data.model.PhotoSearchQuery
import net.theluckycoder.familyphotos.core.data.model.db.MonthSummary
import net.theluckycoder.familyphotos.core.data.model.db.YearSummary
import javax.inject.Inject

/** Searches the synced photos, offline: file and folder names, and dates. */
class SearchRepository @Inject internal constructor(
    private val networkPhotosDao: NetworkPhotosDao,
    private val networkFoldersDao: NetworkFoldersDao,
) {

    fun searchPhotosPaged(query: PhotoSearchQuery): PagingSource<Int, NetworkPhotoThumbnail> =
        networkPhotosDao.searchPhotosPaged(
            query.likeText, query.year, query.month, query.videosOnly, query.favoritesOnly
        )

    fun searchMonthSummaries(query: PhotoSearchQuery): Flow<List<MonthSummary>> =
        networkPhotosDao.searchMonthSummaries(
            query.likeText, query.year, query.month, query.videosOnly, query.favoritesOnly
        )

    /** The folders named like the query's text. None without text. */
    fun searchFolders(query: PhotoSearchQuery): Flow<List<NetworkFolder>> =
        if (query.text.isEmpty()) flowOf(emptyList()) else networkFoldersDao.searchFolders(query.likeText)

    fun getYearSummaries(): Flow<List<YearSummary>> = networkPhotosDao.getYearSummaries()
}
