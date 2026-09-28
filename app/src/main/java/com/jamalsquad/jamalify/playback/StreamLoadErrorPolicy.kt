package com.jamalsquad.jamalify.playback

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.LoadErrorInfo
import com.jamalsquad.jamalify.data.youtube.YouTubeService
import com.jamalsquad.jamalify.playback.StreamResolver.Companion.videoIdOrNull
import java.io.EOFException

/**
 * Setiap percobaan ulang harus memakai URL baru.
 *
 * URL googlevideo terikat ke alamat IP. Begitu HP pindah Wi-Fi ↔ data
 * seluler, URL lama langsung ditolak — dan retry bawaan ExoPlayer akan terus
 * memakai URL yang sama dari cache sampai menyerah. Dengan membuang URL-nya
 * di sini, [StreamResolver] meminta yang baru lewat jaringan yang sekarang.
 */
@UnstableApi
class StreamLoadErrorPolicy : DefaultLoadErrorHandlingPolicy(RETRY_COUNT) {

    /**
     * Lagu terakhir yang gagal dimuat. Error dari lagu BERIKUTNYA (yang sedang
     * disiapkan di penghujung lagu) tetap dilaporkan saat lagu sekarang masih
     * diputar — tanpa ini, lagu yang salah yang dicoba ulang.
     */
    @Volatile
    var lastFailedVideoId: String? = null
        private set

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorInfo): Long {
        val videoId = loadErrorInfo.loadEventInfo.dataSpec.uri.videoIdOrNull()
        videoId?.let(YouTubeService::invalidateUrl)
        lastFailedVideoId = videoId

        val error = loadErrorInfo.exception
        // Video yang memang tidak tersedia tidak akan berubah dengan menunggu.
        if (error.causes().any { it is StreamResolver.StreamUnavailableException }) return C.TIME_UNSET
        // Data habis di tengah atom: isi cache tidak cocok dengan berkasnya.
        // Mencoba ulang di periode yang sama hanya mengulang kesalahan yang
        // sama — dan karena beberapa sampel sempat terbaca, ExoPlayer
        // menganggapnya "ada kemajuan" lalu mengulang selamanya. Serahkan ke
        // PlaybackService supaya cache lagunya dibuang dan dimuat dari nol.
        if (error.causes().any { it is EOFException || it is StreamResolver.StreamVariantChangedException }) {
            return C.TIME_UNSET
        }
        // 403/404/410 wajar sekali-dua kali (URL basi); lebih dari itu, menyerah.
        if (error is HttpDataSource.InvalidResponseCodeException && loadErrorInfo.errorCount > 2) {
            return C.TIME_UNSET
        }
        return super.getRetryDelayMsFor(loadErrorInfo)
    }

    private fun Throwable.causes(): Sequence<Throwable> = generateSequence(this) { it.cause }

    private companion object {
        const val RETRY_COUNT = 6
    }
}
