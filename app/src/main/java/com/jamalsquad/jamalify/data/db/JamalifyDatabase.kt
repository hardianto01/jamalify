package com.jamalsquad.jamalify.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

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
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class JamalifyDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao

    companion object {
        @Volatile
        private var instance: JamalifyDatabase? = null

        fun get(context: Context): JamalifyDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    JamalifyDatabase::class.java,
                    "jamalsquad.db"
                ).build().also { instance = it }
            }
    }
}
