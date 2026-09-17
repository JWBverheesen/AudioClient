package com.example.audioclient.ui

import android.app.Application
import android.content.ComponentName
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import com.example.audioclient.state.AppState
import com.example.audioclient.backend.LocalStore
import com.example.audioclient.backend.MetadataServer
import com.example.audioclient.backend.PlaybackService
import com.example.audioclient.backend.Track
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.milliseconds

class AppViewModel(
    app: Application
) : AndroidViewModel(app) {
/* ===============================================
 *  Member variables
 * =============================================== */
    private val store = LocalStore(app)
    private val _state = MutableStateFlow(AppState(
        library = store.loadLibrary(),
        serverUrl = store.serverUrl(),
        token = store.token()
    ))
    val state: StateFlow<AppState> = _state.asStateFlow()
    private var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val metadataServer = MetadataServer()
    // Playback state variables
    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()
    private var positionUpdateJob: Job? = null

    private val json = Json { ignoreUnknownKeys = true }

    init {
        connectToPlaybackService()
    }

    /* ===============================================
     *  MediaController
     * =============================================== */
    private fun connectToPlaybackService() {

        val context = getApplication<Application>()

        try {
            val componentName = ComponentName(context, PlaybackService::class.java)
            val sessionToken = SessionToken(context, componentName)
            val future = MediaController.Builder(context, sessionToken).buildAsync()
            controllerFuture = future

            future.addListener(
                {
                    try {
                        mediaController = future.get()
                        mediaController?.addListener(playbackListener)
                        startPositionUpdates()
                        updatePlaybackState()
                        Log.d("AudioServer", "Connected to PlaybackService")
                    } catch (e: Exception) {
                        Log.e("AudioServer", "Failed to connect to PlaybackService", e)
                        _state.update { it.copy(error = "${e.javaClass.simpleName}: ${e.message}") }
                    }
                },
                ContextCompat.getMainExecutor(context)
            )

        } catch (e: Exception) {
            Log.e("AudioServer", "Failed to connect to PlaybackService", e)
            _state.update { it.copy(error = "${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob =
            viewModelScope.launch {
                while (true) {
                    updatePlaybackState()
                    delay(250L.milliseconds)
                }
            }
    }

    /* ===============================================
     *  State functions
     * =============================================== */

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun setSearch(value: String) {
        _state.value = _state.value.copy(search = value)
    }
    fun setServerUrl(url: String) {
        _state.update{ it.copy(serverUrl = url) }
        store.setServerUrl(url)
    }
    private fun updatePlaybackState() {

        val controller = mediaController ?: return
        val mediaItem = controller.currentMediaItem
        val metadata = mediaItem?.mediaMetadata
        val duration = controller.duration

        _playbackState.value = PlaybackState(
            isPlaying = controller.isPlaying,
            isBuffering = controller.playbackState == Player.STATE_BUFFERING,
            currentSongId = mediaItem?.mediaId,
            title = metadata?.title?.toString(),
            artist = metadata?.artist?.toString(),
            album = metadata?.albumTitle?.toString(),
            positionMs = controller.currentPosition,
            durationMs = if (duration != C.TIME_UNSET && duration >= 0L) { duration } else { 0L },
            shuffleEnabled = controller.shuffleModeEnabled,
            repeatMode = controller.repeatMode
        )
    }
    /* ===============================================
    *  Playback
    * =============================================== */
    fun playTrack(track: Track) {

        val controller = mediaController

        if (controller == null) {
            Log.w("AudioServer", "MediaController is not connected yet")
            _state.update { it.copy(error = "Playback service is not connected") }
            return
        }

        val currentState = _state.value

        _state.update { it.copy(loading = true, error = null) }

        val extras = Bundle().apply {
            putString(PlaybackService.EXTRA_SERVER_URL, currentState.serverUrl)
            putString(PlaybackService.EXTRA_TOKEN, currentState.token)
            putString(PlaybackService.EXTRA_TRACK_FILENAME, track.filename)
            putString(PlaybackService.EXTRA_TRACK_TITLE, track.title)
            putString(PlaybackService.EXTRA_TRACK_ARTIST, track.artist)
            putString(PlaybackService.EXTRA_TRACK_ALBUM, track.album)
        }
        val command = SessionCommand(PlaybackService.COMMAND_PLAY_TRACK, Bundle.EMPTY)
        val future = controller.sendCustomCommand(command, extras)

        future.addListener(
            {
                try {
                    val result = future.get()

                    if (result.resultCode == SessionResult.RESULT_SUCCESS) {
                        _state.update { it.copy(loading = false, error = null) }
                        Log.d("AudioServer", "Playback requested: ${track.title}")
                    } else {
                        _state.update { it.copy(loading = false, error = "Playback service rejected the request")}
                    }

                } catch (e: Exception) {
                    Log.e("AudioServer", "Playback request failed", e)
                    _state.update { it.copy(loading = false, error = "${e.javaClass.simpleName}: ${e.message}")}
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    fun playAlbum(tracks: List<Track>) {
        val controller = mediaController

        if (controller == null) {
            Log.w("AudioServer", "MediaController is not connected yet")
            _state.update { it.copy(error = "Playback service is not connected") }
            return
        }

        if (tracks.isEmpty()) {
            return
        }

        val currentState = _state.value

        val extras = Bundle().apply {
            putString(PlaybackService.EXTRA_SERVER_URL, currentState.serverUrl)
            putString(PlaybackService.EXTRA_TOKEN, currentState.token)
            putString(PlaybackService.EXTRA_ALBUM_TRACKS, json.encodeToString(tracks))
        }

        val command = SessionCommand(PlaybackService.COMMAND_PLAY_ALBUM, Bundle.EMPTY)
        controller.sendCustomCommand(command, extras)

        Log.d("AudioServer", "Requested album playback: ${tracks.first().album}"
        )
    }

    private val playbackListener =
        object : Player.Listener {

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlaybackState()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                updatePlaybackState()
            }

            override fun onMediaItemTransition(
                mediaItem: androidx.media3.common.MediaItem?,
                reason: Int
            ) {
                updatePlaybackState()
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                updatePlaybackState()
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                updatePlaybackState()
            }

            override fun onPlayerError(e: androidx.media3.common.PlaybackException) {
                Log.e("AudioServer", "Playback error: ${e.errorCodeName}", e)
                _state.update { it.copy(error = "${e.errorCodeName}: ${e.message}") }
                updatePlaybackState()
            }
        }

    /* ===============================================
    *  Metadata
    * =============================================== */
    fun refreshMetadata() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val state = _state.value

            runCatching {
                metadataServer.getMetadata(
                    serverUrl = state.serverUrl,
                    token = state.token
                )
            }.onSuccess { library ->
                store.saveLibrary(library)
                _state.update { it.copy(
                    library = library,
                    loading = false,
                    error = null)
                }

            }.onFailure { e ->
                Log.e("AudioServer", "Metadata request failed", e)
                _state.update { it.copy(
                    loading = false,
                    error = "${e.javaClass.simpleName}: ${e.message}")
                }
            }
        }
    }

    /* ===============================================
    *  Playback controls
    * =============================================== */
    fun togglePlayPause() {
        val controller = mediaController?: return
        if(controller.isPlaying) {
            controller.pause()
        } else {
            controller.play()
        }
    }

    fun play() {
        mediaController?.play()
    }

    fun pause() {
        mediaController?.pause()
    }

    fun nextTrack() {
        val controller = mediaController ?: return

        // We use a custom command for next
        val command = SessionCommand(PlaybackService.COMMAND_NEXT_TRACK, Bundle.EMPTY)
        controller.sendCustomCommand(command, Bundle.EMPTY)
    }

    fun previousTrack() {
        val controller = mediaController?:return
        if(controller.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS))
            controller.seekToPrevious()
    }

    fun seekTo(positionMs: Long) {
        val controller = mediaController?:return
        controller.seekTo(positionMs)
    }

    fun toggleShuffle() {
        val controller = mediaController?:return
        controller.shuffleModeEnabled = !controller.shuffleModeEnabled
    }

    fun setShuffleEnabled(enabled: Boolean) {
        mediaController?.shuffleModeEnabled = enabled
    }

    /* ===============================================
    *  Cleanup
    * =============================================== */

    override fun onCleared() {
        Log.d("AudioServer", "ViewModel cleared")

        positionUpdateJob?.cancel()
        positionUpdateJob = null
        mediaController?.removeListener(playbackListener)
        mediaController?.release()
        mediaController = null
        controllerFuture?.let{ MediaController.releaseFuture(it) }
        controllerFuture = null

        super.onCleared()
    }
}