package com.jamalsquad.jamalify.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.jamalsquad.jamalify.JamalifyApp
import com.jamalsquad.jamalify.data.db.DownloadState
import com.jamalsquad.jamalify.ui.components.Artwork
import com.jamalsquad.jamalify.ui.components.EmptyState
import com.jamalsquad.jamalify.ui.components.HSpace
import com.jamalsquad.jamalify.ui.components.SongRow
import com.jamalsquad.jamalify.ui.components.VSpace
import com.jamalsquad.jamalify.ui.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun PlayerScreen(
    onCollapse: () -> Unit,
    viewModel: PlayerViewModel = viewModel()
) {
    val player = (LocalContext.current.applicationContext as JamalifyApp).playerConnection

    val song by player.currentSong.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val isBuffering by player.isBuffering.collectAsState()
    val position by player.position.collectAsState()
    val duration by player.duration.collectAsState()
    val queue by player.queue.collectAsState()
    val queueIndex by player.queueIndex.collectAsState()
    val shuffle by player.shuffle.collectAsState()
    val repeatMode by player.repeatMode.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()
    val downloadState by viewModel.downloadState.collectAsState()

    var showQueue by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableStateOf<Float?>(null) }

    if (song == null) {
        EmptyState(
            title = "Belum ada lagu diputar",
            subtitle = "Pilih lagu dari beranda atau pencarian."
        )
        return
    }

    val current = song!!

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onCollapse) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Tutup pemutar")
            }
            Text(
                text = "Sedang diputar",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(onClick = { showQueue = true }) {
                Icon(Icons.Rounded.QueueMusic, contentDescription = "Antrean")
            }
        }

        VSpace(16.dp)

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            // Sampul mengecil sedikit saat dijeda — penanda status yang
            // terbaca tanpa harus melihat tombol.
            val artScale by animateFloatAsState(
                targetValue = if (isPlaying) 1f else 0.93f,
                animationSpec = tween(durationMillis = 350),
                label = "art_scale"
            )
            Artwork(
                url = current.thumbnail,
                size = 300.dp,
                corner = 20.dp,
                modifier = Modifier.graphicsLayer {
                    scaleX = artScale
                    scaleY = artScale
                }
            )
            if (isBuffering) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        VSpace(28.dp)

        Text(
            text = current.title,
            style = MaterialTheme.typography.headlineMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        VSpace(6.dp)
        Text(
            text = current.artist,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        VSpace(20.dp)

        val total = duration.takeIf { it > 0 } ?: (current.durationSec * 1000)
        val fraction = when {
            scrubValue != null -> scrubValue!!
            total > 0 -> (position.toFloat() / total).coerceIn(0f, 1f)
            else -> 0f
        }

        Slider(
            value = fraction,
            onValueChange = { scrubValue = it },
            onValueChangeFinished = {
                scrubValue?.let { player.seekTo((it * total).toLong()) }
                scrubValue = null
            },
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.outline
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                formatTime(if (scrubValue != null) (scrubValue!! * total).toLong() else position),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                formatTime(total),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        VSpace(12.dp)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(onClick = player::toggleShuffle) {
                Icon(
                    Icons.Rounded.Shuffle,
                    contentDescription = "Acak",
                    tint = if (shuffle) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = player::previous) {
                Icon(
                    Icons.Rounded.SkipPrevious,
                    contentDescription = "Sebelumnya",
                    modifier = Modifier.size(38.dp)
                )
            }
            IconButton(
                onClick = player::togglePlayPause,
                modifier = Modifier.size(72.dp)
            ) {
                AnimatedContent(
                    targetState = isPlaying,
                    transitionSpec = {
                        (fadeIn(tween(160)) + scaleIn(tween(160), initialScale = 0.6f))
                            .togetherWith(fadeOut(tween(160)))
                    },
                    label = "play_pause"
                ) { playing ->
                    Icon(
                        imageVector = if (playing) Icons.Rounded.Pause
                        else Icons.Rounded.PlayArrow,
                        contentDescription = if (playing) "Jeda" else "Putar",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                }
            }
            IconButton(onClick = player::next) {
                Icon(
                    Icons.Rounded.SkipNext,
                    contentDescription = "Berikutnya",
                    modifier = Modifier.size(38.dp)
                )
            }
            IconButton(onClick = player::cycleRepeatMode) {
                Icon(
                    imageVector = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne
                    else Icons.Rounded.Repeat,
                    contentDescription = "Ulangi",
                    tint = if (repeatMode == Player.REPEAT_MODE_OFF)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.primary
                )
            }
        }

        VSpace(8.dp)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            IconButton(onClick = viewModel::toggleFavorite) {
                Icon(
                    imageVector = if (isFavorite) Icons.Rounded.Favorite
                    else Icons.Rounded.FavoriteBorder,
                    contentDescription = "Favorit",
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HSpace(24.dp)
            IconButton(
                onClick = viewModel::download,
                enabled = downloadState != DownloadState.COMPLETED
            ) {
                Icon(
                    imageVector = if (downloadState == DownloadState.COMPLETED)
                        Icons.Rounded.DownloadDone else Icons.Rounded.Download,
                    contentDescription = "Unduh",
                    tint = if (downloadState == DownloadState.COMPLETED)
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showQueue) {
        ModalBottomSheet(
            onDismissRequest = { showQueue = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Text(
                "Antrean (${queue.size} lagu)",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            LazyColumn {
                itemsIndexed(queue) { index, queued ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SongRow(
                            song = queued,
                            isPlaying = index == queueIndex,
                            onClick = {
                                player.seekToIndex(index)
                                showQueue = false
                            },
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { player.removeFromQueue(index) }) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = "Hapus dari antrean",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            VSpace(24.dp)
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes >= 60) "%d:%02d:%02d".format(minutes / 60, minutes % 60, seconds)
    else "%d:%02d".format(minutes, seconds)
}
