package com.jamalsquad.jamalify.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import com.jamalsquad.jamalify.data.Song
import com.jamalsquad.jamalify.ui.components.Artwork
import com.jamalsquad.jamalify.ui.components.EmptyState
import com.jamalsquad.jamalify.ui.components.LoadingBox
import com.jamalsquad.jamalify.ui.components.SectionHeader
import com.jamalsquad.jamalify.ui.components.SongRow
import com.jamalsquad.jamalify.ui.components.SongSheetController
import com.jamalsquad.jamalify.ui.components.VSpace
import com.jamalsquad.jamalify.ui.viewmodel.HomeSection
import com.jamalsquad.jamalify.ui.viewmodel.HomeViewModel
import java.util.Calendar

@UnstableApi
@Composable
fun HomeScreen(
    currentSongId: String?,
    onPlay: (List<Song>, Int) -> Unit,
    sheet: SongSheetController,
    contentPadding: PaddingValues,
    viewModel: HomeViewModel = viewModel()
) {
    val trending by viewModel.trending.collectAsState()
    val sections by viewModel.sections.collectAsState()
    val recent by viewModel.recentlyPlayed.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val loadingMore by viewModel.loadingMore.collectAsState()
    val canLoadMore by viewModel.canLoadMore.collectAsState()
    val error by viewModel.error.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(text = greeting(), style = MaterialTheme.typography.headlineLarge)
                Text(
                    text = "Musik dari YouTube, tanpa iklan",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (recent.isNotEmpty()) {
            shelf(
                section = HomeSection("recent", "Lanjutkan mendengarkan", recent.take(12)),
                onPlay = onPlay
            )
        }

        if (favorites.isNotEmpty()) {
            item {
                SectionHeader("Pilihan cepat") {
                    Text(
                        text = "${favorites.size} favorit",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(favorites.take(4), key = { "fav_${it.id}" }) { song ->
                SongRow(
                    song = song,
                    isPlaying = song.id == currentSongId,
                    onClick = { onPlay(favorites, favorites.indexOf(song)) },
                    onMore = { sheet.show(song) }
                )
            }
            item { VSpace(8.dp) }
        }

        sections.forEach { section ->
            shelf(section = section, onPlay = onPlay)
        }

        item {
            SectionHeader("Sedang tren") {
                TextButton(onClick = viewModel::refresh) { Text("Muat ulang") }
            }
        }

        if (loading && trending.isEmpty()) {
            item { LoadingBox() }
        } else if (trending.isEmpty()) {
            item {
                EmptyState(
                    title = "Beranda belum terisi",
                    subtitle = error ?: "Coba muat ulang setelah koneksi tersedia.",
                    action = { TextButton(onClick = viewModel::refresh) { Text("Coba lagi") } }
                )
            }
        } else {
            itemsIndexed(trending, key = { _, song -> "trend_${song.id}" }) { index, song ->
                SongRow(
                    song = song,
                    isPlaying = song.id == currentSongId,
                    onClick = { onPlay(trending, index) },
                        modifier = Modifier.animateItem(),
                    onMore = { sheet.show(song) }
                )
            }

            if (canLoadMore) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (loadingMore) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            OutlinedButton(onClick = viewModel::loadMore) {
                                Text("Muat lebih banyak")
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Satu rak horizontal berisi kartu lagu. */
private fun androidx.compose.foundation.lazy.LazyListScope.shelf(
    section: HomeSection,
    onPlay: (List<Song>, Int) -> Unit
) {
    item(key = "header_${section.id}") { SectionHeader(section.title) }
    item(key = "row_${section.id}") {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(section.songs, key = { _, song -> "${section.id}_${song.id}" }) { index, song ->
                SongCard(song = song, onClick = { onPlay(section.songs, index) })
            }
        }
    }
    item(key = "space_${section.id}") { VSpace(8.dp) }
}

/** Kartu besar untuk baris horizontal di beranda. */
@Composable
private fun SongCard(song: Song, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(bottom = 8.dp)
    ) {
        Artwork(url = song.thumbnail, size = 140.dp, corner = 12.dp)
        VSpace(8.dp)
        Text(
            text = song.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = song.artist,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun greeting(): String =
    when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 4..10 -> "Selamat pagi"
        in 11..14 -> "Selamat siang"
        in 15..18 -> "Selamat sore"
        else -> "Selamat malam"
    }
