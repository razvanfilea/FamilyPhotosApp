package net.theluckycoder.familyphotos.ui.composables

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import androidx.media3.common.listen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun rememberPlayerPositionState(player: Player): PlayerProgressState {
    val state = remember { PlayerProgressState(player) }
    LaunchedEffect(player) {
        state.observe()
    }

    DisposableEffect(player) {
        state.startTracking()
        onDispose {
            state.stopTracking()
        }
    }

    return state
}

class PlayerProgressState(private val player: Player) {
    private var positionUpdateJob: Job? = null
    var position by mutableLongStateOf(player.currentPosition.coerceAtLeast(0))
    var currentDuration by mutableLongStateOf(player.duration.coerceAtLeast(1L))
    var currentBufferedPosition by mutableLongStateOf(0L)

    fun seekTo(position: Long) {
        player.seekTo(position)
    }

    fun startTracking() {
        updatePlayingState()
    }

    fun stopTracking() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    suspend fun observe(): Nothing = player.listen { events ->
        if (events.containsAny(
                Player.EVENT_POSITION_DISCONTINUITY,
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_PLAY_WHEN_READY_CHANGED,
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_TIMELINE_CHANGED,
            )
        ) {
            currentDuration = player.duration.coerceAtLeast(1L)
            updatePosition()
        }

        if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) {
            updatePlayingState()
        }
    }

    private fun updatePlayingState() {
        if (player.isPlaying) {
            startPositionUpdateJob()
        } else {
            positionUpdateJob?.cancel()
            positionUpdateJob = null
        }
    }

    private fun updatePosition() {
        position = player.currentPosition.coerceAtLeast(0)
        currentBufferedPosition = player.bufferedPosition
    }

    private fun startPositionUpdateJob() {
        positionUpdateJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                updatePosition()
                delay(250)
            }
        }
    }
}