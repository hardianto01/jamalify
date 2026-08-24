package com.jamalsquad.jamalify.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.jamalsquad.jamalify.JamalifyApp
import com.jamalsquad.jamalify.data.Song
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
    val songs: List<Song>
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

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val recentlyPlayed = repository.recentlyPlayed()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favorites = repository.favorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null

            // Rak-rak ini saling bebas, jadi diambil berbarengan daripada
            // menunggu satu per satu — beranda punya banyak baris sekarang.
            val shelfJobs = SHELF_QUERIES.map { (title, query) ->
                async { HomeSection(query, title, runCatching { repository.shelf(query) }.getOrDefault(emptyList())) }
            }
            val trendingJob = async { runCatching { repository.trending() }.getOrDefault(emptyList()) }
            val radioJob = async { becauseYouListened() }
            val personalJob = async { fromSpotifyTaste() }

            val trendingResult = trendingJob.await()
            _trending.value = trendingResult
            _canLoadMore.value = repository.hasMoreTrending() && trendingResult.isNotEmpty()

            val shelves = buildList {
                // Rak personal paling atas: kalau ada, itu yang paling relevan.
                personalJob.await()?.let { add(it) }
                radioJob.await()?.let { add(it) }
                addAll(shelfJobs.map { it.await() })
            }.filter { it.songs.isNotEmpty() }

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

    /** Rak yang berangkat dari lagu terakhir yang benar-benar diputar. */
    private suspend fun becauseYouListened(): HomeSection? {
        val last = runCatching { repository.recentlyPlayed().first().firstOrNull() }.getOrNull()
            ?: return null
        val related = runCatching { repository.related(last.id) }.getOrDefault(emptyList())
        if (related.isEmpty()) return null
        return HomeSection(
            id = "radio_${last.id}",
            title = "Karena kamu mendengarkan ${last.title}",
            songs = related
        )
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
            songs = interleaved.take(24)
        )
    }

    private companion object {
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
