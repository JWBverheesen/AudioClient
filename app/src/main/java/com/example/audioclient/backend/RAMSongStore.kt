package com.example.audioclient.backend

import java.util.concurrent.ConcurrentHashMap

class RAMSongStore {
    private val songs = mutableMapOf<String, ByteArray>()

    @Synchronized
    fun add(id: String, data: ByteArray) {
        songs[id] = data;
    }

    @Synchronized
    fun get(id: String): ByteArray? {
        return songs[id]
    }

    @Synchronized
    fun delete(id: String) {
        songs.remove(id)
    }

    @Synchronized
    fun contains(id: String): Boolean {
        return songs.containsKey(id)
    }

    @Synchronized
    fun clear() {
        songs.clear()
    }

    @Synchronized
    fun size(): Int {
        return songs.size
    }
}
