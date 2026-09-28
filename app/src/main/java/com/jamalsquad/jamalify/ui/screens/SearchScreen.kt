package com.jamalsquad.jamalify.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import com.jamalsquad.jamalify.JamalifyApp
import com.jamalsquad.jamalify.data.Song
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.jamalsquad.jamalify.ui.components.EmptyState
import com.jamalsquad.jamalify.ui.components.LoadingBox
import com.jamalsquad.jamalify.ui.components.SongRow
import com.jamalsquad.jamalify.ui.components.SongSheetController
import com.jamalsquad.jamalify.ui.viewmodel.SearchViewModel

private val SpotifyGreen = Color(0xFF1DB954)

@UnstableApi
@Composable
fun SearchScreen(
    currentSongId: String?,
    onPlay: (List<Song>, Int) -> Unit,
    sheet: SongSheetController,
    contentPadding: PaddingValues,
    viewModel: SearchViewModel = viewModel()
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val keyboard = LocalSoftwareKeyboardController.current
    val player = (LocalContext.current.applicationContext as JamalifyApp).playerConnection
    LaunchedEffect(results) { player.warmUp(results) }

    Column(modifier = Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            placeholder = { Text("Cari lagu, artis, atau album") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onQueryChange("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Hapus")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    keyboard?.hide()
                    viewModel.submit()
                }
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )

        // Status pencarian bertukar dengan crossfade supaya daftar hasil tidak
        // muncul mendadak menggantikan spinner.
        val phase = when {
            loading && results.isEmpty() -> "loading"
            results.isEmpty() && query.isBlank() -> "idle"
            results.isEmpty() -> "empty"
            else -> "results"
        }

        Crossfade(
            targetState = phase,
            animationSpec = tween(240, easing = FastOutSlowInEasing),
            label = "search_phase"
        ) { current ->
            when (current) {
                "loading" -> LoadingBox()

                "idle" -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    item {
                        Text(
                            text = "Jelajahi Semua Genre",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(bottom = 14.dp)
                        )
                    }
                    item {
                        val genres = listOf(
                            "Pop Indonesia" to Color(0xFF8D43B3),
                            "Indie & Akustik" to Color(0xFFE86326),
                            "Rock & Metal" to Color(0xFFE91429),
                            "K-Pop Hits" to Color(0xFFE1306C),
                            "Dangdut & Koplo" to SpotifyGreen,
                            "Chill & Lofi" to Color(0xFF27856A)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            genres.chunked(2).forEach { rowGenres ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    rowGenres.forEach { (name, color) ->
                                        Surface(
                                            onClick = {
                                                viewModel.onQueryChange(name)
                                                viewModel.submit()
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            color = color,
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(90.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(14.dp)
                                            ) {
                                                Text(
                                                    text = name,
                                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = Color.White,
                                                    modifier = Modifier.align(Alignment.TopStart)
                                                )
                                                Icon(
                                                    imageVector = Icons.Rounded.MusicNote,
                                                    contentDescription = null,
                                                    tint = Color.White.copy(alpha = 0.35f),
                                                    modifier = Modifier
                                                        .size(44.dp)
                                                        .align(Alignment.BottomEnd)
                                                )
                                            }
                                        }
                                    }
                                    if (rowGenres.size == 1) {
                                        Box(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }

                "empty" -> EmptyState(
                    title = "Tidak ada hasil",
                    subtitle = error ?: "Coba kata kunci lain."
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding())
                ) {
                    itemsIndexed(results, key = { _, song -> song.id }) { index, song ->
                        SongRow(
                            song = song,
                            isPlaying = song.id == currentSongId,
                            onClick = { onPlay(results, index) },
                            modifier = Modifier.animateItem(),
                            onMore = { sheet.show(song) }
                        )
                    }
                }
            }
        }
    }
}
