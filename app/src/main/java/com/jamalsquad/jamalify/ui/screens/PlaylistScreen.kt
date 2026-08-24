package com.jamalsquad.jamalify.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import com.jamalsquad.jamalify.data.Song
import com.jamalsquad.jamalify.ui.components.Artwork
import com.jamalsquad.jamalify.ui.components.EmptyState
import com.jamalsquad.jamalify.ui.components.HSpace
import com.jamalsquad.jamalify.ui.components.SongRow
import com.jamalsquad.jamalify.ui.components.SongSheetController
import com.jamalsquad.jamalify.ui.components.VSpace
import com.jamalsquad.jamalify.ui.viewmodel.PlaylistViewModel

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun PlaylistScreen(
    playlistId: Long,
    currentSongId: String?,
    onPlay: (List<Song>, Int) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onBack: () -> Unit,
    sheet: SongSheetController,
    contentPadding: PaddingValues,
    viewModel: PlaylistViewModel = viewModel()
) {
    LaunchedEffect(playlistId) { viewModel.load(playlistId) }

    val songs by viewModel.songs.collectAsState()
    val info by viewModel.info.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        info?.name ?: "Playlist",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding())
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Artwork(url = info?.thumbnail, size = 100.dp, corner = 12.dp)
                    HSpace(16.dp)
                    Column {
                        Text(
                            info?.name ?: "Playlist",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${songs.size} lagu",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onPlay(songs, 0) },
                        enabled = songs.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        HSpace(6.dp)
                        Text("Putar")
                    }
                    OutlinedButton(
                        onClick = { onShuffle(songs) },
                        enabled = songs.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = null)
                        HSpace(6.dp)
                        Text("Acak")
                    }
                    IconButton(
                        onClick = { viewModel.downloadAll(songs) },
                        enabled = songs.isNotEmpty()
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = "Unduh semua")
                    }
                }
            }

            if (songs.isEmpty()) {
                item {
                    EmptyState(
                        title = "Playlist masih kosong",
                        subtitle = "Tambahkan lagu lewat menu tiga titik di hasil pencarian."
                    )
                }
            } else {
                itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                    SongRow(
                        song = song,
                        isPlaying = song.id == currentSongId,
                        onClick = { onPlay(songs, index) },
                        onMore = { sheet.show(song) { viewModel.remove(song.id) } }
                    )
                }
                item { VSpace(24.dp) }
            }
        }
    }
}
