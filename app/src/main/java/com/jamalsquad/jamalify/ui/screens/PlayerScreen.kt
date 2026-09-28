package com.jamalsquad.jamalify.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.jamalsquad.jamalify.JamalifyApp
import com.jamalsquad.jamalify.data.db.DownloadState
import com.jamalsquad.jamalify.data.lyrics.LyricsResult
import com.jamalsquad.jamalify.data.lyrics.LyricsService
import com.jamalsquad.jamalify.playback.PlayerConnection
import com.jamalsquad.jamalify.playback.SoundMode
import com.jamalsquad.jamalify.playback.SoundModeManager
import com.jamalsquad.jamalify.ui.components.Artwork
import com.jamalsquad.jamalify.ui.components.AudioVisualizer
import com.jamalsquad.jamalify.ui.components.EmptyState
import com.jamalsquad.jamalify.ui.components.HSpace
import com.jamalsquad.jamalify.ui.components.HiResBadge
import com.jamalsquad.jamalify.ui.components.MiniPlayer
import com.jamalsquad.jamalify.ui.components.QueueSongRow
import com.jamalsquad.jamalify.ui.components.SoundModeSheet
import com.jamalsquad.jamalify.ui.components.SongRow
import com.jamalsquad.jamalify.ui.components.VSpace
import com.jamalsquad.jamalify.ui.theme.rememberAmbientColors
import com.jamalsquad.jamalify.ui.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun PlayerScreen(
    onCollapse: () -> Unit,
    viewModel: PlayerViewModel = viewModel()
) {
    val context = LocalContext.current
    val player = (context.applicationContext as JamalifyApp).playerConnection

    val song by player.currentSong.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val isBuffering by player.isBuffering.collectAsState()
    // Dibaca hanya di tempat yang butuh (lirik), bukan di sini: membacanya
    // di atas membuat seluruh layar pemutar disusun ulang tiap 500ms.
    val position = player.position.collectAsState()
    val queue by player.queue.collectAsState()
    val queueIndex by player.queueIndex.collectAsState()
    val shuffle by player.shuffle.collectAsState()
    val repeatMode by player.repeatMode.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()
    val downloadState by viewModel.downloadState.collectAsState()
    val karaokeMode by player.karaokeMode.isKaraokeMode.collectAsState()
    val soundMode by SoundModeManager.mode.collectAsState()

    val sleepTimerRemaining by player.sleepTimer.remainingMillis.collectAsState()
    val isEndOfSongTimer by player.sleepTimer.isEndOfSongMode.collectAsState()

    // Aktifkan shake-to-extend saat sleep timer aktif
    LaunchedEffect(sleepTimerRemaining, isEndOfSongTimer) {
        if (sleepTimerRemaining != null || isEndOfSongTimer) {
            player.enableShakeToExtend()
        } else {
            player.disableShakeToExtend()
        }
    }

    var showQueue by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showSoundMode by remember { mutableStateOf(false) }

    if (song == null) {
        EmptyState(
            title = "Belum ada lagu diputar",
            subtitle = "Pilih lagu dari beranda atau pencarian."
        )
        return
    }

    val current = song!!
    val ambient = rememberAmbientColors(current.thumbnail)
    val animatedTopColor by androidx.compose.animation.animateColorAsState(targetValue = ambient.topColor, animationSpec = tween(700), label = "topColor")
    val animatedCenterColor by androidx.compose.animation.animateColorAsState(targetValue = ambient.centerColor, animationSpec = tween(700), label = "centerColor")
    val animatedAccentColor by androidx.compose.animation.animateColorAsState(targetValue = ambient.accentColor, animationSpec = tween(700), label = "accentColor")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        animatedTopColor,
                        animatedCenterColor,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onCollapse) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Tutup pemutar")
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Sedang diputar",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (sleepTimerRemaining != null || isEndOfSongTimer) {
                        val timerText = if (isEndOfSongTimer) "Timer: Akhir Lagu"
                        else "Timer: ${formatTime(sleepTimerRemaining!!)}"
                        Text(
                            text = timerText,
                            style = MaterialTheme.typography.labelMedium,
                            color = animatedAccentColor
                        )
                    }
                }
                IconButton(onClick = { showQueue = true }) {
                    Icon(Icons.Rounded.QueueMusic, contentDescription = "Antrean")
                }
            }

            VSpace(16.dp)

            // Spotify Inline Lyrics vs Artwork Crossfade Box
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Crossfade(
                    targetState = showLyrics,
                    animationSpec = tween(durationMillis = 300),
                    label = "artwork_lyrics_swap"
                ) { isLyrics ->
                    if (!isLyrics) {
                        Box(contentAlignment = Alignment.Center) {
                            val artScale by animateFloatAsState(
                                targetValue = if (isPlaying) 1f else 0.93f,
                                animationSpec = tween(300),
                                label = "artwork_scale"
                            )
                            Artwork(
                                url = current.thumbnail,
                                size = 300.dp,
                                corner = 16.dp,
                                modifier = Modifier.graphicsLayer {
                                    scaleX = artScale
                                    scaleY = artScale
                                }
                            )
                        }
                    } else {
                        InlineLyricsCard(
                            size = 300.dp,
                            corner = 16.dp,
                            songTitle = current.title,
                            artistName = current.artist,
                            durationSec = current.durationSec.toInt(),
                            currentPositionMs = position.value,
                            accentColor = animatedAccentColor,
                            onDismiss = { showLyrics = false }
                        )
                    }
                }
            }

            VSpace(24.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = current.title,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = current.artist,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { viewModel.toggleFavorite(current) }) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Favorit",
                        tint = if (isFavorite) animatedAccentColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            VSpace(16.dp)

            PlaybackProgress(
                player = player,
                accentColor = animatedAccentColor
            )

            VSpace(16.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(onClick = player::toggleShuffle) {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = "Acak",
                        tint = if (shuffle) animatedAccentColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = player::previous) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "Sebelumnya",
                        modifier = Modifier.size(36.dp)
                    )
                }
                IconButton(
                    onClick = player::togglePlayPause,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(animatedAccentColor)
                ) {
                    if (isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = Color.Black,
                            strokeWidth = 3.dp
                        )
                    } else {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Jeda" else "Putar",
                            tint = Color.Black,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
                IconButton(onClick = player::next) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Berikutnya",
                        modifier = Modifier.size(36.dp)
                    )
                }
                IconButton(onClick = player::cycleRepeatMode) {
                    Icon(
                        imageVector = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        contentDescription = "Ulang",
                        tint = if (repeatMode != Player.REPEAT_MODE_OFF) animatedAccentColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            VSpace(20.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { player.karaokeMode.toggleKaraoke() }) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = "Karaoke",
                        tint = if (karaokeMode) animatedAccentColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { showSoundMode = true }) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = "Mode Suara",
                        tint = if (soundMode != SoundMode.NORMAL) animatedAccentColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = viewModel::toggleFavorite) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Favorit",
                        tint = if (isFavorite) animatedAccentColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = viewModel::download) {
                    val icon = when (downloadState) {
                        DownloadState.COMPLETED -> Icons.Rounded.DownloadDone
                        else -> Icons.Rounded.Download
                    }
                    val tint = when (downloadState) {
                        DownloadState.COMPLETED, DownloadState.RUNNING, DownloadState.QUEUED -> animatedAccentColor
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Icon(imageVector = icon, contentDescription = "Unduh", tint = tint)
                }
                IconButton(onClick = { showSleepTimer = true }) {
                    Icon(
                        imageVector = Icons.Rounded.Bedtime,
                        contentDescription = "Sleep Timer",
                        tint = if (sleepTimerRemaining != null || isEndOfSongTimer) animatedAccentColor
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { player.openEqualizer(context) }) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = "Equalizer",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { showLyrics = !showLyrics }) {
                    Icon(
                        imageVector = Icons.Rounded.Lyrics,
                        contentDescription = "Lirik Lagu",
                        tint = if (showLyrics) animatedAccentColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (soundMode != SoundMode.NORMAL) {
                VSpace(8.dp)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(50))
                        .background(animatedAccentColor.copy(alpha = 0.15f))
                        .clickable { showSoundMode = true }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    if (soundMode.hiRes) {
                        HiResBadge(height = 24.dp)
                    } else {
                        Text(
                            text = "${soundMode.emoji} ${soundMode.label}",
                            style = MaterialTheme.typography.labelLarge,
                            color = animatedAccentColor
                        )
                    }
                }
            }
        }
    }

    if (showQueue) {
        ModalBottomSheet(
            onDismissRequest = { showQueue = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Antrean Pemutaran (${queue.size} lagu)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    AudioVisualizer(isPlaying = isPlaying, barCount = 4, height = 16.dp)
                }
                VSpace(12.dp)
            }
            LazyColumn {
                itemsIndexed(queue) { index, queued ->
                    QueueSongRow(
                        song = queued,
                        index = index,
                        isPlaying = index == queueIndex,
                        isDragging = false,
                        onMove = { from, to -> player.moveQueueItem(from, to) },
                        onRemove = { idx -> player.removeFromQueue(idx) },
                        onClick = {
                            player.seekToIndex(index)
                            showQueue = false
                        }
                    )
                }
            }
            VSpace(24.dp)
        }
    }

    if (showSoundMode) {
        SoundModeSheet(accentColor = animatedAccentColor, onDismiss = { showSoundMode = false })
    }

    if (showSleepTimer) {
        ModalBottomSheet(
            onDismissRequest = { showSleepTimer = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Sleep Timer",
                    style = MaterialTheme.typography.titleLarge
                )
                VSpace(16.dp)
                val options = listOf(
                    "15 Menit" to 15,
                    "30 Menit" to 30,
                    "45 Menit" to 45,
                    "60 Menit" to 60
                )
                options.forEach { (label, min) ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                player.sleepTimer.startTimer(min)
                                showSleepTimer = false
                            }
                            .padding(vertical = 12.dp, horizontal = 16.dp)
                    ) {
                        Text(text = label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            player.sleepTimer.setEndOfSongMode()
                            showSleepTimer = false
                        }
                        .padding(vertical = 12.dp, horizontal = 16.dp)
                ) {
                    Text(text = "Saat lagu selesai", style = MaterialTheme.typography.bodyLarge)
                }
                if (sleepTimerRemaining != null || isEndOfSongTimer) {
                    VSpace(12.dp)
                    // Tombol perpanjang timer
                    if (sleepTimerRemaining != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(animatedAccentColor.copy(alpha = 0.15f))
                                    .clickable {
                                        player.sleepTimer.extendTimer(5)
                                        showSleepTimer = false
                                    }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "+5 Menit",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = animatedAccentColor
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(animatedAccentColor.copy(alpha = 0.15f))
                                    .clickable {
                                        player.sleepTimer.extendTimer(15)
                                        showSleepTimer = false
                                    }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "+15 Menit",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = animatedAccentColor
                                )
                            }
                        }
                        VSpace(8.dp)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
                            .clickable {
                                player.sleepTimer.cancelTimer()
                                showSleepTimer = false
                            }
                            .padding(vertical = 12.dp, horizontal = 16.dp)
                    ) {
                        Text(
                            text = "Matikan Timer",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                VSpace(20.dp)
            }
        }
    }
}

