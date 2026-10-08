package com.jamalsquad.jamalify.desktop.ui.preview

import com.jamalsquad.jamalify.data.Song
import com.jamalsquad.jamalify.desktop.core.AppSettings
import com.jamalsquad.jamalify.desktop.core.DesktopTheme
import com.jamalsquad.jamalify.desktop.core.HomeSection
import com.jamalsquad.jamalify.desktop.core.LibraryStore
import com.jamalsquad.jamalify.desktop.core.LocalPlaylist
import com.jamalsquad.jamalify.desktop.core.MusicCatalog
import com.jamalsquad.jamalify.desktop.core.PlayerController
import com.jamalsquad.jamalify.desktop.core.PlayerUiState
import com.jamalsquad.jamalify.ui.components.PetKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/*
 * Implementasi palsu di memori, hanya untuk mengembangkan UI tanpa VLC,
 * database, atau jaringan YouTube (kecuali sampulnya).
 */

private fun song(id: String, title: String, artist: String, sec: Long) =
    Song(id, title, artist, "https://i.ytimg.com/vi/$id/hqdefault.jpg", sec)

val FakeSongs = listOf(
    song("dQw4w9WgXcQ", "Never Gonna Give You Up", "Rick Astley", 213),
    song("kJQP7kiw5Fk", "Despacito", "Luis Fonsi ft. Daddy Yankee", 282),
    song("JGwWNGJdvx8", "Shape of You", "Ed Sheeran", 263),
    song("fJ9rUzIMcZQ", "Bohemian Rhapsody", "Queen", 355),
    song("9bZkp7q19f0", "Gangnam Style", "PSY", 252),
    song("hTWKbfoikeg", "Smells Like Teen Spirit", "Nirvana", 301),
    song("OPf0YbXqDm0", "Uptown Funk", "Mark Ronson ft. Bruno Mars", 270),
    song("YQHsXMglC9A", "Hello", "Adele", 366),
)

class FakeCatalog : MusicCatalog {
    override suspend fun search(query: String): List<Song> {
        delay(300)
        return FakeSongs.filter { query.isBlank() || it.title.contains(query, true) || it.artist.contains(query, true) }
            .ifEmpty { FakeSongs.shuffled().take(4) }
    }

    override suspend fun home(): List<HomeSection> {
        delay(400)
        return listOf(
            HomeSection("Lagi tren", FakeSongs),
            HomeSection("Klasik sepanjang masa", FakeSongs.reversed()),
            HomeSection("Mix harian", FakeSongs.shuffled()),
        )
    }

    override suspend fun related(song: Song): List<Song> = FakeSongs.filter { it.id != song.id }

    override suspend fun lyrics(song: Song): String? {
        delay(200)
        if (song.id != FakeSongs[0].id) return null
        return """
            [00:00.50]We're no strangers to love
            [00:04.00]You know the rules and so do I
            [00:08.00]A full commitment's what I'm thinking of
            [00:12.00]You wouldn't get this from any other guy
            [00:16.00]I just wanna tell you how I'm feeling
            [00:20.00]Gotta make you understand
            [00:24.00]Never gonna give you up
            [00:26.00]Never gonna let you down
        """.trimIndent()
    }
}

