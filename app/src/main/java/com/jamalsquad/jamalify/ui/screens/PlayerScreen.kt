package com.jamalsquad.jamalify.ui.screens

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import com.jamalsquad.jamalify.ui.components.PlayerIcons
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jamalsquad.jamalify.ui.components.GlassIconButton
import com.jamalsquad.jamalify.ui.components.glow
import com.jamalsquad.jamalify.ui.components.liquidGlass
import com.jamalsquad.jamalify.util.Thumbnails
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
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
import androidx.compose.foundation.layout.offset
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
import com.jamalsquad.jamalify.ui.components.PetKind
import com.jamalsquad.jamalify.ui.components.PetManager
import com.jamalsquad.jamalify.ui.components.PetPerch
import com.jamalsquad.jamalify.ui.components.AudioVisualizer
import com.jamalsquad.jamalify.ui.components.EmptyState
import com.jamalsquad.jamalify.ui.components.HSpace
import com.jamalsquad.jamalify.ui.components.HiResBadge
import com.jamalsquad.jamalify.ui.components.MiniPlayer
import com.jamalsquad.jamalify.ui.components.QueueSongRow
import com.jamalsquad.jamalify.ui.components.SoundModeSheet
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
    // Monokrom: yang aktif putih, sisanya abu-abu redup.
    val animatedAccentColor = MaterialTheme.colorScheme.onBackground
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var showMore by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
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
                    Icon(PlayerIcons.ChevronDown, contentDescription = "Tutup pemutar")
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Sedang Diputar",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    if (sleepTimerRemaining != null || isEndOfSongTimer) {
                        val timerText = if (isEndOfSongTimer) "Timer: Akhir Lagu"
                        else "Timer: ${formatTime(sleepTimerRemaining!!)}"
                        Text(
                            text = timerText,
                            style = MaterialTheme.typography.labelMedium,
                            color = muted
                        )
                    }
                }
                IconButton(onClick = { showQueue = true }) {
                    Icon(PlayerIcons.Queue, contentDescription = "Antrean")
                }
            }

            VSpace(12.dp)

            // Sampul mengisi sisa tinggi layar, jadi kontrol selalu turun
            // mendekati bottom bar alih-alih menyisakan celah kosong.
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                val artSize = minOf(maxWidth, maxHeight, 340.dp)
                Crossfade(
                    targetState = showLyrics,
                    animationSpec = tween(durationMillis = 300),
                    label = "artwork_lyrics_swap"
                ) { isLyrics ->
                    if (!isLyrics) {
                        val artScale by animateFloatAsState(
                            targetValue = if (isPlaying) 1f else 0.9f,
                            animationSpec = tween(300),
                            label = "artwork_scale"
                        )
                        Artwork(
                            url = current.thumbnail,
                            size = artSize,
                            corner = 28.dp,
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = artScale
                                    scaleY = artScale
                                }
                                .glow(alpha = 0.3f, spread = 0.9f)
                        )
                    } else {
                        InlineLyricsCard(
                            size = artSize,
                            corner = 28.dp,
                            songTitle = current.title,
                            artistName = current.artist,
                            durationSec = current.durationSec.toInt(),
                            currentPositionMs = position.value,
                            accentColor = animatedAccentColor,
                            onDismiss = { showLyrics = false }
                        )
                    }
                }
                // Peliharaan beranda ikut nongkrong di pojok sampul, sedikit menyembul.
                val pet by PetManager.kind.collectAsState()
                if (pet != PetKind.NONE && !showLyrics) PetPerch(
                    isPlaying = isPlaying,
                    kind = pet,
                    size = if (pet == PetKind.CAT) 48.dp else 58.dp,
                    songTitle = current.title,
                    modifier = Modifier
                        .size(artSize)
                        .offset(x = 12.dp, y = 16.dp)
                )
            }

            VSpace(24.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.toggleFavorite(current) }) {
                    Icon(
                        imageVector = if (isFavorite) PlayerIcons.HeartFilled else PlayerIcons.Heart,
                        contentDescription = "Favorit",
                        tint = if (isFavorite) animatedAccentColor else muted
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = current.title,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = current.artist,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
                        color = muted,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // Alat tambahan disimpan di balik "⋯", seperti desain acuan:
                // layar utama hanya berisi sampul, judul, dan kontrol putar.
                Box {
                    IconButton(onClick = { showMore = true }) {
                        Icon(PlayerIcons.More, contentDescription = "Lainnya", tint = muted)
                    }
                    DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                        MoreItem(
                            label = if (downloadState == DownloadState.COMPLETED) "Sudah diunduh" else "Unduh",
                            icon = if (downloadState == DownloadState.COMPLETED) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
                            active = downloadState == DownloadState.RUNNING || downloadState == DownloadState.QUEUED
                        ) { showMore = false; viewModel.download() }
                        MoreItem("Lirik", Icons.Rounded.Lyrics, showLyrics) { showMore = false; showLyrics = !showLyrics }
                        MoreItem("Karaoke", Icons.Rounded.MusicNote, karaokeMode) { showMore = false; player.karaokeMode.toggleKaraoke() }
                        MoreItem("Mode suara", Icons.Rounded.GraphicEq, soundMode != SoundMode.NORMAL) { showMore = false; showSoundMode = true }
                        MoreItem("Sleep timer", Icons.Rounded.Bedtime, sleepTimerRemaining != null || isEndOfSongTimer) { showMore = false; showSleepTimer = true }
                        MoreItem("Equalizer", Icons.Rounded.Tune, false) { showMore = false; player.openEqualizer(context) }
                    }
                }
            }

            VSpace(20.dp)

            WaveformProgress(player = player, seed = current.id)

            VSpace(16.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = player::toggleShuffle) {
                    Icon(
                        imageVector = PlayerIcons.Shuffle,
                        contentDescription = "Acak",
                        tint = if (shuffle) animatedAccentColor else muted
                    )
                }
                IconButton(onClick = player::previous) {
                    Icon(
                        imageVector = PlayerIcons.SkipPrevious,
                        contentDescription = "Sebelumnya",
                        modifier = Modifier.size(30.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .glow(alpha = 0.3f, spread = 1.1f)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = player::togglePlayPause),
                    contentAlignment = Alignment.Center
                ) {
                    if (isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 3.dp
                        )
                    } else {
                        Icon(
                            imageVector = if (isPlaying) PlayerIcons.Pause else PlayerIcons.Play,
                            contentDescription = if (isPlaying) "Jeda" else "Putar",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                IconButton(onClick = player::next) {
                    Icon(
                        imageVector = PlayerIcons.SkipNext,
                        contentDescription = "Berikutnya",
                        modifier = Modifier.size(30.dp)
                    )
                }
                IconButton(onClick = player::cycleRepeatMode) {
                    Icon(
                        imageVector = if (repeatMode == Player.REPEAT_MODE_ONE) PlayerIcons.RepeatOne else PlayerIcons.Repeat,
                        contentDescription = "Ulang",
                        tint = if (repeatMode != Player.REPEAT_MODE_OFF) animatedAccentColor else muted
                    )
                }
            }

            if (soundMode != SoundMode.NORMAL) {
                VSpace(20.dp)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { showSoundMode = true }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    if (soundMode.hiRes) {
                        HiResBadge(height = 24.dp)
                    } else {
                        Text(
                            text = "${soundMode.emoji} ${soundMode.label}",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
            VSpace(24.dp)
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
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(36.dp)
                    )
                    VSpace(8.dp)
                    Text(
                        "Lirik belum tersedia",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Ketuk untuk kembali ke cover album",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            color = MaterialTheme.colorScheme.onSurface
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
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreItem(label: String, icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    val tint = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    DropdownMenuItem(
        text = {
            Text(label, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
        },
        leadingIcon = { Icon(icon, contentDescription = null, tint = tint) },
        onClick = onClick
    )
}

/**
 * Seekbar berbentuk gelombang suara. Bentuknya bukan analisis audio sungguhan
 * — itu butuh mengunduh seluruh berkas — melainkan pola acak yang ditentukan
 * ID lagu, jadi tiap lagu punya bentuk sendiri dan selalu sama.
 *
 * Satu-satunya bagian yang disusun ulang tiap posisi berubah.
 */
@UnstableApi
@Composable
private fun WaveformProgress(player: PlayerConnection, seed: String) {
    val position by player.position.collectAsState()
    val duration by player.duration.collectAsState()
    var scrubValue by remember { mutableStateOf<Float?>(null) }
    val currentDuration by rememberUpdatedState(duration)
    val bars = remember(seed) { waveformBars(seed) }

    val target = (scrubValue ?: if (duration > 0) position.toFloat() / duration else 0f).coerceIn(0f, 1f)
    // Posisi datang tiap 500ms; dianimasikan supaya isian mengalir, bukan melompat.
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = if (scrubValue != null) snap() else tween(500, easing = LinearEasing),
        label = "waveform_progress"
    )
    val played = MaterialTheme.colorScheme.onBackground
    val unplayed = played.copy(alpha = 0.28f)

    fun seek(fraction: Float) {
        player.seekTo((fraction.coerceIn(0f, 1f) * currentDuration).toLong())
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .pointerInput(Unit) {
                detectTapGestures { seek(it.x / size.width) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { scrubValue = (it.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = {
                        scrubValue?.let(::seek)
                        scrubValue = null
                    },
                    onDragCancel = { scrubValue = null }
                ) { change, _ ->
                    scrubValue = (change.position.x / size.width).coerceIn(0f, 1f)
                }
            }
    ) {
        val gap = 1.6.dp.toPx()
        val barWidth = (size.width - gap * (bars.size - 1)) / bars.size
        bars.forEachIndexed { index, height ->
            val barHeight = size.height * height
            val center = (index + 0.5f) / bars.size
            drawRoundRect(
                color = if (center <= progress) played else unplayed,
                topLeft = Offset(index * (barWidth + gap), (size.height - barHeight) / 2),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2)
            )
        }
    }

    VSpace(6.dp)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val displayPosition = scrubValue?.let { (it * duration).toLong() } ?: position
        Text(
            text = formatTime(displayPosition),
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = formatTime(duration),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun waveformBars(seed: String, count: Int = WAVEFORM_BARS): List<Float> {
    val random = Random(seed.hashCode())
    var level = 0.5f
    return List(count) { index ->
        // Dihaluskan dengan nilai sebelumnya supaya naik-turunnya mirip
        // lagu, lalu diberi selubung agar tengah lagu lebih "ramai".
        level = level * 0.25f + (0.1f + random.nextFloat() * 0.9f) * 0.75f
        val envelope = 0.1f + 0.9f * sin(index.toFloat() / count * PI.toFloat())
        (level * envelope).coerceIn(0.06f, 1f)
    }
}

private const val WAVEFORM_BARS = 96

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes >= 60) "%d:%02d:%02d".format(minutes / 60, minutes % 60, seconds)
    else "%d:%02d".format(minutes, seconds)
}
