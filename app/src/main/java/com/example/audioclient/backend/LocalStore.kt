package com.example.audioclient.backend

import android.content.Context
import kotlinx.serialization.json.Json
import androidx.core.content.edit

class LocalStore(context: Context) {
    private val prefs = context.getSharedPreferences("audio_server_player", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun saveLibrary(library: Library) {
        prefs.edit { putString("library", json.encodeToString(library)) }
    }

    fun loadLibrary(): Library? =
        prefs.getString("library", null)?.let {
            runCatching { json.decodeFromString<Library>(it) }.getOrNull()
        }
    fun setServerUrl(url: String) =
        prefs.edit { putString("server_url", url) }

    fun serverUrl(): String =
        prefs.getString("server_url", "http://192.168.1.100:8080")!!

    fun setToken(token: String) =
        prefs.edit { putString("token", token) }

    fun token(): String =
        prefs.getString("token", "")!!
}
