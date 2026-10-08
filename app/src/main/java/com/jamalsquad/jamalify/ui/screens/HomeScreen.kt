package com.jamalsquad.jamalify.ui.screens

import androidx.compose.material3.IconButton
import androidx.compose.foundation.lazy.rememberLazyListState
import com.jamalsquad.jamalify.ui.components.PlaylistAction
import com.jamalsquad.jamalify.ui.viewmodel.FAVORITES_PLAYLIST_ID
import com.jamalsquad.jamalify.ui.components.PetRoam
import com.jamalsquad.jamalify.ui.components.PetKind
import com.jamalsquad.jamalify.ui.components.PetManager
import androidx.activity.compose.BackHandler
import com.jamalsquad.jamalify.ui.components.PlayerIcons
import com.jamalsquad.jamalify.ui.components.PlaylistDetail
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import com.jamalsquad.jamalify.JamalifyApp
import com.jamalsquad.jamalify.data.PlaylistSummary
import com.jamalsquad.jamalify.data.Song
import com.jamalsquad.jamalify.data.repo.RecommendationEngine.PersonalPlaylist
import com.jamalsquad.jamalify.ui.components.Artwork
import com.jamalsquad.jamalify.ui.components.EmptyState
import com.jamalsquad.jamalify.ui.components.HSpace
import com.jamalsquad.jamalify.ui.components.LoadingBox
import com.jamalsquad.jamalify.ui.components.SectionHeader
import com.jamalsquad.jamalify.ui.components.SongRow
import com.jamalsquad.jamalify.ui.components.SongSheetController
import com.jamalsquad.jamalify.ui.components.VSpace
import com.jamalsquad.jamalify.ui.viewmodel.HomeSection
import com.jamalsquad.jamalify.ui.viewmodel.HomeViewModel
import java.util.Calendar


