package net.theluckycoder.familyphotos.ui.screen

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.flow.Flow
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.model.Photo
import net.theluckycoder.familyphotos.ui.composables.SelectableItem

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotosList(
    headerContent: (@Composable LazyItemScope.() -> Unit)? = null,
    photosPagingList: Flow<PagingData<Any>>,
) = Column(Modifier.fillMaxSize()) {
    val photos = photosPagingList.collectAsLazyPagingItems()

    val columnCount =
        if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT) 5 else 10
    val listState = rememberLazyListState()

    val selectedPhotoIds = remember { mutableStateListOf<Long>() }

    if (selectedPhotoIds.isNotEmpty()) {
        TopAppBar(
            modifier = Modifier.fillMaxWidth(),
            navigationIcon = {
                IconButton(onClick = {
                    selectedPhotoIds.clear()
                }) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = null)
                }
            },
            title = {
                Text(text = "${selectedPhotoIds.size} Selected")
            },
//            actions = appBarActions,
            elevation = 0.dp,
            backgroundColor = Color.Transparent
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState
    ) {
        if (headerContent != null && selectedPhotoIds.isEmpty()) {
            item("headerContent") {
                headerContent()
            }
        }

        val list = ArrayList<Int>(columnCount)

        for (index in 0 until photos.itemCount) {
            when (val data = photos.peek(index)) {
                is String -> {
                    val listCopy = list.toList()
                    list.clear()
                    if (listCopy.isNotEmpty()) {
                        item(key = (photos.peek(listCopy.first()) as Photo).id) {
                            PhotoRow(
                                columnCount,
                                listCopy.map { photos[it] as Photo },
                                selectedPhotoIds,
                            )
                        }
                    }

                    stickyHeader(key = data) {
                        Text(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colors.background)
                                .padding(
                                    top = 42.dp,
                                    bottom = 16.dp,
                                    start = 16.dp,
                                    end = 16.dp
                                ),
                            text = photos[index] as String,
                            style = MaterialTheme.typography.h4
                        )
                    }
                }
                is Photo -> {
                    list.add(index)
                    if (list.size == columnCount) {
                        val listCopy = list.toList()
                        list.clear()
                        item(key = (photos.peek(listCopy.first()) as Photo).id) {
                            PhotoRow(
                                columnCount,
                                listCopy.map { photos[it] as Photo },
                                selectedPhotoIds,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoRow(
    columnCount: Int,
    list: List<Photo>,
    selectedItems: SnapshotStateList<Long>,
) {
    val navigator = LocalNavigator.currentOrThrow

    Row(Modifier.fillMaxWidth()) {
        list.forEach { photo ->
            key({ photo.id.takeIf { it != 0L } }) {
                SelectableItem(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .padding(1.5.dp)
                        .background(Color.DarkGray),
                    selected = selectedItems.contains(photo.id),
                    enabled = selectedItems.isNotEmpty(),
                    onClick = { longPress ->
                        if (selectedItems.isNotEmpty() || longPress) {
                            if (selectedItems.contains(photo.id))
                                selectedItems -= photo.id
                            else
                                selectedItems += photo.id
                        } else {
                            navigator.push(PhotoDetailScreen(photo))
                        }
                    }
                ) {
                    CoilPhoto(
                        photo = photo,
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
        for (i in list.size until columnCount) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .padding(1.dp)
            )
        }
    }
}
