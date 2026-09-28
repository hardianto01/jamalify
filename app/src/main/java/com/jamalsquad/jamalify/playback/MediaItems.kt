package com.jamalsquad.jamalify.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.jamalsquad.jamalify.data.Song
import com.jamalsquad.jamalify.data.youtube.YouTubeService
import com.jamalsquad.jamalify.util.Thumbnails

/** Konversi dua arah antara model aplikasi dan MediaItem milik Media3. */
object MediaItems {

    fun from(song: Song): MediaItem = MediaItem.Builder()
        .setMediaId(song.id)
        .setUri(StreamResolver.uriFor(song.id, YouTubeService.hiRes))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setAlbumTitle(song.artist)
                .setArtworkUri(Thumbnails.normalize(song.thumbnail)?.let { Uri.parse(it) })
                .setExtras(
                    android.os.Bundle().apply {
                        putLong(KEY_DURATION, song.durationSec)
                    }
                )
                .build()
        )
        .build()

    fun toSong(item: MediaItem?): Song? {
        if (item == null) return null
        val metadata = item.mediaMetadata
        return Song(
            id = item.mediaId,
            title = metadata.title?.toString().orEmpty(),
            artist = metadata.artist?.toString().orEmpty(),
            thumbnail = metadata.artworkUri?.toString(),
            durationSec = metadata.extras?.getLong(KEY_DURATION) ?: 0L
        )
    }

    private const val KEY_DURATION = "jamalsquad_duration"
}
