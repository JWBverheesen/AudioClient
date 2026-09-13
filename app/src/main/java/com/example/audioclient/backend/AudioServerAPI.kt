package com.example.audioclient.backend

import android.util.Log
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class AudioServerApi(
    private val baseUrlProvider: String,
    private val tokenProvider: String
) {
    private val client = OkHttpClient()

    fun fetchMetadata(): String {
        val url = normalizedBaseUrl() + "/metadata"
        return getString(url)
    }

    fun fetchSong(track: Track) : ByteArray {
        val url = (normalizedBaseUrl() + "/song").toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("artist", track.artist)
            .addQueryParameter("album", track.album)
            .addQueryParameter("track", track.title)
            .build()
            .toString() + ".ogg"
        Log.d("AudioServer: ", "fetching $url")
        return getByes(url)
    }
    private fun normalizedBaseUrl(): String =
        baseUrlProvider.trim().trimEnd('/')

    private fun buildRequest(url: String): Request {
        val requestBuilder = Request.Builder().url(url).get()
        tokenProvider.trim().takeIf { it.isNotEmpty() }?.let {
            requestBuilder.header("Authorization", "Bearer $it")
        }
        return requestBuilder.build()
    }

    private fun getByes(url: String) : ByteArray {
        Log.d("AudioServer", "REQUEST URL = $url")
        Log.d("AudioServer", "TOKEN PRESENT = ${tokenProvider.isNotBlank()}")

        client.newCall(buildRequest(url)).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${response.message}")
            }
            return response.body?.bytes()
                ?: throw IOException("Server returned an empty response")
        }
    }
    private fun getString(url: String): String {
        Log.d("AudioServer", "REQUEST URL = $url")
        Log.d("AudioServer", "TOKEN PRESENT = ${tokenProvider.isNotBlank()}")

        client.newCall(buildRequest(url)).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${response.message}")
            }
            return response.body?.string()
                ?: throw IOException("Server returned an empty response")
        }
    }
}
