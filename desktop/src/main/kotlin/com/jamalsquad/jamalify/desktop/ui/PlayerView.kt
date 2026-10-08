package com.jamalsquad.jamalify.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.jamalsquad.jamalify.data.Song
import com.jamalsquad.jamalify.desktop.core.MusicCatalog
import com.jamalsquad.jamalify.desktop.core.PlayerController
import com.jamalsquad.jamalify.ui.components.PetKind
import com.jamalsquad.jamalify.ui.components.PetPerch
import com.jamalsquad.jamalify.ui.components.PlayerIcons
import com.jamalsquad.jamalify.ui.components.glow
import com.jamalsquad.jamalify.util.Thumbnails

/**
 * Tampilan pemutar penuh: sampul besar dengan peliharaan duduk di atasnya,
 * dan lirik di sebelah kanan bila tersedia.
 */
@Composable
fun PlayerView(
    player: PlayerController,
    catalog: MusicCatalog,
    pet: PetKind,
    contentPadding: PaddingValues,
    onClose: () -> Unit
) {
    val state by player.state.collectAsState()
    val song = state.current

    Box(Modifier.fillMaxSize()) {
        // Sampul yang diburamkan sebagai latar, seperti layar Player Android.
        if (song?.thumbnail != null) {
            AsyncImage(
                model = Thumbnails.sized(song.thumbnail, 480),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(80.dp).alpha(0.35f)
            )
        }
        Column(Modifier.fillMaxSize().padding(contentPadding)) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(PlayerIcons.ChevronDown, "Tutup pemutar") }
                Text("Sedang diputar", style = MaterialTheme.typography.titleMedium)
            }
            if (song == null) {
                EmptyState("Belum ada lagu diputar", "Pilih lagu dari Beranda atau Cari")
                return@Column
            }
            Row(
                Modifier.fillMaxSize().padding(start = 40.dp, end = 40.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(48.dp)
            ) {
                BoxWithConstraints(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    val side = min(maxWidth, maxHeight - 90.dp).coerceAtLeast(160.dp)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(side)) {
                            Box(Modifier.fillMaxSize().glow(0.16f, 0.75f))
                            Artwork(song.thumbnail, size = side, corner = 24.dp)
                            if (pet != PetKind.NONE) {
                                PetPerch(
                                    isPlaying = state.isPlaying,
                                    kind = pet,
                                    size = 64.dp,
                                    songTitle = song.title,
                                    modifier = Modifier.fillMaxSize().padding(10.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                        Text(
                            song.title, style = MaterialTheme.typography.headlineMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(side)
                        )
                        Text(
                            song.artist, style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(side)
                        )
                    }
                }
                Lyrics(song, catalog, state.positionMs, Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

private data class LyricLine(val timeMs: Long, val text: String)

private val LRC_TIME = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")

/** Baris LRC bisa punya beberapa cap waktu ("[00:10.00][01:20.00]reff"). */
private fun parseLrc(text: String): List<LyricLine> = text.lineSequence().flatMap { raw ->
    val stamps = LRC_TIME.findAll(raw).toList()
    val body = raw.replace(LRC_TIME, "").trim()
    stamps.map { m ->
        val (min, sec, frac) = m.destructured
        val ms = frac.padEnd(3, '0').take(3).toLongOrNull() ?: 0
        LyricLine(min.toLong() * 60_000 + sec.toLong() * 1000 + ms, body)
    }
}.sortedBy { it.timeMs }.toList()

@Composable
private fun Lyrics(song: Song, catalog: MusicCatalog, positionMs: Long, modifier: Modifier) {
    var loading by remember(song.id) { mutableStateOf(true) }
    var text by remember(song.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(song.id) {
        text = runCatching { catalog.lyrics(song) }.getOrNull()
        loading = false
    }
    val synced = remember(text) { text?.let(::parseLrc).orEmpty() }

    Box(
        modifier.clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f)).padding(28.dp)
    ) {
        when {
            loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            text.isNullOrBlank() -> EmptyState("Lirik belum tersedia", "Belum ketemu lirik untuk lagu ini", Modifier.align(Alignment.Center))
            synced.isEmpty() -> Text(
                text!!,
                style = MaterialTheme.typography.titleMedium.copy(lineHeight = 28.sp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            )
            else -> {
                val active = synced.indexOfLast { it.timeMs <= positionMs }.coerceAtLeast(0)
                val listState = rememberLazyListState()
                LaunchedEffect(active) { listState.animateScrollToItem((active - 2).coerceAtLeast(0)) }
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(synced) { i, line ->
                        Text(
                            line.text.ifBlank { "♪" },
                            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 22.sp, lineHeight = 30.sp),
                            fontWeight = if (i == active) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (i == active) MaterialTheme.colorScheme.onBackground
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}
