package com.example.audioclient.backend

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class AudioServer() {

    private val json = Json { ignoreUnknownKeys = true }
    private fun api(
        serverUrl: String,
        token: String
    ) = AudioServerApi(serverUrl ,token)

    suspend fun refreshMetadata(
        serverUrl: String,
        token: String
    ): Library {
        val raw = withContext(Dispatchers.IO) {
            api(serverUrl, token).fetchMetadata()
        }
        return json.decodeFromString<Library>(raw)
    }
}