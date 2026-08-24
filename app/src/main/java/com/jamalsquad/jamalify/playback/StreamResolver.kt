package com.jamalsquad.jamalify.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import com.jamalsquad.jamalify.data.repo.MusicRepository
import com.jamalsquad.jamalify.data.youtube.YouTubeService
import kotlinx.coroutines.runBlocking

/**
 * MediaItem hanya menyimpan `jamalsquad://song/<videoId>`. URL asli baru dicari di
 * sini, saat ExoPlayer benar-benar mulai memuat, karena URL stream YouTube
 * kedaluwarsa dalam hitungan jam — menyimpannya di queue akan membuat lagu
 * gagal diputar setelah aplikasi lama terbuka.
 *
 * Kalau lagunya sudah diunduh, berkas lokal yang dipakai dan jaringan
 * tidak disentuh sama sekali.
 */
@UnstableApi
class StreamResolver(context: Context) : ResolvingDataSource.Resolver {

    private val repository = MusicRepository.get(context)

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val videoId = dataSpec.uri.videoIdOrNull() ?: return dataSpec

        val localFile = runBlocking { repository.localFileFor(videoId) }
        if (localFile != null) {
            return dataSpec.withUri(Uri.fromFile(localFile))
        }

        val streamUrl = runBlocking { YouTubeService.resolveAudioUrl(videoId) }
        return dataSpec.withUri(Uri.parse(streamUrl))
    }

    private fun Uri.videoIdOrNull(): String? =
        if (scheme == SCHEME) lastPathSegment else null

    companion object {
        const val SCHEME = "jamalsquad"

        fun uriFor(videoId: String): Uri = Uri.parse("$SCHEME://song/$videoId")
    }
}