class FakePlayer : PlayerController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(PlayerUiState(volume = 0.8f))
    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    private val _index = MutableStateFlow(-1)
    override val state: StateFlow<PlayerUiState> = _state.asStateFlow()
    override val queue: StateFlow<List<Song>> = _queue.asStateFlow()
    override val queueIndex: StateFlow<Int> = _index.asStateFlow()

    init {
        // Detak palsu supaya seek bar dan lirik sinkron terlihat bergerak.
        scope.launch {
            while (isActive) {
                delay(250)
                val s = _state.value
                if (s.isPlaying && s.current != null) {
                    val pos = s.positionMs + 250
                    if (pos >= s.durationMs) next() else _state.update { it.copy(positionMs = pos) }
                }
            }
        }
    }

    private fun load(index: Int) {
        val song = _queue.value.getOrNull(index) ?: return
        _index.value = index
        _state.update { it.copy(current = song, isPlaying = true, positionMs = 0, durationMs = song.durationSec * 1000) }
    }

    override fun playSongs(songs: List<Song>, startIndex: Int) {
        _queue.value = songs
        load(startIndex)
    }

    override fun playNext(song: Song) {
        val q = _queue.value.toMutableList()
        q.add((_index.value + 1).coerceAtMost(q.size), song)
        _queue.value = q
        if (_index.value < 0) load(0)
    }

    override fun addToQueue(song: Song) {
        _queue.value = _queue.value + song
        if (_index.value < 0) load(0)
    }

    override fun removeFromQueue(index: Int) {
        val q = _queue.value.toMutableList()
        if (index !in q.indices) return
        q.removeAt(index)
        _queue.value = q
        if (index < _index.value) _index.value -= 1
    }

    override fun skipTo(index: Int) = load(index)
    override fun togglePlay() = _state.update { it.copy(isPlaying = it.current != null && !it.isPlaying) }
    override fun next() {
        val i = _index.value
        when {
            _state.value.repeatOne -> load(i)
            _state.value.shuffle && _queue.value.size > 1 -> load(_queue.value.indices.filter { it != i }.random())
            i + 1 < _queue.value.size -> load(i + 1)
            else -> _state.update { it.copy(isPlaying = false, positionMs = 0) }
        }
    }
    override fun previous() {
        if (_state.value.positionMs > 3000 || _index.value <= 0) seekTo(0) else load(_index.value - 1)
    }
    override fun seekTo(positionMs: Long) = _state.update { it.copy(positionMs = positionMs) }
    override fun setVolume(volume: Float) = _state.update { it.copy(volume = volume) }
    override fun toggleShuffle() = _state.update { it.copy(shuffle = !it.shuffle) }
    override fun toggleRepeatOne() = _state.update { it.copy(repeatOne = !it.repeatOne) }
    override fun release() {}
}

class FakeLibrary : LibraryStore {
    private val favs = MutableStateFlow(FakeSongs.take(3))
    private val recent = MutableStateFlow(FakeSongs.drop(2).take(5))
    private val lists = MutableStateFlow(
        mapOf(1L to ("Lagu santai" to FakeSongs.take(4)), 2L to ("Ngoding" to FakeSongs.takeLast(3)))
    )
    private var nextId = 3L

    override val favorites: Flow<List<Song>> = favs
    override val recentlyPlayed: Flow<List<Song>> = recent
    override val playlists: Flow<List<LocalPlaylist>> = lists.map { m ->
        m.map { (id, p) -> LocalPlaylist(id, p.first, p.second.size, p.second.firstOrNull()?.thumbnail) }
    }

    override fun isFavorite(songId: String): Flow<Boolean> = favs.map { l -> l.any { it.id == songId } }
    override suspend fun toggleFavorite(song: Song) = favs.update { l ->
        if (l.any { it.id == song.id }) l.filterNot { it.id == song.id } else listOf(song) + l
    }
    override fun playlistSongs(playlistId: Long): Flow<List<Song>> = lists.map { it[playlistId]?.second.orEmpty() }
    override suspend fun createPlaylist(name: String): Long {
        val id = nextId++
        lists.update { it + (id to (name to emptyList())) }
        return id
    }
    override suspend fun renamePlaylist(playlistId: Long, name: String) =
        lists.update { m -> m[playlistId]?.let { m + (playlistId to (name to it.second)) } ?: m }
    override suspend fun deletePlaylist(playlistId: Long) = lists.update { it - playlistId }
    override suspend fun addToPlaylist(playlistId: Long, song: Song) = lists.update { m ->
        val p = m[playlistId] ?: return@update m
        if (p.second.any { it.id == song.id }) m else m + (playlistId to (p.first to p.second + song))
    }
    override suspend fun removeFromPlaylist(playlistId: Long, songId: String) = lists.update { m ->
        val p = m[playlistId] ?: return@update m
        m + (playlistId to (p.first to p.second.filterNot { it.id == songId }))
    }
    override suspend fun recordPlayStart(song: Song): Long {
        recent.update { l -> listOf(song) + l.filterNot { it.id == song.id } }
        return 0
    }
    override suspend fun recordPlayEnd(historyId: Long, listenedMs: Long, completed: Boolean) {}
}

class FakeSettings : AppSettings {
    private val _pet = MutableStateFlow(PetKind.DUO)
    private val _theme = MutableStateFlow(DesktopTheme.DARK)
    private val _volume = MutableStateFlow(0.8f)
    override val pet: StateFlow<PetKind> = _pet
    override val theme: StateFlow<DesktopTheme> = _theme
    override val volume: StateFlow<Float> = _volume
    override fun setPet(kind: PetKind) { _pet.value = kind }
    override fun setTheme(theme: DesktopTheme) { _theme.value = theme }
    override fun setVolume(volume: Float) { _volume.value = volume }
}