@Composable
private fun InlineLyricsCard(
    size: Dp,
    corner: Dp,
    songTitle: String,
    artistName: String,
    durationSec: Int,
    currentPositionMs: Long,
    accentColor: Color,
    onDismiss: () -> Unit
) {
    var lyricsState by remember(songTitle, artistName) { mutableStateOf<LyricsResult?>(null) }
    var isLoading by remember(songTitle, artistName) { mutableStateOf(true) }

    LaunchedEffect(songTitle, artistName) {
        isLoading = true
        lyricsState = LyricsService.fetchLyrics(songTitle, artistName, durationSec)
        isLoading = false
    }

    Card(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .clickable(onClick = onDismiss),
        shape = RoundedCornerShape(corner),
        colors = CardDefaults.cardColors(
            containerColor = accentColor.copy(alpha = 0.22f)
        ),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = accentColor
                )
            } else if (lyricsState == null) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lyrics,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(36.dp)
                    )
                    VSpace(8.dp)
                    Text(
                        "Lirik belum tersedia",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Text(
                        "Ketuk untuk kembali ke cover album",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            } else {
                val lyrics = lyricsState!!
                if (lyrics.isPlainOnly && !lyrics.plainLyrics.isNullOrBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = lyrics.plainLyrics,
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
                            color = Color.White
                        )
                    }
                } else if (lyrics.syncedLines.isNotEmpty()) {
                    val listState = rememberLazyListState()
                    val activeIndex = remember(currentPositionMs, lyrics.syncedLines) {
                        val idx = lyrics.syncedLines.indexOfLast { it.timestampMs <= currentPositionMs }
                        if (idx >= 0) idx else 0
                    }

                    LaunchedEffect(activeIndex) {
                        if (activeIndex >= 0) {
                            listState.animateScrollToItem((activeIndex - 1).coerceAtLeast(0))
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(lyrics.syncedLines) { index, line ->
                            val isActive = index == activeIndex
                            Text(
                                text = line.text,
                                style = if (isActive) MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                ) else MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp
                                ),
                                color = if (isActive) accentColor
                                else Color.White.copy(alpha = 0.55f),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Slider dan waktu putar; satu-satunya bagian yang disusun ulang tiap posisi berubah. */
@UnstableApi
@Composable
private fun PlaybackProgress(
    player: PlayerConnection,
    accentColor: Color
) {
    val position by player.position.collectAsState()
    val duration by player.duration.collectAsState()
    var scrubValue by remember { mutableStateOf<Float?>(null) }

    val currentProgress = scrubValue ?: (if (duration > 0) position.toFloat() / duration else 0f)

    Slider(
        value = currentProgress.coerceIn(0f, 1f),
        onValueChange = { scrubValue = it },
        onValueChangeFinished = {
            val targetMs = ((scrubValue ?: 0f) * duration).toLong()
            player.seekTo(targetMs)
            scrubValue = null
        },
        colors = SliderDefaults.colors(
            thumbColor = accentColor,
            activeTrackColor = accentColor,
            inactiveTrackColor = Color.White.copy(alpha = 0.2f)
        ),
        modifier = Modifier.fillMaxWidth()
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val displayPosition = scrubValue?.let { (it * duration).toLong() } ?: position
        Text(
            text = formatTime(displayPosition),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = formatTime(duration),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
