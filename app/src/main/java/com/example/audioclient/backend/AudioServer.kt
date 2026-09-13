package com.example.audioclient.backend

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.MediaSession

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import androidx.core.net.toUri
import androidx.media3.common.Player

class AudioServer(context: Context) {
    private val applicationContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }
    private val RAMstore = RAMSongStore()
    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var currentSongId: String ?= null

    private fun api(
        serverUrl: String,
        token: String
    ) = AudioServerApi(serverUrl ,token)

    suspend fun getMetadata(
        serverUrl: String,
        token: String
    ): Library {
        val raw = withContext(Dispatchers.IO) {
            api(serverUrl, token).fetchMetadata()
        }
        return json.decodeFromString<Library>(raw)
    }

    private fun getPlayer(): ExoPlayer {
        player?.let {return it}
        var newPlayer = ExoPlayer.Builder(applicationContext).build()
        player = newPlayer
        mediaSession = MediaSession.Builder(applicationContext, newPlayer).build()
        return newPlayer
    }

    @OptIn(UnstableApi::class)
    suspend fun playSong(
        track: Track,
        serverUrl: String,
        token: String) {
        val data = withContext(Dispatchers.IO) {
            api(serverUrl, token).fetchSong(track)
        }

        val songId = track.filename

        // Store file in ram
        RAMstore.add(songId,data)

        // create mediaSource
        val mediaSource = createMediaSource(
            songId = songId,
            audioData = data,
            title = track.title,
            artist = track.artist,
            album = track.album
        )

        val player = getPlayer()

        // Release the previous media item before replacing it.
        releaseCurrentSong()

        currentSongId = songId

        player.setMediaSource(mediaSource)
        player.prepare()
        player.play()
    }

    @OptIn(UnstableApi::class)
    private fun createMediaSource(
        songId: String,
        audioData: ByteArray,
        title: String,
        artist: String,
        album: String
    ): MediaSource {
        val dataSourceFactory = DataSource.Factory {
            ByteArrayDataSource(audioData)
        }

        val mediaItem = MediaItem.Builder()
            .setMediaId(songId)
            .setUri("ram://$songId".toUri())
            .setMimeType(MimeTypes.AUDIO_OGG)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .build()
            )
            .build()

        return ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
    }

/* -------------------------------------------------------------------------
 * Playback controls
 * -----------------------------------------------------------------------*/

    fun play() {
        getPlayer().play()
    }

    fun pause() {
        player?.pause()
    }

    fun togglePlayPause() {
        val player = getPlayer()
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun seekForward() {
        player?.seekForward()
    }

    fun seekBack() {
        player?.seekBack()
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
    }

    fun skipToNext() {
        player?.seekToNext()
    }

    fun skipToPrevious() {
        player?.seekToPrevious()
    }
/* -------------------------------------------------------------------------
 * Shuffle
 * -----------------------------------------------------------------------*/
fun setShuffleEnabled(enabled: Boolean) {
    player?.shuffleModeEnabled = enabled
}

    fun toggleShuffle(): Boolean {
        val player = getPlayer()
        player.shuffleModeEnabled = !player.shuffleModeEnabled
        return player.shuffleModeEnabled
    }
/* -------------------------------------------------------------------------
 * Repeat
 * -----------------------------------------------------------------------*/
fun setRepeatMode(mode: Int) {
    getPlayer().repeatMode = mode
}

    fun cycleRepeatMode(): Int {
        val player = getPlayer()
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else ->
                Player.REPEAT_MODE_OFF
        }
        return player.repeatMode
    }
/* -------------------------------------------------------------------------
 * State
 * -----------------------------------------------------------------------*/

    fun isPlaying(): Boolean {
        return player?.isPlaying == true
    }

    fun currentPosition(): Long {
        return player?.currentPosition ?: 0L
    }

    fun duration(): Long {
        return player?.duration ?: 0L
    }

    fun shuffleEnabled(): Boolean {
        return player?.shuffleModeEnabled == true
    }

    fun repeatMode(): Int {
        return player?.repeatMode ?: Player.REPEAT_MODE_OFF
    }
/* -------------------------------------------------------------------------
 * RAM
 * -----------------------------------------------------------------------*/

    fun isSongInRam(songId: String): Boolean {
        return RAMstore.contains(songId)
    }

    fun getSongFromRam(songId: String): ByteArray? {
        return RAMstore.get(songId)
    }

    fun removeSongFromRam(songId: String) {
        RAMstore.delete(songId)
    }

    fun ramSongCount(): Int {
        return RAMstore.size()
    }

    private fun releaseCurrentSong() {
        val oldSongId = currentSongId ?: return
        player?.clearMediaItems()
        RAMstore.delete(oldSongId)
        currentSongId = null
    }

    fun release() {
        player?.release()
        player = null
        mediaSession?.release()
        mediaSession = null
        RAMstore.clear()
        currentSongId = null
    }
}
