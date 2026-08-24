package com.jamalsquad.jamalify.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.jamalsquad.jamalify.MainActivity
import com.jamalsquad.jamalify.data.repo.MusicRepository
import com.jamalsquad.jamalify.data.youtube.YouTubeService
import com.jamalsquad.jamalify.widget.JamalifyWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Menjalankan ExoPlayer di luar siklus hidup Activity supaya musik tetap
 * berjalan saat aplikasi ditutup, lengkap dengan notifikasi dan kontrol
 * dari lock screen / headset.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        val okHttp = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val httpFactory = OkHttpDataSource.Factory(okHttp)
            .setUserAgent("Mozilla/5.0")

        val resolvingFactory = ResolvingDataSource.Factory(
            DefaultDataSource.Factory(this, httpFactory),
            StreamResolver(this)
        )

        /*
         * Cache duduk di lapisan TERLUAR, di atas resolver. Urutan ini penting:
         * kunci cache-nya jadi `jamalsquad://song/<videoId>` yang stabil, sehingga
         * lagu yang sudah tersimpan diputar tanpa menyentuh jaringan sama
         * sekali — tidak ada halaman watch yang diunduh, tidak ada JavaScript
         * yang dijalankan. Kalau urutannya dibalik, resolve tetap terjadi
         * setiap kali dan kunci cache-nya berubah-ubah ikut tanda tangan URL.
         */
        val cacheFactory = CacheDataSource.Factory()
            .setCache(obtainCache())
            .setUpstreamDataSourceFactory(resolvingFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        /*
         * Hanya ambang MULAI yang diturunkan, bukan ukuran buffer total.
         * Buffer besar bawaan ExoPlayer justru hemat baterai: satu unduhan
         * besar lalu radio boleh tidur lama. Mengecilkannya akan membuat radio
         * bangun-tidur berulang sepanjang lagu.
         */
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                BUFFER_FOR_PLAYBACK_MS,
                BUFFER_AFTER_REBUFFER_MS
            )
            .build()

        val player = ExoPlayer.Builder(this)
            .setRenderersFactory(
                DefaultRenderersFactory(this)
                    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            )
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheFactory))
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        player.addListener(PlaybackObserver())

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        JamalifyWidget.push(this, song = null, isPlaying = false)
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        scope.cancel()
        sharedCache?.release()
        sharedCache = null
        super.onDestroy()
    }

    private fun obtainCache(): SimpleCache =
        sharedCache ?: synchronized(PlaybackService::class.java) {
            sharedCache ?: SimpleCache(
                File(cacheDir, "audio"),
                LeastRecentlyUsedCacheEvictor(CACHE_SIZE_BYTES),
                StandaloneDatabaseProvider(this)
            ).also { sharedCache = it }
        }

    /**
     * Siapkan URL lagu berikutnya sekarang, selagi radio menyala untuk lagu
     * yang sedang dimuat. Menundanya sampai lagu hampir habis berarti radio
     * kemungkinan sudah tidur dan harus dibangunkan lagi — byte yang sama,
     * ongkos energi jauh lebih besar.
     */
    private fun prefetchNext() {
        val player = mediaSession?.player ?: return
        val nextIndex = player.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET) return
        val nextId = player.getMediaItemAt(nextIndex).mediaId
        if (nextId.isBlank() || YouTubeService.isUrlCached(nextId)) return

        scope.launch {
            // Lagu yang sudah diunduh tidak perlu resolve sama sekali.
            val local = runCatching {
                MusicRepository.get(this@PlaybackService).localFileFor(nextId)
            }.getOrNull()
            if (local == null) YouTubeService.prefetchAudioUrl(nextId)
        }
    }

    private fun syncWidget() {
        val player = mediaSession?.player ?: return
        JamalifyWidget.push(
            context = this,
            song = MediaItems.toSong(player.currentMediaItem),
            isPlaying = player.isPlaying
        )
    }

    /**
     * Satu listener untuk tiga urusan yang semuanya dipicu perubahan lagu:
     * catat riwayat, siapkan lagu berikutnya, perbarui widget.
     */
    private inner class PlaybackObserver : Player.Listener {

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val song = MediaItems.toSong(mediaItem)
            if (song != null && song.title.isNotBlank()) {
                scope.launch {
                    runCatching { MusicRepository.get(this@PlaybackService).recordPlayed(song) }
                }
            }
            prefetchNext()
            syncWidget()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) prefetchNext()
            // Widget hanya diperbarui saat status berubah — tidak ada polling,
            // tidak ada progress bar yang harus di-refresh tiap detik.
            syncWidget()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) prefetchNext()
        }
    }

    private companion object {
        const val CACHE_SIZE_BYTES = 512L * 1024 * 1024
        const val BUFFER_FOR_PLAYBACK_MS = 1_000
        const val BUFFER_AFTER_REBUFFER_MS = 2_000

        @Volatile
        var sharedCache: SimpleCache? = null
    }
}
