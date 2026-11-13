package net.theluckycoder.familyphotos.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.core.data.model.Memory
import net.theluckycoder.familyphotos.ui.LocalNavBackStack
import net.theluckycoder.familyphotos.ui.MemoryNav
import net.theluckycoder.familyphotos.ui.PhotoViewerListNav
import net.theluckycoder.familyphotos.ui.composables.CoilPhoto
import net.theluckycoder.familyphotos.ui.composables.StoryPlayer
import net.theluckycoder.familyphotos.ui.composables.StoryPlayerState
import net.theluckycoder.familyphotos.ui.composables.StoryProgressBar
import net.theluckycoder.familyphotos.ui.composables.StoryTopBar
import net.theluckycoder.familyphotos.ui.composables.memoryDateText
import net.theluckycoder.familyphotos.ui.composables.rememberStoryPlayerState
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel
import net.theluckycoder.familyphotos.ui.viewmodel.PhotoViewerViewModel
import net.theluckycoder.familyphotos.ui.viewmodel.MemoryStoryViewModel

@Composable
fun MemoryStoryScreen(
    key: MemoryNav,
    mainViewModel: MainViewModel,
    memoryStoryViewModel: MemoryStoryViewModel = viewModel(),
    photoViewerViewModel: PhotoViewerViewModel = viewModel(),
) {
    val backStack = LocalNavBackStack.current
    val state by memoryStoryViewModel.state.collectAsState()

    LaunchedEffect(key) {
        memoryStoryViewModel.load(key)
    }

    LaunchedEffect(state) {
        if (state == MemoryStoryViewModel.State.Missing && backStack.lastOrNull() == key) {
            backStack.removeLastOrNull()
        }
    }

    when (val current = state) {
        is MemoryStoryViewModel.State.Found -> MemoryStory(current.memory, key, mainViewModel, photoViewerViewModel)
        else -> Box(Modifier.fillMaxSize().background(Color.Black))
    }
}

@Composable
private fun MemoryStory(
    memory: Memory,
    key: MemoryNav,
    mainViewModel: MainViewModel,
    photoViewerViewModel: PhotoViewerViewModel,
) {
    val backStack = LocalNavBackStack.current
    val state = rememberStoryPlayerState()

    fun close() {
        // Only this story: a second close during the exit animation must not pop anything else
        if (backStack.lastOrNull() == key) backStack.removeLastOrNull()
    }

    val showTitle = state.index == 0 && !state.finished

    StoryPlayer(
        slides = memory.highlights,
        state = state,
        onClose = ::close,
        // Among all the memory's photos, to see the ones around it. Back returns to the story.
        onGoToPhoto = { photo ->
            val index = memory.allPhotos.indexOfFirst { it.id == photo.id }.coerceAtLeast(0)
            backStack.add(PhotoViewerListNav(memory.allPhotos, index))
        },
        mainViewModel = mainViewModel,
        photoViewerViewModel = photoViewerViewModel,
        slideOverlay = {
            AnimatedVisibility(
                visible = showTitle,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomStart),
            ) {
                MemoryTitle(memory)
            }
        },
        overlay = {
            AnimatedVisibility(visible = state.finished, enter = fadeIn(), exit = fadeOut()) {
                StoryEndCard(
                    memory = memory,
                    onReplay = { state.play(0) },
                    onSeeAll = {
                        close()
                        backStack.add(PhotoViewerListNav(memory.allPhotos))
                    },
                )
            }
        },
        // Above the end card, so that it can still be closed
        topBar = { MemoryTopBar(memory, state, onClose = ::close) },
    )
}

@Composable
private fun MemoryTitle(memory: Memory) = Column(
    Modifier
        .fillMaxWidth()
        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
        .navigationBarsPadding()
        .padding(start = 24.dp, end = 24.dp, top = 64.dp, bottom = 96.dp)
) {
    Text(
        pluralStringResource(R.plurals.years_ago, memory.yearsAgo, memory.yearsAgo),
        color = Color.White,
        fontSize = 40.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 44.sp,
    )
    Text(
        memoryDateText(memory.date),
        color = Color.White.copy(alpha = 0.85f),
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
private fun MemoryTopBar(
    memory: Memory,
    state: StoryPlayerState,
    onClose: () -> Unit,
) = StoryTopBar(
    onClose = onClose,
    progress = {
        memory.highlights.indices.forEach { i ->
            StoryProgressBar(
                fill = {
                    when {
                        state.finished || i < state.index -> 1f
                        i == state.index -> state.progress.value
                        else -> 0f
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }
    },
    title = {
        // The cover's title card already says it
        val showTitle = state.index > 0 || state.finished
        Column(Modifier.weight(1f).graphicsLayer { alpha = if (showTitle) 1f else 0f }) {
            Text(
                pluralStringResource(R.plurals.years_ago, memory.yearsAgo, memory.yearsAgo),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                memoryDateText(memory.date),
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    },
)

@Composable
private fun StoryEndCard(
    memory: Memory,
    onReplay: () -> Unit,
    onSeeAll: () -> Unit,
) = Box(
    Modifier
        .fillMaxSize()
        .background(Color.Black.copy(alpha = 0.75f)),
    contentAlignment = Alignment.Center,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(32.dp),
    ) {
        CoilPhoto(
            photo = memory.cover,
            preview = true,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 120.dp, height = 160.dp)
                .clip(RoundedCornerShape(20.dp)),
        )

        Spacer(Modifier.height(20.dp))

        Text(
            pluralStringResource(R.plurals.years_ago, memory.yearsAgo, memory.yearsAgo),
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            memoryDateText(memory.date),
            color = Color.White.copy(alpha = 0.8f),
        )

        Spacer(Modifier.height(28.dp))

        Button(onClick = onSeeAll) {
            Text(
                pluralStringResource(R.plurals.memory_see_all, memory.allPhotos.size, memory.allPhotos.size)
            )
        }

        Spacer(Modifier.height(8.dp))

        FilledTonalButton(onClick = onReplay) {
            Text(stringResource(R.string.memory_replay))
        }
    }
}
