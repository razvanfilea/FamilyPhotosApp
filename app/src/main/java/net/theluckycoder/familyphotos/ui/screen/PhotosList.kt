package net.theluckycoder.familyphotos.ui.screen

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Parcel
import android.os.Parcelable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.flow.Flow
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.model.NetworkPhoto
import net.theluckycoder.familyphotos.model.Photo
import net.theluckycoder.familyphotos.model.isVideo
import net.theluckycoder.familyphotos.ui.composables.PhotoUtilitiesActions
import net.theluckycoder.familyphotos.ui.composables.SelectableItem
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
                    .width(162.dp)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotosList(
    headerContent: (@Composable () -> Unit),
    photosPagingList: Flow<PagingData<Any>>,
    mainViewModel: MainViewModel,
    initialPhotoId: Long = 0L,
    onSaveInitialPhotoId: (Long?) -> Unit = {}
) = Column(Modifier.fillMaxSize()) {

    val navigator = LocalNavigator.currentOrThrow
    val photos = photosPagingList.collectAsLazyPagingItems()

    val columnCount =
        if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT) 5 else 10

    val selectedPhotoIds = remember { mutableStateListOf<Long>() }

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
                Text("${selectedPhotoIds.size} Selected")
            },
            actions = {
                PhotoUtilitiesActions(NetworkPhoto::class, selectedPhotoIds, mainViewModel)
            },
        )
    }

    val listState = rememberLazyGridState()
    val getCurrentSnapshot = {
        photos.itemSnapshotList.mapNotNull { it as? Photo? }
    }

    LazyVerticalGrid(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        columns = GridCells.Fixed(columnCount)
    ) {
        header(-1) { headerContent() }

        items(
            items = photos,
            key = { if (it is Photo) it.id else it },
            span = { GridItemSpan(if (it is String) columnCount else 1) },
            contentType = { if (it is String) "string" else "photo"}
        ) { item ->
            when (item) {
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
                    val isVideo = remember(item) { item.isVideo }

                    SelectableItem(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .padding(1.5.dp)
                            .background(Color.DarkGray),
                        selected = selectedPhotoIds.contains(item.id),
                        enabled = selectedPhotoIds.isNotEmpty(),
                        onClick = { longPress ->
                            if (selectedPhotoIds.isNotEmpty() || longPress) {
                                if (selectedPhotoIds.contains(item.id))
                                    selectedPhotoIds -= item.id
                                else
                                    selectedPhotoIds += item.id
                            } else {
                                navigator.push(
                                    PhotoDetailScreen(item, getCurrentSnapshot())
                                )
                            }
                        }
                    ) {
                        CoilPhoto(
                            photo = item,
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
            try {
                var index = listState.firstVisibleItemIndex

                while ((photos.peek(index) as? Photo?) == null)
                    ++index

                onSaveInitialPhotoId((photos.peek(index) as Photo).id)
            } catch (e: Exception) {
                // It's really not necessary for this to actually happen
            }
        }
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
        val CREATOR: Parcelable.Creator<PagingPlaceholderKey> =
            object : Parcelable.Creator<PagingPlaceholderKey> {
                override fun createFromParcel(parcel: Parcel) =
                    PagingPlaceholderKey(parcel.readInt())

                override fun newArray(size: Int) = arrayOfNulls<PagingPlaceholderKey?>(size)
            }
    }
}

private fun <T : Any> LazyGridScope.items(
    items: LazyPagingItems<T>,
    key: ((item: T) -> Any)? = null,
    span: ((item: T) -> GridItemSpan)? = null,
    contentType: ((item: T) -> Any)? = null,
    itemContent: @Composable LazyGridItemScope.(value: T?) -> Unit
) {
    items(
        count = items.itemCount,
        key = if (key == null) null else { index ->
            val item = items.peek(index)
            if (item == null) {
                PagingPlaceholderKey(index)
            } else {
                key(item)
            }
        },
        span = if (span == null) null else { index ->
            val item = items.peek(index)
            if (item == null) {
                GridItemSpan(1)
            } else {
                span(item)
            }
        },
        contentType = if (contentType == null) {
            { null }
        } else { index ->
            val item = items.peek(index)
            if (item == null) {
                null
            } else {
                contentType(item)
            }
        }
    ) { index ->
        itemContent(items[index])
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
