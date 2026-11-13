package net.theluckycoder.familyphotos.ui.composables

import android.content.Context
import android.os.Build
import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import net.theluckycoder.familyphotos.core.data.model.Photo
import net.theluckycoder.familyphotos.core.data.model.getScreenUri
import net.theluckycoder.familyphotos.core.data.model.getUri
import net.theluckycoder.familyphotos.core.data.model.isNetwork
import net.theluckycoder.familyphotos.core.data.model.isVideo
import net.theluckycoder.familyphotos.ui.LocalImageLoader
import net.theluckycoder.familyphotos.ui.composables.player.NoSubtitlesExtractorsFactory
import net.theluckycoder.familyphotos.ui.composables.player.NoSubtitlesRenderersFactory
import net.theluckycoder.familyphotos.ui.viewmodel.MainViewModel
import net.theluckycoder.familyphotos.ui.viewmodel.PhotoViewerViewModel

private const val PHOTO_DURATION_MS = 5_000
private const val MAX_VIDEO_DURATION_MS = 15_000L

/** How far a photo zooms in over its slide (the "Ken Burns" effect). */
private const val KEN_BURNS_ZOOM = 0.08f

/** Where a [StoryPlayer] is. The slide and the end are saved; holding and progress aren't. */
@Stable
class StoryPlayerState internal constructor(index: Int, finished: Boolean) {

    var index by mutableIntStateOf(index)
        private set

    /** Past the last slide. Never for players that loop. */
    var finished by mutableStateOf(finished)
        private set

    /** The current slide's progress, from 0 to 1: a new one for each slide and each restart of a slide. */
    var progress by mutableStateOf(Animatable(if (finished) 1f else 0f))
        private set

    /** Pressed and held, which pauses and hides the chrome. */
    var holding by mutableStateOf(false)
        internal set

    internal var dragging by mutableStateOf(false)

    /** Plays slide [index] from its beginning. */
    fun play(index: Int = this.index) {
        finished = false
        this.index = index
        progress = Animatable(0f)
    }

    fun finish() {
        finished = true
    }

    companion object {
        val Saver = listSaver<StoryPlayerState, Any>(
            save = { listOf(it.index, it.finished) },
            restore = { StoryPlayerState(it[0] as Int, it[1] as Boolean) },
        )
    }
}

@Composable
fun rememberStoryPlayerState(): StoryPlayerState =
    rememberSaveable(saver = StoryPlayerState.Saver) { StoryPlayerState(0, false) }

/**
 * Plays [slides] one after another like a story: photos for a few seconds with a slow zoom,
 * videos muted for up to [MAX_VIDEO_DURATION_MS]. Tapping the left third goes back, the rest
 * forward; holding pauses; swiping down closes.
 *
 * @param loop starts over after the last slide instead of finishing
 * @param slideOverlay drawn above the slide, below the controls
 * @param overlay drawn above the controls, below [topBar]
 * @param topBar faded out while holding, like the bottom bar
 */
