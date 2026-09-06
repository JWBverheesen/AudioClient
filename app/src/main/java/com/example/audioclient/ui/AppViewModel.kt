package com.example.audioclient.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppState(
    val error: String? = null
)

class AppViewModel(
    app: Application
) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(AppState())

    val state: StateFlow<AppState> = _state.asStateFlow()

    fun refresh() {

    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}