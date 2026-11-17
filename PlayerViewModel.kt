package com.nova.music

import android.app.Application
import android.content.ComponentName
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import java.util.concurrent.Future
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

const val NO_SONG = -1L

/** Lo que la pantalla necesita saber del reproductor (sin la posición, que cambia muy seguido). */
data class PlayerUiState(
    val currentSongId: Long = NO_SONG,
    val isPlaying: Boolean = false,
    val durationMs: Long = 0L,
    val shuffleOn: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
)

class PlayerViewModel(private val app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    // La posición va aparte para que solo se redibuje la barra, no toda la pantalla
    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    private var controllerFuture: Future<out MediaController>? = null
    private var controller: MediaController? = null
    private var tickerJob: Job? = null
    private var uiVisible = false

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            sync(player)
        }
    }

    init {
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                val connected = try {
                    future.get()
                } catch (e: Exception) {
                    null
                }
                if (connected != null) {
                    controller = connected
                    connected.addListener(listener)
                    sync(connected)
                }
            },
            ContextCompat.getMainExecutor(app),
        )
    }

    /** Copia el estado del reproductor a la pantalla. */
    private fun sync(player: Player) {
        val duration = player.duration
        val playing = player.playWhenReady &&
            (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING)

        _state.value = PlayerUiState(
            currentSongId = player.currentMediaItem?.mediaId?.toLongOrNull() ?: NO_SONG,
            isPlaying = playing,
            durationMs = if (duration == C.TIME_UNSET) 0L else duration.coerceAtLeast(0L),
            shuffleOn = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
        )
        _position.value = player.currentPosition.coerceAtLeast(0L)
        updateTicker()
    }

    /** La posición solo se actualiza mientras suena y la app está a la vista, para gastar menos batería. */
    private fun updateTicker() {
        val shouldRun = uiVisible && _state.value.isPlaying
        if (shouldRun) {
            if (tickerJob?.isActive == true) return
            tickerJob = viewModelScope.launch {
                while (isActive) {
                    delay(POSITION_REFRESH_MS)
                    val current = controller ?: break
                    _position.value = current.currentPosition.coerceAtLeast(0L)
                }
            }
        } else {
            tickerJob?.cancel()
            tickerJob = null
        }
    }

    fun setUiVisible(visible: Boolean) {
        uiVisible = visible
        val current = controller
        if (current != null) sync(current) else updateTicker()
    }

    /** Arma la cola con toda tu lista y empieza por la canción elegida. */
    fun playFrom(songs: List<Song>, index: Int) {
        val current = controller ?: return
        if (index !in songs.indices) return
        current.setMediaItems(songs.map(::toMediaItem), index, 0L)
        current.prepare()
        current.play()
    }

    fun togglePlayPause(songs: List<Song>, displayedSongId: Long) {
        val current = controller ?: return
        when {
            current.mediaItemCount == 0 -> {
                val index = songs.indexOfFirst { it.id == displayedSongId }.coerceAtLeast(0)
                playFrom(songs, index)
            }
            _state.value.isPlaying -> current.pause()
            else -> {
                if (current.playbackState == Player.STATE_IDLE) current.prepare()
                if (current.playbackState == Player.STATE_ENDED) current.seekToDefaultPosition()
                current.play()
            }
        }
    }

    fun next() {
        controller?.seekToNext()
    }

    fun previous() {
        controller?.seekToPrevious()
    }

    fun seekToFraction(fraction: Float) {
        val current = controller ?: return
        val duration = _state.value.durationMs
        if (duration <= 0L) return
        val target = (duration * fraction.coerceIn(0f, 1f)).toLong()
        current.seekTo(target)
        _position.value = target
    }

    fun toggleShuffle() {
        val current = controller ?: return
        current.shuffleModeEnabled = !current.shuffleModeEnabled
    }

    /** Apagado → repetir todo → repetir una. */
    fun cycleRepeat() {
        val current = controller ?: return
        current.repeatMode = when (current.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    private fun toMediaItem(song: Song): MediaItem =
        MediaItem.Builder()
            .setMediaId(song.id.toString())
            .setUri(song.uri)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(song.uri).build())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .build()
            )
            .build()

    override fun onCleared() {
        tickerJob?.cancel()
        controller?.removeListener(listener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        controllerFuture = null
        super.onCleared()
    }

    private companion object {
        const val POSITION_REFRESH_MS = 250L
    }
}
