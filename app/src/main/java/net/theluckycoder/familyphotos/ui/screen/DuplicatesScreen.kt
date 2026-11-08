package net.theluckycoder.familyphotos.ui.screen

import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.core.data.model.DuplicateBulkAction
import net.theluckycoder.familyphotos.core.data.model.DuplicateChoice
import net.theluckycoder.familyphotos.core.data.model.DuplicateGroups
import net.theluckycoder.familyphotos.core.data.model.NetworkPhoto
import net.theluckycoder.familyphotos.core.data.model.isPublic
import net.theluckycoder.familyphotos.ui.LocalNavBackStack
import net.theluckycoder.familyphotos.ui.UiMessageType
import net.theluckycoder.familyphotos.ui.composables.NavBackTopAppBar
import net.theluckycoder.familyphotos.ui.composables.TypedSnackbarHost
import net.theluckycoder.familyphotos.ui.composables.TypedSnackbarVisuals
import net.theluckycoder.familyphotos.ui.composables.ZoomableImage
import net.theluckycoder.familyphotos.ui.composables.photoDateText
import net.theluckycoder.familyphotos.ui.dialog.NetworkPhotoInfoDialog
import net.theluckycoder.familyphotos.ui.viewmodel.DuplicatesState
import net.theluckycoder.familyphotos.ui.viewmodel.UtilitiesViewModel

private enum class BulkKind { KeepFamily, RemoveExtra }

