package com.jamalsquad.jamalify.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jamalsquad.jamalify.data.Song
import com.jamalsquad.jamalify.desktop.core.PlayerController
import com.jamalsquad.jamalify.ui.components.AudioVisualizer
import com.jamalsquad.jamalify.ui.components.NavIcons
import com.jamalsquad.jamalify.ui.components.PlayerIcons
import com.jamalsquad.jamalify.ui.components.glow
import kotlinx.coroutines.flow.flowOf

enum class Screen(val label: String) {
    HOME("Beranda"), SEARCH("Cari"), LIBRARY("Pustaka"), SETTINGS("Pengaturan")
}

@Composable
fun Sidebar(selected: Screen, onSelect: (Screen) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.width(220.dp).fillMaxHeight().padding(horizontal = 12.dp, vertical = 20.dp)) {
        Text(
            "Jamalify",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 12.dp, bottom = 24.dp)
        )
        Screen.entries.forEach { screen ->
            val active = screen == selected
            val icon = when (screen) {
                Screen.HOME -> if (active) NavIcons.HomeFilled else NavIcons.Home
                Screen.SEARCH -> if (active) NavIcons.SearchBold else NavIcons.Search
                Screen.LIBRARY -> if (active) NavIcons.LibraryFilled else NavIcons.Library
                Screen.SETTINGS -> PlayerIcons.Settings
            }
            NavItem(screen.label, icon, active) { onSelect(screen) }
        }
    }
}

@Composable
private fun NavItem(label: String, icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) colors.onBackground.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (active) colors.onBackground else colors.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            color = if (active) colors.onBackground else colors.onSurfaceVariant
        )
    }
}

/**
 * Bar pemutar di bawah jendela: lagu (kiri), kontrol + seek bar (tengah),
 * lirik/antrean/volume (kanan). Mengganti MiniPlayer Android yang terlalu
 * kecil untuk layar lebar.
 */
