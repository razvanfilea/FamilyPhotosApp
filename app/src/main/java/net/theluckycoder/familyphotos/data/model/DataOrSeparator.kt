package net.theluckycoder.familyphotos.data.model

import androidx.compose.runtime.Immutable
import androidx.paging.compose.LazyPagingItems
import net.theluckycoder.familyphotos.data.model.db.Photo

@Immutable
sealed class DataOrSeparator<T : Photo> {
    @Immutable
    class Data<T : Photo>(val data: T) : DataOrSeparator<T>()

    @Immutable
    class Separator<T : Photo>(val text: String) : DataOrSeparator<T>()
}

typealias LazyPagingData<T> = LazyPagingItems<DataOrSeparator<T>>