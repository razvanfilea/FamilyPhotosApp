package net.theluckycoder.familyphotos.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.core.data.model.NetworkFolder
import net.theluckycoder.familyphotos.core.data.model.NetworkPhotoThumbnail
import net.theluckycoder.familyphotos.core.data.model.Photo
import net.theluckycoder.familyphotos.core.data.model.db.YearSummary
import net.theluckycoder.familyphotos.ui.FolderNav
import net.theluckycoder.familyphotos.ui.LocalNavBackStack
import net.theluckycoder.familyphotos.ui.PhotoViewerListNav
import net.theluckycoder.familyphotos.ui.composables.CoilPhoto
import net.theluckycoder.familyphotos.ui.composables.PhotoUtilitiesActions
import net.theluckycoder.familyphotos.ui.composables.PhotosList
import net.theluckycoder.familyphotos.ui.composables.PhotosSelectionBar
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel
import net.theluckycoder.familyphotos.ui.viewmodel.SearchViewModel

/** How many photos around the opened one the viewer gets, like the slideshow's "Go to photo". */
private const val OPEN_PHOTO_RANGE = 100

@Composable
fun SearchScreen(
    mainViewModel: MainViewModel,
    viewModel: SearchViewModel = viewModel(),
) {
    val backStack = LocalNavBackStack.current
    val textState = rememberTextFieldState(viewModel.text.value)
    val query by viewModel.query.collectAsState()
    val videosOnly by viewModel.videosOnly.collectAsState()
    val favoritesOnly by viewModel.favoritesOnly.collectAsState()
    val selectedPhotoIds = remember { mutableStateSetOf<Long>() }

    LaunchedEffect(textState) {
        snapshotFlow { textState.text.toString() }.collect(viewModel::setText)
    }
    LaunchedEffect(query) { selectedPhotoIds.clear() }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            SearchInput(
                state = textState,
                onBack = backStack::removeLastOrNull,
                modifier = Modifier.windowInsetsPadding(TopAppBarDefaults.windowInsets),
            )

            FiltersRow(
                videosOnly = videosOnly,
                favoritesOnly = favoritesOnly,
                onVideosOnlyChange = viewModel::setVideosOnly,
                onFavoritesOnlyChange = viewModel::setFavoritesOnly,
            )

            if (query.isEmpty) {
                val years by viewModel.years.collectAsState()
                Suggestions(
                    years = years,
                    onYearClick = { textState.setTextAndPlaceCursorAtEnd(it.toString()) },
                )
            } else {
                val photos = viewModel.photosPager.collectAsLazyPagingItems()
                val timelineLayout by viewModel.timelineLayout.collectAsState()
                val folders by viewModel.folders.collectAsState()
                val gridState by viewModel.gridState.collectAsState()

                PhotosList(
                    photos = photos,
                    gridState = gridState,
                    timelineLayout = timelineLayout,
                    selectedPhotoIds = selectedPhotoIds,
                    headerContent = {
                        if (folders.isNotEmpty()) {
                            FoldersRow(folders)
                        }
                        ResultsCount(
                            count = timelineLayout.totalPhotoCount,
                            loading = photos.loadState.refresh is LoadState.Loading,
                        )
                    },
                    openPhoto = { index, _ -> openPhoto(photos, index)?.let(backStack::add) },
                )
            }
        }

        PhotosSelectionBar(
            selectedPhotoIds = selectedPhotoIds,
            modifier = Modifier
                .wrapContentSize(Alignment.TopCenter)
                .windowInsetsPadding(TopAppBarDefaults.windowInsets)
                .padding(top = 8.dp)
        ) {
            PhotoUtilitiesActions(
                isLocalPhoto = false,
                selectedItems = selectedPhotoIds,
                mainViewModel = mainViewModel
            )
        }
    }
}

/** The results around [index], those already loaded: the viewer takes a list, not a pager. */
private fun openPhoto(photos: LazyPagingItems<NetworkPhotoThumbnail>, index: Int): PhotoViewerListNav? {
    val snapshot = photos.itemSnapshotList
    val photo = snapshot.getOrNull(index) ?: return null
    val around = snapshot
        .subList((index - OPEN_PHOTO_RANGE).coerceAtLeast(0), (index + OPEN_PHOTO_RANGE + 1).coerceAtMost(snapshot.size))
        .filterNotNull()
    return PhotoViewerListNav(around, around.indexOfFirst { it.id == photo.id })
}

