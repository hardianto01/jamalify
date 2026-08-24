package com.jamalsquad.jamalify.data

/** Satu lagu hasil pencarian / dari library. [id] adalah videoId YouTube. */
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val thumbnail: String?,
    val durationSec: Long
) {
    val durationLabel: String
        get() {
            if (durationSec <= 0) return "--:--"
            val m = durationSec / 60
            val s = durationSec % 60
            return if (m >= 60) "%d:%02d:%02d".format(m / 60, m % 60, s)
            else "%d:%02d".format(m, s)
        }
}

/** Ringkasan playlist YouTube/Spotify sebelum diimpor. */
data class RemotePlaylist(
    val title: String,
    val author: String,
    val thumbnail: String?,
    val songs: List<Song>
)

/** Satu baris lagu Spotify yang masih perlu dicocokkan ke YouTube. */
data class SpotifyTrack(
    val title: String,
    val artist: String
) {
    val searchQuery: String get() = "$artist $title"
}

sealed interface ImportState {
    data object Idle : ImportState
    data class Loading(val message: String) : ImportState
    data class Matching(val done: Int, val total: Int, val current: String) : ImportState
    data class Success(val playlistName: String, val imported: Int, val skipped: Int) : ImportState
    data class Error(val message: String) : ImportState
}
