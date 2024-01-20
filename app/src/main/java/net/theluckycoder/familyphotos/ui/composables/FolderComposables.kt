package net.theluckycoder.familyphotos.ui.composables

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshots.SnapshotStateList
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
import net.theluckycoder.familyphotos.ui.VerticallyAnimatedInt

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
                enter = fadeIn(),
                exit = fadeOut(),
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
    modifier: Modifier = Modifier,
    photo: Photo,
    name: String,
    photosCount: Int,
    onClick: () -> Unit,
    content: @Composable (BoxScope.() -> Unit)? = null
) = Column(
    modifier = modifier.padding(8.dp)
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
    selectedIds: SnapshotStateList<Long>,
    appBarActions: @Composable RowScope.() -> Unit,
    itemContent: @Composable LazyGridItemScope.(photo: T) -> Unit
) = Column(Modifier.fillMaxSize()) {
    val navigator = LocalNavigator.currentOrThrow

    TopAppBar(
        navigationIcon = {
            if (selectedIds.isEmpty()) {
                IconButton(onClick = {
                    navigator.pop()
                }) {
                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = null)
                }
            } else {
                IconButton(onClick = {
                    selectedIds.clear()
                }) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = null)
                }
            }
        },
        title = {
            if (selectedIds.isEmpty()) {
                Text(text = folderName)
            } else {
                Row {
                    VerticallyAnimatedInt(targetState = selectedIds.size) { count ->
                        Text("$count ")
                    }

                    Text(stringResource(R.string.action_selected))
                }
            }
        },
        actions = appBarActions,
    )

    val columnCount =
        if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT) 4 else 9

    val gridState = rememberLazyGridState()

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(columnCount),
        modifier = Modifier
            .fillMaxSize(),
    ) {
        items(photosList) { photo ->
            key(photo.id) {
                itemContent(photo)
            }
        }
    }
}