/** The timeline's way into the search, above the photo type chips. */
@Composable
fun SearchEntryBar(onClick: () -> Unit, modifier: Modifier = Modifier) = Surface(
    onClick = onClick,
    shape = CircleShape,
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    modifier = modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
        .height(52.dp),
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        Icon(
            painterResource(R.drawable.ic_search),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.search_hint),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchInput(
    state: TextFieldState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val colors = SearchBarDefaults.inputFieldColors()
    val interactionSource = remember { MutableInteractionSource() }

    // Straight to typing when opened, but not when coming back to a search
    LaunchedEffect(Unit) {
        if (state.text.isEmpty()) focusRequester.requestFocus()
    }

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        BasicTextField(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            lineLimits = TextFieldLineLimits.SingleLine,
            textStyle = LocalTextStyle.current.merge(TextStyle(color = colors.focusedTextColor)),
            cursorBrush = SolidColor(colors.cursorColor),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            onKeyboardAction = { keyboard?.hide() },
            interactionSource = interactionSource,
            decorator = TextFieldDefaults.decorator(
                state = state,
                enabled = true,
                lineLimits = TextFieldLineLimits.SingleLine,
                outputTransformation = null,
                interactionSource = interactionSource,
                placeholder = {
                    Text(stringResource(R.string.search_hint), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                leadingIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = null)
                    }
                },
                trailingIcon = {
                    AnimatedVisibility(state.text.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                        IconButton(onClick = { state.clearText() }) {
                            Icon(painterResource(R.drawable.ic_close), contentDescription = null)
                        }
                    }
                },
                colors = colors,
                contentPadding = TextFieldDefaults.contentPaddingWithoutLabel(),
                container = {},
            ),
        )
    }
}

@Composable
private fun FiltersRow(
    videosOnly: Boolean,
    favoritesOnly: Boolean,
    onVideosOnlyChange: (Boolean) -> Unit,
    onFavoritesOnlyChange: (Boolean) -> Unit,
) = Row(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 4.dp),
) {
    FilterChip(
        selected = videosOnly,
        onClick = { onVideosOnlyChange(!videosOnly) },
        label = { Text(stringResource(R.string.search_filter_videos)) },
        leadingIcon = {
            Icon(
                painterResource(R.drawable.ic_video_play),
                contentDescription = null,
                modifier = Modifier.size(FilterChipDefaults.IconSize),
            )
        },
        shape = RoundedCornerShape(16.dp),
    )
    FilterChip(
        selected = favoritesOnly,
        onClick = { onFavoritesOnlyChange(!favoritesOnly) },
        label = { Text(stringResource(R.string.title_favorites)) },
        leadingIcon = {
            Icon(
                painterResource(if (favoritesOnly) R.drawable.ic_star_filled else R.drawable.ic_star_outline),
                contentDescription = null,
                modifier = Modifier.size(FilterChipDefaults.IconSize),
            )
        },
        shape = RoundedCornerShape(16.dp),
    )
}

/** What to search for before typing: for now, the years. */
@Composable
private fun Suggestions(years: List<YearSummary>, onYearClick: (Int) -> Unit) = LazyVerticalGrid(
    columns = GridCells.Adaptive(150.dp),
    contentPadding = PaddingValues(16.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier.fillMaxSize(),
) {
    item(span = { GridItemSpan(maxLineSpan) }) {
        SectionTitle(stringResource(R.string.search_years))
    }
    items(years, key = { it.year }) { year ->
        CoverCard(
            cover = year.cover,
            title = year.year.toString(),
            subtitle = pluralStringResource(R.plurals.items_photos, year.photoCount, year.photoCount),
            onClick = { onYearClick(year.year) },
            modifier = Modifier.aspectRatio(1f),
        )
    }
}

@Composable
private fun FoldersRow(folders: List<NetworkFolder>) {
    val backStack = LocalNavBackStack.current
    SectionTitle(
        stringResource(R.string.section_folders),
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 8.dp),
    ) {
        items(folders, key = { it.id }) { folder ->
            CoverCard(
                cover = folder.getCoverPhoto(),
                title = folder.name,
                subtitle = pluralStringResource(R.plurals.items_photos, folder.count, folder.count),
                onClick = {
                    backStack.add(FolderNav(FolderNav.Source.Network(folder.id, folder.name, folder.count)))
                },
                modifier = Modifier.width(140.dp).aspectRatio(0.8f),
            )
        }
    }
}

@Composable
private fun ResultsCount(count: Int, loading: Boolean) {
    val text = when {
        count > 0 -> pluralStringResource(R.plurals.items_photos, count, count)
        loading -> return
        else -> stringResource(R.string.search_no_results)
    }
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) = Text(
    text,
    style = MaterialTheme.typography.titleMedium,
    fontWeight = FontWeight.SemiBold,
    modifier = modifier.padding(vertical = 8.dp),
)

@Composable
private fun CoverCard(
    cover: Photo,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = Box(
    modifier
        .clip(RoundedCornerShape(20.dp))
        .clickable(onClick = onClick)
) {
    CoilPhoto(
        photo = cover,
        modifier = Modifier.fillMaxSize(),
        preview = true,
        contentScale = ContentScale.Crop,
    )
    Column(
        Modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
            .padding(start = 12.dp, end = 12.dp, top = 24.dp, bottom = 10.dp),
    ) {
        Text(
            title,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            subtitle,
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
