package net.theluckycoder.familyphotos.utils

import android.content.Context
import android.net.Uri
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class PlayerController(
    private val context: Context
) {

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _playbackState = MutableStateFlow(ExoPlayer.STATE_IDLE)
    val playbackState = _playbackState.asStateFlow()

    private val _durationState = MutableStateFlow(Duration.seconds(1))
    val durationState = _durationState.asStateFlow()

    val currentPositionFlow = flow {
        while (true) {
            emit(Duration.milliseconds(exoPlayer.currentPosition))
            delay(600)
        }
    }.distinctUntilChanged()

    private val exoPlayer by lazy {
        SimpleExoPlayer.Builder(context).build().apply {
            playWhenReady = _isPlaying.value

            addListener(object: Player.Listener {
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    _isPlaying.value = playWhenReady
                    _durationState.value = Duration.milliseconds(duration)
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    _playbackState.value = playbackState
                    _durationState.value = Duration.milliseconds(duration)

                    if (playbackState == ExoPlayer.STATE_ENDED)
                        _isPlaying.value = false
                }
            })
        }
    }

    fun playPause() {
        exoPlayer.playWhenReady = !isPlaying.value
        _isPlaying.value = exoPlayer.playWhenReady

        if (playbackState.value == ExoPlayer.STATE_ENDED)
            exoPlayer.seekTo(0)
    }

    fun prepare(sourceUri: Uri) {
        val dataSourceFactory: DataSource.Factory = DefaultDataSourceFactory(
            context,
            Util.getUserAgent(context, context.packageName)
        )

        val mediaItem = MediaItem.Builder()
            .setUri(sourceUri)
            .build()

        val source = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(mediaItem)

        exoPlayer.setMediaSource(source)
        exoPlayer.prepare()
    }

    fun setPlayerView(playerView: PlayerView) {
        playerView.player = exoPlayer
    }

    fun seekTo(positionMs: Long) = exoPlayer.seekTo(positionMs)

    fun clear() {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        exoPlayer.clearVideoSurface()
        exoPlayer.release()
    }
}
