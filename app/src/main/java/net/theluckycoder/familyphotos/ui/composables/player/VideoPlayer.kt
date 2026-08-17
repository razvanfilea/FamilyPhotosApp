package net.theluckycoder.familyphotos.ui.composables.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util.getStringForTime
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.compose.material3.Player
import androidx.media3.ui.compose.material3.buttons.MuteButton
import androidx.media3.ui.compose.material3.buttons.PlayPauseButton
import androidx.media3.ui.compose.material3.buttons.SeekBackButton
import androidx.media3.ui.compose.material3.buttons.SeekForwardButton
import androidx.media3.ui.compose.material3.indicator.ProgressSlider
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval

@OptIn(UnstableApi::class, ExperimentalApi::class)
@Composable
fun VideoPlayer(
    sourceUri: Uri,
    dataSourceFactory: DataSource.Factory,
    showControls: MutableState<Boolean>,
    modifier: Modifier = Modifier,
    controlsPadding: PaddingValues = PaddingValues()
) {
    val context = LocalContext.current
    val lifecycleOwner = rememberUpdatedState(LocalLifecycleOwner.current)

    val exoPlayer = remember(sourceUri) {
        val autoAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(sourceUri)
            .build()

        val source = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(mediaItem)

        ExoPlayer.Builder(context)
            .setAudioAttributes(autoAttributes, true)
            .setSeekBackIncrementMs(5000)
            .setSeekForwardIncrementMs(5000)
            .build()
            .apply {
                setMediaSource(source)
                prepare()
            }
    }

    Player(
        player = exoPlayer,
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
        ) {
            showControls.value = !showControls.value
        },
        showControls = showControls.value,
        contentScale = ContentScale.Fit,
        topControls = null,
        centerControls = { player, visible ->
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                if (player != null) {
                    MinimalControls(player)
                }
            }
        },
        bottomControls = { player, visible ->
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                if (player != null) {
                    TimeProgressBar(
                        player = player,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(controlsPadding)
                            .navigationBarsPadding()
                    )
                }
            }
        }
    )

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

@OptIn(UnstableApi::class, ExperimentalApi::class)
@Composable
private fun TimeProgressBar(
    player: Player,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) = Column(
    modifier = modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp),
) {
    val textProgressState = rememberProgressStateWithTickInterval(player, 1000L)
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    var duration by remember { mutableLongStateOf(player.duration.coerceAtLeast(0L)) }
    LaunchedEffect(player.currentTimeline) {
        duration = player.duration.coerceAtLeast(0L)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.size(42.dp))

        val currentMs = if (isDragging) {
            (dragProgress * duration).toLong()
        } else {
            textProgressState.currentPositionMs.coerceAtLeast(0)
        }

        Text(
            text = "${getStringForTime(currentMs)} / ${getStringForTime(duration)}",
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
            textAlign = TextAlign.Start,
            fontFamily = FontFamily.Monospace,
        )

        MuteButton(player)
    }

    ProgressSlider(
        player = player,
        modifier = Modifier.fillMaxWidth(),
        onValueChange = { progress ->
            isDragging = true
            dragProgress = progress
        },
        onValueChangeFinished = {
            isDragging = false
        }
    )
}
