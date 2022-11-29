package net.theluckycoder.familyphotos.ui.screen

import android.content.res.Configuration
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.model.Photo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderFilterTextField(folderNameFilter: String, onFilterChange: (String) -> Unit) {
    OutlinedTextField(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        value = folderNameFilter,
        onValueChange = onFilterChange,
        label = { Text(stringResource(R.string.folder_name)) },
        singleLine = true,
        leadingIcon = {
            Icon(painterResource(R.drawable.ic_search), contentDescription = null)
        },
        trailingIcon = {
            AnimatedVisibility(
                folderNameFilter.isNotEmpty(),
                enter = fadeIn() + expandIn(expandFrom = Alignment.Center),
                exit = fadeOut() + shrinkOut(shrinkTowards = Alignment.Center),
            ) {
                IconButton(onClick = { onFilterChange("") }) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = null)
                }
            }
        }
    )
}

@Composable
fun FolderPreviewItem(
    photo: Photo,
    name: String,
    photosCount: Int,
    onClick: () -> Unit,
    content: @Composable (BoxScope.() -> Unit)? = null
) = Column(
    modifier = Modifier.padding(8.dp)
) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
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
        text = "$photosCount photos",
        fontSize = 14.sp,
        fontWeight = FontWeight.Light
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T : Photo> FolderPhotos(
    folderName: String,
    photosList: List<T>,
    selectedItems: SnapshotStateList<Long>,
    appBarActions: @Composable RowScope.() -> Unit,
    itemContent: @Composable LazyGridItemScope.(photo: T) -> Unit
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
                Text(text = "${selectedItems.size} Selected")
        },
        actions = appBarActions,
    )

    val columnCount =
        if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT) 4 else 9

    LazyVerticalGrid(
        columns = GridCells.Fixed(columnCount),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(photosList) { photo ->
            key(photo.id) {
                itemContent(photo)
            }
        }
    }
}

@Composable
fun SimpleSquarePhoto(photo: Photo) {
    CoilPhoto(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(1.dp),
        photo = photo,
        thumbnail = true,
        contentScale = ContentScale.Crop,
    )
}
