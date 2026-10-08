package com.jamalsquad.jamalify.desktop.core

import com.jamalsquad.jamalify.data.Song
import com.jamalsquad.jamalify.ui.components.PetKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/*
 * Kontrak antar-lapisan versi desktop. UI hanya bergantung pada antarmuka di
 * sini; implementasinya (VLC, SQLite, YouTube) bisa diganti tanpa menyentuh
 * layar.
 */

data class PlayerUiState(
    val current: Song? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val volume: Float = 1f,
    val shuffle: Boolean = false,
    val repeatOne: Boolean = false,
    val error: String? = null
)

interface PlayerController {
    val state: StateFlow<PlayerUiState>
    val queue: StateFlow<List<Song>>
    val queueIndex: StateFlow<Int>

    fun playSongs(songs: List<Song>, startIndex: Int = 0)
    fun playNext(song: Song)
    fun addToQueue(song: Song)
    fun removeFromQueue(index: Int)
    fun skipTo(index: Int)
    fun togglePlay()
    fun next()
    fun previous()
    fun seekTo(positionMs: Long)
    fun setVolume(volume: Float)
    fun toggleShuffle()
    fun toggleRepeatOne()
    fun release()

    /** Siapkan lagu-lagu yang kemungkinan besar akan ditekan (daftar yang sedang tampil). */
    fun warmUp(songs: List<Song>) {}
}

data class HomeSection(val title: String, val songs: List<Song>)

/** Sumber lagu online (YouTube via NewPipeExtractor). */
interface MusicCatalog {
    suspend fun search(query: String): List<Song>
    suspend fun home(): List<HomeSection>
    suspend fun related(song: Song): List<Song>
    /** Lirik sinkron format LRC atau teks biasa; null kalau tidak ketemu. */
    suspend fun lyrics(song: Song): String?
}

data class LocalPlaylist(val id: Long, val name: String, val songCount: Int, val cover: String?)

/** Data pengguna yang tersimpan di disk. */
interface LibraryStore {
    val favorites: Flow<List<Song>>
    val recentlyPlayed: Flow<List<Song>>
    val playlists: Flow<List<LocalPlaylist>>

    fun isFavorite(songId: String): Flow<Boolean>
    suspend fun toggleFavorite(song: Song)
    fun playlistSongs(playlistId: Long): Flow<List<Song>>
    suspend fun createPlaylist(name: String): Long
    suspend fun renamePlaylist(playlistId: Long, name: String)
    suspend fun deletePlaylist(playlistId: Long)
    suspend fun addToPlaylist(playlistId: Long, song: Song)
    suspend fun removeFromPlaylist(playlistId: Long, songId: String)

    /** Dipanggil player saat lagu mulai dan saat berhenti, untuk riwayat & rekomendasi. */
    suspend fun recordPlayStart(song: Song): Long
    suspend fun recordPlayEnd(historyId: Long, listenedMs: Long, completed: Boolean)
}

enum class DesktopTheme { SYSTEM, LIGHT, DARK }

/** Preferensi sederhana (pet, tema, volume terakhir). */
interface AppSettings {
    val pet: StateFlow<PetKind>
    val theme: StateFlow<DesktopTheme>
    val volume: StateFlow<Float>
    fun setPet(kind: PetKind)
    fun setTheme(theme: DesktopTheme)
    fun setVolume(volume: Float)
}
