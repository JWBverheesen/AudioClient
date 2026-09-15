package com.example.audioclient.ui

import androidx.media3.common.Player

data class PlaybackState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,

    val currentSongId: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,

    val positionMs: Long = 0L,
    val durationMs: Long = 0L,

    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF
)
