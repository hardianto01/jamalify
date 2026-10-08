package com.jamalsquad.jamalify.desktop.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.jamalsquad.jamalify.data.Song
import com.jamalsquad.jamalify.desktop.core.LibraryStore
import com.jamalsquad.jamalify.desktop.core.LocalPlaylist
import com.jamalsquad.jamalify.desktop.core.PlayerController
import com.jamalsquad.jamalify.ui.components.AudioVisualizer
import com.jamalsquad.jamalify.ui.components.NewPlaylistDialog
import com.jamalsquad.jamalify.ui.components.PlayerIcons
import com.jamalsquad.jamalify.util.Thumbnails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * Aksi lagu yang dibutuhkan menu konteks di mana pun barisnya tampil.
 * Disediakan lewat CompositionLocal supaya setiap layar tidak perlu
 * meneruskan player, library, dan daftar playlist satu per satu.
 */
class SongActions(
    val player: PlayerController,
    val library: LibraryStore,
    val scope: CoroutineScope,
    val notify: (String) -> Unit
) {
    fun playNext(song: Song) { player.playNext(song); notify("Diputar berikutnya: ${song.title}") }
    fun addToQueue(song: Song) { player.addToQueue(song); notify("Ditambahkan ke antrean") }
    fun toggleFavorite(song: Song) = scope.launch { library.toggleFavorite(song) }
    fun addToPlaylist(playlist: LocalPlaylist, song: Song) = scope.launch {
        library.addToPlaylist(playlist.id, song)
        notify("Ditambahkan ke ${playlist.name}")
    }
    fun addToNewPlaylist(name: String, song: Song) = scope.launch {
        library.addToPlaylist(library.createPlaylist(name), song)
        notify("Playlist \"$name\" dibuat")
    }
}

val LocalSongActions = staticCompositionLocalOf<SongActions> { error("SongActions belum disediakan") }

/** Sampul lagu; ukuran URL disesuaikan dengan ukuran tampil seperti di Android. */
@Composable
fun Artwork(url: String?, modifier: Modifier = Modifier, size: Dp = 48.dp, corner: Dp = 8.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (url.isNullOrBlank()) {
            Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val px = with(LocalDensity.current) { size.roundToPx() }
            val sized = remember(url, px) { Thumbnails.sized(url, px) }
            AsyncImage(
                model = sized,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().then(
                    // hqdefault 4:3 berisi video 16:9 berbingkai hitam; diperbesar supaya bingkainya terpotong.
                    if (sized?.endsWith("/hqdefault.jpg") == true) Modifier.graphicsLayer(scaleX = 4f / 3f, scaleY = 4f / 3f)
                    else Modifier
                )
            )
        }
    }
}

/**
 * Baris lagu desktop: klik kiri memutar, klik kanan atau tombol ⋮ membuka
 * menu konteks di posisi kursor. [extraMenu] untuk aksi khusus layar,
 * misalnya "Hapus dari playlist".
 */
