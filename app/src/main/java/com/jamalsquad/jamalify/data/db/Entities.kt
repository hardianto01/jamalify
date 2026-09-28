package com.jamalsquad.jamalify.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.jamalsquad.jamalify.data.Song

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val thumbnail: String?,
    val durationSec: Long,
    val isFavorite: Boolean = false,
    val addedAt: Long = System.currentTimeMillis()
) {
    fun toSong() = Song(id, title, artist, thumbnail, durationSec)

    companion object {
        fun from(song: Song, favorite: Boolean = false) = SongEntity(
            id = song.id,
            title = song.title,
            artist = song.artist,
            thumbnail = song.thumbnail,
            durationSec = song.durationSec,
            isFavorite = favorite
        )
    }
}

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val thumbnail: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("songId"), Index("playlistId")]
)
data class PlaylistSongEntity(
    val playlistId: Long,
    val songId: String,
    val position: Int
)

@Entity(
    tableName = "history",
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("songId"), Index("playedAt")]
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: String,
    val playedAt: Long = System.currentTimeMillis(),
    /**
     * Berapa lama lagu ini benar-benar terdengar pada pemutaran ini.
     * -1 = tidak diketahui (riwayat lama, atau lagu tidak sempat dimuat).
     * Inilah pembeda "didengarkan" dan "di-skip" untuk rekomendasi.
     */
    @ColumnInfo(defaultValue = "-1") val listenedMs: Long = UNKNOWN_LISTENED
) {
    companion object {
        const val UNKNOWN_LISTENED = -1L
    }
}

/** Satu kejadian pemutaran beserta data lagunya, bahan mentah rekomendasi. */
data class PlayEvent(
    val songId: String,
    val title: String,
    val artist: String,
    val thumbnail: String?,
    val durationSec: Long,
    val isFavorite: Boolean,
    val playedAt: Long,
    val listenedMs: Long
) {
    fun toSong() = Song(songId, title, artist, thumbnail, durationSec)
}

enum class DownloadState { QUEUED, RUNNING, COMPLETED, FAILED }

@Entity(
    tableName = "downloads",
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class DownloadEntity(
    @PrimaryKey val songId: String,
    val filePath: String? = null,
    val state: DownloadState = DownloadState.QUEUED,
    val progress: Int = 0,
    val sizeBytes: Long = 0,
    val error: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

/** Baris gabungan lagu + status unduhannya, untuk layar Unduhan. */
data class DownloadedSong(
    val id: String,
    val title: String,
    val artist: String,
    val thumbnail: String?,
    val durationSec: Long,
    val filePath: String?,
    val state: DownloadState,
    val progress: Int
) {
    fun toSong() = Song(id, title, artist, thumbnail, durationSec)
}

/** Playlist beserta jumlah lagunya, untuk daftar di Library. */
data class PlaylistWithCount(
    val id: Long,
    val name: String,
    val thumbnail: String?,
    val songCount: Int
)
