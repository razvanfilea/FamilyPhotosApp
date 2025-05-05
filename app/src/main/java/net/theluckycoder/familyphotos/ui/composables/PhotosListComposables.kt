package net.theluckycoder.familyphotos.ui.composables

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Parcel
import android.os.Parcelable
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.flow.Flow
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.model.NetworkPhoto
import net.theluckycoder.familyphotos.model.Photo
import net.theluckycoder.familyphotos.model.isVideo
import net.theluckycoder.familyphotos.ui.VerticallyAnimatedInt
import net.theluckycoder.familyphotos.ui.screen.PhotoScreen
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel

private val PORTRAIT_ZOOM_LEVELS = intArrayOf(4, 5, 7, 10)
private val LANDSCAPE_ZOOM_LEVELS = intArrayOf(8, 10, 14, 20)
val MAX_ZOOM_LEVEL_INDEX = 4

@Composable
fun getZoomColumnCount(zoomIndex: Int): Int {
    val levels = if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT)
        PORTRAIT_ZOOM_LEVELS
    else
        LANDSCAPE_ZOOM_LEVELS

    return levels[zoomIndex]
}

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
                    .width(162.dp)
                    .aspectRatio(0.75f)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        navigator.push(PhotoScreen(photos.first(), PhotoScreen.Source.Memories))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotosList(
    headerContent: (@Composable () -> Unit)? = null,
    memoriesContent: @Composable () -> Unit,
    photosPagingList: Flow<PagingData<Any>>,
    initialPhotoIdState: MutableState<Long?>,
    zoomIndexState: MutableIntState,
) = Column(Modifier.fillMaxSize()) {

    val selectedPhotoIds = remember { mutableStateListOf<Long>() }

    AnimatedVisibility(
        visible = selectedPhotoIds.isNotEmpty(),
        enter = expandVertically(),
        exit = shrinkVertically(),
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
                Row {
                    VerticallyAnimatedInt(targetState = selectedPhotoIds.size) { count ->
                        Text("$count ")
                    }

                    Text(stringResource(R.string.action_selected))
                }
            },
            actions = {
                PhotoUtilitiesActions(NetworkPhoto::class, selectedPhotoIds)
            },
        )
    }

    val navigator = LocalNavigator.currentOrThrow
    val photos = photosPagingList.collectAsLazyPagingItems()
    val columnCount = getZoomColumnCount(zoomIndexState.intValue)
    val listState = rememberLazyGridState()

    LazyVerticalGrid(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .detectZoomIn(MAX_ZOOM_LEVEL_INDEX, zoomIndexState),
        columns = GridCells.Fixed(columnCount)
    ) {
        if (headerContent != null) {
            header(-2) {
                AnimatedVisibility(
                    visible = selectedPhotoIds.isEmpty(),
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    headerContent()
                }
            }
        }

        header(-1) { memoriesContent() }

        items(
            count = photos.itemCount,
            key = photos.itemKey { if (it is Photo) it.id else it },
            span = photos.itemSpan { GridItemSpan(if (it is String) columnCount else 1) },
            contentType = photos.itemContentType { if (it is String) "title" else "photo" }
        ) { index ->

            when (val item = photos[index]) {
                is String -> {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(
                                top = 42.dp,
                                bottom = 16.dp,
                                start = 16.dp,
                                end = 16.dp
                            ),
                        text = item,
                        style = MaterialTheme.typography.headlineLarge
                    )
                }

                is Photo -> {
                    PhotoItem(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .padding(0.5.dp)
                            .animateItem(fadeInSpec = null, fadeOutSpec = null),
                        photo = item,
                        selectedPhotoIds = selectedPhotoIds,
                        navigator = navigator
                    )
                }
            }

        }
    }

    var restored by remember { mutableStateOf(false) }

    if (!restored) {
        LaunchedEffect(photos.itemSnapshotList.size) {
            if (initialPhotoIdState.value != 0L) {
                val index = photos.itemSnapshotList.items
                    .indexOfFirst { (it as? Photo)?.id == initialPhotoIdState.value }

                if (index != -1 && index != 0) {
                    listState.scrollToItem(index)
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                var index = listState.firstVisibleItemIndex

                while ((photos.peek(index) as? Photo?) == null)
                    ++index

                initialPhotoIdState.value = ((photos.peek(index) as Photo).id)
            } catch (e: Exception) {
                Log.w("PhotoList", "Failed to restore state", e)
            }
        }
    }
}

@Composable
private fun PhotoItem(
    modifier: Modifier,
    photo: Photo,
    selectedPhotoIds: SnapshotStateList<Long>,
    navigator: Navigator
) {
    val isVideo = remember(photo) { photo.isVideo }

    SelectablePhoto(
        modifier = modifier,
        inSelectionMode = selectedPhotoIds.isNotEmpty(),
        selected = selectedPhotoIds.contains(photo.id),
        onClick = { navigator.push(PhotoScreen(photo, PhotoScreen.Source.PagedList)) },
        onSelect = { selectedPhotoIds += photo.id },
        onDeselect = { selectedPhotoIds -= photo.id }
    ) {
        CoilPhoto(
            photo = photo,
            preview = true,
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

fun <T : Any> LazyPagingItems<T>.itemSpan(
    span: (LazyGridItemSpanScope.(item: @JvmSuppressWildcards T) -> GridItemSpan)
): LazyGridItemSpanScope.(index: Int) -> GridItemSpan {
    return { index ->
        val item = peek(index)
        if (item == null) GridItemSpan(1) else span(item)
    }
}

@SuppressLint("BanParcelableUsage")
private data class PagingPlaceholderKey(private val index: Int) : Parcelable {
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(index)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object {
        @Suppress("unused")
        @JvmField
        val CREATOR = object : Parcelable.Creator<PagingPlaceholderKey> {
            override fun createFromParcel(parcel: Parcel) =
                PagingPlaceholderKey(parcel.readInt())

            override fun newArray(size: Int) = arrayOfNulls<PagingPlaceholderKey?>(size)
        }
    }
}

private fun LazyGridScope.header(
    key: Any? = null,
    content: @Composable LazyGridItemScope.() -> Unit
) {
    item(
        key = key,
        contentType = "header",
        span = { GridItemSpan(this.maxLineSpan) },
        content = content
    )
}
