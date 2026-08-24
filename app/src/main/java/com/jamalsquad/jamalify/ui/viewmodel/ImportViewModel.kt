package com.jamalsquad.jamalify.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.jamalsquad.jamalify.JamalifyApp
import com.jamalsquad.jamalify.data.ImportState
import com.jamalsquad.jamalify.data.Song
import com.jamalsquad.jamalify.data.SpotifyTrack
import com.jamalsquad.jamalify.data.spotify.SpotifyAuth
import com.jamalsquad.jamalify.data.spotify.SpotifyClient
import com.jamalsquad.jamalify.data.spotify.SpotifyUserApi
import com.jamalsquad.jamalify.data.youtube.YouTubeService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Impor playlist dari YouTube maupun Spotify.
 *
 * Spotify tidak menyediakan audio, jadi untuk sumber Spotify yang diimpor
 * hanyalah daftar judul + artis; tiap baris lalu dicari padanannya di YouTube.
 */
@UnstableApi
class ImportViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = (app as JamalifyApp).repository
    private val prefs = app.getSharedPreferences("jamalify_prefs", Context.MODE_PRIVATE)

    private val _link = MutableStateFlow("")
    val link: StateFlow<String> = _link.asStateFlow()

    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state.asStateFlow()

    // Client ID dipakai bersama oleh login PKCE dan jalur client-credentials.
    private val _clientId = MutableStateFlow(SpotifyAuth.clientId(app))
    val clientId: StateFlow<String> = _clientId.asStateFlow()

    private val _clientSecret = MutableStateFlow(prefs.getString(KEY_CLIENT_SECRET, "").orEmpty())
    val clientSecret: StateFlow<String> = _clientSecret.asStateFlow()

    private val _loggedIn = MutableStateFlow(SpotifyAuth.isLoggedIn(app))
    val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

    private val _account = MutableStateFlow(SpotifyAuth.displayName(app))
    val account: StateFlow<String> = _account.asStateFlow()

    private var job: Job? = null

    // --- input ---

    fun onLinkChange(value: String) {
        _link.value = value
        if (_state.value is ImportState.Error) _state.value = ImportState.Idle
    }

    fun onClientIdChange(value: String) {
        _clientId.value = value
        SpotifyAuth.setClientId(getApplication(), value)
    }

    fun onClientSecretChange(value: String) {
        _clientSecret.value = value
        prefs.edit().putString(KEY_CLIENT_SECRET, value).apply()
    }

    fun reset() {
        _state.value = ImportState.Idle
    }

    fun cancel() {
        job?.cancel()
        _state.value = ImportState.Idle
    }

    // --- login Spotify ---

    /** URL izin Spotify; null kalau Client ID belum diisi. */
    fun authorizeUrl(): String? = SpotifyAuth.buildAuthorizeUrl(getApplication())

    fun onLoginRedirect(uri: Uri) {
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = ImportState.Loading("Menyelesaikan login Spotify…")
            SpotifyAuth.completeLogin(getApplication(), uri)
                .onSuccess {
                    val token = SpotifyAuth.accessToken(getApplication())
                    val name = token?.let {
                        runCatching { SpotifyUserApi.displayName(it) }.getOrNull()
                    }.orEmpty()
                    SpotifyAuth.setDisplayName(getApplication(), name)
                    _account.value = name
                    _loggedIn.value = true
                    _state.value = ImportState.Idle
                }
                .onFailure {
                    _state.value = ImportState.Error(it.message ?: "Login Spotify gagal.")
                }
        }
    }

    fun logout() {
        SpotifyAuth.logout(getApplication())
        _loggedIn.value = false
        _account.value = ""
        _state.value = ImportState.Idle
    }

    // --- impor otomatis dari akun ---

    /** Tarik seluruh playlist milik pengguna, satu per satu jadi playlist lokal. */
    fun importAllPlaylists() {
        runAuthorized { token ->
            _state.value = ImportState.Loading("Membaca daftar playlist kamu…")
            val playlists = SpotifyUserApi.playlists(token)
            if (playlists.isEmpty()) {
                _state.value = ImportState.Error("Tidak ada playlist di akun ini.")
                return@runAuthorized
            }

            var imported = 0
            var skipped = 0
            playlists.forEach { playlist ->
                val tracks = runCatching {
                    SpotifyUserApi.playlistTracks(token, playlist.id)
                }.getOrDefault(emptyList())
                if (tracks.isEmpty()) return@forEach

                val result = importTracks(playlist.name, tracks, "playlist ${playlist.name}")
                imported += result.first
                skipped += result.second
            }

            _state.value = ImportState.Success(
                playlistName = "${playlists.size} playlist Spotify",
                imported = imported,
                skipped = skipped
            )
        }
    }

    /** Tarik lagu yang ditandai suka di Spotify. */
    fun importSavedTracks() {
        runAuthorized { token ->
            _state.value = ImportState.Loading("Membaca lagu yang kamu sukai…")
            val tracks = SpotifyUserApi.savedTracks(token)
            if (tracks.isEmpty()) {
                _state.value = ImportState.Error("Belum ada lagu yang disukai di akun ini.")
                return@runAuthorized
            }
            val name = "Lagu Disukai Spotify"
            val (imported, skipped) = importTracks(name, tracks, name)
            _state.value = ImportState.Success(name, imported, skipped)
        }
    }

    private fun runAuthorized(block: suspend (String) -> Unit) {
        job?.cancel()
        job = viewModelScope.launch {
            runCatching {
                val token = SpotifyAuth.accessToken(getApplication())
                if (token == null) {
                    _state.value = ImportState.Error("Sesi Spotify habis. Masuk ulang.")
                    _loggedIn.value = false
                    return@launch
                }
                block(token)
            }.onFailure {
                _state.value = ImportState.Error(it.message ?: "Impor dari Spotify gagal.")
            }
        }
    }

    // --- impor dari link ---

    fun import() {
        val input = _link.value.trim()
        if (input.isEmpty()) {
            _state.value = ImportState.Error("Tempel dulu link playlist-nya.")
            return
        }

        job?.cancel()
        job = viewModelScope.launch {
            runCatching {
                when {
                    SpotifyClient.isSpotifyLink(input) -> importSpotifyLink(input)
                    YouTubeService.isPlaylistUrl(input) -> importYouTubePlaylist(input)
                    YouTubeService.extractVideoId(input) != null -> importSingleVideo(input)
                    else -> _state.value = ImportState.Error(
                        "Link tidak dikenali. Tempel link playlist YouTube atau Spotify."
                    )
                }
            }.onFailure { throwable ->
                _state.value = ImportState.Error(
                    throwable.message ?: "Impor gagal karena kesalahan tidak diketahui."
                )
            }
        }
    }

    private suspend fun importYouTubePlaylist(url: String) {
        _state.value = ImportState.Loading("Membaca playlist YouTube…")
        val remote = repository.youtubePlaylist(url)
        if (remote.songs.isEmpty()) {
            _state.value = ImportState.Error("Playlist kosong atau tidak bisa dibaca.")
            return
        }
        val playlistId = repository.createPlaylist(remote.title)
        repository.addToPlaylist(playlistId, remote.songs)
        _state.value = ImportState.Success(remote.title, remote.songs.size, 0)
    }

    private suspend fun importSingleVideo(url: String) {
        val videoId = YouTubeService.extractVideoId(url) ?: return
        _state.value = ImportState.Loading("Membaca lagu…")
        val song = repository.songInfo(videoId)
        val playlistId = repository.createPlaylist(song.title)
        repository.addToPlaylist(playlistId, listOf(song))
        _state.value = ImportState.Success(song.title, 1, 0)
    }

    private suspend fun importSpotifyLink(url: String) {
        _state.value = ImportState.Loading("Membaca playlist Spotify…")

        // Kalau sudah login, token pengguna dipakai supaya playlist privat
        // ikut terbaca dan tidak terpotong di 100 lagu pertama.
        val token = if (_loggedIn.value) SpotifyAuth.accessToken(getApplication()) else null
        val (type, id) = SpotifyClient.parseLink(url)
            ?: error("Link Spotify tidak dikenali")

        val playlist = if (token != null && type == "playlist") {
            val tracks = SpotifyUserApi.playlistTracks(token, id)
            val name = runCatching {
                SpotifyUserApi.playlists(token).firstOrNull { it.id == id }?.name
            }.getOrNull() ?: "Playlist Spotify"
            name to tracks
        } else {
            val fetched = SpotifyClient.fetch(
                input = url,
                clientId = _clientId.value.takeIf { it.isNotBlank() },
                clientSecret = _clientSecret.value.takeIf { it.isNotBlank() }
            )
            fetched.name to fetched.tracks
        }

        val (name, tracks) = playlist
        if (tracks.isEmpty()) {
            _state.value = ImportState.Error("Tidak ada lagu yang terbaca dari playlist ini.")
            return
        }

        val (imported, skipped) = importTracks(name, tracks, name)
        if (imported == 0) {
            _state.value = ImportState.Error("Tidak satu pun lagu ditemukan di YouTube.")
            return
        }
        _state.value = ImportState.Success(name, imported, skipped)
    }

    // --- inti pencocokan ---

    /**
     * Buat playlist lokal berisi padanan YouTube dari [tracks].
     * @return jumlah yang berhasil dan yang tidak ditemukan.
     */
    private suspend fun importTracks(
        playlistName: String,
        tracks: List<SpotifyTrack>,
        label: String
    ): Pair<Int, Int> {
        val playlistId = repository.createPlaylist(playlistName)
        val matched = mutableListOf<Song>()
        var skipped = 0

        tracks.forEachIndexed { index, track ->
            _state.value = ImportState.Matching(
                done = index,
                total = tracks.size,
                current = "$label — ${track.artist} · ${track.title}"
            )
            val song = matchOnYouTube(track)
            if (song != null) matched += song else skipped++

            // Disimpan bertahap supaya impor panjang yang dihentikan di tengah
            // jalan tidak kehilangan seluruh progresnya.
            if (matched.size >= BATCH_SIZE) {
                repository.addToPlaylist(playlistId, matched.toList())
                matched.clear()
            }
        }
        if (matched.isNotEmpty()) repository.addToPlaylist(playlistId, matched.toList())

        val imported = tracks.size - skipped
        if (imported == 0) repository.deletePlaylist(playlistId)
        return imported to skipped
    }

    /** Ambil hasil pencarian YouTube yang durasinya paling masuk akal. */
    private suspend fun matchOnYouTube(track: SpotifyTrack): Song? {
        val results = runCatching { repository.search(track.searchQuery) }.getOrNull()
        if (results.isNullOrEmpty()) return null
        return results.firstOrNull { it.durationSec in 30..1800 } ?: results.first()
    }

    private companion object {
        const val KEY_CLIENT_SECRET = "spotify_client_secret"
        const val BATCH_SIZE = 20
    }
}
