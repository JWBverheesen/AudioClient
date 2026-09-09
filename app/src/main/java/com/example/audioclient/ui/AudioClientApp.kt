package com.example.audioclient.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

import com.example.audioclient.R
import com.example.audioclient.state.AppState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioClientApp(
    vm: AppViewModel = viewModel()
) {
    val state by vm.state.collectAsState()

    var tab by remember {
        mutableIntStateOf(1)
    }

/* ===============================================
 *  Main application layout
 * =============================================== */
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Audio Server") },
                actions = {
                    IconButton(onClick = vm::refreshMetadata ) {
                        Icon(
                            painter = painterResource(id = R.drawable.outline_play_circle_24),
                            contentDescription = "Refresh library")
                    }
                }
            )
        }, bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = {
                        Icon(painter = painterResource(id = R.drawable.outline_play_circle_24),
                            contentDescription = null)
                    }, label = { Text("Player") }
                )

                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = {
                        Icon(painter = painterResource(id = R.drawable.outline_library_music_24),
                            contentDescription = null)
                    }, label = { Text("Library") }
                )

                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = {
                        Icon(painter = painterResource(id = R.drawable.outline_queue_music_24),
                            contentDescription = null)
                    }, label = { Text("Queue") }
                )

                NavigationBarItem(
                    selected = tab == 3,
                    onClick = { tab = 3 },
                    icon = { Icon(painter = painterResource(id = R.drawable.outline_settings_24),
                            contentDescription = null)
                    }, label = { Text("Settings") }
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            state.error?.let { error ->
                Card(modifier = Modifier.fillMaxWidth().padding(12.dp),
                     colors = CardDefaults.cardColors(containerColor =
                            MaterialTheme.colorScheme.errorContainer)) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "error",  modifier = Modifier.weight(1f))

                        TextButton(
                            onClick = {
                                vm.refreshMetadata()
                                vm.clearError()
                            })
                            { Text("Retry") }
                    }
                }
            }

            when (tab) {
                0 -> PlayerScreen()
                1 -> LibraryScreen()
                2 -> QueueScreen()
                else -> SettingsScreen(state = state,
                    vm = vm)
            }
        }
    }
}

/* ===============================================
 *  Navigator bar items
 * =============================================== */
@Composable
private fun PlayerScreen() {
    EmptyPlayer()
}

@Composable
private fun EmptyPlayer() {
    Box(modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(id = R.drawable.outline_album_24),
                contentDescription = null,
                Modifier.size(96.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text("Nothing playing", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text("Choose a song or album from your library.")
        }
    }
}

@Composable
private fun LibraryScreen() {

}

@Composable
private fun QueueScreen() {

}
@Composable
private fun SettingsScreen(
    state: AppState,
    vm: AppViewModel
) {
    Column(modifier = Modifier.fillMaxWidth().padding((16.dp))) {
        Text(text="Server", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(value = state.serverUrl,
            onValueChange = vm::setServerUrl,
            modifier = Modifier.fillMaxWidth(),
            label = {Text("Audio Server URL")},
            supportingText = {
                Text("Example: http://192.168.1.50:8080")},
            singleLine = true)
        Spacer(Modifier.height(20.dp))

        Button(onClick = vm::refreshMetadata) { Text("Refresh metadata")}
        Spacer(Modifier.height(12.dp))

        Text(text = "The library is cached on the device. " +
             "Playback still requires the audio server.",
            style = MaterialTheme.typography.bodySmall)
    }
}
