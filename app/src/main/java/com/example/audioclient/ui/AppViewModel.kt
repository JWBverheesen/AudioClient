package com.example.audioclient.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import com.example.audioclient.backend.AudioServer
import com.example.audioclient.state.AppState
import com.example.audioclient.backend.LocalStore

class AppViewModel(
    app: Application
) : AndroidViewModel(app) {
/* ===============================================
 *  Member variables
 * =============================================== */
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()
    val audioServer = AudioServer()
    private val store = LocalStore(app)

    /* ===============================================
     *  Functions
     * =============================================== */

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    // Fetch metadata from backend audio server
    fun refreshMetadata() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val state = _state.value

            runCatching {
                audioServer.refreshMetadata(
                    serverUrl = state.serverUrl,
                    token = state.token
                )
            }.onSuccess { library ->
                store.saveLibrary(library)
                _state.update { it.copy(
                    library = library,
                    loading = false,
                    error = null)
                }

            }.onFailure { e ->
                Log.e("AudioServer", "Metadata request failed", e)
                _state.update { it.copy(
                    loading = false,
                    error = "${e.javaClass.simpleName}: ${e.message}")
                }
            }
        }
    }

    fun setServerUrl(
        url: String
    ) {
        _state.update{ it.copy(serverUrl = url) }
    }
}