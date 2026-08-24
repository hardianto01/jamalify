package com.jamalsquad.jamalify.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import com.jamalsquad.jamalify.data.Song
import com.jamalsquad.jamalify.data.db.DownloadState
import com.jamalsquad.jamalify.ui.components.Artwork
import com.jamalsquad.jamalify.ui.components.EmptyState
import com.jamalsquad.jamalify.ui.components.HSpace
import com.jamalsquad.jamalify.ui.components.SongRow
import com.jamalsquad.jamalify.ui.components.SongSheetController
import com.jamalsquad.jamalify.ui.viewmodel.LibraryViewModel

private val TABS = listOf("Playlist", "Favorit", "Unduhan", "Riwayat")

@UnstableApi
@Composable
fun LibraryScreen(
    currentSongId: String?,
    onPlay: (List<Song>, Int) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onOpenImport: () -> Unit,
    onOpenAbout: () -> Unit,
    sheet: SongSheetController,
    contentPadding: PaddingValues,
    viewModel: LibraryViewModel = viewModel()
) {
    var tabIndex by remember { mutableIntStateOf(0) }
    var showCreateDialog by remember { mutableStateOf(false) }

    val playlists by viewModel.playlists.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val downloads by viewModel.downloads.collectAsState()
    val history by viewModel.history.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Library", style = MaterialTheme.typography.headlineMedium)
            Row {
                IconButton(onClick = onOpenAbout) {
                    Icon(Icons.Rounded.Info, contentDescription = "Tentang aplikasi")
                }
                IconButton(onClick = onOpenImport) {
                    Icon(Icons.Rounded.CloudDownload, contentDescription = "Impor playlist")
                }
                IconButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = "Playlist baru")
                }
            }
        }

        ScrollableTabRow(
            selectedTabIndex = tabIndex,
            edgePadding = 20.dp,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            TABS.forEachIndexed { index, label ->
                Tab(
                    selected = tabIndex == index,
                    onClick = { tabIndex = index },
                    text = { Text(label) }
                )
            }
        }

        val bottom = PaddingValues(bottom = contentPadding.calculateBottomPadding())

        // Isi tab bertukar dengan crossfade, bukan berganti mendadak.
        Crossfade(
            targetState = tabIndex,
            animationSpec = tween(240, easing = FastOutSlowInEasing),
            label = "library_tab"
        ) { tab ->
            when (tab) {
                0 -> PlaylistTab(playlists, bottom, onOpenPlaylist, viewModel, onOpenImport) {
                    showCreateDialog = true
                }

                1 -> SongTab(
                    songs = favorites,
                    emptyTitle = "Belum ada favorit",
                    emptySubtitle = "Ketuk ikon hati di pemutar untuk menyimpan lagu ke sini.",
                    currentSongId = currentSongId,
                    onPlay = onPlay,
                    sheet = sheet,
                    contentPadding = bottom
                )

                2 -> DownloadTab(downloads, currentSongId, onPlay, viewModel, bottom)

                3 -> SongTab(
                    songs = history,
                    emptyTitle = "Riwayat masih kosong",
                    emptySubtitle = "Lagu yang kamu putar akan muncul di sini.",
                    currentSongId = currentSongId,
                    onPlay = onPlay,
                    sheet = sheet,
                    contentPadding = bottom,
                    header = {
                        if (history.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = viewModel::clearHistory) { Text("Hapus riwayat") }
                            }
                        }
                    }
                )
            }
        }
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                viewModel.createPlaylist(name)
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun PlaylistTab(
    playlists: List<com.jamalsquad.jamalify.data.db.PlaylistWithCount>,
    contentPadding: PaddingValues,
    onOpen: (Long) -> Unit,
    viewModel: LibraryViewModel,
    onOpenImport: () -> Unit,
    onCreate: () -> Unit
) {
    if (playlists.isEmpty()) {
        EmptyState(
            title = "Belum ada playlist",
            subtitle = "Buat playlist sendiri, atau impor langsung dari YouTube dan Spotify.",
            action = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onCreate) { Text("Buat playlist") }
                    TextButton(onClick = onOpenImport) { Text("Impor") }
                }
            }
        )
        return
    }

    LazyColumn(contentPadding = contentPadding) {
        items(playlists, key = { it.id }) { playlist ->
            var menuOpen by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(playlist.id) }
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Artwork(url = playlist.thumbnail, size = 56.dp)
                HSpace(14.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        playlist.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${playlist.songCount} lagu",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Opsi playlist")
                }
            }

            if (menuOpen) {
                AlertDialog(
                    onDismissRequest = { menuOpen = false },
                    title = { Text(playlist.name) },
                    text = { Text("Hapus playlist ini? Lagu di dalamnya tetap ada di library.") },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.deletePlaylist(playlist.id)
                            menuOpen = false
                        }) { Text("Hapus") }
                    },
                    dismissButton = {
                        TextButton(onClick = { menuOpen = false }) { Text("Batal") }
                    }
                )
            }
        }
    }
}

