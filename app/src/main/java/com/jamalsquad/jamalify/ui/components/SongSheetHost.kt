package com.jamalsquad.jamalify.ui.components

import androidx.compose.runtime.Composable
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
import com.jamalsquad.jamalify.ui.viewmodel.PlayerViewModel

/** Menyimpan lagu mana yang sheet-nya sedang dibuka. */
class SongSheetController {
    var song by mutableStateOf<Song?>(null)
        private set
    var onRemove by mutableStateOf<(() -> Unit)?>(null)
        private set

    fun show(song: Song, onRemove: (() -> Unit)? = null) {
        this.song = song
        this.onRemove = onRemove
    }

    fun dismiss() {
        song = null
        onRemove = null
    }
}

@Composable
fun rememberSongSheetController(): SongSheetController = remember { SongSheetController() }

/**
 * Merakit [SongActionSheet] dengan aksi yang sama di seluruh aplikasi, jadi
 * tiap layar cukup memanggil `controller.show(song)`.
 */
@UnstableApi
@Composable
fun SongSheetHost(
    controller: SongSheetController,
    viewModel: PlayerViewModel = viewModel()
) {
    val song = controller.song ?: return
    val app = LocalContext.current.applicationContext as JamalifyApp
    val player = app.playerConnection
    val playlists by viewModel.playlists.collectAsState()

    // Sheet dibuka untuk lagu mana pun, bukan hanya yang sedang diputar,
    // jadi status favoritnya dibaca terpisah dari PlayerViewModel.
    val isFavorite by remember(song.id) {
        app.repository.isFavorite(song.id)
    }.collectAsState(initial = false)

    SongActionSheet(
        song = song,
        isFavorite = isFavorite,
        playlists = playlists,
        actions = SongActions(
            onPlayNext = { player.playNext(song) },
            onAddToQueue = { player.addToQueue(song) },
            onToggleFavorite = { viewModel.toggleFavorite(song) },
            onDownload = { viewModel.download(song) },
            onStartRadio = {
                player.play(song)
                viewModel.startRadio(song)
            },
            onAddToPlaylist = { playlistId -> viewModel.addToPlaylist(playlistId, song) },
            onRemove = controller.onRemove
        ),
        onDismiss = { controller.dismiss() }
    )
}
