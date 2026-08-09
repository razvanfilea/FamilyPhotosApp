package net.theluckycoder.familyphotos.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.launch
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.ui.LocalSharedTransitionScope
import net.theluckycoder.familyphotos.ui.composables.FoldersGridList
import net.theluckycoder.familyphotos.ui.composables.PhotosList
import net.theluckycoder.familyphotos.ui.viewmodel.LocalPhotoPickerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalPhotoPickerSheet(
    targetFolderId: Long,
    targetFolderName: String,
    onDismiss: () -> Unit,
    viewModel: LocalPhotoPickerViewModel = viewModel(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        contentWindowInsets = { BottomSheetDefaults.windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top) },
        modifier = Modifier.fillMaxSize()
    ) {
        @OptIn(ExperimentalSharedTransitionApi::class)
        SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                LocalPhotoPickerContent(
                    targetFolderId = targetFolderId,
                    targetFolderName = targetFolderName,
                    viewModel = viewModel,
                    sheetState = sheetState,
                    onDismiss = onDismiss
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocalPhotoPickerContent(
    targetFolderId: Long,
    targetFolderName: String,
    viewModel: LocalPhotoPickerViewModel,
    sheetState: SheetState,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedLocalFolder by remember { mutableStateOf<String?>(null) }
    val selectedPhotoIds = remember { mutableStateSetOf<Long>() }

    val activeFolder = selectedLocalFolder

    BackHandler(enabled = activeFolder != null && selectedPhotoIds.isEmpty()) {
        selectedLocalFolder = null
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = {
                        if (activeFolder != null && selectedPhotoIds.isEmpty()) {
                            selectedLocalFolder = null
                        } else {
                            coroutineScope.launch { sheetState.hide() }
                                .invokeOnCompletion { onDismiss() }
                        }
                    }) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            contentDescription = null
                        )
                    }
                },
                title = {
                    val titleText = when {
                        selectedPhotoIds.isNotEmpty() -> pluralStringResource(
                            R.plurals.items_selected,
                            selectedPhotoIds.size,
                            selectedPhotoIds.size
                        )

                        activeFolder != null -> activeFolder
                        else -> stringResource(
                            R.string.title_upload_to,
                            targetFolderName
                        )
                    }

                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        floatingActionButton = {
            AnimatedVisibility(visible = selectedPhotoIds.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = {
                        viewModel.uploadPhotos(
                            selectedPhotoIds = selectedPhotoIds,
                            targetFolderId = targetFolderId,
                            targetFolderName = targetFolderName,
                        )
                        coroutineScope.launch { sheetState.hide() }
                            .invokeOnCompletion { onDismiss() }
                    },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_cloud_upload_outline),
                            contentDescription = null
                        )
                    },
                    text = {
                        Text("${stringResource(R.string.action_upload)} (${selectedPhotoIds.size})")
                    }
                )
            }
        }
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = contentPadding.calculateTopPadding())
        ) {
            AnimatedContent(
                targetState = activeFolder,
                label = "FolderSlideAnimation",
                transitionSpec = {
                    if (targetState != null) {
                        slideInHorizontally { width -> width } + fadeIn() togetherWith
                                slideOutHorizontally { width -> -width } + fadeOut()
                    } else {
                        slideInHorizontally { width -> -width } + fadeIn() togetherWith
                                slideOutHorizontally { width -> width } + fadeOut()
                    }
                }
            ) { activeFolderState ->
                if (activeFolderState == null) {
                    val localFolders by viewModel.localFolders.collectAsState()
                    val sortOrder by viewModel.localFolderSortOrder.collectAsState()

                    FoldersGridList(
                        folders = localFolders,
                        onFolderClick = { folder -> selectedLocalFolder = folder.name },
                        sortOrder = sortOrder,
                        onSortOrderChange = viewModel::setLocalFolderSortOrder,
                        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding())
                    )
                } else {
                    val folderPhotos = remember(activeFolderState) {
                        viewModel.getFolderPhotosPager(activeFolderState)
                    }.collectAsLazyPagingItems()

                    val timelineLayout by remember(activeFolderState) {
                        viewModel.getFolderTimelineLayout(activeFolderState)
                    }.collectAsState()

                    PhotosList(
                        photos = folderPhotos,
                        timelineLayout = timelineLayout,
                        selectedPhotoIds = selectedPhotoIds,
                        modifier = Modifier.fillMaxSize(),
                        openPhoto = { _, photoId -> selectedPhotoIds.add(photoId) }
                    )
                }
            }
        }
    }
}
