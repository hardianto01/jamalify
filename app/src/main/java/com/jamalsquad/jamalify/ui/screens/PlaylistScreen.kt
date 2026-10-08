package com.jamalsquad.jamalify.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import com.jamalsquad.jamalify.JamalifyApp
import com.jamalsquad.jamalify.data.Song
import com.jamalsquad.jamalify.playback.SmartShuffleManager
import com.jamalsquad.jamalify.ui.components.EmptyState
import com.jamalsquad.jamalify.ui.components.NewPlaylistDialog
import com.jamalsquad.jamalify.ui.components.PlayerIcons
import com.jamalsquad.jamalify.ui.components.PlaylistAction
import com.jamalsquad.jamalify.ui.components.PlaylistDetail
import com.jamalsquad.jamalify.ui.components.SongSheetController
import com.jamalsquad.jamalify.ui.viewmodel.PlaylistViewModel

/**
 * Playlist buatan pengguna, juga "Lagu yang Disukai" lewat
 * [com.jamalsquad.jamalify.ui.viewmodel.FAVORITES_PLAYLIST_ID]. Keduanya bisa
 * ditambah dari saran algoritma di bawah daftar lagu; hanya playlist biasa
 * yang bisa diganti nama dan dihapus.
 */
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
    val suggestions by viewModel.suggestions.collectAsState()
    val suggesting by viewModel.suggesting.collectAsState()
    val context = LocalContext.current
    val smartShuffle = remember { SmartShuffleManager(context) }
    val player = (context.applicationContext as JamalifyApp).playerConnection
    LaunchedEffect(songs) { player.warmUp(songs) }

    val favorites = playlistId < 0
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    PlaylistDetail(
        title = info?.name ?: "Playlist",
        meta = "${if (favorites) "Favorit" else "Playlist"} • ${songs.size} lagu",
        artworkUrl = info?.thumbnail,
        songs = songs,
        currentSongId = currentSongId,
        onBack = onBack,
        onPlay = onPlay,
        onShuffle = { onShuffle(smartShuffle.shuffleWithRecommendations(songs)) },
        onMore = { song -> sheet.show(song) { viewModel.remove(song) } },
        shuffleLabel = "Acak Cerdas",
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding()
        ),
        actions = {
            PlaylistAction(PlayerIcons.Download, "Unduh semua", onClick = { viewModel.downloadAll(songs) })
            if (!favorites) {
                PlaylistAction(PlayerIcons.Edit, "Ganti nama", onClick = { renaming = true })
                PlaylistAction(PlayerIcons.Trash, "Hapus playlist", onClick = { confirmDelete = true })
            }
        },
        empty = {
            EmptyState(
                title = if (favorites) "Belum ada favorit" else "Playlist masih kosong",
                subtitle = "Pilih dari saran di bawah, atau lewat menu tiga titik di lagu mana pun."
            )
        },
        suggestions = suggestions,
        suggesting = suggesting,
        onAddSuggestion = { song ->
            viewModel.add(song)
            Toast.makeText(context, "Ditambahkan: ${song.title}", Toast.LENGTH_SHORT).show()
        },
        onPlaySuggestion = { song -> onPlay(listOf(song), 0) },
        onRefreshSuggestions = viewModel::refreshSuggestions
    )

    if (renaming) {
        NewPlaylistDialog(
            title = "Ganti nama",
            initialName = info?.name.orEmpty(),
            confirmLabel = "Simpan",
            onDismiss = { renaming = false },
            onConfirm = { name ->
                viewModel.rename(name)
                renaming = false
            }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Hapus playlist?") },
            text = { Text("\"${info?.name.orEmpty()}\" akan dihapus. Lagunya tetap ada di favorit dan unduhan.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                    onBack()
                }) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Batal") } }
        )
    }
}
