package com.jamalsquad.jamalify.desktop.data

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.jamalsquad.jamalify.data.db.DownloadEntity
import com.jamalsquad.jamalify.data.db.DownloadState
import com.jamalsquad.jamalify.data.db.HistoryEntity
import com.jamalsquad.jamalify.data.db.MusicDao
import com.jamalsquad.jamalify.data.db.PlaylistEntity
import com.jamalsquad.jamalify.data.db.PlaylistSongEntity
import com.jamalsquad.jamalify.data.db.SongEntity
import kotlinx.coroutines.Dispatchers
import java.io.File

class DesktopConverters {
    // Sama dengan Converters di Android: nilai rusak jatuh ke QUEUED, bukan crash.
    @TypeConverter
    fun toDownloadState(value: String?): DownloadState =
        value?.let { runCatching { DownloadState.valueOf(it) }.getOrNull() } ?: DownloadState.QUEUED

    @TypeConverter
    fun fromDownloadState(state: DownloadState?): String = (state ?: DownloadState.QUEUED).name
}

/*
 * Entitas & DAO dipakai bersama dari :app; versi 2 = skema Android terkini,
 * jadi berkas jamalsquad.db dari HP bisa langsung dibuka di sini.
 */
@Database(
    entities = [
        SongEntity::class,
        PlaylistEntity::class,
        PlaylistSongEntity::class,
        HistoryEntity::class,
        DownloadEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(DesktopConverters::class)
abstract class DesktopDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao

    companion object {
        fun open(file: File): DesktopDatabase {
            file.parentFile?.mkdirs()
            return Room.databaseBuilder<DesktopDatabase>(name = file.absolutePath)
                // SQLite bawaan (native di dalam jar), tidak bergantung pada libsqlite sistem.
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                // Satu koneksi saja: pool baca-tulis WAL membuat driver native
                // crash (SIGSEGV) saat banyak Flow dibuka bersamaan di Linux.
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .build()
        }
    }
}

/** Lokasi data per-OS, mengikuti konvensi masing-masing platform. */
object DataPaths {
    val dataDir: File by lazy {
        val home = System.getProperty("user.home")
        val dir = if (System.getProperty("os.name").lowercase().contains("win")) {
            File(System.getenv("APPDATA")?.takeIf { it.isNotBlank() } ?: "$home\\AppData\\Roaming", "Jamalify")
        } else {
            File(System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() } ?: "$home/.local/share", "jamalify")
        }
        dir.apply { mkdirs() }
    }

    val databaseFile: File get() = File(dataDir, "jamalsquad.db")
    val settingsFile: File get() = File(dataDir, "settings.properties")
}
