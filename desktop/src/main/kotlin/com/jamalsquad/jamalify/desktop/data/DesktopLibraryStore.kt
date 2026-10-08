package com.jamalsquad.jamalify.desktop.data

import com.jamalsquad.jamalify.data.Song
import com.jamalsquad.jamalify.data.repo.RecommendationSource
import com.jamalsquad.jamalify.data.youtube.YouTubeService
import com.jamalsquad.jamalify.data.db.HistoryEntity
import com.jamalsquad.jamalify.data.db.PlayEvent
import com.jamalsquad.jamalify.data.db.PlaylistEntity
import com.jamalsquad.jamalify.data.db.SongEntity
import com.jamalsquad.jamalify.desktop.core.LibraryStore
import com.jamalsquad.jamalify.desktop.core.LocalPlaylist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Library lokal di atas DAO yang sama dengan Android; logikanya meniru MusicRepository. */
class DesktopLibraryStore(private val db: DesktopDatabase) : LibraryStore, RecommendationSource {

    constructor(file: File = DataPaths.databaseFile) : this(DesktopDatabase.open(file))

    private val dao = db.musicDao()

    /** Durasi lagu per baris riwayat yang sedang berjalan; DAO bersama tidak punya query-nya. */
    private val playingDurations = ConcurrentHashMap<Long, Long>()

    override val favorites: Flow<List<Song>> = dao.observeFavorites().toSongs()

    override val recentlyPlayed: Flow<List<Song>> = dao.observeRecentlyPlayed().toSongs()

    override val playlists: Flow<List<LocalPlaylist>> = dao.observePlaylists().map { list ->
        list.map { LocalPlaylist(it.id, it.name, it.songCount, it.thumbnail) }
    }

    override fun isFavorite(songId: String): Flow<Boolean> = dao.observeIsFavorite(songId)

    override suspend fun toggleFavorite(song: Song) {
        dao.insertSong(SongEntity.from(song))
        val current = dao.getSong(song.id)?.isFavorite ?: false
        dao.setFavorite(song.id, !current)
    }

    override fun playlistSongs(playlistId: Long): Flow<List<Song>> =
        dao.observePlaylistSongs(playlistId).toSongs()

    override suspend fun createPlaylist(name: String): Long =
        dao.insertPlaylist(PlaylistEntity(name = name))

    override suspend fun renamePlaylist(playlistId: Long, name: String) =
        dao.renamePlaylist(playlistId, name)

    override suspend fun deletePlaylist(playlistId: Long) = dao.deletePlaylist(playlistId)

    override suspend fun addToPlaylist(playlistId: Long, song: Song) {
        dao.addSongToPlaylist(playlistId, SongEntity.from(song))
        // Playlist baru belum punya sampul; pakai lagu pertama yang punya gambar.
        if (song.thumbnail != null && dao.observePlaylist(playlistId).first()?.thumbnail == null) {
            dao.setPlaylistThumbnail(playlistId, song.thumbnail)
        }
    }

    override suspend fun removeFromPlaylist(playlistId: Long, songId: String) =
        dao.removeFromPlaylist(playlistId, songId)

    override suspend fun recordPlayStart(song: Song): Long {
        dao.insertSong(SongEntity.from(song))
        return dao.insertHistory(HistoryEntity(songId = song.id))
            .also { playingDurations[it] = song.durationSec * 1000 }
    }

    override suspend fun recordPlayEnd(historyId: Long, listenedMs: Long, completed: Boolean) {
        // Lagu tuntas dihitung penuh walau posisi terakhir sedikit di bawah durasi.
        val durationMs = playingDurations.remove(historyId)?.takeIf { completed } ?: 0L
        dao.setListened(historyId, maxOf(listenedMs, durationMs).coerceAtLeast(0))
    }

    // --- bahan rekomendasi, sama dengan MusicRepository di Android ---

    override suspend fun playEventsSince(since: Long): List<PlayEvent> = dao.playEventsSince(since)

    override suspend fun getFavoritesList(limit: Int): List<Song> = dao.getFavoritesList(limit).map { it.toSong() }

    override suspend fun getRecentlyPlayedList(limit: Int): List<Song> =
        dao.getRecentlyPlayedList(limit).map { it.toSong() }

    override suspend fun related(videoId: String, limit: Int): List<Song> = YouTubeService.related(videoId, limit)

    suspend fun clearHistory() = dao.clearHistory()

    fun close() = db.close()
}

private fun Flow<List<SongEntity>>.toSongs(): Flow<List<Song>> = map { list -> list.map { it.toSong() } }
