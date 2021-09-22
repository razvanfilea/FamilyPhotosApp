package net.theluckycoder.familyphotos.ui.screen

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.GridCells
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.flow.Flow
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.model.Photo

@Composable
fun LazyItemScope.FolderPreviewItem(
    photo: Photo,
    name: String,
    photosCount: Int,
    onClick: () -> Unit,
    content: (@Composable BoxScope.() -> Unit)? = null
) = Column(
    modifier = Modifier.padding(8.dp)
) {
    Box(
        Modifier
            .fillParentMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.DarkGray)
            .clickable(onClick = onClick)
    ) {

        CoilPhoto(
            modifier = Modifier.fillMaxSize(),
            photo = photo,
            contentScale = ContentScale.Crop,
        )

        if (content != null)
            content()
    }

    Text(
        modifier = Modifier.padding(top = 4.dp),
        text = name,
        fontWeight = FontWeight.Bold
    )

    Text(
        modifier = Modifier.padding(bottom = 8.dp),
        text = "$photosCount items",
        fontSize = 14.sp,
        fontWeight = FontWeight.Light
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T : Photo> FolderPhotos(
    folderName: String,
    photosList: List<T>,
    selectedItems: SnapshotStateList<Long>,
    appBarActions: @Composable RowScope.() -> Unit,
    itemContent: @Composable LazyItemScope.(photo: T) -> Unit
) = Column(Modifier.fillMaxSize()) {
    val navigator = LocalNavigator.currentOrThrow

    TopAppBar(
        navigationIcon = {
            if (selectedItems.isEmpty()) {
                IconButton(onClick = {
                    navigator.pop()
                }) {
                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = null)
                }
            } else {
                IconButton(onClick = {
                    selectedItems.clear()
                }) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = null)
                }
            }
        },
        title = {
            if (selectedItems.isEmpty())
                Text(text = folderName)
            else
                Text(text = " ${selectedItems.size} Selected")
        },
        actions = appBarActions,
        elevation = 0.dp,
        backgroundColor = Color.Transparent
    )

    val columnCount =
        if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT) 3 else 8

    LazyVerticalGrid(
        modifier = Modifier.fillMaxSize(),
        cells = GridCells.Fixed(columnCount),
    ) {
        items(photosList) { photo ->
            key(photo.id) {
                itemContent(photo)
            }
        }
    }
}

@Composable
fun LazyItemScope.SimpleSquarePhotoItem(photo: Photo) {
    CoilPhoto(
        modifier = Modifier
            .fillParentMaxWidth()
            .aspectRatio(1f)
            .padding(1.dp),
        photo = photo,
        contentScale = ContentScale.Crop,
    )
}
