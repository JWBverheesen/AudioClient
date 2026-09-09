package com.example.audioclient.backend

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class AudioServerApi(
    private val baseUrlProvider: String,
    private val tokenProvider: String
) {
    private val client = OkHttpClient()

    suspend fun fetchMetadata(): String {
        val url = normalizedBaseUrl() + "/metadata"
        return get(url)
    }

    private fun normalizedBaseUrl(): String =
        baseUrlProvider.trim().trimEnd('/')

    private fun get(url: String): String {
        Log.d("AudioServer", "REQUEST URL = $url")
        Log.d("AudioServer", "TOKEN PRESENT = ${tokenProvider.isNotBlank()}")
        val requestBuilder = Request.Builder().url(url).get()
        tokenProvider.trim().takeIf { it.isNotEmpty() }?.let {
            requestBuilder.header("Authorization", "Bearer $it")
        }
        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${response.message}")
            }
            return response.body?.string()
                ?: throw IOException("Server returned an empty response")
        }
    }
}