@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
fun HomeScreen(
    currentSongId: String?,
    onPlay: (List<Song>, Int) -> Unit,
    sheet: SongSheetController,
    contentPadding: PaddingValues,
    onOpenSettings: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val trending by viewModel.trending.collectAsState()
    val sections by viewModel.sections.collectAsState()
    val dailyMixes by viewModel.dailyMixes.collectAsState()
    val recent by viewModel.recentlyPlayed.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val loadingMore by viewModel.loadingMore.collectAsState()
    val canLoadMore by viewModel.canLoadMore.collectAsState()
    val error by viewModel.error.collectAsState()

    var selectedFilter by remember { mutableStateOf("Semua") }
    var activeExploreSection by remember { mutableStateOf<HomeSection?>(null) }

    val personalPlaylists by viewModel.personalPlaylists.collectAsState()
    val suggestedPlaylists by viewModel.suggestedPlaylists.collectAsState()
    val openingPlaylist by viewModel.openingPlaylist.collectAsState()
    val context = LocalContext.current
    val player = (context.applicationContext as JamalifyApp).playerConnection

    val historyCount by viewModel.historyCount.collectAsState()
    val topArtistRecap by viewModel.topArtist.collectAsState()

    val listState = rememberLazyListState()
    val savedSections by viewModel.savedSections.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()

    // Playlist dibuka sebagai halaman penuh, bukan sheet, seperti desain
    // acuan. Daftar beranda tetap diingat posisinya lewat [listState].
    activeExploreSection?.let { section ->
        BackHandler { activeExploreSection = null }
        LaunchedEffect(section.id) { player.warmUp(section.songs) }
        PlaylistDetail(
            title = section.title,
            meta = "Playlist • ${section.songs.size} lagu",
            subtitle = section.subtitle,
            artworkUrl = section.songs.firstOrNull()?.thumbnail,
            songs = section.songs,
            currentSongId = currentSongId,
            onBack = { activeExploreSection = null },
            onPlay = onPlay,
            onShuffle = { onPlay(section.songs.shuffled(), 0) },
            onMore = sheet::show,
            contentPadding = contentPadding,
            actions = {
                val saved = section.id in savedSections
                PlaylistAction(
                    icon = if (saved) PlayerIcons.Check else PlayerIcons.SaveToLibrary,
                    contentDescription = if (saved) "Tersimpan di Library" else "Simpan ke Library",
                    onClick = {
                        if (!saved) {
                            viewModel.saveAsPlaylist(section)
                            Toast.makeText(context, "Disimpan ke Library", Toast.LENGTH_SHORT).show()
                        }
                    },
                    tint = if (saved) MaterialTheme.colorScheme.onSurface else Color.Unspecified
                )
            }
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding
        ) {
            // Spotify Greeting & Filter Pills
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = greeting(),
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onOpenSettings) {
                            Icon(PlayerIcons.Settings, contentDescription = "Pengaturan")
                        }
                    }
                    VSpace(12.dp)
                    // Filter Pills Row
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val filters = listOf("Semua", "Musik", "Mix Harian", "Favorit")
                        items(filters) { filter ->
                            val isSelected = filter == selectedFilter
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.clickable { selectedFilter = filter }
                            ) {
                                Text(
                                    text = filter,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Playlist personal: lagu yang didengar dikelompokkan menurut selera
            if ((selectedFilter == "Semua" || selectedFilter == "Musik") && personalPlaylists.isNotEmpty()) {
                item(key = "personal_playlists") {
                    Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
                        Text(
                            text = "Playlist untukmu",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            personalPlaylists.take(MAX_PLAYLIST_TILES).chunked(2).forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowItems.forEach { playlist ->
                                        PlaylistTile(
                                            playlist = playlist,
                                            isPlaying = playlist.songs.any { it.id == currentSongId },
                                            onClick = {
                                                if (playlist.id == "liked") {
                                                    onOpenPlaylist(FAVORITES_PLAYLIST_ID)
                                                    return@PlaylistTile
                                                }
                                                activeExploreSection = HomeSection(
                                                    id = playlist.id,
                                                    title = playlist.title,
                                                    songs = playlist.songs,
                                                    subtitle = playlist.subtitle
                                                )
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (rowItems.size == 1) {
                                        Box(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Playlist YouTube yang cocok dengan artis yang paling sering didengar
            if ((selectedFilter == "Semua" || selectedFilter == "Musik") && suggestedPlaylists.isNotEmpty()) {
                item(key = "suggested_playlists") {
                    Column(modifier = Modifier.padding(bottom = 12.dp)) {
                        SectionHeader("Playlist yang cocok dengan seleramu")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(suggestedPlaylists, key = { it.url }) { playlist ->
                                SuggestedPlaylistCard(
                                    playlist = playlist,
                                    loading = openingPlaylist == playlist.url,
                                    onClick = {
                                        viewModel.openPlaylist(playlist) { section ->
                                            if (section != null) {
                                                activeExploreSection = section
                                            } else {
                                                Toast.makeText(context, "Playlist gagal dimuat", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Daily Mix Section (Spotify Mix Harian Cards)
            if (selectedFilter == "Semua" || selectedFilter == "Mix Harian") {
                item {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        SectionHeader("Dibuat Untukmu — Mix Harian") {
                            TextButton(onClick = {
                                activeExploreSection = HomeSection(
                                    id = "daily_mixes",
                                    title = "Mix Harian Untukmu",
                                    songs = dailyMixes.flatMap { it.songs }.distinctBy { it.id }
                                        .ifEmpty { (trending + recent).distinctBy { it.id } }
                                )
                            }) {
                                Text("Lihat semua", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            if (dailyMixes.isNotEmpty()) {
                                itemsIndexed(dailyMixes, key = { _, mix -> mix.id }) { index, mix ->
                                    DailyMixCard(
                                        mixNumber = index + 1,
                                        subtitle = mix.songs.map { it.artist }.distinct().take(3).joinToString(", "),
                                        gradient = MIX_GRADIENTS[index % MIX_GRADIENTS.size],
                                        onClick = { onPlay(mix.songs, 0) }
                                    )
                                }
                            } else {
                                // Belum ada riwayat untuk dijadikan acuan.
                                item {
                                    DailyMixCard(
                                        mixNumber = 1,
                                        subtitle = "Pop Indonesia & Akustik",
                                        gradient = MIX_GRADIENTS[0],
                                        onClick = { onPlay(trending, 0) }
                                    )
                                }
                                item {
                                    DailyMixCard(
                                        mixNumber = 2,
                                        subtitle = "Indie & Hits Terbaru",
                                        gradient = MIX_GRADIENTS[1],
                                        onClick = { onPlay(trending.drop(10), 0) }
                                    )
                                }
                            }
                        }
                    }
                    VSpace(12.dp)
                }
            }

            // AI Mood Stations Section — lagu diambil langsung dari YouTube Music search, bukan filter lokal
            if (selectedFilter == "Semua" || selectedFilter == "Musik") {
                item {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        SectionHeader("Stasiun Radio Suasana Hati")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item {
                                MoodStationCard(
                                    title = "Fokus & Koding",
                                    subtitle = "Lofi & Deep Beats",
                                    onClick = { viewModel.moodSearch("lofi beats study chill instrumental") { songs -> if (songs.isNotEmpty()) onPlay(songs, 0) } }
                                )
                            }
                            item {
                                MoodStationCard(
                                    title = "Hujan & Santai",
                                    subtitle = "Indie Folk & Akustik",
                                    onClick = { viewModel.moodSearch("lagu akustik santai indie folk indonesia") { songs -> if (songs.isNotEmpty()) onPlay(songs, 0) } }
                                )
                            }
                            item {
                                MoodStationCard(
                                    title = "Workout Enerjik",
                                    subtitle = "EDM & Upbeat Hits",
                                    onClick = { viewModel.moodSearch("workout music EDM upbeat gym motivation") { songs -> if (songs.isNotEmpty()) onPlay(songs, 0) } }
                                )
                            }
                            item {
                                MoodStationCard(
                                    title = "Senja & Chill",
                                    subtitle = "Acoustic Pop & Jazz",
                                    onClick = { viewModel.moodSearch("acoustic pop jazz senja chill vibes") { songs -> if (songs.isNotEmpty()) onPlay(songs, 0) } }
                                )
                            }
                            item {
                                MoodStationCard(
                                    title = "Semangat Pagi",
                                    subtitle = "Pop Ceria & Hits",
                                    onClick = { viewModel.moodSearch("lagu semangat pagi pop ceria hits indonesia") { songs -> if (songs.isNotEmpty()) onPlay(songs, 0) } }
                                )
                            }
                        }
                    }
                    VSpace(12.dp)
                }
            }

            // Jamalify Recap / Wrapped Card — statistik diambil dari database riwayat, bukan ukuran list
            if (historyCount > 0 || recent.isNotEmpty() || favorites.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable {
                                    activeExploreSection = HomeSection(
                                        id = "wrapped_recap",
                                        title = "Rekap Selera Musikmu 📊",
                                        songs = (favorites + recent).distinctBy { it.id }
                                    )
                                },
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Jamalify Recap — Selera Musik 📊",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        VSpace(4.dp)
                                        val displayArtist = topArtistRecap ?: (favorites + recent).firstOrNull()?.artist ?: "Artis Pilihan"
                                        Text(
                                            text = "$historyCount× Diputar • Artis Favorit: $displayArtist",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(MaterialTheme.colorScheme.primary)
                                            .padding(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = "Lihat Rekap",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                    VSpace(12.dp)
                }
            }

            // Recent Listening Shelf
            if ((selectedFilter == "Semua" || selectedFilter == "Favorit") && recent.isNotEmpty()) {
                shelf(
                    section = HomeSection("recent", "Lanjutkan mendengarkan", recent),
                    onPlay = onPlay,
                    onExploreMore = { activeExploreSection = HomeSection("recent", "Lanjutkan mendengarkan", recent) }
                )
            }

            // Favorite Quick Picks List
            if ((selectedFilter == "Semua" || selectedFilter == "Favorit") && favorites.isNotEmpty()) {
                item {
                    SectionHeader("Lagu Favoritmu") {
                        Text(
                            text = "${favorites.size} lagu",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(favorites, key = { "fav_${it.id}" }) { song ->
                    SongRow(
                        song = song,
                        isPlaying = song.id == currentSongId,
                        onClick = { onPlay(favorites, favorites.indexOf(song)) },
                        onMore = { sheet.show(song) }
                    )
                }
                item { VSpace(12.dp) }
            }

            // Category Sections (Pop, Indie, Dangdut, K-Pop, Focus)
            if (selectedFilter == "Semua" || selectedFilter == "Musik") {
                sections.forEach { section ->
                    shelf(
                        section = section,
                        onPlay = onPlay,
                        onExploreMore = { activeExploreSection = section }
                    )
                }
            }

            // Trending Music Section
            if (selectedFilter == "Semua" || selectedFilter == "Musik") {
                item {
                    SectionHeader("Sedang tren & Populer") {
                        TextButton(onClick = {
                            activeExploreSection = HomeSection("trending_all", "Sedang tren & Populer", trending)
                        }) {
                            Text("Lihat semua", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
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

        // Peliharaan gentayangan di atas beranda, di dalam area aman
        // antara status bar dan mini player.
        val pet by PetManager.kind.collectAsState()
        val song by player.currentSong.collectAsState()
        if (pet != PetKind.NONE) PetRoam(
            isPlaying = isPlaying,
            kind = pet,
            songTitle = song?.title,
            // Karakter chibi berkepala besar, jadi digambar sedikit lebih besar.
            size = if (pet == PetKind.CAT) 52.dp else 64.dp,
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = contentPadding.calculateTopPadding() + 8.dp,
                    bottom = contentPadding.calculateBottomPadding(),
                    start = 8.dp,
                    end = 8.dp
                )
        )
    }
}

private const val MAX_PLAYLIST_TILES = 8

/** Ubin playlist personal di grid atas beranda, seperti pintasan Spotify. */
@Composable
private fun PlaylistTile(
    playlist: PersonalPlaylist,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.height(56.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (playlist.id == "liked") {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(PlayerIcons.HeartFilled, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                }
            } else {
                Artwork(url = playlist.songs.firstOrNull()?.thumbnail, size = 56.dp, corner = 0.dp)
            }
            HSpace(10.dp)
            Text(
                text = playlist.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            )
        }
    }
}

/** Kartu playlist YouTube; isinya baru diambil saat diketuk. */
@Composable
private fun SuggestedPlaylistCard(
    playlist: PlaylistSummary,
    loading: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(145.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !loading, onClick = onClick)
            .padding(bottom = 8.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Artwork(url = playlist.thumbnail, size = 145.dp, corner = 12.dp)
            if (loading) {
                Box(
                    modifier = Modifier
                        .size(145.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                }
            }
        }
        VSpace(8.dp)
        Text(
            text = playlist.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (playlist.author.isNotBlank()) {
            Text(
                text = playlist.author,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Spotify Dual-Tone Daily Mix Card */
// Monokrom, senada dengan palet aplikasi: gelap di kedua tema supaya teks
// putih di atasnya selalu terbaca.
private val MIX_GRADIENTS = listOf(
    listOf(Color(0xFF2B3345), Color(0xFF66708A)),
    listOf(Color(0xFF1F2633), Color(0xFF465169)),
    listOf(Color(0xFF363E52), Color(0xFF8A93A8))
)

@Composable
private fun DailyMixCard(
    mixNumber: Int,
    subtitle: String,
    gradient: List<Color>,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(145.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(145.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(brush = Brush.linearGradient(gradient))
                .padding(12.dp)
        ) {
            Column(modifier = Modifier.align(Alignment.BottomStart)) {
                Text(
                    text = "MIX HARIAN",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White.copy(alpha = 0.75f),
                    letterSpacing = 1.sp
                )
                Text(
                    text = "#$mixNumber",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }
        }
        VSpace(8.dp)
        Text(
            text = "Mix Harian $mixNumber",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Satu rak horizontal berisi kartu lagu. */
private fun androidx.compose.foundation.lazy.LazyListScope.shelf(
    section: HomeSection,
    onPlay: (List<Song>, Int) -> Unit,
    onExploreMore: () -> Unit
) {
    item(key = "header_${section.id}") {
        SectionHeader(section.title) {
            TextButton(onClick = onExploreMore) {
                Text("Lihat semua", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
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

@Composable
private fun MoodStationCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .size(width = 160.dp, height = 100.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(
                    PlayerIcons.PlayCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(26.dp)
                )
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
