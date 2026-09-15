package com.example.audioclient.backend

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.net.toUri

class AudioServer() {
    private val ramStore = RAMSongStore()

    private fun api(
        serverUrl: String,
        token: String
    ) = AudioServerApi(serverUrl ,token)

    @OptIn(UnstableApi::class)
    suspend fun playSong(
        track: Track,
        serverUrl: String,
        token: String): MediaSource {
        val songId = track.filename
        val data = withContext(Dispatchers.IO) {
            api(serverUrl, token).fetchSong(track)
        }

        // TODO() TMP LOGGING -> REMOVE
        Log.d("AudioServer", "RAM ADD: $songId (${data.size} bytes)")
        // Store file in ram
        ramStore.add(songId,data)

        // TODO() TMP LOGGING -> REMOVE
        Log.d("AudioServer", "RAM COUNT: ${ramStore.size()}")

        // create mediaSource
        return createMediaSource(
            songId = songId,
            audioData = data,
            title = track.title,
            artist = track.artist,
            album = track.album
        )
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
 * RAM
 * -----------------------------------------------------------------------*/

    fun isSongInRam(songId: String): Boolean {
        return ramStore.contains(songId)
    }

    fun getSongFromRam(songId: String): ByteArray? {
        return ramStore.get(songId)
    }

    fun removeSongFromRam(songId: String) {
        // TODO() TMP LOGGING -> REMOVE
        Log.d("AudioServer", "RAM DELETE: $songId")
        ramStore.delete(songId)
    }

    fun ramSongCount(): Int {
        return ramStore.size()
    }

    fun clearRam() {
        // TODO() TMP LOGGING -> REMOVE
        Log.d("AudioServer", "RAM CLEAR: ${ramStore.size()} songs"
        )
        ramStore.clear()
    }
}
