package net.theluckycoder.familyphotos.ui.screen

import android.content.Context
import android.os.Build
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.compose.material3.Player
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.core.data.model.Memory
import net.theluckycoder.familyphotos.core.data.model.NetworkPhotoThumbnail
import net.theluckycoder.familyphotos.core.data.model.getScreenUri
import net.theluckycoder.familyphotos.core.data.model.getUri
import net.theluckycoder.familyphotos.core.data.model.isVideo
import net.theluckycoder.familyphotos.ui.LocalImageLoader
import net.theluckycoder.familyphotos.ui.LocalNavBackStack
import net.theluckycoder.familyphotos.ui.MemoryNav
import net.theluckycoder.familyphotos.ui.PhotoViewerListNav
import net.theluckycoder.familyphotos.ui.composables.CoilPhoto
import net.theluckycoder.familyphotos.ui.composables.SharePhotoIconButton
import net.theluckycoder.familyphotos.ui.composables.memoryDateText
import net.theluckycoder.familyphotos.ui.composables.photoSharedBounds
import net.theluckycoder.familyphotos.ui.composables.player.NoSubtitlesExtractorsFactory
import net.theluckycoder.familyphotos.ui.composables.player.NoSubtitlesRenderersFactory
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel
import net.theluckycoder.familyphotos.ui.viewmodel.PhotoViewerViewModel
import net.theluckycoder.familyphotos.ui.viewmodel.TimelineViewModel

private const val PHOTO_DURATION_MS = 5_000
private const val MAX_VIDEO_DURATION_MS = 15_000L

/** How far a photo zooms in over its slide (the "Ken Burns" effect). */
private const val KEN_BURNS_ZOOM = 0.08f

@Composable
fun MemoryStoryScreen(
    yearsAgo: Int,
    timelineViewModel: TimelineViewModel,
    mainViewModel: MainViewModel,
    photoViewerViewModel: PhotoViewerViewModel = viewModel(),
) {
    val backStack = LocalNavBackStack.current
    val memories by timelineViewModel.memories.collectAsState()

    // Kept once found: the story mustn't reshuffle while playing (e.g. after favoriting a photo)
    var memory by remember { mutableStateOf<Memory?>(null) }
    LaunchedEffect(memories) {
        val all = memories ?: return@LaunchedEffect
        if (memory != null) return@LaunchedEffect
        memory = all.find { it.yearsAgo == yearsAgo }
        // Gone, e.g. the day changed while the app was in the background
        if (memory == null && backStack.lastOrNull() == MemoryNav(yearsAgo)) {
            backStack.removeLastOrNull()
        }
    }

    val current = memory
    if (current == null) {
        Box(Modifier.fillMaxSize().background(Color.Black))
    } else {
        MemoryStory(current, yearsAgo, mainViewModel, photoViewerViewModel)
    }
}