@Composable
fun NowPlayingBar(
    player: PlayerController,
    queueOpen: Boolean,
    playerOpen: Boolean,
    onToggleQueue: () -> Unit,
    onTogglePlayer: () -> Unit,
    onVolume: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by player.state.collectAsState()
    val song = state.current
    val actions = LocalSongActions.current
    val isFav by remember(song?.id) {
        song?.let { actions.library.isFavorite(it.id) } ?: flowOf(false)
    }.collectAsState(false)
    val colors = MaterialTheme.colorScheme

    Row(modifier.fillMaxWidth().height(84.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        // Lagu
        Row(Modifier.weight(0.3f), verticalAlignment = Alignment.CenterVertically) {
            if (song != null) {
                Box(Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onTogglePlayer).pointerHoverIcon(PointerIcon.Hand)) {
                    Artwork(song.thumbnail, size = 56.dp, corner = 10.dp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f, fill = false)) {
                    Text(song.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { actions.toggleFavorite(song) }) {
                    Icon(
                        if (isFav) PlayerIcons.HeartFilled else PlayerIcons.Heart,
                        if (isFav) "Hapus dari favorit" else "Tambah ke favorit",
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                Text("Belum ada lagu diputar", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
        }

        // Kontrol
        Column(Modifier.weight(0.4f), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ToggleIcon(PlayerIcons.Shuffle, "Acak", state.shuffle, player::toggleShuffle)
                IconButton(onClick = player::previous) { Icon(PlayerIcons.SkipPrevious, "Sebelumnya", Modifier.size(22.dp)) }
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(colors.onBackground)
                        .clickable(enabled = song != null, onClick = player::togglePlay)
                        .pointerHoverIcon(PointerIcon.Hand),
                    contentAlignment = Alignment.Center
                ) {
                    if (state.isBuffering) CircularProgressIndicator(Modifier.size(20.dp), color = colors.background, strokeWidth = 2.dp)
                    else Icon(
                        if (state.isPlaying) PlayerIcons.Pause else PlayerIcons.Play,
                        if (state.isPlaying) "Jeda" else "Putar",
                        tint = colors.background, modifier = Modifier.size(22.dp)
                    )
                }
                IconButton(onClick = player::next) { Icon(PlayerIcons.SkipNext, "Berikutnya", Modifier.size(22.dp)) }
                ToggleIcon(if (state.repeatOne) PlayerIcons.RepeatOne else PlayerIcons.Repeat, "Ulangi satu", state.repeatOne, player::toggleRepeatOne)
            }
            SeekBar(state.positionMs, state.durationMs, player::seekTo)
        }

        // Lain-lain
        Row(Modifier.weight(0.3f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            ToggleIcon(Icons.Rounded.Lyrics, "Pemutar & lirik", playerOpen, onTogglePlayer)
            ToggleIcon(PlayerIcons.Queue, "Antrean", queueOpen, onToggleQueue)
            var lastVolume by remember { mutableStateOf(1f) }
            IconButton(onClick = {
                // Klik ikon = bisu/pulihkan, seperti pemutar desktop umumnya.
                if (state.volume > 0f) { lastVolume = state.volume; onVolume(0f) } else onVolume(lastVolume)
            }) {
                Icon(
                    when {
                        state.volume <= 0f -> Icons.AutoMirrored.Rounded.VolumeOff
                        state.volume < 0.5f -> Icons.AutoMirrored.Rounded.VolumeDown
                        else -> Icons.AutoMirrored.Rounded.VolumeUp
                    },
                    "Volume", Modifier.size(20.dp)
                )
            }
            ThinSlider(state.volume, onVolume, Modifier.width(110.dp))
        }
    }
}

/**
 * Slider tipis ala pemutar desktop. Slider M3 bawaan (trek tebal, celah di
 * jempol, titik di ujung) terlalu berat untuk bar setinggi ini.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThinSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    enabled: Boolean = true
) {
    val fg = MaterialTheme.colorScheme.onBackground
    val colors = SliderDefaults.colors(
        thumbColor = fg, activeTrackColor = fg, inactiveTrackColor = fg.copy(alpha = 0.18f),
        disabledThumbColor = fg.copy(alpha = 0.3f), disabledInactiveTrackColor = fg.copy(alpha = 0.12f)
    )
    val interaction = remember { MutableInteractionSource() }
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        enabled = enabled,
        interactionSource = interaction,
        modifier = modifier.height(20.dp),
        thumb = {
            Box(Modifier.size(12.dp).clip(CircleShape).background(if (enabled) fg else fg.copy(alpha = 0.3f)))
        },
        track = { st ->
            SliderDefaults.Track(
                sliderState = st, colors = colors, enabled = enabled,
                modifier = Modifier.height(4.dp), thumbTrackGapSize = 0.dp, drawStopIndicator = null
            )
        }
    )
}

@Composable
private fun ToggleIcon(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            icon, label,
            tint = if (active) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp).then(if (active) Modifier.glow(0.18f, 0.9f) else Modifier)
        )
    }
}

/** Seek bar; posisi seret disimpan lokal supaya tidak melompat-lompat saat posisi player diperbarui. */
@Composable
fun SeekBar(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit, modifier: Modifier = Modifier) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val fraction = dragging ?: if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
    Row(modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(formatMs((fraction * durationMs).toLong()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ThinSlider(
            value = fraction.coerceIn(0f, 1f),
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { onSeek((it * durationMs).toLong()) }
                dragging = null
            },
            enabled = durationMs > 0,
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
        )
        Text(formatMs(durationMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun QueuePanel(player: PlayerController, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val queue by player.queue.collectAsState()
    val index by player.queueIndex.collectAsState()
    val isPlaying by remember { player.state }.collectAsState()
    val listState = rememberLazyListState()
    LaunchedEffect(index) { if (index > 0) listState.animateScrollToItem((index - 1).coerceAtLeast(0)) }

    Column(modifier.width(320.dp).fillMaxHeight()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Antrean", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Tutup antrean") }
        }
        if (queue.isEmpty()) {
            EmptyState("Antrean kosong", "Putar lagu atau tambahkan lewat menu ⋮")
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(queue, key = { i, s -> "$i-${s.id}" }) { i, song ->
                QueueRow(song, current = i == index, playing = isPlaying.isPlaying, onClick = { player.skipTo(i) }, onRemove = { player.removeFromQueue(i) })
            }
        }
    }
}

@Composable
private fun QueueRow(song: Song, current: Boolean, playing: Boolean, onClick: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(10.dp))
            .background(if (current) MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = 10.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(song.thumbnail, size = 40.dp, corner = 6.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.bodyMedium, fontWeight = if (current) FontWeight.SemiBold else null, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (current) AudioVisualizer(isPlaying = playing, barCount = 3, barWidth = 2.dp, height = 14.dp)
        IconButton(onClick = onRemove) {
            Icon(Icons.Rounded.Close, "Hapus dari antrean", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}
