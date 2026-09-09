package com.example.audioclient.backend

import kotlinx.serialization.Serializable

@Serializable
data class Track(
    val artist: String,
    val album: String,
    val filename: String
) {
    val title: String get() = filename.removeSuffix(".ogg")
}

@Serializable
data class Album(
    val name: String,
    val tracks: List<String>
)

@Serializable
data class Artist(
    val artist: String,
    val albums: List<Album>
)

@Serializable
data class Library(
    val database: List<Artist>
) {
    fun allTracks(): List<Track> =
        database.flatMap { artist ->
            artist.albums.flatMap { album ->
                album.tracks.map {
                    Track(
                        artist = artist.artist,
                        album = album.name,
                        filename = it
                    )
                }
            }
        }
}
