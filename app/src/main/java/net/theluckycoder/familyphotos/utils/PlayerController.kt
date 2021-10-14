package net.theluckycoder.familyphotos.utils

import android.content.Context
import android.net.Uri
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
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

            addListener(object : Player.Listener {
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    _isPlaying.value = playWhenReady

                    val newDuration = contentDuration
                    if (newDuration > 0)
                        _durationState.value = Duration.milliseconds(newDuration)
                }

                override fun onIsLoadingChanged(isLoading: Boolean) {
                    if (!isLoading) {
                        val newDuration = contentDuration
                        if (newDuration > 0)
                            _durationState.value = Duration.milliseconds(newDuration)
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    _playbackState.value = playbackState

                    val newDuration = contentDuration
                    if (newDuration > 0)
                        _durationState.value = Duration.milliseconds(newDuration)

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
