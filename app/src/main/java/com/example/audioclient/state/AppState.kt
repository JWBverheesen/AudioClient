package com.example.audioclient.state

import com.example.audioclient.backend.Library

data class AppState(
    val error: String? = null,
    val library: Library? = null,
    var loading: Boolean = false,
    val search: String = "",
    val serverUrl: String = "",
    val token: String = ""
) {
    val allTracks get() = library?.allTracks().orEmpty()
}
