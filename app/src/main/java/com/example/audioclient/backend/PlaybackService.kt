package com.example.audioclient.backend

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class PlaybackService : MediaSessionService() {

    companion object {
        const val COMMAND_PLAY_TRACK = "com.example.audioclient.PLAY_TRACK"
        const val COMMAND_NEXT_TRACK = "com.example.audioclient.NEXT_TRACK"
        const val EXTRA_SERVER_URL = "server_url"
        const val EXTRA_TOKEN = "token"
        const val EXTRA_TRACK_FILENAME = "track_filename"
        const val EXTRA_TRACK_TITLE = "track_title"
        const val EXTRA_TRACK_ARTIST = "track_artist"
        const val EXTRA_TRACK_ALBUM = "track_album"
        const val COMMAND_PLAY_ALBUM = "com.example.audioclient.PLAY_ALBUM"
        const val EXTRA_ALBUM_TRACKS = "album_tracks"
    }
    private var currentSongId: String ?= null
    private var albumTracks: List<Track> = emptyList()
    private var isAdvancingAlbum = false
    private var playbackJob: Job? = null
    private var currentAlbumIndex = 0
    private var albumServerUrl: String = ""
    private var albumToken: String = ""
    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession
    private lateinit var audioServer: AudioServer

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val playTrackCommand = SessionCommand(COMMAND_PLAY_TRACK, Bundle.EMPTY)
    private val nextTrackCommand = SessionCommand(COMMAND_NEXT_TRACK, Bundle.EMPTY)
    private val playAlbumCommand = SessionCommand(COMMAND_PLAY_ALBUM, Bundle.EMPTY)
    private val previousRestartPositionMs = 3_000L
    // Wrapper around exoplayer to properly expose android play/seek/next
    @UnstableApi
    private inner class AlbumForwardingPlayer(
        private val delegate: Player
    ) : ForwardingPlayer(delegate) {
        override fun getAvailableCommands(): Player.Commands {
            val commands = super.getAvailableCommands().buildUpon()
            if (hasNextAlbumTrack()) {
                commands.add(COMMAND_SEEK_TO_NEXT)
            } else {
                commands.remove(COMMAND_SEEK_TO_NEXT)
            }
            // Previous is available while playing an album.
            if (albumTracks.isNotEmpty()) {
                commands.add(COMMAND_SEEK_TO_PREVIOUS)
            }
            return commands.build()
        }

        override fun isCommandAvailable(command: Int): Boolean {
            return when (command) {
                Player.COMMAND_SEEK_TO_NEXT -> hasNextAlbumTrack()
                Player.COMMAND_SEEK_TO_PREVIOUS -> albumTracks.isNotEmpty()
                else -> super.isCommandAvailable(command)
            }
        }

        override fun seekToNext() {
            if (!hasNextAlbumTrack() || isAdvancingAlbum) {
                return
            }

            val nextIndex = currentAlbumIndex + 1

            isAdvancingAlbum = true

            playbackJob?.cancel()
            playbackJob = serviceScope.launch {
                try {
                    playAlbumTrack(nextIndex)
                } finally {
                    isAdvancingAlbum = false
                }
            }
        }

        override fun seekToPrevious() {
            if (albumTracks.isEmpty() || isAdvancingAlbum) {
                return
            }

            if (currentPosition > previousRestartPositionMs) {
                seekTo(0L)
                return
            }

            val previousIndex = currentAlbumIndex - 1
            if (previousIndex < 0) {
                // Already at the first track. Just restart it.
                seekTo(0L)
                return
            }

            isAdvancingAlbum = true

            playbackJob?.cancel()
            playbackJob = serviceScope.launch {
                try {
                    playAlbumTrack(previousIndex)
                } finally {
                    isAdvancingAlbum = false
                }
            }
        }
        private fun hasNextAlbumTrack(): Boolean {
            return albumTracks.isNotEmpty() && currentAlbumIndex + 1 < albumTracks.size
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        audioServer = AudioServer()

        player = ExoPlayer.Builder(applicationContext)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .build()

        val forwardingPlayer = AlbumForwardingPlayer(player)

        val callback = object : MediaSession.Callback {

            @OptIn(UnstableApi::class)
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {

                val sessionCommands =
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                        .buildUpon()
                        .add(playTrackCommand)
                        .add(playAlbumCommand)
                        .add(nextTrackCommand)
                        .build()

                return MediaSession.ConnectionResult
                    .AcceptedResultBuilder(session, controller)
                    .setAvailableSessionCommands(sessionCommands)
                    .build()
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                return when (customCommand.customAction) {
                    COMMAND_PLAY_TRACK -> {
                        playTrack(args)
                        Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                    COMMAND_PLAY_ALBUM -> {
                        playAlbum(args)
                        Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                    COMMAND_NEXT_TRACK -> {
                        playNextTrack()
                        Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
                    }

                    else -> {
                        Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED)
                        )
                    }
                }
            }
        }

        mediaSession = MediaSession.Builder(applicationContext, forwardingPlayer)
            .setCallback(callback)
            .build()

        player.addListener(
            object : Player.Listener {

                override fun onMediaItemTransition(
                    mediaItem: androidx.media3.common.MediaItem?,
                    reason: Int
                ) {
                    // Album queue / RAM cleanup will be handled here
                    // when implement the queue.
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState != Player.STATE_ENDED) {
                        return
                    }

                    // Single-track playback.
                    if (albumTracks.isEmpty()) {
                        Log.d("AudioServer", "Single track finished: $currentSongId")
                        return
                    }

                    // Album playback.
                    if (player.repeatMode == Player.REPEAT_MODE_ONE) {
                        return
                    }

                    // Prevent duplicate STATE_ENDED callbacks from starting multiple downloads of the same track.
                    if (isAdvancingAlbum) {
                        Log.d("AudioServer", "Ignoring duplicate album advancement")
                        return
                    }

                    val nextIndex = currentAlbumIndex + 1
                    if (nextIndex < albumTracks.size) {
                        isAdvancingAlbum = true
                        Log.d("AudioServer", "Album advancing: ${nextIndex + 1}/${albumTracks.size}")

                        playbackJob?.cancel()
                        playbackJob = serviceScope.launch {
                            try {
                                playAlbumTrack(nextIndex)
                            } finally {
                                isAdvancingAlbum = false
                            }
                        }
                    } else {
                        Log.d("AudioServer", "Album finished")

                        val songId = currentSongId
                        if (songId != null) {
                            audioServer.removeSongFromRam(songId)
                        }

                        currentSongId = null
                        albumTracks = emptyList()
                        currentAlbumIndex = 0
                        albumServerUrl = ""
                        albumToken = ""
                        isAdvancingAlbum = false

                        Log.d("AudioServer", "RAM songs after album finished: " + audioServer.ramSongCount())
                    }
                }
            }
        )
    }

    @OptIn(UnstableApi::class)
    private fun playTrack(args: Bundle) {

        val serverUrl = args.getString(EXTRA_SERVER_URL) ?: return
        val token = args.getString(EXTRA_TOKEN) ?: ""
        val filename = args.getString(EXTRA_TRACK_FILENAME) ?: return
        val artist = args.getString(EXTRA_TRACK_ARTIST) ?: ""
        val album = args.getString(EXTRA_TRACK_ALBUM) ?: ""

        val track = Track(
            filename = filename,
            artist = artist,
            album = album
        )

        /* The user explicitly selected a single track. This cancels any album transition
           that might still be downloading a track in the background.*/
        playbackJob?.cancel()

        isAdvancingAlbum = false

        albumTracks = emptyList()
        currentAlbumIndex = 0
        albumServerUrl = ""
        albumToken = ""

        serviceScope.launch {
            try {
                // Download new song
                val mediaSource = audioServer.playSong(
                    track = track,
                    serverUrl = serverUrl,
                    token = token
                )

                val oldSongId = currentSongId
                player.clearMediaItems()
                // Removing the old MediaItem releases the player's reference to its old MediaSource/DataSource.
                if(oldSongId != null && oldSongId != filename) {
                    audioServer.removeSongFromRam(oldSongId)
                }
                currentSongId = filename

                player.setMediaSource(mediaSource)
                player.prepare()
                player.play()

                Log.d("AudioServer", "RAM songs: ${audioServer.ramSongCount()}")

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun playNextTrack() {
        // For now, ext only makes sense for an album.
        // Single-track shuffle will be added later
        if(albumTracks.isEmpty()) {
            Log.d("AudioServer", "Next requested, but no album is being played")
            return
        }

        if(isAdvancingAlbum) {
            Log.d("AudioServer", "Next ignored: album transition already in progress")
            return
        }

        val nextItem = currentAlbumIndex+1
        if(nextItem >= albumTracks.size) {
            Log.d("AudioServer", "Next requested at end of album")
            return
        }
        isAdvancingAlbum = true
        playbackJob?.cancel()
        playbackJob = serviceScope.launch {
            try {
                playAlbumTrack(nextItem)
            } finally {
                isAdvancingAlbum = false
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun playAlbum(args: Bundle) {
        val serverUrl = args.getString(EXTRA_SERVER_URL) ?: return
        val token = args.getString(EXTRA_TOKEN) ?: ""
        val tracksJson = args.getString(EXTRA_ALBUM_TRACKS) ?: return

        val tracks = try {
            Json.decodeFromString<List<Track>>(tracksJson)
        } catch (e: Exception) {
            Log.e("AudioServer", "Failed to decode album tracks", e)
            return
        }

        if (tracks.isEmpty()) {
            return
        }

        /* The user explicitly selected a new album. Cancel any previous song/album
        download so an old operation cannot later replace this album track. */
        playbackJob?.cancel()

        isAdvancingAlbum = false

        albumTracks = tracks
        albumServerUrl = serverUrl
        albumToken = token
        currentAlbumIndex = 0

        playbackJob = serviceScope.launch {
            playAlbumTrack(0)
        }
    }

    @OptIn(UnstableApi::class)
    private suspend fun playAlbumTrack(index: Int) {
        if (index !in albumTracks.indices) {
            return
        }

        val track = albumTracks[index]

        try {
            val mediaSource = audioServer.playSong(
                track = track,
                serverUrl = albumServerUrl,
                token = albumToken
            )

            val oldSongId = currentSongId
            player.clearMediaItems()

            if (oldSongId != null && oldSongId != track.filename) {
                audioServer.removeSongFromRam(oldSongId)
            }

            currentSongId = track.filename
            currentAlbumIndex = index

            player.setMediaSource(mediaSource)
            player.prepare()
            player.play()

            Log.d("AudioServer", "Album track ${index + 1}/${albumTracks.size}: ${track.title}")
        } catch (e: Exception) {
            Log.e("AudioServer", "Failed to play album track: ${track.filename}", e)
        }
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        /*
         * Whether playback should continue after the user swipes
         * the application away can be decided here.
         *
         * For now, leave the default behaviour.
         */
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        Log.d("PlaybackService", "Cleaning up resources")
        serviceScope.coroutineContext.cancel()
        audioServer.clearRam()
        mediaSession.release()
        player.release()

        super.onDestroy()
    }
}
