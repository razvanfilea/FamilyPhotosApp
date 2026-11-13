package net.theluckycoder.familyphotos.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.core.data.model.Photo
import net.theluckycoder.familyphotos.ui.FolderNav
import net.theluckycoder.familyphotos.ui.LocalNavBackStack
import net.theluckycoder.familyphotos.ui.PhotoViewerListNav
import net.theluckycoder.familyphotos.ui.SlideshowNav
import net.theluckycoder.familyphotos.ui.composables.StoryPlayer
import net.theluckycoder.familyphotos.ui.composables.StoryPlayerState
import net.theluckycoder.familyphotos.ui.composables.StoryProgressBar
import net.theluckycoder.familyphotos.ui.composables.StoryTopBar
import net.theluckycoder.familyphotos.ui.composables.rememberStoryPlayerState
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel
import net.theluckycoder.familyphotos.ui.viewmodel.PhotoViewerViewModel
import net.theluckycoder.familyphotos.ui.viewmodel.SlideshowViewModel

/**
 * How many photos on each side of the current one "Go to photo" opens in the viewer:
 * all of a big folder wouldn't fit in the saved state.
 */
private const val GO_TO_PHOTO_RANGE = 100

@Composable
fun SlideshowScreen(
    key: SlideshowNav,
    mainViewModel: MainViewModel,
    slideshowViewModel: SlideshowViewModel = viewModel(),
    photoViewerViewModel: PhotoViewerViewModel = viewModel(),
) {
    val backStack = LocalNavBackStack.current
    val photos by slideshowViewModel.photos.collectAsState()
    val state = rememberStoryPlayerState()

    fun close() {
        // Only this slideshow: a second close during the exit animation must not pop anything else
        if (backStack.lastOrNull() == key) backStack.removeLastOrNull()
    }

    LaunchedEffect(key.source) {
        slideshowViewModel.load(key.source)
    }

    // Nothing left to show, e.g. the photos were deleted meanwhile
    LaunchedEffect(photos) {
        if (photos?.isEmpty() == true) close()
    }

    val slides = photos
    if (slides.isNullOrEmpty()) {
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }

    val title = when (val source = key.source) {
        is SlideshowNav.Source.Folder -> when (val folder = source.folder) {
            FolderNav.Source.Favorites -> stringResource(R.string.title_favorites)
            is FolderNav.Source.Network -> folder.folderName
            is FolderNav.Source.Local -> folder.name
        }

        else -> null
    }

    StoryPlayer(
        slides = slides,
        state = state,
        onClose = ::close,
        // With the photos around it. Back returns to the slideshow.
        onGoToPhoto = { photo ->
            val index = slides.indexOfFirst { it.id == photo.id }.coerceAtLeast(0)
            val from = (index - GO_TO_PHOTO_RANGE).coerceAtLeast(0)
            val to = (index + GO_TO_PHOTO_RANGE + 1).coerceAtMost(slides.size)
            backStack.add(PhotoViewerListNav(slides.subList(from, to).toList(), index - from))
        },
        mainViewModel = mainViewModel,
        photoViewerViewModel = photoViewerViewModel,
        loop = true,
        topBar = { SlideshowTopBar(title, slides, state, onClose = ::close) },
    )
}

/** One bar for the current photo and a counter: too many photos for a segment each. */
@Composable
private fun SlideshowTopBar(
    title: String?,
    slides: List<Photo>,
    state: StoryPlayerState,
    onClose: () -> Unit,
) = StoryTopBar(
    onClose = onClose,
    progress = {
        StoryProgressBar(fill = { state.progress.value }, modifier = Modifier.weight(1f))
    },
    title = {
        val counter = stringResource(R.string.slideshow_counter, state.index + 1, slides.size)
        Column(Modifier.weight(1f)) {
            if (title != null) {
                Text(
                    title,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    counter,
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text(counter, color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    },
)
