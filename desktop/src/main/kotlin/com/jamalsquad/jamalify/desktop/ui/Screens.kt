package com.jamalsquad.jamalify.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jamalsquad.jamalify.data.Song
import com.jamalsquad.jamalify.desktop.core.AppSettings
import com.jamalsquad.jamalify.desktop.core.DesktopTheme
import com.jamalsquad.jamalify.desktop.core.HomeSection
import com.jamalsquad.jamalify.desktop.core.LibraryStore
import com.jamalsquad.jamalify.desktop.core.LocalPlaylist
import com.jamalsquad.jamalify.desktop.core.MusicCatalog
import com.jamalsquad.jamalify.ui.components.NavIcons
import com.jamalsquad.jamalify.ui.components.NewPlaylistDialog
import com.jamalsquad.jamalify.ui.components.PetKind
import com.jamalsquad.jamalify.ui.components.PetPerch
import com.jamalsquad.jamalify.ui.components.PetRoam
import com.jamalsquad.jamalify.ui.components.PlayerIcons
import com.jamalsquad.jamalify.ui.components.glassCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

/** Memutar [songs] mulai dari indeks tertentu. */
typealias PlayList = (List<Song>, Int) -> Unit

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 4..10 -> "Selamat pagi"
    in 11..14 -> "Selamat siang"
    in 15..18 -> "Selamat sore"
    else -> "Selamat malam"
}

@Composable
fun HomeView(
    catalog: MusicCatalog,
    library: LibraryStore,
    pet: PetKind,
    currentSong: Song?,
    isPlaying: Boolean,
    onPlay: PlayList,
    contentPadding: PaddingValues
) {
    val warmUp = LocalSongActions.current.player::warmUp
    var sections by remember { mutableStateOf<List<HomeSection>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    LaunchedEffect(reload) {
        error = null
        runCatching { catalog.home() }
            .onSuccess { sections = it; it.firstOrNull()?.let { s -> warmUp(s.songs) } }
            .onFailure { error = it.message ?: "Gagal memuat beranda" }
    }
    val recent by library.recentlyPlayed.collectAsState(emptyList())

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
            item { ScreenTitle(greeting()) }
            if (recent.isNotEmpty()) item { SongShelf("Baru diputar", recent.take(20), currentSong, onPlay) }
            when {
                error != null && sections == null -> item {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        EmptyState("Beranda gagal dimuat", error)
                        OutlinedButton(onClick = { reload++ }) { Text("Coba lagi") }
                    }
                }
                sections == null -> item {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                else -> items(sections!!, key = { it.title }) { s -> SongShelf(s.title, s.songs, currentSong, onPlay) }
            }
        }
        if (pet != PetKind.NONE) {
            // Peliharaan gentayangan di atas beranda; hanya badannya yang menangkap klik.
            PetRoam(
                isPlaying = isPlaying, kind = pet, songTitle = currentSong?.title,
                modifier = Modifier.fillMaxSize().padding(contentPadding)
            )
        }
    }
}

@Composable
private fun SongShelf(title: String, songs: List<Song>, current: Song?, onPlay: PlayList) {
    Column {
        SectionTitle(title)
        LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(songs, key = { i, s -> "$i-${s.id}" }) { i, song ->
                SongCard(song, onClick = { onPlay(songs, i) }, isCurrent = song.id == current?.id)
            }
        }
    }
}

