package com.example.audioclient.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

import com.example.audioclient.R

// PLACEFOLDER
fun refresh() {

}

fun clearError() {

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioClientApp(
) {

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
                    IconButton(onClick = { refresh() }) {
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

            //state.error?.let { error ->

                Card(modifier = Modifier.fillMaxWidth().padding(12.dp),
                     colors = CardDefaults.cardColors(containerColor =
                            MaterialTheme.colorScheme.errorContainer)) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "error",  modifier = Modifier.weight(1f))

                        TextButton(
                            onClick = {
                                refresh()
                                clearError()
                            })
                            { Text("Retry") }
                    }
                }
            //}

            when (tab) {
                0 -> PlayerScreen()
                1 -> LibraryScreen()
                2 -> QueueScreen()
                else -> SettingsScreen()
            }
        }
    }
}

/* ===============================================
 *  Navigator bar items
 * =============================================== */
@Composable
private fun PlayerScreen() {

}

@Composable
private fun LibraryScreen() {

}

@Composable
private fun QueueScreen() {

}
@Composable
private fun SettingsScreen() {

}