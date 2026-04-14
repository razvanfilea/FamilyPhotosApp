package net.theluckycoder.familyphotos.ui.screen

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.ui.LocalNavBackStack
import net.theluckycoder.familyphotos.ui.LocalSnackbarHostState
import net.theluckycoder.familyphotos.ui.PhotoViewerListNav
import net.theluckycoder.familyphotos.ui.composables.NavBackTopAppBar
import net.theluckycoder.familyphotos.ui.composables.PhotoListItem
import net.theluckycoder.familyphotos.ui.viewmodel.UtilitiesViewModel
import java.text.DecimalFormat

@Composable
fun LargeFilesScreen() {
    val viewModel: UtilitiesViewModel = viewModel()
    val backStack = LocalNavBackStack.current

    val largePhotosState = viewModel.largePhotos.collectAsState()
    val largePhotos = largePhotosState.value
    val selectedPhotoIds = remember { mutableStateSetOf<Long>() }

    Scaffold(
        snackbarHost = { SnackbarHost(LocalSnackbarHostState.current) },
    ) { paddingValues ->
        val columnCount =
            if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT) 3 else 6

        LazyVerticalGrid(
            modifier = Modifier.fillMaxSize(),
            columns = GridCells.Fixed(columnCount),
            contentPadding = PaddingValues(bottom = paddingValues.calculateBottomPadding())
        ) {
            item(
                key = "header",
                span = { GridItemSpan(columnCount) },
                contentType = "header"
            ) {
                NavBackTopAppBar(
                    title = stringResource(R.string.title_large_files),
                    navIconOnClick = {
                        backStack.removeLastOrNull()
                    },
                    subtitle = largePhotos.size.takeIf { it != 0 }?.toString(),
                )
            }

            if (largePhotos.isEmpty()) {
                item(
                    span = { GridItemSpan(columnCount) },
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (largePhotosState.value.isEmpty()) {
                            Text(
                                stringResource(R.string.large_files_empty),
                                textAlign = TextAlign.Center,
                                fontSize = 18.sp,
                            )
                        } else {
                            CircularProgressIndicator()
                        }
                    }
                }
                return@LazyVerticalGrid
            }

            items(largePhotos, key = { it.id }) { photo ->
                Box {
                    PhotoListItem(
                        modifier = Modifier
                            .animateItem(fadeInSpec = null, fadeOutSpec = null)
                            .aspectRatio(1f)
                            .padding(0.5.dp),
                        photo = photo,
                        selectedPhotoIds = selectedPhotoIds,
                        openPhoto = {
                            backStack.add(PhotoViewerListNav(listOf(photo)))
                        }
                    )

                    Text(
                        text = formatFileSize(photo.fileSize),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .background(
                                color = Color.Black.copy(alpha = 0.6f),
                                shape = MaterialTheme.shapes.small
                            )
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

private const val SIZE_KB = 1024.0f
private const val SIZE_MB = SIZE_KB * SIZE_KB
private const val SIZE_GB = SIZE_MB * SIZE_KB

private fun formatFileSize(size: Long): String {
    val df = DecimalFormat("0.0")
    return when {
        size < SIZE_GB -> df.format(size / SIZE_MB) + " MB"
        else -> df.format(size / SIZE_GB) + " GB"
    }
}
