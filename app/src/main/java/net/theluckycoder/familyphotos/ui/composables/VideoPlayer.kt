package net.theluckycoder.familyphotos.ui.composables

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.ConstraintSet
import androidx.constraintlayout.compose.Dimension
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.ui.PlayerView
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import net.theluckycoder.familyphotos.R
import net.theluckycoder.familyphotos.ui.LocalPlayerController
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

object VideoPlayer {

    @Composable
    fun Surface() {
        val playerController = LocalPlayerController.current

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                PlayerView(context).apply {
                    playerController.setPlayerView(this)
                    useController = false
                }
            }
        )
    }

    @Composable
    fun PauseButton(modifier: Modifier = Modifier) {
        val controller = LocalPlayerController.current

        val isPlaying by controller.isPlaying.collectAsState()
        val playbackState by controller.playbackState.collectAsState()

        IconButton(
            onClick = { controller.playPause() },
            modifier = modifier
        ) {
            val iconModifier = Modifier.size(128.dp)

            when (playbackState) {
                ExoPlayer.STATE_BUFFERING -> CircularProgressIndicator()
                else -> {
                    if (isPlaying) {
                        Icon(
                            modifier = iconModifier,
                            painter = painterResource(R.drawable.ic_pause_circle_outline),
                            contentDescription = null
                        )
                    } else {
                        Icon(
                            modifier = iconModifier,
                            painter = painterResource(R.drawable.ic_play_circle_outline),
                            contentDescription = null
                        )
                    }

                }
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    @Composable
    fun Seekbar() = ConstraintLayout(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        constraintSet = ConstraintSet {
            val position = createRefFor("position")
            val seekbar = createRefFor("seekbar")
            val duration = createRefFor("duration")

            constrain(position) {
                top.linkTo(parent.top)
                start.linkTo(parent.start)
                bottom.linkTo(parent.bottom)
            }

            constrain(seekbar) {
                top.linkTo(parent.top)
                start.linkTo(position.end)
                end.linkTo(duration.start)
                bottom.linkTo(parent.bottom)

                height = Dimension.fillToConstraints
                width = Dimension.fillToConstraints
            }

            constrain(duration) {
                top.linkTo(parent.top)
                end.linkTo(parent.end)
                bottom.linkTo(parent.bottom)
            }
        }
    ) {
        val controller = LocalPlayerController.current

        var position by remember { mutableStateOf(Duration.ZERO)}
        val duration by controller.durationState.collectAsState()

        LaunchedEffect(Unit) {
            controller.currentPositionFlow.collectLatest {
                ensureActive()
                position = it
            }
        }

        fun durationToText(duration: Duration) = buildString {
            val hours = duration.inWholeHours
            val minutes = duration.inWholeMinutes % 60
            val seconds = duration.inWholeSeconds % 60

            if (hours != 0L)
                append(hours).append(':')

            if (minutes != 0L)
                append(minutes)
            else
                append("00")
            append(':')

            if (seconds < 10)
                append(0).append(seconds)
            else
                append(seconds)
        }

        Text(
            modifier = Modifier.layoutId("position"),
            text = remember(position) { durationToText(position) }
        )

        Slider(
            modifier = Modifier
                .padding(4.dp)
                .layoutId("seekbar"),
            value = position.inWholeMilliseconds.toFloat(),
            valueRange = 0f..duration.inWholeMilliseconds.toFloat().coerceAtLeast(1f),
            onValueChange = {
                position = Duration.milliseconds(it.roundToLong())
            },
            onValueChangeFinished = {
                controller.seekTo(position.inWholeMilliseconds)
            }
        )

        Text(
            modifier = Modifier.layoutId("duration"),
            text = remember(duration) { durationToText(duration) }
        )
    }
}