@Composable
private fun MemoryStory(
    memory: Memory,
    yearsAgo: Int,
    mainViewModel: MainViewModel,
    photoViewerViewModel: PhotoViewerViewModel,
) {
    val backStack = LocalNavBackStack.current
    val slides = memory.highlights
    val scope = rememberCoroutineScope()

    var index by rememberSaveable { mutableIntStateOf(0) }
    var finished by rememberSaveable { mutableStateOf(false) }
    var holding by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    // Bumped to restart the current slide from the beginning
    var restartToken by remember { mutableIntStateOf(0) }

    // A restored index can be past the end if the highlights changed meanwhile
    LaunchedEffect(slides.size) {
        if (index > slides.lastIndex) index = slides.lastIndex
    }

    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val paused = holding || dragging || finished || !lifecycleState.isAtLeast(Lifecycle.State.RESUMED)

    val progress = remember(index, restartToken) { Animatable(if (finished) 1f else 0f) }

    fun close() {
        // Only this story: a second close during the exit animation must not pop anything else
        if (backStack.lastOrNull() == MemoryNav(yearsAgo)) backStack.removeLastOrNull()
    }

    fun goNext() {
        when {
            index < slides.lastIndex -> index++
            // Tapping forward on the end card closes the story
            finished -> close()
            else -> finished = true
        }
    }

    fun goPrevious() {
        when {
            finished -> {
                finished = false
                restartToken++
            }
            index > 0 -> index--
            else -> restartToken++
        }
    }

    val photo = slides[index.coerceIn(slides.indices)]

    if (!photo.isVideo) {
        LaunchedEffect(progress, paused) {
            if (paused) return@LaunchedEffect
            val remaining = ((1f - progress.value) * PHOTO_DURATION_MS).toInt()
            progress.animateTo(1f, tween(remaining, easing = LinearEasing))
            goNext()
        }
    }

    // Swipe down to close
    val density = LocalDensity.current
    val dismissThreshold = with(density) { 120.dp.toPx() }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    fun springBack() {
        scope.launch { animate(dragOffset, 0f) { value, _ -> dragOffset = value } }
    }

    val chromeAlpha by animateFloatAsState(if (holding) 0f else 1f, label = "chromeAlpha")

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(slides) {
                detectVerticalDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = {
                        dragging = false
                        if (dragOffset > dismissThreshold) close() else springBack()
                    },
                    onDragCancel = {
                        dragging = false
                        springBack()
                    },
                    onVerticalDrag = { change, amount ->
                        change.consume()
                        dragOffset = (dragOffset + amount).coerceAtLeast(0f)
                    },
                )
            }
            .pointerInput(slides) {
                detectTapGestures(
                    onPress = {
                        tryAwaitRelease()
                        holding = false
                    },
                    // Having a long press callback keeps the release after a hold from counting as a tap
                    onLongPress = { holding = true },
                    onTap = { offset ->
                        if (offset.x < size.width / 3f) goPrevious() else goNext()
                    },
                )
            }
            .graphicsLayer {
                val fraction = (dragOffset / (size.height.coerceAtLeast(1f))).coerceIn(0f, 1f)
                translationY = dragOffset
                scaleX = 1f - fraction * 0.2f
                scaleY = 1f - fraction * 0.2f
            }
    ) {
        val sizePx = with(density) { Size(maxWidth.roundToPx(), maxHeight.roundToPx()) }

        // Fresh state (e.g. a new video player) for each slide and each restart of a slide
        key(photo.id, restartToken) {
            StorySlide(
                photo = photo,
                slideIndex = index,
                progress = progress,
                paused = paused,
                sizePx = sizePx,
                dataSourceFactory = photoViewerViewModel.dataSourceFactory,
                onVideoEnded = ::goNext,
            )
        }

        PreloadNextSlide(slides.getOrNull(index + 1), sizePx)

        AnimatedVisibility(
            visible = index == 0 && !finished,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            Column(
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
        }

        if (!finished) {
            StoryBottomBar(
                photo = photo,
                mainViewModel = mainViewModel,
                photoViewerViewModel = photoViewerViewModel,
                // Among all the memory's photos, to see the ones around it. Back returns to the story.
                onGoToPhoto = {
                    val index = memory.allPhotos.indexOfFirst { it.id == photo.id }.coerceAtLeast(0)
                    backStack.add(PhotoViewerListNav(memory.allPhotos, index))
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer { alpha = chromeAlpha },
            )
        }

        AnimatedVisibility(visible = finished, enter = fadeIn(), exit = fadeOut()) {
            StoryEndCard(
                memory = memory,
                onReplay = {
                    finished = false
                    index = 0
                    restartToken++
                },
                onSeeAll = {
                    close()
                    backStack.add(PhotoViewerListNav(memory.allPhotos))
                },
            )
        }

        // Above the end card, so that it can still be closed
        StoryTopBar(
            memory = memory,
            index = index,
            finished = finished,
            progress = progress,
            onClose = ::close,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .graphicsLayer { alpha = chromeAlpha },
        )
    }
}

@Composable
private fun StorySlide(
    photo: NetworkPhotoThumbnail,
    slideIndex: Int,
    progress: Animatable<Float, AnimationVector1D>,
    paused: Boolean,
    sizePx: Size,
    dataSourceFactory: DataSource.Factory,
    onVideoEnded: () -> Unit,
) = Box(Modifier.fillMaxSize()) {
    // Blurred, dimmed copy behind photos that don't fill the screen (blur needs Android 12)
    CoilPhoto(
        photo = photo,
        preview = true,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .blur(32.dp)
            .graphicsLayer { alpha = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.5f else 0.25f },
    )

    if (photo.isVideo) {
        StoryVideo(
            photo = photo,
            dataSourceFactory = dataSourceFactory,
            paused = paused,
            progress = progress,
            onEnded = onVideoEnded,
            modifier = Modifier
                .fillMaxSize()
                .photoSharedBounds(photo.id),
        )
    } else {
        val direction = if (slideIndex % 2 == 0) 1f else -1f
        val context = LocalContext.current
        // The original if the server can't make a screen rendition (e.g. a format it can't read)
        var useOriginal by remember(photo.id) { mutableStateOf(false) }
        val request = remember(photo.id, sizePx, useOriginal) {
            storyImageRequest(context, photo, sizePx, useOriginal)
        }

        Box(
            Modifier
                .fillMaxSize()
                .photoSharedBounds(photo.id)
                .graphicsLayer {
                    val scale = 1f + KEN_BURNS_ZOOM * progress.value
                    scaleX = scale
                    scaleY = scale
                    translationX = direction * size.width * 0.02f * progress.value
                }
        ) {
            // Low-res preview first, usually already cached from the timeline
            CoilPhoto(photo = photo, preview = true, modifier = Modifier.fillMaxSize())
            AsyncImage(
                model = request,
                imageLoader = LocalImageLoader.current.get(),
                contentDescription = photo.name,
                modifier = Modifier.fillMaxSize(),
                onError = { useOriginal = true },
            )
        }
    }
}

private fun storyImageRequest(
    context: Context,
    photo: NetworkPhotoThumbnail,
    sizePx: Size,
    original: Boolean = false,
) = ImageRequest.Builder(context)
    .data(if (original) photo.getUri() else photo.getScreenUri())
    .size(sizePx)
    .crossfade(true)
    .build()

@Composable
private fun PreloadNextSlide(next: NetworkPhotoThumbnail?, sizePx: Size) {
    val context = LocalContext.current
    val imageLoader = LocalImageLoader.current
    DisposableEffect(next?.id, sizePx) {
        val request = if (next == null || next.isVideo) null
        else imageLoader.get().enqueue(storyImageRequest(context, next, sizePx))
        // Skipping quickly through slides shouldn't leave full-size downloads queued
        onDispose { request?.dispose() }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun StoryVideo(
    photo: NetworkPhotoThumbnail,
    dataSourceFactory: DataSource.Factory,
    paused: Boolean,
    progress: Animatable<Float, AnimationVector1D>,
    onEnded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val currentOnEnded by rememberUpdatedState(onEnded)

    val player = remember(photo.id) {
        // Same factories as VideoPlayer, so R8 can still strip subtitle support
        val mediaSourceFactory =
            ProgressiveMediaSource.Factory(dataSourceFactory, NoSubtitlesExtractorsFactory)
        ExoPlayer.Builder(context, NoSubtitlesRenderersFactory(context), mediaSourceFactory)
            .build()
            .apply {
                setMediaSource(mediaSourceFactory.createMediaSource(MediaItem.fromUri(photo.getUri())))
                volume = 0f
                prepare()
            }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) currentOnEnded()
            }

            // Skip videos that can't be played rather than getting stuck on them
            override fun onPlayerError(error: PlaybackException) = currentOnEnded()
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player, paused) {
        player.playWhenReady = !paused
    }

    LaunchedEffect(player, paused) {
        if (paused) return@LaunchedEffect
        while (true) {
            val duration = player.duration
            if (duration > 0) {
                val shown = duration.coerceAtMost(MAX_VIDEO_DURATION_MS)
                val position = player.currentPosition
                progress.snapTo((position.toFloat() / shown).coerceIn(0f, 1f))
                // Long videos only get a slide's worth of time
                if (position >= MAX_VIDEO_DURATION_MS) {
                    currentOnEnded()
                    return@LaunchedEffect
                }
            }
            delay(50)
        }
    }

    Player(
        player = player,
        modifier = modifier,
        contentScale = ContentScale.Fit,
        shutter = { CoilPhoto(photo = photo, preview = true, modifier = Modifier.fillMaxSize()) },
        topControls = null,
        centerControls = null,
        bottomControls = null,
        errorOverlay = null,
    )
}

@Composable
private fun StoryTopBar(
    memory: Memory,
    index: Int,
    finished: Boolean,
    progress: Animatable<Float, AnimationVector1D>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) = Column(
    modifier
        .fillMaxWidth()
        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
        .statusBarsPadding()
        .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 16.dp)
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        memory.highlights.indices.forEach { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.35f))
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val fill = when {
                                finished || i < index -> 1f
                                i == index -> progress.value
                                else -> 0f
                            }
                            scaleX = fill
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        }
                        .background(Color.White)
                )
            }
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        // The cover's title card already says it
        val showTitle = index > 0 || finished
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

        IconButton(onClick = onClose) {
            Icon(
                painterResource(R.drawable.ic_close),
                contentDescription = stringResource(R.string.action_close),
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun StoryBottomBar(
    photo: NetworkPhotoThumbnail,
    mainViewModel: MainViewModel,
    photoViewerViewModel: PhotoViewerViewModel,
    onGoToPhoto: () -> Unit,
    modifier: Modifier = Modifier,
) = Row(
    modifier
        .fillMaxWidth()
        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f))))
        .navigationBarsPadding()
        .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    // Translucent, to stand out on any photo without hiding much of it
    OutlinedButton(
        onClick = onGoToPhoto,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Black.copy(alpha = 0.3f),
            contentColor = Color.White,
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
    ) {
        Icon(
            painterResource(R.drawable.ic_image_outline),
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize),
        )
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(stringResource(R.string.memory_go_to_photo))
    }

    Spacer(Modifier.weight(1f))

    val isFavorite by remember(photo.id) { photoViewerViewModel.isNetworkPhotoFavorite(photo.id) }
        .collectAsState(false)

    IconButton(onClick = { photoViewerViewModel.updateFavorite(photo.id, !isFavorite) }) {
        Icon(
            painterResource(if (isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline),
            contentDescription = stringResource(R.string.title_favorites),
            tint = Color.White,
        )
    }

    Spacer(Modifier.width(8.dp))

    SharePhotoIconButton(
        subtitle = false,
        tint = Color.White,
        getPhotosUris = { mainViewModel.getNetworkPhotosUriAsync(longArrayOf(photo.id)).await() },
    )
}

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
