package net.theluckycoder.familyphotos.ui.composables.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.material3.buttons.MuteButton
import androidx.media3.ui.compose.material3.buttons.PlayPauseButton
import androidx.media3.ui.compose.material3.buttons.SeekBackButton
import androidx.media3.ui.compose.material3.buttons.SeekForwardButton
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import androidx.media3.ui.compose.state.rememberProgressStateWithTickCount
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import java.util.Locale

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    sourceUri: Uri,
    dataSourceFactory: DataSource.Factory,
    showControls: MutableState<Boolean>,
    modifier: Modifier = Modifier,
    controlsPadding: PaddingValues = PaddingValues()
) = Box(modifier) {

    val context = LocalContext.current
    val lifecycleOwner = rememberUpdatedState(LocalLifecycleOwner.current)

    val exoPlayer = remember(sourceUri) {
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
                .padding(controlsPadding)
        ) {
            MinimalControls(exoPlayer, Modifier.align(Alignment.Center))

            TimeProgressBar2(
                player = exoPlayer,
                modifier = Modifier
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
private fun MinimalControls(player: Player, modifier: Modifier = Modifier) = Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceEvenly,
    verticalAlignment = Alignment.CenterVertically
) {
    val backgroundModifier = Modifier
        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
    val modifierForIconMutton = backgroundModifier.size(42.dp)

    SeekBackButton(player, modifierForIconMutton)

    PlayPauseButton(player, backgroundModifier.size(52.dp))

    SeekForwardButton(player, modifierForIconMutton)
}

@OptIn(UnstableApi::class)
@Composable
private fun TimeProgressBar2(
    player: Player,
    modifier: Modifier = Modifier,
    totalTickCount: Int = 0,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    var ticks by remember(totalTickCount) { mutableIntStateOf(totalTickCount) }
    val barProgressState = rememberProgressStateWithTickCount(player, ticks)
    val textProgressState = rememberProgressStateWithTickInterval(player, 1000L)

    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    val hapticFeedback = LocalHapticFeedback.current

    // Duration caching to prevent UI flicker during seek operations
    var duration by remember { mutableLongStateOf(player.duration.coerceAtLeast(0L)) }
    LaunchedEffect(player.currentTimeline) {
        duration = player.duration.coerceAtLeast(0L)
    }

    val barHeight = 4.dp
    val thumbRadius = 8.dp
    val hitAreaHeight = 48.dp // Taller hit area for better touch accessibility

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.size(48.dp))

            val currentMs =
                if (isDragging) (dragProgress * duration).toLong() else textProgressState.currentPositionMs.coerceAtLeast(
                    0
                )

            // TODO: Maybe switch to TimeText when it properly colors the text
            Text(
                text = "${stringForTime(currentMs)}/${stringForTime(duration)}",
                style = MaterialTheme.typography.labelMedium,
                color = textColor,
                textAlign = TextAlign.Start,
                fontFamily = FontFamily.Monospace,
            )

            MuteButton(player)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(hitAreaHeight)
                .systemGestureExclusion()
                .onSizeChanged { size ->
                    if (totalTickCount == 0 && size.width > 0 && ticks != size.width) {
                        ticks = size.width
                    }
                }
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = if (duration > 0) barProgressState.currentPositionProgress else 0f,
                        range = 0f..1f,
                        steps = 0
                    )
                    // Allow TalkBack users to scrub
                    setProgress { targetValue ->
                        val newPos = (targetValue * duration).toLong()
                        player.seekTo(newPos)
                        true
                    }
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)

                        val change = awaitTouchSlopOrCancellation(down.id) { change, _ ->
                            change.consume()
                        }

                        if (change != null) {
                            // Dragging started
                            isDragging = true
                            val startProgress = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                            dragProgress = startProgress

                            // Handle subsequent drag events
                            drag(change.id) { dragChange ->
                                dragChange.consume()
                                val newProgress = (dragChange.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                dragProgress = newProgress
                            }
                        } else {
                            // It was a Tap
                            val tapProgress = (down.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                            val seekPos = (tapProgress * duration).toLong()
                            player.seekTo(seekPos)
                        }

                        // Gesture finished (Drag or Tap released)
                        if (isDragging) {
                            isDragging = false
                            val seekPos = (dragProgress * duration).toLong()
                            player.seekTo(seekPos)
                        }
                    }
                }
                // D. PERFORMANCE: DrawWithCache
                // By reading state inside onDrawBehind, we skip the Composition/Layout phases entirely
                // and only invalidate the Draw phase when the progress changes (60fps).
                .drawWithCache {
                    val barHeightPx = barHeight.toPx()
                    val thumbRadiusPx = thumbRadius.toPx()
                    val activeThumbRadius = if (isDragging) thumbRadiusPx * 1.5f else thumbRadiusPx
                    val cornerRadius = CornerRadius(barHeightPx / 2)

                    onDrawBehind {
                        // READ STATE HERE (Inside the draw scope)
                        // This ensures only the drawing logic re-runs on tick updates
                        val currentProgress = if (isDragging) dragProgress else barProgressState.currentPositionProgress
                        val bufferedProgress = barProgressState.bufferedPositionProgress

                        val width = size.width
                        val centerY = size.height / 2

                        // 1. Track
                        drawRoundRect(
                            color = trackColor.copy(alpha = 0.5f),
                            topLeft = Offset(0f, centerY - barHeightPx / 2),
                            size = Size(width, barHeightPx),
                            cornerRadius = cornerRadius
                        )

                        // 2. Buffer
                        val safeBuffer = if (bufferedProgress.isNaN()) 0f else bufferedProgress
                        drawRoundRect(
                            color = color.copy(alpha = 0.3f),
                            topLeft = Offset(0f, centerY - barHeightPx / 2),
                            size = Size(width * safeBuffer, barHeightPx),
                            cornerRadius = cornerRadius
                        )

                        // 3. Progress
                        val safeProgress = if (currentProgress.isNaN()) 0f else currentProgress
                        val progressWidth = width * safeProgress
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(0f, centerY - barHeightPx / 2),
                            size = Size(progressWidth, barHeightPx),
                            cornerRadius = cornerRadius
                        )

                        // 4. Thumb
                        drawCircle(
                            color = thumbColor,
                            radius = activeThumbRadius,
                            center = Offset(progressWidth, centerY)
                        )
                    }
                }
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun TimeProgressBar(
    player: Player,
    modifier: Modifier = Modifier,
    totalTickCount: Int = 0,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    // Auto-calculate ticks based on width if totalTickCount is 0 (default)
    var ticks by remember(totalTickCount) { mutableIntStateOf(totalTickCount) }

    val progressState = rememberProgressStateWithTickCount(player, ticks)
    val textProgressState = rememberProgressStateWithTickInterval(player, 1000L)

    // 2. Interaction State
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    // Duration is needed for time text. We can cache it to avoid seeking flicker.
    var duration by remember { mutableLongStateOf(player.duration.coerceAtLeast(0L)) }

    // Update duration periodically or when player changes events (simplified here)
    LaunchedEffect(player.currentTimeline) {
        duration = player.duration.coerceAtLeast(0L)
    }

    // Determine what to show: Drag position or Player position
    val currentProgress = if (isDragging) dragProgress else progressState.currentPositionProgress
    val bufferedProgress = progressState.bufferedPositionProgress

    val barHeight = 4.dp
    val thumbRadius = 8.dp
    val hitAreaHeight = 36.dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.size(48.dp))

            val currentMs =
                if (isDragging) (dragProgress * duration).toLong() else textProgressState.currentPositionMs.coerceAtLeast(
                    0
                )

            Text(
                text = "${stringForTime(currentMs)}/${stringForTime(duration)}",
                style = MaterialTheme.typography.labelMedium,
                color = textColor,
                textAlign = TextAlign.Start,
                fontFamily = FontFamily.Monospace,
            )

            MuteButton(player)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(hitAreaHeight)
                .systemGestureExclusion() // Prevents system gestures (like "back") from interfering with scrubbing
                .onSizeChanged { size ->
                    if (totalTickCount == 0 && size.width > 0) {
                        // Only update if the width actually changed to avoid loop
                        if (ticks != size.width) {
                            ticks = size.width
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            dragProgress = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            isDragging = false
                            val seekPos = (dragProgress * duration).toLong()
                            player.seekTo(seekPos)
                        },
                        onDragCancel = {
                            isDragging = false
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            val delta = dragAmount / size.width.toFloat()
                            dragProgress = (dragProgress + delta).coerceIn(0f, 1f)
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val tappedProgress = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        val seekPos = (tappedProgress * duration).toLong()
                        player.seekTo(seekPos)
                    }
                }
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(hitAreaHeight)
                    .align(Alignment.Center)
            ) {
                val width = size.width

                val centerY = size.height / 2
                val barHeightPx = barHeight.toPx()
                val cornerRadius = CornerRadius(barHeightPx / 2)

                // A. Draw Background Track
                drawRoundRect(
                    color = trackColor.copy(alpha = 0.5f),
                    topLeft = Offset(0f, centerY - barHeightPx / 2),
                    size = Size(width, barHeightPx),
                    cornerRadius = cornerRadius
                )

                // B. Draw Buffer
                val safeBuffer = if (bufferedProgress.isNaN()) 0f else bufferedProgress
                val bufferWidth = width * safeBuffer
                drawRoundRect(
                    color = color.copy(alpha = 0.3f),
                    topLeft = Offset(0f, centerY - barHeightPx / 2),
                    size = Size(bufferWidth, barHeightPx),
                    cornerRadius = cornerRadius
                )

                // C. Draw Played Progress
                val safeProgress = if (currentProgress.isNaN()) 0f else currentProgress
                val progressWidth = width * safeProgress
                drawRoundRect(
                    color = color,
                    topLeft = Offset(0f, centerY - barHeightPx / 2),
                    size = Size(progressWidth, barHeightPx),
                    cornerRadius = cornerRadius
                )

                // D. Draw Thumb
                drawCircle(
                    color = thumbColor,
                    radius = if (isDragging) thumbRadius.toPx() * 1.2f else thumbRadius.toPx(),
                    center = Offset(progressWidth, centerY)
                )
            }
        }
    }
}

private fun stringForTime(timeMs: Long): String {
    val totalSeconds = if (timeMs < 0) 0 else timeMs / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600

    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
