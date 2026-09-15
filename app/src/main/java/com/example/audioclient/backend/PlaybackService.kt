package com.example.audioclient.backend

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PlaybackService : MediaSessionService() {

    companion object {
        const val COMMAND_PLAY_TRACK = "com.example.audioclient.PLAY_TRACK"
        const val EXTRA_SERVER_URL = "server_url"
        const val EXTRA_TOKEN = "token"

        const val EXTRA_TRACK_FILENAME = "track_filename"
        const val EXTRA_TRACK_TITLE = "track_title"
        const val EXTRA_TRACK_ARTIST = "track_artist"
        const val EXTRA_TRACK_ALBUM = "track_album"
    }
    private var currentSongId: String ?= null
    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession
    private lateinit var audioServer: AudioServer

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val playTrackCommand = SessionCommand(COMMAND_PLAY_TRACK, Bundle.EMPTY)

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

                if (customCommand.customAction != COMMAND_PLAY_TRACK) {
                    return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
                }

                playTrack(args)
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS)
                )
            }
        }

        mediaSession = MediaSession.Builder(applicationContext, player)
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

                    if (player.repeatMode == Player.REPEAT_MODE_ONE) {
                        return
                    }

                    val songId = currentSongId ?: return
                    audioServer.removeSongFromRam(songId)
                    Log.d("AudioServer", "RAM DELETE after playback: $songId"
                    )

                    currentSongId = null
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
                if(oldSongId != null) {
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
