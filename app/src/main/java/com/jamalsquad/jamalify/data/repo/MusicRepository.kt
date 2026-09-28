package com.jamalsquad.jamalify.data.repo

import android.content.Context
import com.jamalsquad.jamalify.data.Song
import com.jamalsquad.jamalify.data.db.DownloadEntity
import com.jamalsquad.jamalify.data.db.DownloadState
import com.jamalsquad.jamalify.data.db.DownloadedSong
import com.jamalsquad.jamalify.data.db.HistoryEntity
import com.jamalsquad.jamalify.data.db.MusicDao
import com.jamalsquad.jamalify.data.db.JamalifyDatabase
import com.jamalsquad.jamalify.data.db.PlayEvent
import com.jamalsquad.jamalify.data.db.PlaylistEntity
import com.jamalsquad.jamalify.data.db.PlaylistWithCount
import com.jamalsquad.jamalify.data.db.SongEntity
import com.jamalsquad.jamalify.data.youtube.YouTubeService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

/**
 * Satu-satunya pintu ke data bagi lapisan UI: menggabungkan Room (library
 * lokal) dengan YouTubeService (sumber online).
 */
class MusicRepository(
    private val dao: MusicDao,
    private val downloadDir: File
) {

    /** Satu mesin per proses, supaya cache lagu terkaitnya dipakai bersama. */
    val recommendations by lazy { RecommendationEngine(this) }

    // --- sumber online ---

    suspend fun search(query: String, limit: Int = 50) = YouTubeService.search(query, limit)

    suspend fun trending(limit: Int = 50) = YouTubeService.trending(limit)

    suspend fun trendingMore(limit: Int = 50) = YouTubeService.trendingMore(limit)

    fun hasMoreTrending() = YouTubeService.hasMoreTrending()

    suspend fun shelf(query: String, limit: Int = 50) = YouTubeService.shelf(query, limit)

    suspend fun related(videoId: String, limit: Int = 50) = YouTubeService.related(videoId, limit)

    suspend fun youtubePlaylist(url: String, maxSongs: Int = 5000) = YouTubeService.playlist(url, maxSongs)

    suspend fun searchPlaylists(query: String, limit: Int = 10) = YouTubeService.searchPlaylists(query, limit)

    suspend fun songInfo(videoId: String) = YouTubeService.songInfo(videoId)

    // --- library ---

    fun favorites(): Flow<List<Song>> = dao.observeFavorites().mapToSongs()

    suspend fun getFavoritesList(limit: Int = 20): List<Song> =
        dao.getFavoritesList(limit).map { it.toSong() }

    fun recentlyPlayed(): Flow<List<Song>> = dao.observeRecentlyPlayed().mapToSongs()

    suspend fun getRecentlyPlayedList(limit: Int = 20): List<Song> =
        dao.getRecentlyPlayedList(limit).map { it.toSong() }

    fun playlists(): Flow<List<PlaylistWithCount>> = dao.observePlaylists()

    fun playlistSongs(playlistId: Long): Flow<List<Song>> =
        dao.observePlaylistSongs(playlistId).mapToSongs()

    fun playlistInfo(playlistId: Long): Flow<PlaylistEntity?> = dao.observePlaylist(playlistId)

    fun isFavorite(songId: String): Flow<Boolean> = dao.observeIsFavorite(songId)

    suspend fun toggleFavorite(song: Song) {
        dao.insertSong(SongEntity.from(song))
        val current = dao.getSong(song.id)?.isFavorite ?: false
        dao.setFavorite(song.id, !current)
    }

    /** Mengembalikan id baris riwayat, untuk diisi lama dengarnya nanti. */
    suspend fun recordPlayed(song: Song): Long {
        dao.insertSong(SongEntity.from(song))
        return dao.insertHistory(HistoryEntity(songId = song.id))
    }

    suspend fun setListened(historyId: Long, listenedMs: Long) =
        dao.setListened(historyId, listenedMs)

    suspend fun playEventsSince(since: Long): List<PlayEvent> = dao.playEventsSince(since)

    suspend fun clearHistory() = dao.clearHistory()

    suspend fun getHistoryCount(): Int = dao.getHistoryCount()

    suspend fun getTopArtist(): String? = dao.getTopArtist()

    suspend fun createPlaylist(name: String): Long =
        dao.insertPlaylist(PlaylistEntity(name = name))

    suspend fun deletePlaylist(playlistId: Long) = dao.deletePlaylist(playlistId)

    suspend fun renamePlaylist(playlistId: Long, name: String) =
        dao.renamePlaylist(playlistId, name)

    suspend fun addToPlaylist(playlistId: Long, song: Song) =
        dao.addSongToPlaylist(playlistId, SongEntity.from(song))

    suspend fun addToPlaylist(playlistId: Long, songs: List<Song>) =
        dao.addSongsToPlaylist(playlistId, songs.map { SongEntity.from(it) })

    suspend fun removeFromPlaylist(playlistId: Long, songId: String) =
        dao.removeFromPlaylist(playlistId, songId)

    // --- unduhan ---

    fun downloads(): Flow<List<DownloadedSong>> = dao.observeDownloads()

    fun downloadState(songId: String): Flow<DownloadState?> = dao.observeDownloadState(songId)

    /** Daftarkan lagu ke antrean unduhan. Pemanggil yang menyalakan service. */
    suspend fun enqueueDownload(song: Song) {
        dao.insertSong(SongEntity.from(song))
        val existing = dao.getDownload(song.id)
        if (existing?.state == DownloadState.COMPLETED &&
            existing.filePath?.let { File(it).exists() } == true
        ) return
        dao.upsertDownload(DownloadEntity(songId = song.id, state = DownloadState.QUEUED))
    }

    suspend fun removeDownload(songId: String) {
        dao.getDownload(songId)?.filePath?.let { File(it).delete() }
        dao.deleteDownload(songId)
    }

    /** Berkas lokal untuk sebuah lagu, kalau unduhannya sudah selesai. */
    suspend fun localFileFor(songId: String): File? {
        val download = dao.getCompletedDownload(songId) ?: return null
        val path = download.filePath ?: return null
        return File(path).takeIf { it.exists() && it.length() > 0 }
    }

    fun downloadFileFor(songId: String) = File(downloadDir, "$songId.m4a")

    companion object {
        @Volatile
        private var instance: MusicRepository? = null

        fun get(context: Context): MusicRepository =
            instance ?: synchronized(this) {
                instance ?: run {
                    val dir = File(context.applicationContext.filesDir, "downloads")
                        .apply { mkdirs() }
                    MusicRepository(JamalifyDatabase.get(context).musicDao(), dir)
                        .also { instance = it }
                }
            }
    }
}

private fun Flow<List<SongEntity>>.mapToSongs(): Flow<List<Song>> =
    map { list -> list.map { it.toSong() } }
