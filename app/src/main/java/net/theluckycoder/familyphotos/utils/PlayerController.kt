package net.theluckycoder.familyphotos.utils

import android.content.Context
import android.net.Uri
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ext.okhttp.OkHttpDataSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultDataSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import okhttp3.OkHttpClient
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class PlayerController @Inject constructor(
    @ApplicationContext
    private val context: Context,
    okHttpClient: OkHttpClient,
) {

    private val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _playbackState = MutableStateFlow(ExoPlayer.STATE_IDLE)
    val playbackState = _playbackState.asStateFlow()

    private val _durationState = MutableStateFlow(1.seconds)
    val durationState = _durationState.asStateFlow()

    val currentPositionFlow = flow {
        while (true) {
//            exoPlayer.
            emit(exoPlayer.currentPosition.milliseconds)
            delay(600)
        }
    }.distinctUntilChanged()

    private val exoPlayer by lazy {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = _isPlaying.value

            addListener(object : Player.Listener {
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    _isPlaying.value = playWhenReady

                    val newDuration = contentDuration
                    if (newDuration > 0)
                        _durationState.value = newDuration.milliseconds
                }

                override fun onIsLoadingChanged(isLoading: Boolean) {
                    if (!isLoading) {
                        val newDuration = contentDuration
                        if (newDuration > 0)
                            _durationState.value = newDuration.milliseconds
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    _playbackState.value = playbackState

                    val newDuration = contentDuration
                    if (newDuration > 0)
                        _durationState.value = newDuration.milliseconds

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
        val dataSourceFactory: DataSource.Factory = DefaultDataSource.Factory(
            context,
            dataSourceFactory,
        )

        val mediaItem = MediaItem.Builder()
            .setUri(sourceUri)
            .build()

        val source = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(mediaItem)

        exoPlayer.setMediaSource(source)
        exoPlayer.prepare()
    }

    fun setPlayerView(playerView: StyledPlayerView) {
        playerView.player = exoPlayer
    }

    fun seekTo(positionMs: Long) = exoPlayer.seekTo(positionMs)

    fun reset() {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
    }
}