@Composable
fun DuplicatesScreen(
    utilitiesViewModel: UtilitiesViewModel = viewModel()
) {
    val backStack = LocalNavBackStack.current
    // Shown above the photo, so it doesn't cover the choices like the app-wide one at the bottom
    val undoSnackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        utilitiesViewModel.loadDuplicates()
    }

    val state by utilitiesViewModel.duplicates.collectAsState()
    val loaded = state as? DuplicatesState.Loaded
    val groups = loaded?.groups.orEmpty()
    val folderNames = loaded?.folderNames.orEmpty()

    val pagerState = rememberPagerState { groups.size }
    var infoPhoto by remember { mutableStateOf<NetworkPhoto?>(null) }
    var bulkDialog by remember { mutableStateOf<BulkKind?>(null) }
    var isResolving by remember { mutableStateOf(false) }
    // The page of a group that an undo has just brought back
    var pageToShow by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(pageToShow, groups.size) {
        val page = pageToShow ?: return@LaunchedEffect
        if (page < groups.size) {
            pagerState.scrollToPage(page)
            pageToShow = null
        }
    }

    val keepFamily = remember(groups) { DuplicateGroups.keepFamilyCopy(groups) }
    val removeExtra = remember(groups) { DuplicateGroups.removeExtraCopies(groups) }
    val totalFreed = remember(groups) { DuplicateGroups.totalFreedBytes(groups) }

    fun resolve(page: Int, choice: DuplicateChoice) {
        val group = groups.getOrNull(page) ?: return
        if (isResolving) return
        isResolving = true
        scope.launch {
            val ids = choice.trash.map { it.id }
            val success = utilitiesViewModel.trashPhotos(ids)
            isResolving = false
            if (!success) return@launch

            utilitiesViewModel.removeDuplicateGroups(group.mapTo(mutableSetOf()) { it.id })
            // Only the latest choice can be undone, instead of queueing behind the previous one
            undoSnackbarHostState.currentSnackbarData?.dismiss()
            val result = undoSnackbarHostState.showSnackbar(
                TypedSnackbarVisuals(
                    message = context.resources.getQuantityString(
                        R.plurals.status_photos_trashed, ids.size, ids.size
                    ),
                    type = UiMessageType.Info,
                    actionLabel = context.getString(R.string.action_undo),
                    duration = SnackbarDuration.Long,
                )
            )
            if (result == SnackbarResult.ActionPerformed &&
                utilitiesViewModel.trashPhotos(ids, trash = false)
            ) {
                utilitiesViewModel.restoreDuplicateGroup(page, group)
                pageToShow = page
            }
        }
    }

    Scaffold(
        topBar = {
            NavBackTopAppBar(
                title = stringResource(R.string.title_duplicates),
                navIconOnClick = {
                    backStack.removeLastOrNull()
                },
                subtitle = if (groups.isEmpty()) null else stringResource(
                    R.string.duplicates_progress,
                    pagerState.currentPage.coerceAtMost(groups.lastIndex) + 1,
                    groups.size
                ),
            )
        }
    ) { paddingValues ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                state is DuplicatesState.Loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )

                state is DuplicatesState.Error -> Text(
                    stringResource(R.string.duplicates_error),
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 20.sp,
                    modifier = Modifier.align(Alignment.Center)
                )

                groups.isEmpty() -> Text(
                    stringResource(R.string.duplicates_none),
                    fontSize = 20.sp,
                    modifier = Modifier.align(Alignment.Center)
                )

                else -> Column(Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            stringResource(
                                R.string.duplicates_total_freeable,
                                Formatter.formatShortFileSize(context, totalFreed)
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (keepFamily.groupCount > 0 || removeExtra.groupCount > 0) {
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (keepFamily.groupCount > 0) {
                                    AssistChip(
                                        onClick = { bulkDialog = BulkKind.KeepFamily },
                                        label = {
                                            Text(
                                                stringResource(
                                                    R.string.duplicates_action_label,
                                                    stringResource(R.string.duplicates_action_family),
                                                    keepFamily.groupCount
                                                )
                                            )
                                        }
                                    )
                                }
                                if (removeExtra.groupCount > 0) {
                                    AssistChip(
                                        onClick = { bulkDialog = BulkKind.RemoveExtra },
                                        label = {
                                            Text(
                                                stringResource(
                                                    R.string.duplicates_action_label,
                                                    stringResource(R.string.duplicates_action_extra),
                                                    removeExtra.groupCount
                                                )
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            key = { page -> groups.getOrNull(page)?.first()?.id ?: page }
                        ) { page ->
                            val group = groups.getOrNull(page) ?: return@HorizontalPager
                            PagerContent(
                                copies = group,
                                folderNames = folderNames,
                                enabled = !isResolving,
                                onChoose = { choice -> resolve(page, choice) },
                                onInfoClick = { infoPhoto = it }
                            )
                        }

                        TypedSnackbarHost(
                            undoSnackbarHostState,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }
                }
            }
        }
    }

    bulkDialog?.let { kind ->
        val action = if (kind == BulkKind.KeepFamily) keepFamily else removeExtra
        BulkTrashDialog(
            title = stringResource(
                if (kind == BulkKind.KeepFamily) R.string.duplicates_action_family
                else R.string.duplicates_action_extra
            ),
            detail = stringResource(
                if (kind == BulkKind.KeepFamily) R.string.duplicates_action_family_detail
                else R.string.duplicates_action_extra_detail
            ),
            action = action,
            onDismissRequest = { bulkDialog = null },
            onConfirm = {
                val success = utilitiesViewModel.trashPhotos(action.trashIds)
                if (success) {
                    utilitiesViewModel.removeDuplicateGroups(
                        action.resolutions.flatMapTo(mutableSetOf()) { r -> r.copies.map { it.id } }
                    )
                    utilitiesViewModel.showTrashed(action.photoCount)
                    bulkDialog = null
                }
            }
        )
    }

    infoPhoto?.let { photo ->
        NetworkPhotoInfoDialog(
            photo = photo,
            onDismissRequest = { infoPhoto = null }
        )
    }
}

@Composable
private fun BulkTrashDialog(
    title: String,
    detail: String,
    action: DuplicateBulkAction,
    onDismissRequest: () -> Unit,
    onConfirm: suspend () -> Unit,
) {
    var isWorking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = { if (!isWorking) onDismissRequest() },
        icon = {
            Icon(painterResource(R.drawable.ic_action_delete), contentDescription = null)
        },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(detail)
                Text(
                    pluralStringResource(
                        R.plurals.duplicates_bulk_confirm,
                        action.photoCount,
                        action.photoCount,
                        Formatter.formatShortFileSize(context, action.freedBytes)
                    ),
                    fontWeight = FontWeight.SemiBold
                )
                Text(stringResource(R.string.duplicates_not_permanent))
            }
        },
        confirmButton = {
            Button(
                enabled = !isWorking,
                onClick = {
                    scope.launch {
                        isWorking = true
                        try {
                            onConfirm()
                        } finally {
                            isWorking = false
                        }
                    }
                }
            ) {
                if (isWorking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(R.string.duplicates_move_to_trash))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest, enabled = !isWorking) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun PagerContent(
    copies: List<NetworkPhoto>,
    folderNames: Map<Long, String>,
    enabled: Boolean,
    onChoose: (DuplicateChoice) -> Unit,
    onInfoClick: (NetworkPhoto) -> Unit,
) = Column(Modifier.fillMaxSize()) {
    val context = LocalContext.current
    val choices = remember(copies) { DuplicateGroups.choices(copies) }
    // A single choice: the copies can't be told apart, so keeping one is all there is to decide
    val keepOne = choices.size == 1

    ZoomableImage(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .clipToBounds(),
        photo = copies.first(),
        showUI = {}
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(stringResource(R.string.duplicates_which_to_keep))
        Text(
            stringResource(
                R.string.duplicates_frees,
                Formatter.formatShortFileSize(context, DuplicateGroups.freedBytes(copies))
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // The suggested copy is selected first, so keeping it is a single tap
        var selected by remember(choices) { mutableStateOf(choices.first()) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            choices.forEachIndexed { index, choice ->
                ChoiceCard(
                    photo = choice.keep,
                    folderNames = folderNames,
                    selected = choice == selected,
                    selectable = !keepOne,
                    suggested = index == 0 && !keepOne,
                    enabled = enabled,
                    onClick = { selected = choice },
                    onInfoClick = { onInfoClick(choice.keep) },
                )
            }
        }

        Button(
            onClick = { onChoose(selected) },
            enabled = enabled,
            modifier = Modifier.padding(top = 4.dp)
        ) {
            Text(stringResource(if (keepOne) R.string.duplicates_keep_one else R.string.duplicates_keep_selected))
        }
    }
}

@Composable
private fun ChoiceCard(
    photo: NetworkPhoto,
    folderNames: Map<Long, String>,
    selected: Boolean,
    selectable: Boolean,
    suggested: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onInfoClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val highlighted = selected && selectable
    val cardModifier = if (selectable) {
        Modifier.selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
    } else {
        Modifier
    }

    OutlinedCard(
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            if (highlighted) 2.dp else 1.dp,
            if (highlighted) colors.primary else colors.outlineVariant
        ),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (highlighted) colors.primary.copy(alpha = 0.08f) else colors.surface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = cardModifier
                .fillMaxWidth()
                .padding(start = if (selectable) 4.dp else 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectable) {
                // The whole card is the radio button, this is only its indicator
                RadioButton(
                    selected = selected,
                    onClick = null,
                    enabled = enabled,
                    modifier = Modifier.padding(start = 8.dp, end = 12.dp)
                )
            }

            Icon(
                painterResource(if (photo.isPublic) R.drawable.ic_family_outline else R.drawable.ic_person_outline),
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.padding(end = 12.dp)
            )

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val folderName = photo.folderId?.let { folderNames[it] }
                    Text(
                        if (folderName.isNullOrEmpty()) stringResource(R.string.photo_detail_no_folder) else folderName,
                        style = MaterialTheme.typography.titleSmall,
                        fontStyle = if (folderName.isNullOrEmpty()) FontStyle.Italic else null,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (suggested) {
                        Text(
                            stringResource(R.string.duplicates_suggested),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onPrimaryContainer,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .background(colors.primaryContainer, MaterialTheme.shapes.small)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    photo.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    // Copies' names often differ only near the end (e.g. "_BURST000_COVER")
                    overflow = TextOverflow.MiddleEllipsis,
                )
                Text(
                    photo.photoDateText(),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }

            IconButton(onClick = onInfoClick) {
                Icon(
                    painter = painterResource(R.drawable.ic_outline_info),
                    contentDescription = stringResource(R.string.action_info)
                )
            }
        }
    }
}
