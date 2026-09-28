package com.jamalsquad.jamalify.playback

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.ContentMetadataMutations
import com.jamalsquad.jamalify.BuildConfig
import com.jamalsquad.jamalify.data.repo.MusicRepository
import com.jamalsquad.jamalify.data.youtube.YouTubeService
import kotlinx.coroutines.runBlocking
import java.io.IOException

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
class StreamResolver(context: Context, private val cache: Cache) : ResolvingDataSource.Resolver {

    private val repository = MusicRepository.get(context)

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val videoId = dataSpec.uri.videoIdOrNull() ?: return dataSpec
        val key = dataSpec.key ?: dataSpec.uri.toString()

        val localFile = runBlocking { repository.localFileFor(videoId) }
        if (localFile != null) {
            if (!matchesCachedVariant(key, localFile.length())) throw StreamVariantChangedException(videoId)
            return dataSpec.withUri(Uri.fromFile(localFile))
        }

        val hiRes = dataSpec.uri.isHiRes()
        var streamUrl = resolve(videoId, hiRes)
        var attempts = 0
        while (!matchesCachedVariant(key, streamUrl.contentLength())) {
            if (++attempts > VARIANT_ATTEMPTS) throw StreamVariantChangedException(videoId)
            YouTubeService.invalidateUrl(videoId)
            streamUrl = resolve(videoId, hiRes)
        }
        return dataSpec.withUri(Uri.parse(streamUrl))
    }

    private fun resolve(videoId: String, hiRes: Boolean): String {
        val startedAt = SystemClock.elapsedRealtime()
        return try {
            runBlocking { YouTubeService.resolveAudioUrl(videoId, hiRes) }.also {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "resolve $videoId: ${SystemClock.elapsedRealtime() - startedAt} ms, host=${Uri.parse(it).host}, c=${Uri.parse(it).getQueryParameter("c")}")
                }
            }
        } catch (e: IOException) {
            if (BuildConfig.DEBUG) Log.w(TAG, "resolve $videoId gagal (jaringan)", e)
            // Gangguan jaringan — biarkan ExoPlayer mencoba lagi.
            throw e
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "resolve $videoId gagal", e)
            // Selain IOException, ExoPlayer menganggapnya bug dan langsung
            // berhenti tanpa pesan yang jelas. Bungkus supaya bisa ditangani.
            throw StreamUnavailableException(videoId, e)
        }
    }

    /**
     * YouTube kadang menyajikan lagu yang sama sebagai dua berkas yang
     * berbeda beberapa byte (lihat `clen` di URL). Cache disusun per posisi
     * byte, jadi awal dari berkas A disambung sisa dari berkas B membuat
     * semua offset bergeser — ExoPlayer lalu kehabisan data di tengah atom
     * dan mencoba ulang tanpa henti, biasanya tepat di penghujung lagu.
     * Karena itu setiap kunci cache dipatok ke satu ukuran berkas.
     */
    private fun matchesCachedVariant(key: String, length: Long): Boolean {
        if (length <= 0) return true
        return try {
            val metadata = cache.getContentMetadata(key)
            val pinned = metadata.get(METADATA_LENGTH, C.LENGTH_UNSET.toLong())
            val known = ContentMetadata.getContentLength(metadata)
            val hasBytes = cache.getCachedSpans(key).isNotEmpty()
            when {
                hasBytes && pinned != C.LENGTH_UNSET.toLong() -> pinned == length
                hasBytes && known != C.LENGTH_UNSET.toLong() -> known == length
                else -> {
                    if (pinned != length) {
                        cache.applyContentMetadataMutations(
                            key,
                            ContentMetadataMutations().set(METADATA_LENGTH, length)
                        )
                    }
                    true
                }
            }
        } catch (e: Cache.CacheException) {
            true
        }
    }

    private fun String.contentLength(): Long =
        Uri.parse(this).getQueryParameter("clen")?.toLongOrNull() ?: C.LENGTH_UNSET.toLong()

    /** Video tidak bisa diambil streamnya (dihapus, dibatasi umur/wilayah, dsb). */
    class StreamUnavailableException(val videoId: String, cause: Throwable) :
        IOException("Stream tidak tersedia untuk $videoId", cause)

    /** Byte yang tersimpan berasal dari berkas lain; cache lagu ini harus dibuang. */
    class StreamVariantChangedException(val videoId: String) :
        IOException("Berkas stream $videoId berbeda dari yang tersimpan di cache")

    companion object {
        const val SCHEME = "jamalsquad"
        private const val TAG = "JamalifyResolve"
        private const val METADATA_LENGTH = "jamalify-clen"
        /** Resolve ulang untuk mendapat berkas yang sama dengan isi cache. */
        private const val VARIANT_ATTEMPTS = 2

        private const val QUALITY_PARAM = "q"
        private const val QUALITY_HI_RES = "hi"

        /**
         * Kualitas ikut tertulis di URI, jadi juga di kunci cache. Satu lagu
         * tidak pernah mencampur byte M4A dan Opus, dan setiap item di antrean
         * tetap memakai kualitas yang sama sepanjang diputar.
         */
        fun uriFor(videoId: String, hiRes: Boolean = false): Uri =
            if (hiRes) Uri.parse("$SCHEME://song/$videoId?$QUALITY_PARAM=$QUALITY_HI_RES")
            else Uri.parse("$SCHEME://song/$videoId")

        fun Uri.isHiRes(): Boolean =
            scheme == SCHEME && getQueryParameter(QUALITY_PARAM) == QUALITY_HI_RES

        fun Uri.videoIdOrNull(): String? =
            if (scheme == SCHEME) lastPathSegment else null
    }
}