@Composable
fun SearchView(
    catalog: MusicCatalog,
    currentSong: Song?,
    onPlay: PlayList,
    focusRequester: FocusRequester,
    contentPadding: PaddingValues
) {
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Song>>(emptyList()) }
    val warmUp = LocalSongActions.current.player::warmUp
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Debounce: cari setelah pengguna berhenti mengetik, bukan per huruf.
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.isEmpty()) { results = emptyList(); loading = false; error = null; return@LaunchedEffect }
        delay(400)
        loading = true
        error = null
        runCatching { catalog.search(q) }
            .onSuccess { results = it; warmUp(it) }
            .onFailure { error = it.message ?: "Pencarian gagal" }
        loading = false
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            Column(Modifier.padding(horizontal = 24.dp).padding(top = 20.dp, bottom = 12.dp)) {
                Text("Cari", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(14.dp))
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Lagu, artis, atau album") },
                    leadingIcon = { Icon(NavIcons.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Hapus") }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().focusRequester(focusRequester)
                )
            }
        }
        when {
            loading -> item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            error != null -> item { EmptyState("Pencarian gagal", error) }
            query.isBlank() -> item { EmptyState("Cari lagu favoritmu", "Tekan Ctrl+F dari mana saja untuk langsung mencari") }
            results.isEmpty() -> item { EmptyState("Tidak ada hasil", "Coba kata kunci lain") }
            else -> itemsIndexed(results, key = { i, s -> "$i-${s.id}" }) { i, song ->
                SongRow(song, onClick = { onPlay(results, i) }, isCurrent = song.id == currentSong?.id)
            }
        }
    }
}

private enum class LibraryTab(val label: String) { FAVORITES("Favorit"), RECENT("Baru diputar"), PLAYLISTS("Playlist") }

@Composable
fun LibraryView(library: LibraryStore, currentSong: Song?, onPlay: PlayList, contentPadding: PaddingValues) {
    var tab by rememberSaveable { mutableStateOf(LibraryTab.FAVORITES) }
    var openPlaylist by remember { mutableStateOf<Long?>(null) }
    var creating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val favorites by library.favorites.collectAsState(emptyList())
    val recent by library.recentlyPlayed.collectAsState(emptyList())
    val playlists by library.playlists.collectAsState(emptyList())

    openPlaylist?.let { id ->
        val playlist = playlists.firstOrNull { it.id == id }
        if (playlist != null) {
            PlaylistDetailView(playlist, library, currentSong, onPlay, onBack = { openPlaylist = null }, contentPadding)
            return
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item { ScreenTitle("Pustaka") }
        item {
            Row(Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LibraryTab.entries.forEach { t -> Pill(t.label, t == tab) { tab = t } }
            }
        }
        when (tab) {
            LibraryTab.FAVORITES ->
                if (favorites.isEmpty()) item { EmptyState("Belum ada favorit", "Klik ikon hati atau menu ⋮ untuk menyimpan lagu") }
                else itemsIndexed(favorites, key = { _, s -> s.id }) { i, s -> SongRow(s, { onPlay(favorites, i) }, isCurrent = s.id == currentSong?.id) }
            LibraryTab.RECENT ->
                if (recent.isEmpty()) item { EmptyState("Belum ada riwayat", "Lagu yang kamu putar muncul di sini") }
                else itemsIndexed(recent, key = { i, s -> "$i-${s.id}" }) { i, s -> SongRow(s, { onPlay(recent, i) }, isCurrent = s.id == currentSong?.id) }
            LibraryTab.PLAYLISTS -> {
                item {
                    PlaylistItem(null, "Playlist baru", "Buat playlist kosong") { creating = true }
                }
                items(playlists, key = { it.id }) { p ->
                    PlaylistItem(p.cover, p.name, "${p.songCount} lagu") { openPlaylist = p.id }
                }
            }
        }
    }
    if (creating) {
        NewPlaylistDialog(
            onDismiss = { creating = false },
            onConfirm = { name -> creating = false; scope.launch { openPlaylist = library.createPlaylist(name) } }
        )
    }
}

@Composable
private fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Text(
        label,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
        color = if (selected) colors.onPrimary else colors.onSurface,
        modifier = Modifier.clip(RoundedCornerShape(20.dp))
            .background(if (selected) colors.primary else colors.surfaceContainerHigh)
            .clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun PlaylistItem(cover: String?, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (cover == null && title == "Playlist baru") {
            Box(Modifier.size(52.dp).glassCard(RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Icon(PlayerIcons.Plus, null)
            }
        } else Artwork(cover, size = 52.dp, corner = 10.dp)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PlaylistDetailView(
    playlist: LocalPlaylist,
    library: LibraryStore,
    currentSong: Song?,
    onPlay: PlayList,
    onBack: () -> Unit,
    contentPadding: PaddingValues
) {
    val songs by remember(playlist.id) { library.playlistSongs(playlist.id) }.collectAsState(emptyList())
    val warmUp = LocalSongActions.current.player::warmUp
    LaunchedEffect(songs) { warmUp(songs) }
    val scope = rememberCoroutineScope()
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            IconButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp, top = 12.dp)) {
                Icon(PlayerIcons.ChevronLeft, "Kembali")
            }
        }
        item {
            Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
                Artwork(songs.firstOrNull()?.thumbnail ?: playlist.cover, size = 160.dp, corner = 16.dp)
                Spacer(Modifier.width(24.dp))
                Column {
                    Text("Playlist • ${songs.size} lagu", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(playlist.name, style = MaterialTheme.typography.headlineLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { onPlay(songs, 0) }, enabled = songs.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onBackground)
                        ) { Icon(PlayerIcons.Play, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Putar") }
                        OutlinedButton(onClick = { onPlay(songs.shuffled(), 0) }, enabled = songs.isNotEmpty()) {
                            Icon(PlayerIcons.Shuffle, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Acak")
                        }
                        IconButton(onClick = { renaming = true }) { Icon(PlayerIcons.Edit, "Ganti nama") }
                        IconButton(onClick = { deleting = true }) { Icon(PlayerIcons.Trash, "Hapus playlist") }
                    }
                }
            }
        }
        if (songs.isEmpty()) item { EmptyState("Playlist masih kosong", "Tambahkan lagu lewat klik kanan → Tambah ke playlist") }
        itemsIndexed(songs, key = { _, s -> s.id }) { i, s ->
            SongRow(
                s, { onPlay(songs, i) }, number = i + 1, isCurrent = s.id == currentSong?.id,
                extraMenu = listOf("Hapus dari playlist" to { scope.launch { library.removeFromPlaylist(playlist.id, s.id) }; Unit })
            )
        }
    }

    if (renaming) {
        NewPlaylistDialog(
            onDismiss = { renaming = false },
            onConfirm = { name -> renaming = false; scope.launch { library.renamePlaylist(playlist.id, name) } },
            title = "Ganti nama playlist", initialName = playlist.name, confirmLabel = "Simpan"
        )
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Hapus playlist?") },
            text = { Text("\"${playlist.name}\" akan dihapus. Lagunya tetap ada di favorit dan riwayat.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    onBack()
                    scope.launch { library.deletePlaylist(playlist.id) }
                }) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Batal") } }
        )
    }
}

