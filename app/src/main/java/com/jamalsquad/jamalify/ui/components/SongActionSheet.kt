package com.jamalsquad.jamalify.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jamalsquad.jamalify.data.Song
import com.jamalsquad.jamalify.data.db.PlaylistWithCount

/** Aksi yang bisa dilakukan pada satu lagu dari daftar mana pun. */
data class SongActions(
    val onPlayNext: () -> Unit,
    val onAddToQueue: () -> Unit,
    val onToggleFavorite: () -> Unit,
    val onDownload: () -> Unit,
    val onStartRadio: () -> Unit,
    val onAddToPlaylist: (Long) -> Unit,
    val onRemove: (() -> Unit)? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongActionSheet(
    song: Song,
    isFavorite: Boolean,
    playlists: List<PlaylistWithCount>,
    actions: SongActions,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showPlaylistPicker by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Artwork(url = song.thumbnail, size = 48.dp)
            HSpace(14.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    song.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        VSpace(4.dp)

        if (showPlaylistPicker) {
            if (playlists.isEmpty()) {
                EmptyState(
                    title = "Belum ada playlist",
                    subtitle = "Buat playlist dulu di tab Library."
                )
            } else {
                LazyColumn {
                    items(playlists, key = { it.id }) { playlist ->
                        SheetItem(
                            icon = Icons.Rounded.QueueMusic,
                            label = playlist.name,
                            secondary = "${playlist.songCount} lagu"
                        ) {
                            actions.onAddToPlaylist(playlist.id)
                            onDismiss()
                        }
                    }
                }
            }
        } else {
            SheetItem(Icons.Rounded.SkipNext, "Putar berikutnya") {
                actions.onPlayNext(); onDismiss()
            }
            SheetItem(Icons.Rounded.QueueMusic, "Tambah ke antrean") {
                actions.onAddToQueue(); onDismiss()
            }
            SheetItem(Icons.Rounded.Radio, "Mulai radio dari lagu ini") {
                actions.onStartRadio(); onDismiss()
            }
            SheetItem(
                if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                if (isFavorite) "Hapus dari favorit" else "Tambah ke favorit"
            ) {
                actions.onToggleFavorite(); onDismiss()
            }
            SheetItem(Icons.Rounded.Download, "Unduh untuk offline") {
                actions.onDownload(); onDismiss()
            }
            SheetItem(Icons.Rounded.PlaylistAdd, "Tambah ke playlist") {
                showPlaylistPicker = true
            }
            actions.onRemove?.let { remove ->
                SheetItem(Icons.Rounded.Delete, "Hapus dari daftar ini") {
                    remove(); onDismiss()
                }
            }
        }

        VSpace(24.dp)
    }
}

@Composable
private fun SheetItem(
    icon: ImageVector,
    label: String,
    secondary: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        HSpace(18.dp)
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (secondary != null) {
                Text(
                    secondary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
