package net.theluckycoder.familyphotos.ui.screen

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.bottomSheet.LocalBottomSheetNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.model.NetworkPhoto
import net.theluckycoder.familyphotos.model.Photo
import net.theluckycoder.familyphotos.model.isVideo
import net.theluckycoder.familyphotos.ui.composables.SelectableItem
import net.theluckycoder.familyphotos.ui.dialog.DeletePhotosDialog
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel

@Composable
fun MemoriesList(
    memoriesList: List<Pair<Int, List<NetworkPhoto>>>
) {
    val navigator = LocalNavigator.currentOrThrow

    LazyRow(Modifier.fillMaxWidth()) {
        item {
            Spacer(Modifier.size(8.dp))
        }

        items(memoriesList) { (yearsAgo, photos) ->
            if (photos.isEmpty()) return@items

            Box(
                Modifier
                    .width(160.dp)
                    .aspectRatio(0.75f)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        navigator.push(PhotoDetailScreen(photos.first(), photos))
                    }
            ) {

                CoilPhoto(
                    modifier = Modifier.fillMaxSize(),
                    photo = photos.first(),
                    contentScale = ContentScale.Crop,
                )

                Text(
                    "$yearsAgo years ago",
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
                )
            }
        }

        item {
            Spacer(Modifier.size(8.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotosList(
    headerContent: (@Composable () -> Unit),
    photosPagingList: Flow<PagingData<Any>>,
    mainViewModel: MainViewModel,
    initialPhotoId: Long = 0L,
    onSaveInitialPhotoId: (Long?) -> Unit = {}
) = Column(Modifier.fillMaxSize()) {
    val navigator = LocalNavigator.currentOrThrow
    val bottomSheetNavigator = LocalBottomSheetNavigator.current

    val photos = photosPagingList.collectAsLazyPagingItems()

    val columnCount =
        if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT) 5 else 10

    val selectedPhotoIds = remember { mutableStateListOf<Long>() }
    val scope = rememberCoroutineScope()

    AnimatedVisibility(
        visible = selectedPhotoIds.isNotEmpty(),
    ) {
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
            actions = {
                IconButton(onClick = {
                    if (selectedPhotoIds.isNotEmpty()) {
                        val items = selectedPhotoIds.toList()

                        scope.launch {
                            val selectedPhotos =
                                items.mapNotNull { mainViewModel.getNetworkPhotoFlow(it).first() }
                            bottomSheetNavigator.show(DeletePhotosDialog(selectedPhotos))
                            selectedPhotoIds.clear()
                        }
                    }
                }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_action_delete),
                        contentDescription = stringResource(R.string.action_delete),
                        tint = Color.White,
                    )
                }

                IconButton(onClick = {
                    if (selectedPhotoIds.isNotEmpty()) {
                        navigator.push(MovePhotosScreen(selectedPhotoIds.toList()))
                    }
                }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_move_folder),
                        contentDescription = stringResource(R.string.action_move),
                        tint = Color.White,
                    )
                }
            },
            elevation = 0.dp,
            backgroundColor = Color.Transparent
        )
    }

    val listState = rememberLazyListState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState
    ) {
        item(-1) {
            headerContent()
        }

        val list = ArrayList<Int>(columnCount)

        for (mainIndex in 0 until photos.itemCount) {
            when (val data = photos.peek(mainIndex)) {
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
                            text = photos[mainIndex] as String,
                            style = MaterialTheme.typography.h4
                        )
                    }
                }
                is Photo -> {
                    list.add(mainIndex)
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

    var restored by remember { mutableStateOf(false) }

    if (!restored) {
        LaunchedEffect(photos.itemSnapshotList.size) {
            if (initialPhotoId != 0L) {
                val index = photos.itemSnapshotList.items
                    .indexOfFirst { (it as? Photo)?.id == initialPhotoId }

                if (index != -1) {
                    listState.scrollToItem(index)
                    restored = true
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            var index = listState.firstVisibleItemIndex

            while ((photos.peek(index) as? Photo?) == null)
                ++index

            onSaveInitialPhotoId((photos.peek(index) as Photo).id)
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
                val isVideo = remember { photo.isVideo }

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
                            navigator.push(PhotoDetailScreen(photo, list))
                        }
                    }
                ) {
                    CoilPhoto(
                        photo = photo,
                        contentScale = ContentScale.Crop,
                    )

                    if (isVideo) {
                        Icon(
                            modifier = Modifier
                                .padding(8.dp)
                                .align(Alignment.TopEnd),
                            painter = painterResource(R.drawable.ic_play_circle_filled),
                            contentDescription = null
                        )
                    }
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
