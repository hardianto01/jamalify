package com.jamalsquad.jamalify.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.SystemClock
import android.media.audiofx.AudioEffect
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.jamalsquad.jamalify.BuildConfig
import com.jamalsquad.jamalify.MainActivity
import com.jamalsquad.jamalify.data.Song
import com.jamalsquad.jamalify.data.repo.MusicRepository
import com.jamalsquad.jamalify.data.youtube.StreamHeadersInterceptor
import com.jamalsquad.jamalify.data.youtube.YouTubeService
import com.jamalsquad.jamalify.util.NetworkMonitor
import com.jamalsquad.jamalify.widget.JamalifyWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

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

        /*
         * Timeout baca dibuat pendek: saat jaringan berganti, soket lama bisa
         * menggantung tanpa error. Makin cepat gagal, makin cepat retry lewat
         * jaringan yang baru — selagi buffer masih cukup untuk menutupinya.
         */
        val okHttp = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(StreamHeadersInterceptor())
            .build()
            .also { httpClient = it }

        val httpFactory = OkHttpDataSource.Factory(okHttp)

        val resolvingFactory = ResolvingDataSource.Factory(
            DefaultDataSource.Factory(this, httpFactory),
            StreamResolver(this, obtainCache())
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
            .also { cacheDataSourceFactory = it }

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
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(cacheFactory)
                    .setLoadErrorHandlingPolicy(StreamLoadErrorPolicy().also { loadErrorPolicy = it })
            )
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
        // Build debug: catat setiap load, error, dan perubahan state lengkap
        // dengan waktunya — satu-satunya cara melihat retry yang diam-diam.
        if (BuildConfig.DEBUG) player.addAnalyticsListener(EventLogger("JamalifyPlayer"))

        // Broadcast Audio Session ID ke sistem (Dolby Atmos/Wavelet/System EQ)
        runCatching {
            val openIntent = Intent("android.media.action.OPEN_AUDIO_EFFECT_SESSION").apply {
                putExtra("android.media.extra.AUDIO_SESSION", player.audioSessionId)
                putExtra("android.media.extra.PACKAGE_NAME", packageName)
                putExtra("android.media.extra.CONTENT_TYPE", 0)
            }
            sendBroadcast(openIntent)
        }

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        instance = this

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()

        setupNetworkAutoRecovery()
        setupSoundMode(player)
    }

    private var soundMode: SoundModeApplier? = null

    private fun setupSoundMode(player: ExoPlayer) {
        SoundModeManager.init(this)
        val applier = SoundModeApplier(player).also { soundMode = it }
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) = applier.onAudioSessionIdChanged()
        })
        mainScope.launch { SoundModeManager.mode.collect(applier::apply) }
    }

    private var httpClient: OkHttpClient? = null
    private var cacheDataSourceFactory: CacheDataSource.Factory? = null
    private var loadErrorPolicy: StreamLoadErrorPolicy? = null

    @Volatile
    private var prebufferWriter: CacheWriter? = null

    @Volatile
    private var prebufferingId: String? = null
    private var networkMonitor: NetworkMonitor? = null

    /** Semua akses ke player wajib di main thread — ExoPlayer melempar exception kalau tidak. */
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var recoveryJob: Job? = null
    private var failedItemId: String? = null
    private var contentFailures = 0
    private var networkRetries = 0

    private fun setupNetworkAutoRecovery() {
        val monitor = NetworkMonitor(this).also { networkMonitor = it }
        monitor.start()

        mainScope.launch {
            monitor.networkChanged.collect { onNetworkChanged() }
        }
    }

    /**
     * Wi-Fi ↔ data seluler: URL stream lama terikat IP lama dan koneksi
     * yang terbuka menempel di jaringan yang sudah pergi. Buang keduanya,
     * lalu lanjutkan pemutaran kalau memang sedang tersendat atau gagal.
     */
    private fun onNetworkChanged() {
        YouTubeService.clearAllCachedUrls()
        val client = httpClient
        scope.launch { client?.connectionPool?.evictAll() }

        val player = mediaSession?.player ?: return
        when {
            player.playerError != null -> {
                networkRetries = 0
                recoveryJob?.cancel()
                recoverNow()
            }
            // Masih menunggu data dari soket lama — putuskan supaya ExoPlayer
            // langsung membuka ulang lewat jaringan baru, tidak menunggu timeout.
            player.playbackState == Player.STATE_BUFFERING -> client?.dispatcher?.cancelAll()
        }
    }

    private fun handlePlayerError(error: PlaybackException) {
        val player = mediaSession?.player ?: return
        val videoId = failedVideoId(player, error)
        if (videoId.isNotBlank()) YouTubeService.invalidateUrl(videoId)
        if (videoId != failedItemId) {
            failedItemId = videoId
            contentFailures = 0
            networkRetries = 0
        }

        if (isNetworkProblem(error)) {
            if (networkRetries < MAX_NETWORK_RETRIES) {
                val delayMs = (NETWORK_RETRY_BASE_MS shl networkRetries).coerceAtMost(NETWORK_RETRY_MAX_MS)
                networkRetries++
                scheduleRecovery(delayMs, dropCachedBytes = false)
                return
            }
            // Online tapi terus gagal: kemungkinan besar bukan jaringannya.
        }

        contentFailures++
        if (contentFailures <= MAX_CONTENT_RETRIES) {
            // Byte yang tersimpan bisa berasal dari stream berbeda dan rusak
            // kalau dicampur — buang supaya percobaan ulang benar-benar bersih.
            scheduleRecovery(CONTENT_RETRY_DELAY_MS, dropCachedBytes = isCorruptData(error))
        } else {
            skipBrokenItem(player, videoId)
        }
    }

    /**
     * Lagu yang sebenarnya gagal. Di penghujung lagu, ExoPlayer sudah memuat
     * lagu berikutnya — kalau lagu itulah yang rusak, errornya tetap muncul
     * selagi lagu sekarang yang tercatat sebagai `currentMediaItem`.
     */
    private fun failedVideoId(player: Player, error: PlaybackException): String {
        val causes = generateSequence<Throwable>(error) { it.cause }
        causes.firstNotNullOfOrNull {
            when (it) {
                is StreamResolver.StreamUnavailableException -> it.videoId
                is StreamResolver.StreamVariantChangedException -> it.videoId
                else -> null
            }
        }?.let { return it }
        if (error.errorCode in IO_ERROR_CODES) {
            loadErrorPolicy?.lastFailedVideoId?.let { return it }
        }
        return player.currentMediaItem?.mediaId.orEmpty()
    }

    private fun scheduleRecovery(delayMs: Long, dropCachedBytes: Boolean) {
        val videoId = failedItemId
        recoveryJob?.cancel()
        recoveryJob = mainScope.launch {
            delay(delayMs)
            if (dropCachedBytes && !videoId.isNullOrBlank()) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        sharedCache?.removeResource(StreamResolver.uriFor(videoId).toString())
                        sharedCache?.removeResource(StreamResolver.uriFor(videoId, hiRes = true).toString())
                    }
                }
            }
            // Offline: tidak ada gunanya mencoba. Tunggu sampai ada jaringan.
            networkMonitor?.isOnline?.first { it }
            recoverNow()
        }
    }

    /** ExoPlayer mempertahankan posisi dan status play/pause saat di-prepare ulang. */
    private fun recoverNow() {
        val player = mediaSession?.player ?: return
        if (player.playerError == null || player.mediaItemCount == 0) return
        player.prepare()
    }

    private fun skipBrokenItem(player: Player, videoId: String) {
        recoveryJob?.cancel()
        failedItemId = null
        contentFailures = 0
        networkRetries = 0
        val current = player.currentMediaItemIndex
        val broken = (0 until player.mediaItemCount).firstOrNull {
            it != current && player.getMediaItemAt(it).mediaId == videoId
        }
        when {
            // Lagu di depan antrean yang rusak: keluarkan saja, lagu yang
            // sedang diputar jalan terus sampai habis.
            player.currentMediaItem?.mediaId != videoId && broken != null -> {
                player.removeMediaItem(broken)
                player.prepare()
            }
            player.hasNextMediaItem() -> {
                player.seekToNextMediaItem()
                player.prepare()
            }
        }
    }

    private fun isNetworkProblem(error: PlaybackException): Boolean {
        if (networkMonitor?.isOnline?.value == false) return true
        if (error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
        ) return true
        return generateSequence<Throwable>(error) { it.cause }.any {
            it is UnknownHostException || it is SocketException || it is SocketTimeoutException ||
                it is SSLException || (it is IOException && it.message == "Canceled")
        }
    }

    private fun isCorruptData(error: PlaybackException): Boolean =
        generateSequence<Throwable>(error) { it.cause }.any {
            it is EOFException || it is StreamResolver.StreamVariantChangedException
        } ||
            error.errorCode in PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED..PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED ||
            error.errorCode in PlaybackException.ERROR_CODE_DECODER_INIT_FAILED..PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED

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
        listening.finish()
        JamalifyWidget.push(this, song = null, isPlaying = false)
        prebufferWriter?.cancel()
        networkMonitor?.stop()
        networkMonitor = null
        mainScope.cancel()
        soundMode?.release()
        soundMode = null
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        scope.cancel()
        if (instance === this) instance = null
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
        // Selama lagu sekarang masih memuat, lagu berikutnya jangan ikut
        // berebut jaringan — itu yang membuat lagu yang ditekan lambat bunyi.
        if (player.playbackState != Player.STATE_READY) return
        val nextIndex = player.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET) return
        val nextItem = player.getMediaItemAt(nextIndex)
        val nextId = nextItem.mediaId
        if (nextId.isBlank() || nextId == prebufferingId) return
        val cache = sharedCache ?: return
        val nextUri = nextItem.localConfiguration?.uri ?: StreamResolver.uriFor(nextId)
        val key = nextUri.toString()
        if (cache.isCached(key, 0, PREBUFFER_BYTES)) return

        prebufferWriter?.cancel()
        prebufferingId = nextId
        scope.launch {
            try {
                // Lagu yang sudah diunduh tidak perlu resolve sama sekali.
                val local = runCatching {
                    MusicRepository.get(this@PlaybackService).localFileFor(nextId)
                }.getOrNull()
                if (local != null) return@launch
                prebuffer(nextUri, key)
            } finally {
                if (prebufferingId == nextId) prebufferingId = null
            }
        }
    }

    /**
     * Seperti Spotify: bukan hanya URL lagu berikutnya yang disiapkan, tapi
     * juga beberapa detik pertama audionya. Saat lagu berganti — otomatis
     * atau ditekan "next" — ExoPlayer langsung memutar dari cache, tanpa
     * menunggu halaman YouTube diurai maupun buffer awal terisi.
     *
     * Hanya awalnya saja: lagu berikutnya belum tentu didengarkan sampai
     * habis, dan sisanya tetap termuat jauh sebelum dibutuhkan.
     */
    private fun prebuffer(uri: android.net.Uri, key: String) {
        val factory = cacheDataSourceFactory ?: return
        val spec = DataSpec.Builder()
            .setUri(uri)
            .setKey(key)
            .setLength(PREBUFFER_BYTES)
            .build()
        val writer = CacheWriter(factory.createDataSource(), spec, null, null)
        prebufferWriter = writer
        runCatching { writer.cache() }
        if (prebufferWriter === writer) prebufferWriter = null
    }

    private val warmingIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /**
     * Awal audio lagu yang kemungkinan besar akan ditekan (paling atas di
     * daftar yang sedang dibuka). URL saja belum cukup: koneksi pertama ke
     * googlevideo dan buffer awal masih makan ±1 detik. Terpisah dari
     * [prebufferWriter] supaya tidak membatalkan persiapan lagu berikutnya.
     */
    private fun warmStart(videoId: String) {
        val factory = cacheDataSourceFactory ?: return
        val cache = sharedCache ?: return
        val uri = StreamResolver.uriFor(videoId, YouTubeService.hiRes)
        val key = uri.toString()
        if (cache.isCached(key, 0, PREBUFFER_BYTES) || !warmingIds.add(videoId)) return
        scope.launch {
            try {
                val spec = DataSpec.Builder()
                    .setUri(uri)
                    .setKey(key)
                    .setLength(PREBUFFER_BYTES)
                    .build()
                runCatching { CacheWriter(factory.createDataSource(), spec, null, null).cache() }
            } finally {
                warmingIds.remove(videoId)
            }
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

    private var isAutoplayLoading = false

    private val listening = ListeningTracker()

    /**
     * Mengukur berapa lama tiap lagu BENAR-BENAR terdengar — hanya waktu
     * saat memutar, bukan saat dijeda atau buffering. Angka ini yang
     * membedakan "didengarkan sampai habis" dan "di-skip setelah 5 detik",
     * sinyal terpenting bagi [com.jamalsquad.jamalify.data.repo.RecommendationEngine].
     *
     * Disimpan setiap kali jeda, bukan hanya saat ganti lagu, supaya tidak
     * hilang kalau proses dimatikan sistem di tengah lagu.
     */
    private inner class ListeningTracker {
        private var historyRow: Deferred<Long?>? = null
        private var accumulatedMs = 0L
        private var playingSince = 0L
        private var loaded = false

        /** Tidak ikut dibatalkan di onDestroy, supaya lagu terakhir tetap tercatat. */
        private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun begin(song: Song, playingNow: Boolean) {
            accumulatedMs = 0L
            loaded = false
            playingSince = if (playingNow) SystemClock.elapsedRealtime() else 0L
            historyRow = scope.async {
                runCatching { MusicRepository.get(this@PlaybackService).recordPlayed(song) }.getOrNull()
            }
        }

        fun onLoaded() {
            loaded = true
        }

        fun onIsPlayingChanged(isPlaying: Boolean) {
            if (historyRow == null) return
            if (isPlaying) {
                if (playingSince == 0L) playingSince = SystemClock.elapsedRealtime()
            } else {
                stopClock()
                save()
            }
        }

        fun finish() {
            if (historyRow == null) return
            stopClock()
            save()
            historyRow = null
        }

        private fun stopClock() {
            if (playingSince != 0L) {
                accumulatedMs += SystemClock.elapsedRealtime() - playingSince
                playingSince = 0L
            }
        }

        private fun save() {
            val row = historyRow ?: return
            // Lagu yang gagal dimuat tidak pernah terdengar; itu bukan skip.
            if (!loaded) return
            val listenedMs = accumulatedMs
            saveScope.launch {
                val id = runCatching { row.await() }.getOrNull() ?: return@launch
                runCatching { MusicRepository.get(this@PlaybackService).setListened(id, listenedMs) }
            }
        }
    }

    /**
     * Infinite Queue / Autoplay:
     * Otomatis menambah lagu-lagu rekomendasi terkait saat antrean mendekati akhir
     * atau saat lagu terakhir selesai diputar, berdasarkan lagu yang sedang diputar
     * dan riwayat / preferensi pengguna. Pemutaran tidak akan pernah habis.
     */
    private fun checkAutoplay() {
        val player = mediaSession?.player ?: return
        if (isAutoplayLoading) return
        if (player.repeatMode == Player.REPEAT_MODE_ONE) return

        val totalCount = player.mediaItemCount
        if (totalCount == 0) return

        val currentIndex = player.currentMediaItemIndex
        val remaining = totalCount - (currentIndex + 1)
        val isNearEnd = remaining <= 3 || player.playbackState == Player.STATE_ENDED

        if (!isNearEnd) return

        isAutoplayLoading = true
        scope.launch(Dispatchers.IO) {
            try {
                val currentMediaItem = withContext(Dispatchers.Main) {
                    if (player.mediaItemCount > 0) {
                        player.getMediaItemAt(player.currentMediaItemIndex.coerceIn(0, player.mediaItemCount - 1))
                    } else null
                }
                val currentSong = MediaItems.toSong(currentMediaItem)

                val existingIds = mutableSetOf<String>()
                withContext(Dispatchers.Main) {
                    for (i in 0 until player.mediaItemCount) {
                        val id = player.getMediaItemAt(i).mediaId
                        if (id.isNotBlank()) existingIds.add(id)
                    }
                }

                val repository = MusicRepository.get(this@PlaybackService)
                val filtered = repository.recommendations
                    .autoplay(currentSong, existingIds, limit = 10)
                    .ifEmpty {
                        // Belum ada riwayat dan lagu terkait tidak didapat: jatuh ke tren.
                        runCatching { repository.trending(15) }.getOrDefault(emptyList())
                            .filter { it.id !in existingIds }
                            .take(10)
                    }

                if (filtered.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        val mediaItems = filtered.map { MediaItems.from(it) }
                        player.addMediaItems(mediaItems)
                        if (player.playbackState == Player.STATE_ENDED) {
                            player.prepare()
                            player.play()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isAutoplayLoading = false
            }
        }
    }

    /**
     * Satu listener untuk tiga urusan yang semuanya dipicu perubahan lagu:
     * catat riwayat, siapkan lagu berikutnya, perbarui widget.
     */
    private inner class PlaybackObserver : Player.Listener {

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            listening.finish()
            val song = MediaItems.toSong(mediaItem)
            if (song != null && song.title.isNotBlank()) {
                listening.begin(song, playingNow = mediaSession?.player?.isPlaying == true)
            }
            prefetchNext()
            syncWidget()
            checkAutoplay()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            listening.onIsPlayingChanged(isPlaying)
            if (isPlaying) prefetchNext()
            syncWidget()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                listening.onLoaded()
                // Berhasil jalan lagi — hitungan kegagalan dimulai dari nol.
                failedItemId = null
                contentFailures = 0
                networkRetries = 0
                prefetchNext()
            }
            if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_READY) checkAutoplay()
        }

        override fun onPlayerError(error: PlaybackException) = handlePlayerError(error)
    }

    companion object {
        private const val CACHE_SIZE_BYTES = 512L * 1024 * 1024
        /** ±15 detik audio 128 kbps — cukup untuk mulai tanpa jeda. */
        private const val PREBUFFER_BYTES = 256L * 1024
        private const val MAX_NETWORK_RETRIES = 8
        private const val NETWORK_RETRY_BASE_MS = 1_000L
        private const val NETWORK_RETRY_MAX_MS = 30_000L
        private const val MAX_CONTENT_RETRIES = 1
        private const val CONTENT_RETRY_DELAY_MS = 500L
        private val IO_ERROR_CODES =
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED..PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE
        private const val BUFFER_FOR_PLAYBACK_MS = 1_000
        private const val BUFFER_AFTER_REBUFFER_MS = 2_000

        @Volatile
        private var sharedCache: SimpleCache? = null

        @Volatile
        private var instance: PlaybackService? = null

        /** Tidak melakukan apa-apa kalau layanan belum berjalan (belum pernah memutar). */
        fun warmStart(videoId: String) {
            instance?.warmStart(videoId)
        }
    }
}
