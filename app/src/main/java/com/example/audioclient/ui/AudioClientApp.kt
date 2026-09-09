package com.example.audioclient.ui

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import com.example.audioclient.backend.Track
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
                1 -> LibraryScreen(state = state, vm = vm)
                2 -> QueueScreen()
                else -> SettingsScreen(state = state, vm = vm)
            }
        }
    }
}

/* ===============================================
 *  Navigator bar items
 * =============================================== */
@Composable
private fun PlayerScreen() {
    Log.d("AudioClient:", "Opening player screen")
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
private fun LibraryScreen(
    state: AppState,
    vm: AppViewModel
) {
    Log.d("AudioClient:", "Opening library screen")

    var search by remember(state.search) {
        mutableStateOf(state.search)
    }
    val tracks = state.allTracks
    val query = search.trim()
    val matchingTracks = getMatchingTrack(tracks, query)

    val albums = remember(tracks) {
        tracks.groupBy { it.album }.toList().sortedBy { it.first.lowercase() }
    }

    val matchingAlbums = getMatchingAlbum(tracks, query)
    val matchingArtists = getMatchingArtist(tracks, query)

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        OutlinedTextField(
            value = search,
            onValueChange = {
                search = it
                vm.setSearch(it)
            },
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            leadingIcon = {
                Icon(
                    painter = painterResource(id = R.drawable.outline_search_24),
                    contentDescription = null
                )
            },
            placeholder = { Text("Search songs, albums or artists") },
            singleLine = true
        )
        // If the library is empty, offer to load metadata
        if (state.library == null && !state.loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No music metadata cached.")
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = vm::refreshMetadata) { Text("Load library") }
                }
            }
        } else if (query.isBlank()) {
            Log.d("AudioClient", "Should be entering here?" + "Album size: " + albums.size)
            // default library view. Show albums instead of individual songs.
            LazyColumn {
                item {
                    Text(
                        text = "${albums.size} albums",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                items(
                    items = albums,
                    key = { "album-${it.first}" }) { (album, albumTracks) ->
                    AlbumRow(
                        album = album,
                        tracks = albumTracks,
                        onClick = { vm.playAlbum(albumTracks) }
                    )
                }
            }
        } else {
            // search results
            LazyColumn {
                if (matchingTracks.isNotEmpty()) {
                    item { SectionHeader("Songs") }
                    items(
                        items = matchingTracks,
                        key = { "song|" + "${it.artist}|" + "${it.album}|" + it.filename }) { track ->
                        TrackRow(
                            track = track,
                            onClick = { vm.playTrack(track) },
                            onLongClick = { /* TODO() Do we want this?*/ }
                        )
                    }
                }

                if (matchingAlbums.isNotEmpty()) {
                    item { SectionHeader("Albums") }
                    items(
                        items = matchingAlbums,
                        key = { "album|" + it.first }) { (_, albumTracks) ->
                        AlbumRow(
                            album = albumTracks.first().album,
                            tracks = albumTracks,
                            onClick = { vm.playAlbum(albumTracks) }
                        )
                    }
                }

                if (matchingArtists.isNotEmpty()) {
                    item { SectionHeader("Artists") }
                    items(
                        items = matchingArtists,
                        key = { "artist|" + it.first }
                    ) { (artist, artistTracks) ->
                        ListItem(
                            headlineContent = { Text(artist) },
                            supportingContent = { Text("${artistTracks.size} tracks") },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(id = R.drawable.outline_person_24),
                                    contentDescription = null
                                )
                            }
                        )
                    }
                }

                if (matchingTracks.isEmpty() && matchingAlbums.isEmpty() && matchingArtists.isEmpty()
                ) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) { Text("No results") }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueScreen() {
    Log.d("AudioClient:", "Opening Queue screen")
}


@Composable
private fun SettingsScreen(
    state: AppState,
    vm: AppViewModel
) {
    Log.d("AudioClient:", "Opening settings screen")
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


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(track.title, maxLines = 1) },
        supportingContent = {
            Text("${track.artist} • ${track.album}", maxLines = 1) },
        leadingContent = {
            Icon(
                painter = painterResource(id = R.drawable.outline_music_note_24),
                contentDescription = null
            )
        },
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        )
    )
}

@Composable
private fun SectionHeader(
    title: String
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 8.dp)
    )
}

@Composable
private fun AlbumRow(
    album: String,
    tracks: List<Track>,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(album, maxLines = 1) },
        supportingContent = {
            Text("${tracks.firstOrNull()?.artist ?: ""} • " + "${tracks.size} tracks",
                maxLines = 1) },
        leadingContent = {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 4.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(id = R.drawable.outline_album_24),
                        contentDescription = null
                    )
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

/* ===============================================
 *  Helper methods. TODO() move to backend
 * =============================================== */
@Composable
fun getMatchingTrack(
    tracks: List<Track>,
    query: String
) : List<Track> {
    return remember(tracks, query) {
        if(query.isBlank()) {
            emptyList()
        } else {
            tracks.filter { track ->
                track.title.contains(query, ignoreCase = true) ||
                track.artist.contains(query, ignoreCase = true) ||
                track.album.contains(query, ignoreCase = true)
            }
        }
    }
}

@Composable
fun getMatchingAlbum(
    tracks: List<Track>,
    query: String
) : List<Pair<String,List<Track>>> {
    return remember(tracks, query) {
        if (query.isBlank()) {
            emptyList()
        } else {
            tracks.groupBy { it.album }.filter { (album, albumTracks) ->
                album.contains(query, ignoreCase = true) ||
                albumTracks.any { track ->
                    track.artist.contains(query, ignoreCase = true) ||
                    track.title.contains(query, ignoreCase = true)
                }
            }.toList().sortedBy { it.first.lowercase() }
        }
    }
}

@Composable
fun getMatchingArtist(
    tracks: List<Track>,
    query: String
) : List<Pair<String,List<Track>>> {
    return remember(tracks, query) {
        if (query.isBlank()) {
            emptyList()
        } else {
            tracks.groupBy { it.artist }.filter { (artist, artistTracks) ->
                artist.contains(query, ignoreCase = true) ||
                artistTracks.any { track ->
                    track.album.contains(query, ignoreCase = true) ||
                    track.title.contains(query, ignoreCase = true)
                }
            }.toList().sortedBy { it.first.lowercase() }
        }
    }
}