@UnstableApi
@Composable
private fun SongTab(
    songs: List<Song>,
    emptyTitle: String,
    emptySubtitle: String,
    currentSongId: String?,
    onPlay: (List<Song>, Int) -> Unit,
    sheet: SongSheetController,
    contentPadding: PaddingValues,
    header: @Composable (() -> Unit)? = null
) {
    if (songs.isEmpty()) {
        EmptyState(title = emptyTitle, subtitle = emptySubtitle)
        return
    }

    LazyColumn(contentPadding = contentPadding) {
        if (header != null) item { header() }
        itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
            SongRow(
                song = song,
                isPlaying = song.id == currentSongId,
                onClick = { onPlay(songs, index) },
                onMore = { sheet.show(song) }
            )
        }
    }
}

@Composable
private fun DownloadTab(
    downloads: List<com.jamalsquad.jamalify.data.db.DownloadedSong>,
    currentSongId: String?,
    onPlay: (List<Song>, Int) -> Unit,
    viewModel: LibraryViewModel,
    contentPadding: PaddingValues
) {
    if (downloads.isEmpty()) {
        EmptyState(
            title = "Belum ada unduhan",
            subtitle = "Unduh lagu lewat menu tiga titik agar bisa didengar tanpa internet."
        )
        return
    }

    val playable = downloads.filter { it.state == DownloadState.COMPLETED }.map { it.toSong() }

    LazyColumn(contentPadding = contentPadding) {
        items(downloads, key = { it.id }) { download ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = download.state == DownloadState.COMPLETED) {
                        val song = download.toSong()
                        onPlay(playable, playable.indexOfFirst { it.id == song.id }.coerceAtLeast(0))
                    }
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Artwork(url = download.thumbnail, size = 52.dp)
                HSpace(14.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        download.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (download.id == currentSongId) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when (download.state) {
                            DownloadState.COMPLETED -> download.artist
                            DownloadState.RUNNING -> "Mengunduh ${download.progress}%"
                            DownloadState.QUEUED -> "Menunggu giliran"
                            DownloadState.FAILED -> "Gagal diunduh — ketuk ikon untuk mencoba lagi"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (download.state == DownloadState.FAILED)
                            MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                when (download.state) {
                    DownloadState.RUNNING, DownloadState.QUEUED ->
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )

                    DownloadState.FAILED ->
                        IconButton(onClick = { viewModel.download(download.toSong()) }) {
                            Icon(Icons.Rounded.Download, contentDescription = "Ulangi unduhan")
                        }

                    DownloadState.COMPLETED ->
                        IconButton(onClick = { viewModel.removeDownload(download.id) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Hapus unduhan")
                        }
                }
            }
        }
    }
}

@Composable
private fun CreatePlaylistDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playlist baru") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("Nama playlist") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank()
            ) { Text("Buat") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}