@OptIn(ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    number: Int? = null,
    extraMenu: List<Pair<String, () -> Unit>> = emptyList()
) {
    var menuOffset by remember { mutableStateOf<DpOffset?>(null) }
    var rowSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()

    Box(modifier.fillMaxWidth().onSizeChanged { rowSize = it }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    when {
                        isCurrent -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)
                        hovered -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.04f)
                        else -> Color.Transparent
                    }
                )
                .hoverable(hover)
                .onPointerEvent(PointerEventType.Press) { e ->
                    if (e.buttons.isSecondaryPressed) {
                        val p = e.changes.first().position
                        // Menu ditambatkan di bawah baris; geser ke atas supaya muncul di kursor.
                        menuOffset = with(density) { DpOffset(p.x.toDp(), (p.y - rowSize.height).toDp()) }
                    }
                }
                .clickable(onClick = onClick)
                .pointerHoverIcon(PointerIcon.Hand)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (number != null) {
                Box(Modifier.width(32.dp)) {
                    if (isCurrent) AudioVisualizer(isPlaying = true, barCount = 4, barWidth = 2.dp, height = 14.dp)
                    else Text(
                        "%02d".format(number),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Artwork(song.thumbnail, size = 44.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    song.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else null,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    song.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            if (isCurrent && number == null) {
                AudioVisualizer(isPlaying = true, barCount = 3, barWidth = 2.5.dp, height = 16.dp)
                Spacer(Modifier.width(12.dp))
            }
            Text(
                song.durationLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(onClick = { menuOffset = with(density) { DpOffset(rowSize.width.toDp() - 24.dp, 0.dp) } }) {
                Icon(PlayerIcons.More, "Opsi lainnya", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }
        SongMenu(song, menuOffset, onDismiss = { menuOffset = null }, extra = extraMenu)
    }
}

@Composable
fun SongMenu(
    song: Song,
    offset: DpOffset?,
    onDismiss: () -> Unit,
    extra: List<Pair<String, () -> Unit>> = emptyList()
) {
    val actions = LocalSongActions.current
    var pickPlaylist by remember(offset) { mutableStateOf(false) }
    var newPlaylist by remember { mutableStateOf(false) }
    val expanded = offset != null
    // Status favorit dan daftar playlist hanya dibaca selama menu terbuka.
    val isFav by remember(expanded, song.id) {
        if (expanded) actions.library.isFavorite(song.id) else flowOf(false)
    }.collectAsState(false)
    val playlists by remember(expanded) {
        if (expanded) actions.library.playlists else flowOf(emptyList())
    }.collectAsState(emptyList())

    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, offset = offset ?: DpOffset.Zero) {
        if (!pickPlaylist) {
            MenuItem("Putar berikutnya", Icons.Rounded.PlaylistPlay) { actions.playNext(song); onDismiss() }
            MenuItem("Tambah ke antrean", Icons.AutoMirrored.Rounded.QueueMusic) { actions.addToQueue(song); onDismiss() }
            MenuItem(
                if (isFav) "Hapus dari favorit" else "Tambah ke favorit",
                if (isFav) PlayerIcons.HeartFilled else PlayerIcons.Heart
            ) { actions.toggleFavorite(song); onDismiss() }
            MenuItem("Tambah ke playlist…", Icons.AutoMirrored.Rounded.PlaylistAdd) { pickPlaylist = true }
            if (extra.isNotEmpty()) HorizontalDivider()
            extra.forEach { (label, action) ->
                MenuItem(label, Icons.Rounded.RemoveCircleOutline) { action(); onDismiss() }
            }
        } else {
            MenuItem("Playlist baru…", Icons.Rounded.Add) { newPlaylist = true; onDismiss() }
            if (playlists.isNotEmpty()) HorizontalDivider()
            playlists.forEach { p ->
                MenuItem(p.name, Icons.AutoMirrored.Rounded.QueueMusic) { actions.addToPlaylist(p, song); onDismiss() }
            }
        }
    }
    if (newPlaylist) {
        NewPlaylistDialog(
            onDismiss = { newPlaylist = false },
            onConfirm = { name -> actions.addToNewPlaylist(name, song); newPlaylist = false }
        )
    }
}

@Composable
private fun MenuItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(icon, null, modifier = Modifier.size(20.dp)) },
        onClick = onClick
    )
}

/** Kartu lagu bersampul besar untuk baris horizontal di Beranda. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun SongCard(song: Song, onClick: () -> Unit, isCurrent: Boolean = false) {
    var menuOffset by remember { mutableStateOf<DpOffset?>(null) }
    var cardHeight by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    Box(Modifier.onSizeChanged { cardHeight = it.height }) {
        Column(
            modifier = Modifier
                .width(164.dp)
                .clip(RoundedCornerShape(14.dp))
                .onPointerEvent(PointerEventType.Press) { e ->
                    if (e.buttons.isSecondaryPressed) {
                        val p = e.changes.first().position
                        menuOffset = with(density) { DpOffset(p.x.toDp(), (p.y - cardHeight).toDp()) }
                    }
                }
                .clickable(onClick = onClick)
                .pointerHoverIcon(PointerIcon.Hand)
                .padding(6.dp)
        ) {
            Box {
                Artwork(song.thumbnail, size = 152.dp, corner = 12.dp)
                if (isCurrent) {
                    AudioVisualizer(
                        isPlaying = true, barCount = 4, height = 16.dp, color = Color.White,
                        modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(song.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                song.artist, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        SongMenu(song, menuOffset, onDismiss = { menuOffset = null })
    }
}

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.headlineMedium, modifier = modifier.padding(horizontal = 24.dp, vertical = 20.dp))
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.titleLarge,
        modifier = modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 8.dp)
    )
}

@Composable
fun EmptyState(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Spacer(Modifier.height(8.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

fun formatMs(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return if (m >= 60) "%d:%02d:%02d".format(m / 60, m % 60, s) else "%d:%02d".format(m, s)
}
