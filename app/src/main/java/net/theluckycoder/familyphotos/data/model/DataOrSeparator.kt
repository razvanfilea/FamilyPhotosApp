package net.theluckycoder.familyphotos.data.model

import androidx.paging.compose.LazyPagingItems
import net.theluckycoder.familyphotos.data.model.db.Photo

sealed class DataOrSeparator<T : Photo> {
    class Data<T : Photo>(val data: T) : DataOrSeparator<T>()

    class Separator<T : Photo>(val text: String) : DataOrSeparator<T>()
}

typealias LazyPagingData<T> = LazyPagingItems<DataOrSeparator<T>>