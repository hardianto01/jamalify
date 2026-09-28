package com.jamalsquad.jamalify.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {

    // --- lagu ---

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSong(song: SongEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSongs(songs: List<SongEntity>)

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getSong(id: String): SongEntity?

    @Query("SELECT * FROM songs WHERE id = :id")
    fun observeSong(id: String): Flow<SongEntity?>

    @Query("UPDATE songs SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    @Query("SELECT * FROM songs WHERE isFavorite = 1 ORDER BY addedAt DESC")
    fun observeFavorites(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE isFavorite = 1 ORDER BY addedAt DESC LIMIT :limit")
    suspend fun getFavoritesList(limit: Int = 20): List<SongEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM songs WHERE id = :id AND isFavorite = 1)")
    fun observeIsFavorite(id: String): Flow<Boolean>

    // --- riwayat ---

    @Insert
    suspend fun insertHistory(entry: HistoryEntity): Long

    @Query("UPDATE history SET listenedMs = :listenedMs WHERE id = :historyId")
    suspend fun setListened(historyId: Long, listenedMs: Long)

    @Query(
        """
        SELECT h.songId AS songId, s.title AS title, s.artist AS artist,
               s.thumbnail AS thumbnail, s.durationSec AS durationSec,
               s.isFavorite AS isFavorite, h.playedAt AS playedAt, h.listenedMs AS listenedMs
        FROM history h
        INNER JOIN songs s ON s.id = h.songId
        WHERE h.playedAt >= :since
        ORDER BY h.playedAt DESC
        """
    )
    suspend fun playEventsSince(since: Long): List<PlayEvent>

    @Query(
        """
        SELECT s.* FROM songs s
        INNER JOIN (SELECT songId, MAX(playedAt) AS lastPlayed FROM history GROUP BY songId) h
            ON s.id = h.songId
        ORDER BY h.lastPlayed DESC
        LIMIT :limit
        """
    )
    fun observeRecentlyPlayed(limit: Int = 50): Flow<List<SongEntity>>

    @Query(
        """
        SELECT s.* FROM songs s
        INNER JOIN (SELECT songId, MAX(playedAt) AS lastPlayed FROM history GROUP BY songId) h
            ON s.id = h.songId
        ORDER BY h.lastPlayed DESC
        LIMIT :limit
        """
    )
    suspend fun getRecentlyPlayedList(limit: Int = 20): List<SongEntity>

    @Query("SELECT COUNT(*) FROM history")
    suspend fun getHistoryCount(): Int

    @Query(
        """
        SELECT s.artist FROM songs s
        INNER JOIN history h ON s.id = h.songId
        GROUP BY s.artist
        ORDER BY COUNT(*) DESC
        LIMIT 1
        """
    )
    suspend fun getTopArtist(): String?

    @Query("DELETE FROM history")
    suspend fun clearHistory()

    // --- playlist ---

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("UPDATE playlists SET name = :name WHERE id = :playlistId")
    suspend fun renamePlaylist(playlistId: Long, name: String)

    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    fun observePlaylist(playlistId: Long): Flow<PlaylistEntity?>

    @Query(
        """
        SELECT p.id AS id, p.name AS name, p.thumbnail AS thumbnail,
               (SELECT COUNT(*) FROM playlist_songs ps WHERE ps.playlistId = p.id) AS songCount
        FROM playlists p
        ORDER BY p.createdAt DESC
        """
    )
    fun observePlaylists(): Flow<List<PlaylistWithCount>>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun nextPosition(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistSong(entry: PlaylistSongEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistSongs(entries: List<PlaylistSongEntity>)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeFromPlaylist(playlistId: Long, songId: String)

    @Query(
        """
        SELECT s.* FROM songs s
        INNER JOIN playlist_songs ps ON s.id = ps.songId
        WHERE ps.playlistId = :playlistId
        ORDER BY ps.position ASC
        """
    )
    fun observePlaylistSongs(playlistId: Long): Flow<List<SongEntity>>

    @Query("UPDATE playlists SET thumbnail = :thumbnail WHERE id = :playlistId")
    suspend fun setPlaylistThumbnail(playlistId: Long, thumbnail: String?)

    @Transaction
    suspend fun addSongToPlaylist(playlistId: Long, song: SongEntity) {
        insertSong(song)
        insertPlaylistSong(PlaylistSongEntity(playlistId, song.id, nextPosition(playlistId)))
    }

    @Transaction
    suspend fun addSongsToPlaylist(playlistId: Long, songs: List<SongEntity>) {
        if (songs.isEmpty()) return
        insertSongs(songs)
        val start = nextPosition(playlistId)
        insertPlaylistSongs(
            songs.mapIndexed { index, song ->
                PlaylistSongEntity(playlistId, song.id, start + index)
            }
        )
        songs.firstOrNull { it.thumbnail != null }?.let {
            setPlaylistThumbnail(playlistId, it.thumbnail)
        }
    }

    // --- unduhan ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDownload(download: DownloadEntity)

    @Query("SELECT * FROM downloads WHERE songId = :songId")
    suspend fun getDownload(songId: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE state = 'COMPLETED' AND songId = :songId")
    suspend fun getCompletedDownload(songId: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE state IN ('QUEUED', 'RUNNING') ORDER BY updatedAt ASC LIMIT 1")
    suspend fun nextPendingDownload(): DownloadEntity?

    @Query("DELETE FROM downloads WHERE songId = :songId")
    suspend fun deleteDownload(songId: String)

    @Query(
        """
        SELECT s.id AS id, s.title AS title, s.artist AS artist, s.thumbnail AS thumbnail,
               s.durationSec AS durationSec, d.filePath AS filePath, d.state AS state,
               d.progress AS progress
        FROM downloads d
        INNER JOIN songs s ON s.id = d.songId
        ORDER BY d.updatedAt DESC
        """
    )
    fun observeDownloads(): Flow<List<DownloadedSong>>

    @Query("SELECT state FROM downloads WHERE songId = :songId")
    fun observeDownloadState(songId: String): Flow<DownloadState?>
}
