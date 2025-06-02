package net.theluckycoder.familyphotos.ui.composables

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.PlayPauseButtonState
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberPresentationState
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.ui.LocalOkHttpClient

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    sourceUri: Uri,
    showControls: MutableState<Boolean>,
    modifier: Modifier = Modifier,
) = Box(modifier) {

    val context = LocalContext.current
    val lifecycleOwner = rememberUpdatedState(LocalLifecycleOwner.current)
    val okHttpClient = LocalOkHttpClient.current.get()

    val exoPlayer = remember {
        val dataSourceFactory: DataSource.Factory = DefaultDataSource.Factory(
            context,
            OkHttpDataSource.Factory(okHttpClient),
        )

        val mediaItem = MediaItem.Builder()
            .setUri(sourceUri)
            .build()

        val source = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(mediaItem)

        ExoPlayer.Builder(context)
            .setSeekBackIncrementMs(5000)
            .setSeekForwardIncrementMs(5000)
            .build()
            .apply {
                setMediaSource(source)
                prepare()
            }
    }

    val presentationState = rememberPresentationState(exoPlayer)

    PlayerSurface(
        player = exoPlayer,
        surfaceType = SURFACE_TYPE_SURFACE_VIEW,
        modifier = Modifier
            .resizeWithContentScale(
                ContentScale.Fit,
                presentationState.videoSizeDp
            )
            .clickable(
                interactionSource = remember {
                    MutableInteractionSource()
                },
                indication = null,
            ) {
                showControls.value = !showControls.value
            },
    )

    if (presentationState.coverSurface) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black)
        )
    }

    if (showControls.value) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(0.1f))
        ) {
            MinimalControls(exoPlayer, Modifier.align(Alignment.Center))

            PlaybackTimeBar(
                exoPlayer,
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
            )
        }
    }

    DisposableEffect(Unit) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    exoPlayer.pause()
                }

                else -> Unit
            }
        }

        val lifecycle = lifecycleOwner.value.lifecycle
        lifecycle.addObserver(observer)

        onDispose {
            lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun PlayPauseButton(state: PlayPauseButtonState, modifier: Modifier = Modifier) {
    val icon =
        if (state.showPlay) painterResource(R.drawable.ic_video_play) else painterResource(R.drawable.ic_video_pause)

    IconButton(
        onClick = state::onClick,
        modifier = modifier,
        enabled = state.isEnabled
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(30.dp),
            tint = Color.White
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun MinimalControls(player: Player, modifier: Modifier = Modifier) = Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceEvenly,
    verticalAlignment = Alignment.CenterVertically
) {
    val playPauseState = rememberPlayPauseButtonState(player)
    val backgroundModifier = Modifier
        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
    val modifierForIconMutton = backgroundModifier.size(42.dp)

    IconButton(
        onClick = player::seekBack,
        modifier = modifierForIconMutton,
        enabled = playPauseState.isEnabled
    ) {
        Icon(
            painterResource(R.drawable.ic_video_backward_5),
            contentDescription = null,
            tint = Color.White
        )
    }

    PlayPauseButton(playPauseState, backgroundModifier.size(52.dp))

    IconButton(
        onClick = player::seekForward,
        modifier = modifierForIconMutton,
        enabled = playPauseState.isEnabled
    ) {
        Icon(
            painterResource(R.drawable.ic_video_forward_5),
            contentDescription = null,
            tint = Color.White
        )
    }
}

@Composable
private fun PlaybackTimeBar(player: Player, modifier: Modifier = Modifier) = Row(
    modifier = modifier
        .fillMaxWidth()
        .padding(
            vertical = 8.dp,
            horizontal = 16.dp
        ),
    verticalAlignment = Alignment.CenterVertically
) {
    val state = rememberPlayerPositionState(player)

    DefaultTimeBar(
        currentPosition = state.position,
        bufferingPosition = state.currentBufferedPosition,
        duration = state.currentDuration,
        onSeek = { position ->
            state.seekTo(position)
        }
    )
}

@Composable
private fun DefaultTimeBar(
    currentPosition: Long,
    bufferingPosition: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val progressColor = MaterialTheme.colorScheme.primary
    val bufferingColor = progressColor.copy(alpha = 0.5f)
    val thumbColor = progressColor
    val backgroundColor = Color.Gray

    var progressBarSize by remember { mutableStateOf(IntSize.Zero) }

    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }

    val progressFraction = if (duration > 0) currentPosition.toFloat() / duration else 0f
    val buggeredFraction = if (duration > 0) bufferingPosition.toFloat() / duration else 0f

    val thumbPositionFraction = if (isDragging) dragPosition else progressFraction

    val animatedThumbPosition by animateFloatAsState(
        targetValue = thumbPositionFraction,
        animationSpec = if (isDragging) {
            spring(
                dampingRatio = 1f,
                stiffness = 300f
            )
        } else {
            spring(
                dampingRatio = 0.8f,
                stiffness = 150f
            )
        },
        label = "thumbPosition"
    )

    val currentTimeText = formatTime(
        if (isDragging) (thumbPositionFraction * duration).toLong() else currentPosition
    )
    val durationText = remember(duration) { formatTime(duration) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .onSizeChanged { progressBarSize = it }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = { offset ->
                            isDragging = true
                            dragPosition = (offset.x / progressBarSize.width).coerceIn(0f, 1f)

                            tryAwaitRelease()

                            isDragging = false
                            val seekPosition = (dragPosition * duration).toLong()
                            onSeek(seekPosition)
                        }
                    )

                    /*detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            dragPosition = (offset.x / progressBarSize.width).coerceIn(0f, 1f)
                        },
                        onDrag = { change, _ ->
                            dragPosition =
                                (change.position.x / progressBarSize.width).coerceIn(0f, 1f)
                            change.consume()
                        },
                        onDragEnd = {
                            val seekPosition = (dragPosition * duration).toLong()
                            onSeek(seekPosition)
                            isDragging = false
                        },
                        onDragCancel = {
                            isDragging = false
                        }
                    )*/
                }
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(backgroundColor)
            )

            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(buggeredFraction)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(bufferingColor)
            )

            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(animatedThumbPosition)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(progressColor)
            )

            Box(
                modifier = Modifier
                    .offset(
                        x = with(LocalDensity.current) {
                            (animatedThumbPosition * progressBarSize.width - 6.dp.toPx()).coerceAtLeast(
                                0f
                            ).toDp()
                        }
                    )
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(thumbColor)
                    .align(Alignment.CenterStart)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = currentTimeText,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White
            )

            Text(
                text = durationText,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White
            )
        }
    }
}

private fun formatTime(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
