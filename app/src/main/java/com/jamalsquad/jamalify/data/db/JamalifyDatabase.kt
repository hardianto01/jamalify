package com.jamalsquad.jamalify.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Converters {
    @TypeConverter
    fun toDownloadState(value: String?): DownloadState =
        value?.let { runCatching { DownloadState.valueOf(it) }.getOrNull() } ?: DownloadState.QUEUED

    @TypeConverter
    fun fromDownloadState(state: DownloadState?): String = (state ?: DownloadState.QUEUED).name
}

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
@TypeConverters(Converters::class)
abstract class JamalifyDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao

    companion object {
        /** Riwayat lama tetap ada; durasi dengarnya ditandai "tidak diketahui". */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE history ADD COLUMN listenedMs INTEGER NOT NULL DEFAULT -1")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_history_playedAt ON history(playedAt)")
            }
        }

        @Volatile
        private var instance: JamalifyDatabase? = null

        fun get(context: Context): JamalifyDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    JamalifyDatabase::class.java,
                    "jamalsquad.db"
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