@Composable
fun StoryPlayer(
    slides: List<Photo>,
    state: StoryPlayerState,
    onClose: () -> Unit,
    onGoToPhoto: (Photo) -> Unit,
    mainViewModel: MainViewModel,
    photoViewerViewModel: PhotoViewerViewModel,
    modifier: Modifier = Modifier,
    loop: Boolean = false,
    slideOverlay: @Composable BoxScope.() -> Unit = {},
    overlay: @Composable BoxScope.() -> Unit = {},
    topBar: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()

    // A restored index can be past the end if the slides changed meanwhile
    LaunchedEffect(slides.size) {
        if (state.index > slides.lastIndex) state.play(slides.lastIndex)
    }

    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val paused = state.holding || state.dragging || state.finished ||
            !lifecycleState.isAtLeast(Lifecycle.State.RESUMED)

    fun goNext() {
        when {
            state.index < slides.lastIndex -> state.play(state.index + 1)
            loop -> state.play(0)
            // Tapping forward at the end closes the player
            state.finished -> onClose()
            else -> state.finish()
        }
    }

    // Videos that failed in a row: when a whole pass fails, there's nothing to show
    var failedInARow by remember(slides) { mutableIntStateOf(0) }
    fun onSlideEnded() {
        failedInARow = 0
        goNext()
    }
    fun onSlideFailed() {
        if (++failedInARow >= slides.size) onClose() else goNext()
    }

    fun goPrevious() {
        if (state.index > 0 && !state.finished) state.play(state.index - 1) else state.play()
    }

    val photo = slides[state.index.coerceIn(slides.indices)]
    val progress = state.progress

    if (!photo.isVideo) {
        LaunchedEffect(progress, paused) {
            if (paused) return@LaunchedEffect
            val remaining = ((1f - progress.value) * PHOTO_DURATION_MS).toInt()
            progress.animateTo(1f, tween(remaining, easing = LinearEasing))
            onSlideEnded()
        }
    }

    // Swipe down to close
    val density = LocalDensity.current
    val dismissThreshold = with(density) { 120.dp.toPx() }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    fun springBack() {
        scope.launch { animate(dragOffset, 0f) { value, _ -> dragOffset = value } }
    }

    val chromeAlpha by animateFloatAsState(if (state.holding) 0f else 1f, label = "chromeAlpha")

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(slides) {
                detectVerticalDragGestures(
                    onDragStart = { state.dragging = true },
                    onDragEnd = {
                        state.dragging = false
                        if (dragOffset > dismissThreshold) onClose() else springBack()
                    },
                    onDragCancel = {
                        state.dragging = false
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
                        state.holding = false
                    },
                    // Having a long press callback keeps the release after a hold from counting as a tap
                    onLongPress = { state.holding = true },
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
        key(photo.id, progress) {
            StorySlide(
                photo = photo,
                slideIndex = state.index,
                progress = progress,
                paused = paused,
                sizePx = sizePx,
                dataSourceFactory = photoViewerViewModel.dataSourceFactory,
                onVideoEnded = ::onSlideEnded,
                onVideoFailed = ::onSlideFailed,
            )
        }

        PreloadNextSlide(slides.getOrNull(state.index + 1) ?: slides.first().takeIf { loop }, sizePx)

        slideOverlay()

        if (!state.finished) {
            StoryBottomBar(
                photo = photo,
                mainViewModel = mainViewModel,
                photoViewerViewModel = photoViewerViewModel,
                onGoToPhoto = { onGoToPhoto(photo) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer { alpha = chromeAlpha },
            )
        }

        overlay()

        Box(
            Modifier
                .align(Alignment.TopCenter)
                .graphicsLayer { alpha = chromeAlpha }
        ) {
            topBar()
        }
    }
}

/**
 * The top of a [StoryPlayer]: [progress] (e.g. [StoryProgressBar]s) above [title] and a close button,
 * on a gradient so they show on any photo.
 */
@Composable
fun StoryTopBar(
    onClose: () -> Unit,
    progress: @Composable RowScope.() -> Unit,
    title: @Composable RowScope.() -> Unit,
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
        content = progress,
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        title()

        IconButton(onClick = onClose) {
            Icon(
                painterResource(R.drawable.ic_close),
                contentDescription = stringResource(R.string.action_close),
                tint = Color.White,
            )
        }
    }
}

/** A thin bar filled to [fill] (0 to 1), read only when drawing. */
@Composable
fun StoryProgressBar(
    fill: () -> Float,
    modifier: Modifier = Modifier,
) = Box(
    modifier
        .height(3.dp)
        .clip(RoundedCornerShape(2.dp))
        .background(Color.White.copy(alpha = 0.35f))
) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = fill()
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
            .background(Color.White)
    )
}

@Composable
private fun StorySlide(
    photo: Photo,
    slideIndex: Int,
    progress: Animatable<Float, AnimationVector1D>,
    paused: Boolean,
    sizePx: Size,
    dataSourceFactory: DataSource.Factory,
    onVideoEnded: () -> Unit,
    onVideoFailed: () -> Unit,
    modifier: Modifier = Modifier,
) = Box(modifier.fillMaxSize()) {
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
            onFailed = onVideoFailed,
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
    photo: Photo,
    sizePx: Size,
    original: Boolean = false,
) = ImageRequest.Builder(context)
    .data(if (original) photo.getUri() else photo.getScreenUri())
    .size(sizePx)
    .crossfade(true)
    .build()

@Composable
private fun PreloadNextSlide(next: Photo?, sizePx: Size) {
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
    photo: Photo,
    dataSourceFactory: DataSource.Factory,
    paused: Boolean,
    progress: Animatable<Float, AnimationVector1D>,
    onEnded: () -> Unit,
    onFailed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val currentOnEnded by rememberUpdatedState(onEnded)
    val currentOnFailed by rememberUpdatedState(onFailed)

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
            override fun onPlayerError(error: PlaybackException) = currentOnFailed()
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
private fun StoryBottomBar(
    photo: Photo,
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

    // Only network photos can be favorites
    if (photo.isNetwork) {
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
    }

    SharePhotoIconButton(
        subtitle = false,
        tint = Color.White,
        getPhotosUris = {
            val ids = longArrayOf(photo.id)
            if (photo.isNetwork) mainViewModel.getNetworkPhotosUriAsync(ids).await()
            else mainViewModel.getLocalPhotosUriAsync(ids).await()
        },
    )
}
