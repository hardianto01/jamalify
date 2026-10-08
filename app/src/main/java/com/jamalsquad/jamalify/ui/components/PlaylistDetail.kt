package com.jamalsquad.jamalify.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jamalsquad.jamalify.data.Song

/**
 * Halaman detail playlist mengikuti desain acuan: sampul kecil berpendar di
 * kiri, judul besar di kanan, dua tombol lebar (Putar terang, Acak gelap),
 * lalu daftar lagu bernomor. Dipakai playlist buatan pengguna maupun
 * playlist dari beranda, supaya keduanya tampil sama.
 */
@Composable
fun PlaylistDetail(
    title: String,
    meta: String,
    artworkUrl: String?,
    songs: List<Song>,
    currentSongId: String?,
    onBack: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    onShuffle: () -> Unit,
    onMore: (Song) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    shuffleLabel: String = "Acak",
    actions: @Composable RowScope.() -> Unit = {},
    empty: @Composable () -> Unit = {},
    suggestions: List<Song>? = null,
    suggesting: Boolean = false,
    onAddSuggestion: (Song) -> Unit = {},
    onPlaySuggestion: (Song) -> Unit = {},
    onRefreshSuggestions: () -> Unit = {}
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(PlayerIcons.ChevronLeft, contentDescription = "Kembali")
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Artwork(
                    url = artworkUrl ?: songs.firstOrNull()?.thumbnail,
                    size = 124.dp,
                    corner = 20.dp,
                    modifier = Modifier.glow(alpha = 0.22f, spread = 0.9f)
                )
                HSpace(18.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    VSpace(2.dp)
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    VSpace(6.dp)
                    Row(verticalAlignment = Alignment.CenterVertically, content = actions)
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                WideButton(
                    label = "Putar",
                    icon = PlayerIcons.PlayCircle,
                    container = MaterialTheme.colorScheme.primary,
                    content = MaterialTheme.colorScheme.onPrimary,
                    enabled = songs.isNotEmpty(),
                    onClick = { onPlay(songs, 0) },
                    modifier = Modifier.weight(1f)
                )
                WideButton(
                    label = shuffleLabel,
                    icon = PlayerIcons.Shuffle,
                    container = MaterialTheme.colorScheme.surfaceContainerHigh,
                    content = MaterialTheme.colorScheme.onSurface,
                    enabled = songs.isNotEmpty(),
                    onClick = onShuffle,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (songs.isEmpty()) {
            item { empty() }
        } else {
            itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                SongRow(
                    song = song,
                    number = index + 1,
                    isPlaying = song.id == currentSongId,
                    onClick = { onPlay(songs, index) },
                    onMore = { onMore(song) }
                )
            }
            item { VSpace(16.dp) }
        }

        // Saran dari algoritma rekomendasi, supaya menambah lagu tidak harus
        // lewat pencarian. Null berarti halaman ini tidak bisa diubah.
        if (suggestions != null) {
            item(key = "suggest_header") {
                SuggestionHeader(suggesting = suggesting, onRefresh = onRefreshSuggestions)
            }
            if (suggestions.isEmpty()) {
                item(key = "suggest_state") {
                    Text(
                        text = if (suggesting) "Mencari lagu yang cocok…" else "Belum ada saran. Coba segarkan.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                }
            } else {
                items(suggestions, key = { "suggest_${it.id}" }) { song ->
                    SongRow(
                        song = song,
                        isPlaying = song.id == currentSongId,
                        onClick = { onPlaySuggestion(song) },
                        trailing = {
                            IconButton(onClick = { onAddSuggestion(song) }) {
                                Icon(PlayerIcons.PlusCircle, contentDescription = "Tambahkan ${song.title}")
                            }
                        },
                        modifier = Modifier.animateItem()
                    )
                }
            }
            item { VSpace(16.dp) }
        }
    }
}

@Composable
private fun SuggestionHeader(suggesting: Boolean, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Tambahkan lagu",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "Disarankan dari isi dan selera dengarmu",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (suggesting) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(12.dp).size(20.dp))
        } else {
            IconButton(onClick = onRefresh) {
                Icon(PlayerIcons.Refresh, contentDescription = "Segarkan saran")
            }
        }
    }
}

/** Ikon aksi kecil di bawah judul playlist (unduh, hapus, dst). */
@Composable
fun PlaylistAction(icon: ImageVector, contentDescription: String, onClick: () -> Unit, tint: Color = Color.Unspecified) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else tint,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun WideButton(
    label: String,
    icon: ImageVector,
    container: Color,
    content: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(shape)
            .background(if (enabled) container else container.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        HSpace(8.dp)
        Text(label, color = content, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
    }
}

/** Latar polos untuk [PlaylistDetail] yang dibuka di atas layar lain. */
@Composable
fun PlaylistDetailSurface(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) { content() }
}