@Composable
fun SettingsView(settings: AppSettings, contentPadding: PaddingValues) {
    val pet by settings.pet.collectAsState()
    val theme by settings.theme.collectAsState()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item { ScreenTitle("Pengaturan") }
        item {
            SettingsGroup("Tema") {
                DesktopTheme.entries.forEach { t ->
                    SettingsRow(t.label, null, selected = t == theme) { settings.setTheme(t) }
                }
            }
        }
        item {
            SettingsGroup("Peliharaan") {
                PetKind.entries.forEach { k ->
                    SettingsRow(k.label, k.description, selected = k == pet) { settings.setPet(k) }
                }
            }
        }
        if (pet != PetKind.NONE) item {
            // Pratinjau kecil supaya pilihan langsung terlihat tanpa pindah ke Beranda.
            Box(Modifier.padding(horizontal = 24.dp, vertical = 8.dp).size(140.dp).glassCard(RoundedCornerShape(18.dp))) {
                PetPerch(isPlaying = true, kind = pet, size = 64.dp, modifier = Modifier.fillMaxSize().padding(24.dp))
            }
        }
        item {
            SettingsGroup("Pintasan keyboard") {
                listOf("Spasi" to "Putar / jeda", "Ctrl + →" to "Lagu berikutnya", "Ctrl + ←" to "Lagu sebelumnya", "Ctrl + F" to "Cari")
                    .forEach { (key, desc) -> SettingsRow(desc, key, selected = false, onClick = null) }
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(Modifier.widthIn(max = 720.dp).padding(horizontal = 24.dp, vertical = 10.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) { content() }
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String?, selected: Boolean, onClick: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected) {
            Icon(
                Icons.Rounded.Check, "Terpilih", tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).padding(3.dp)
            )
        }
    }
}
