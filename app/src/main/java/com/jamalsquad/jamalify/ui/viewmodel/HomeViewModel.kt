package com.jamalsquad.jamalify.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.jamalsquad.jamalify.JamalifyApp
import com.jamalsquad.jamalify.data.PlaylistSummary
import com.jamalsquad.jamalify.data.Song
import com.jamalsquad.jamalify.data.repo.RecommendationEngine.PersonalPlaylist
import com.jamalsquad.jamalify.data.spotify.SpotifyUserApi
import com.jamalsquad.jamalify.data.spotify.SpotifyAuth
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Satu rak rekomendasi horizontal di beranda. */
data class HomeSection(
    val id: String,
    val title: String,
    val songs: List<Song>,
    val subtitle: String? = null
)

@UnstableApi
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = (app as JamalifyApp).repository

    private val _trending = MutableStateFlow<List<Song>>(emptyList())
    val trending: StateFlow<List<Song>> = _trending.asStateFlow()

    private val _sections = MutableStateFlow<List<HomeSection>>(emptyList())
    val sections: StateFlow<List<HomeSection>> = _sections.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore.asStateFlow()

    private val _canLoadMore = MutableStateFlow(false)
    val canLoadMore: StateFlow<Boolean> = _canLoadMore.asStateFlow()

    /** Kosong untuk pengguna baru; layar lalu memakai kartu mix cadangan. */
    private val _dailyMixes = MutableStateFlow<List<HomeSection>>(emptyList())
    val dailyMixes: StateFlow<List<HomeSection>> = _dailyMixes.asStateFlow()

    /** Playlist hasil pengelompokan selera; kosong untuk pengguna baru. */
    private val _personalPlaylists = MutableStateFlow<List<PersonalPlaylist>>(emptyList())
    val personalPlaylists: StateFlow<List<PersonalPlaylist>> = _personalPlaylists.asStateFlow()

    /** Playlist YouTube Music yang dicari dari artis yang paling disukai. */
    private val _suggestedPlaylists = MutableStateFlow<List<PlaylistSummary>>(emptyList())
    val suggestedPlaylists: StateFlow<List<PlaylistSummary>> = _suggestedPlaylists.asStateFlow()

    /** URL playlist yang sedang diambil isinya, untuk indikator di kartunya. */
    private val _openingPlaylist = MutableStateFlow<String?>(null)
    val openingPlaylist: StateFlow<String?> = _openingPlaylist.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val recentlyPlayed = repository.recentlyPlayed()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favorites = repository.favorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _historyCount = MutableStateFlow(0)
    val historyCount: StateFlow<Int> = _historyCount.asStateFlow()

    private val _topArtist = MutableStateFlow<String?>(null)
    val topArtist: StateFlow<String?> = _topArtist.asStateFlow()

    init {
        refresh()
        loadRecapStats()
    }

    fun refresh() {
        loadPlaylists()
        viewModelScope.launch {
            _loading.value = true
            _error.value = null

            // Rak-rak ini saling bebas, jadi diambil berbarengan dengan batas waktu
            // agar beranda tidak pernah macet di indikator pemuatan.
            val shelfJobs = SHELF_QUERIES.map { (title, query) ->
                async {
                    HomeSection(
                        id = query,
                        title = title,
                        songs = runCatching {
                            kotlinx.coroutines.withTimeoutOrNull(8000L) { repository.shelf(query) }
                        }.getOrNull() ?: emptyList()
                    )
                }
            }
            val trendingJob = async {
                runCatching {
                    kotlinx.coroutines.withTimeoutOrNull(10000L) { repository.trending() }
                }.getOrNull() ?: emptyList()
            }
            val personalShelvesJob = async {
                runCatching {
                    kotlinx.coroutines.withTimeoutOrNull(12000L) { personalShelves() }
                }.getOrNull() ?: emptyList()
            }
            val mixesJob = async {
                runCatching {
                    kotlinx.coroutines.withTimeoutOrNull(12000L) { dailyMixes() }
                }.getOrNull() ?: emptyList()
            }
            val personalJob = async {
                runCatching {
                    kotlinx.coroutines.withTimeoutOrNull(8000L) { fromSpotifyTaste() }
                }.getOrNull()
            }

            val trendingResult = trendingJob.await()
            _trending.value = trendingResult
            _canLoadMore.value = repository.hasMoreTrending() && trendingResult.isNotEmpty()

            val shelves = buildList {
                addAll(personalShelvesJob.await())
                personalJob.await()?.let { add(it) }
                addAll(shelfJobs.map { it.await() })
            }.filter { it.songs.isNotEmpty() }

            _dailyMixes.value = mixesJob.await()

            _sections.value = shelves

            if (shelves.isEmpty() && trendingResult.isEmpty()) {
                _error.value = "Tidak ada yang bisa dimuat. Cek koneksi internet lalu coba lagi."
            }
            _loading.value = false
        }
    }

    fun loadMore() {
        if (_loadingMore.value || !_canLoadMore.value) return
        viewModelScope.launch {
            _loadingMore.value = true
            val more = runCatching { repository.trendingMore() }.getOrDefault(emptyList())
            if (more.isNotEmpty()) {
                val existing = _trending.value.map { it.id }.toSet()
                _trending.value = _trending.value + more.filterNot { it.id in existing }
            }
            _canLoadMore.value = repository.hasMoreTrending() && more.isNotEmpty()
            _loadingMore.value = false
        }
    }

    /**
     * Playlist tampil dua tahap: versi dari database dulu supaya beranda
     * langsung terisi, lalu diganti versi yang sudah ditambah lagu serupa.
     */
    private fun loadPlaylists() {
        val engine = repository.recommendations
        viewModelScope.launch {
            _personalPlaylists.value = runCatching { engine.personalPlaylists(enrich = false) }
                .getOrDefault(emptyList())
            runCatching {
                kotlinx.coroutines.withTimeoutOrNull(15000L) { engine.personalPlaylists(enrich = true) }
            }.getOrNull()?.let { _personalPlaylists.value = it }
        }
        viewModelScope.launch {
            _suggestedPlaylists.value = runCatching {
                kotlinx.coroutines.withTimeoutOrNull(12000L) { suggestedPlaylists() }
            }.getOrNull() ?: emptyList()
        }
    }

    private suspend fun suggestedPlaylists(): List<PlaylistSummary> = coroutineScope {
        val artists = repository.recommendations.favoriteArtists()
        if (artists.isEmpty()) return@coroutineScope emptyList()
        val perArtist = artists.map { artist ->
            async { repository.searchPlaylists(artist, limit = 5) }
        }.awaitAll()
        // Diselang-seling supaya playlist dari artis teratas tidak memborong rak.
        buildList {
            for (i in 0 until (perArtist.maxOfOrNull { it.size } ?: 0)) {
                perArtist.forEach { list -> list.getOrNull(i)?.let { add(it) } }
            }
        }.distinctBy { it.url }.take(MAX_SUGGESTED_PLAYLISTS)
    }

    /** Ambil isi playlist YouTube; [onResult] menerima null kalau gagal atau kosong. */
    fun openPlaylist(playlist: PlaylistSummary, onResult: (HomeSection?) -> Unit) {
        if (_openingPlaylist.value != null) return
        viewModelScope.launch {
            _openingPlaylist.value = playlist.url
            val songs = runCatching {
                repository.youtubePlaylist(playlist.url, maxSongs = 100).songs
            }.getOrDefault(emptyList())
            _openingPlaylist.value = null
            onResult(
                songs.takeIf { it.isNotEmpty() }?.let {
                    HomeSection(
                        id = playlist.url,
                        title = playlist.title,
                        songs = it,
                        subtitle = listOf(playlist.author, "${it.size} lagu")
                            .filter { part -> part.isNotBlank() }
                            .joinToString(" • ")
                    )
                }
            )
        }
    }

    /** Cari lagu berdasarkan mood via YouTube Music search (bukan filter lokal) */
    fun moodSearch(query: String, onResult: (List<Song>) -> Unit) {
        viewModelScope.launch {
            val results = runCatching { repository.shelf(query, limit = 20) }.getOrDefault(emptyList())
            onResult(results)
        }
    }

    private fun loadRecapStats() {
        viewModelScope.launch {
            _historyCount.value = runCatching { repository.getHistoryCount() }.getOrDefault(0)
            _topArtist.value = runCatching { repository.getTopArtist() }.getOrNull()
        }
    }

    /**
     * Rak personal dari [RecommendationEngine]. Semuanya kosong untuk pengguna
     * baru — beranda lalu hanya menampilkan rak genre dan tren.
     */
    private suspend fun personalShelves(): List<HomeSection> = coroutineScope {
        val engine = repository.recommendations
        val quick = async { engine.quickPicks(limit = 20) }
        val forgotten = async { engine.forgottenFavorites() }
        buildList {
            quick.await().takeIf { it.isNotEmpty() }?.let {
                add(HomeSection("quick_picks", "Pilihan cepat untukmu", it))
            }
            forgotten.await().takeIf { it.size >= 5 }?.let {
                add(HomeSection("forgotten", "Favorit yang lama tak diputar", it))
            }
        }
    }

    /** Tiap Mix Harian berangkat dari satu lagu acuan yang berbeda. */
    private suspend fun dailyMixes(): List<HomeSection> =
        repository.recommendations.similarTo(count = 3, perShelf = 25)
            .mapIndexed { index, (seed, songs) ->
                HomeSection(id = "mix_${seed.id}", title = "Mix Harian ${index + 1}", songs = songs)
            }

    /**
     * Rak yang dibentuk dari artis yang paling sering didengar di Spotify.
     *
     * Spotify hanya dipakai untuk mengetahui seleranya; lagunya tetap dicari
     * dan diputar dari YouTube. Kalau pengguna belum login, tidak ada
     * permintaan jaringan yang dikeluarkan sama sekali.
     */
    private suspend fun fromSpotifyTaste(): HomeSection? {
        val app = getApplication<Application>()
        if (!SpotifyAuth.isLoggedIn(app)) return null

        val token = SpotifyAuth.accessToken(app) ?: return null
        val artists = runCatching { SpotifyUserApi.topArtists(token, limit = 6) }
            .getOrDefault(emptyList())
        if (artists.isEmpty()) return null

        // Ambil sedikit dari tiap artis lalu diselang-seling, supaya satu artis
        // tidak memborong seluruh rak.
        val perArtist = coroutineScope {
            artists.map { artist ->
                async { runCatching { repository.shelf(artist) }.getOrDefault(emptyList()).take(4) }
            }.awaitAll()
        }

        val interleaved = buildList {
            val maxSize = perArtist.maxOfOrNull { it.size } ?: 0
            for (i in 0 until maxSize) {
                perArtist.forEach { list -> list.getOrNull(i)?.let { add(it) } }
            }
        }.distinctBy { it.id }

        if (interleaved.isEmpty()) return null
        return HomeSection(
            id = "spotify_taste",
            title = "Untukmu, dari selera Spotify-mu",
            songs = interleaved
        )
    }

    private companion object {
        const val MAX_SUGGESTED_PLAYLISTS = 12

        /** Judul rak dipasangkan dengan kata kunci pencarian YouTube Music. */
        val SHELF_QUERIES = listOf(
            "Pop Indonesia" to "lagu pop indonesia terbaru",
            "Santai sore" to "lagu akustik indonesia santai",
            "Indie Indonesia" to "indie indonesia",
            "Dangdut" to "dangdut terbaru",
            "K-Pop" to "kpop hits",
            "Fokus kerja" to "lofi instrumental focus"
        )
    }
}
