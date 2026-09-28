package com.jamalsquad.jamalify.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.jamalsquad.jamalify.data.Song
import com.jamalsquad.jamalify.data.repo.MusicRepository
import com.jamalsquad.jamalify.data.youtube.YouTubeService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Sisi klien dari [PlaybackService]. Menyimpan MediaController dan
 * menerjemahkan callback Player menjadi StateFlow yang bisa dipakai Compose.
 */
@UnstableApi
class PlayerConnection(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var controller: MediaController? = null

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _queueIndex = MutableStateFlow(0)
    val queueIndex: StateFlow<Int> = _queueIndex.asStateFlow()

    private val _shuffle = MutableStateFlow(false)
    val shuffle: StateFlow<Boolean> = _shuffle.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val sleepTimer = SleepTimerManager { controller }
    val crossfade = CrossfadeManager { controller }
    val wiFiPreCache = WiFiPreCacheManager(context)
    val karaokeMode = KaraokeModeManager()
    val shakeDetector = ShakeDetector(context)

    private var warmUpJob: Job? = null

    /**
     * Siapkan lagu-lagu teratas dari daftar yang baru tampil, sebelum
     * ditekan. Resolve URL makan ±1,5 detik dan itulah sebagian besar jeda
     * saat lagu baru ditekan; lagu pertama sekalian diambil awal audionya.
     *
     * Berurutan dan dibatasi [WARM_UP_COUNT] supaya membuka daftar tidak
     * memicu rentetan permintaan ke YouTube. Daftar baru membatalkan yang lama.
     */
    fun warmUp(songs: List<Song>) {
        val ids = songs.asSequence()
            .map { it.id }
            .filter { it.isNotBlank() && it != _currentSong.value?.id }
            .take(WARM_UP_COUNT)
            .toList()
        if (ids.isEmpty()) return
        warmUpJob?.cancel()
        warmUpJob = scope.launch(Dispatchers.IO) {
            // Tunda sebentar: daftar yang cuma dilewati saat scroll tidak perlu disiapkan.
            delay(WARM_UP_DELAY_MS)
            ids.forEachIndexed { index, id ->
                YouTubeService.prefetchAudioUrl(id)
                if (index == 0) PlaybackService.warmStart(id)
            }
        }
    }

    /** Perintah yang datang sebelum controller siap ditahan dulu di sini. */
    private var pendingAction: ((MediaController) -> Unit)? = null

    private var tickerJob: Job? = null
    private var uiVisible = false

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = syncFrom(player)

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            _error.value = when (error.errorCode) {
                androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                    "Koneksi terputus. Lagu lanjut otomatis begitu jaringan kembali."
                else -> "Lagu ini gagal diputar. Dicoba ulang, lalu dilewati kalau tetap gagal."
            }
        }
    }

    fun connect() {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            val result = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = result
            result.addListener(listener)
            syncFrom(result)
            pendingAction?.invoke(result)
            pendingAction = null
            if (uiVisible) startPositionTicker()
        }, MoreExecutors.directExecutor())
    }

    fun release() {
        tickerJob?.cancel()
        tickerJob = null
        controller?.removeListener(listener)
        controller?.release()
        controller = null
    }

    fun clearError() {
        _error.value = null
    }

    // --- perintah ---

    /** Ganti seluruh antrean dengan [songs] lalu mulai dari [startIndex]. */
    fun play(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        withController { player ->
            player.setMediaItems(songs.map { MediaItems.from(it) }, startIndex, 0L)
            player.prepare()
            player.play()
            // Lagu yang dipilih sendiri harus langsung terdengar penuh.
            // Fade-in dari nol membuat awal lagu terasa seperti masih memuat.
            crossfade.reset()
            wiFiPreCache.preCacheUpcoming(songs, startIndex)
        }
    }

    fun play(song: Song) = play(listOf(song), 0)

    fun playNext(song: Song) = withController { player ->
        val index = (player.currentMediaItemIndex + 1).coerceAtMost(player.mediaItemCount)
        player.addMediaItem(index, MediaItems.from(song))
        if (player.mediaItemCount == 1) {
            player.prepare()
            player.play()
        }
    }

    fun addToQueue(song: Song) = withController { player ->
        player.addMediaItem(MediaItems.from(song))
        if (player.mediaItemCount == 1) {
            player.prepare()
            player.play()
        }
    }

    fun addToQueue(songs: List<Song>) = withController { player ->
        val wasEmpty = player.mediaItemCount == 0
        player.addMediaItems(songs.map { MediaItems.from(it) })
        if (wasEmpty) {
            player.prepare()
            player.play()
        }
    }

    fun removeFromQueue(index: Int) = withController { player ->
        if (index in 0 until player.mediaItemCount) player.removeMediaItem(index)
    }

    /** Pindahkan lagu dari [fromIndex] ke [toIndex] di antrean. */
    fun moveQueueItem(fromIndex: Int, toIndex: Int) = withController { player ->
        if (fromIndex in 0 until player.mediaItemCount && toIndex in 0 until player.mediaItemCount) {
            val item = player.getMediaItemAt(fromIndex)
            player.removeMediaItem(fromIndex)
            player.addMediaItem(toIndex, item)
        }
    }

    /**
     * Aktifkan deteksi shake untuk memperpanjang sleep timer.
     * Saat HP digoyang, sleep timer diperpanjang 15 menit.
     */
    fun enableShakeToExtend() {
        shakeDetector.start()
        scope.launch(Dispatchers.Main) {
            shakeDetector.shakeEvent.collect {
                sleepTimer.extendTimer(15)
            }
        }
    }

    fun disableShakeToExtend() {
        shakeDetector.stop()
    }

    fun togglePlayPause() = withController { player ->
        if (player.isPlaying) player.pause() else {
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            player.play()
        }
    }

    fun next() = withController { player ->
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
            crossfade.reset()
        } else {
            val currentMedia = player.currentMediaItem
            val currentSong = MediaItems.toSong(currentMedia)
            val existingIds = (0 until player.mediaItemCount).mapNotNull { player.getMediaItemAt(it).mediaId }.toSet()

            scope.launch(Dispatchers.IO) {
                val repository = MusicRepository.get(context)
                val filtered = repository.recommendations
                    .autoplay(currentSong, existingIds, limit = 5)
                    .ifEmpty {
                        runCatching { repository.trending(10) }.getOrDefault(emptyList())
                            .filter { it.id !in existingIds }
                            .take(5)
                    }

                if (filtered.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        val startIndex = player.mediaItemCount
                        player.addMediaItems(filtered.map { MediaItems.from(it) })
                        player.seekToDefaultPosition(startIndex)
                        player.prepare()
                        player.play()
                    }
                }
            }
        }
    }

    fun previous() = withController { player ->
        if (player.currentPosition > 3_000) player.seekTo(0) else player.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs) }

    fun seekToIndex(index: Int) = withController { it.seekToDefaultPosition(index) }

    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    fun cycleRepeatMode() = withController { player ->
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    private fun withController(action: (MediaController) -> Unit) {
        val current = controller
        if (current != null) action(current) else {
            pendingAction = action
            connect()
        }
    }

    // --- sinkronisasi state ---

    private fun syncFrom(player: Player) {
        _isPlaying.value = player.isPlaying
        _isBuffering.value = player.playbackState == Player.STATE_BUFFERING
        _currentSong.value = MediaItems.toSong(player.currentMediaItem)
        _duration.value = player.duration.takeIf { it > 0 } ?: 0L
        _position.value = player.currentPosition.coerceAtLeast(0L)
        _queueIndex.value = player.currentMediaItemIndex
        _shuffle.value = player.shuffleModeEnabled
        _repeatMode.value = player.repeatMode
        _queue.value = buildList {
            for (i in 0 until player.mediaItemCount) {
                MediaItems.toSong(player.getMediaItemAt(i))?.let { add(it) }
            }
        }
    }

    /**
     * Posisi hanya perlu diperbarui kalau ada layar yang menampilkannya.
     *
     * Sebelumnya loop ini berjalan seumur hidup proses, dua kali sedetik,
     * bahkan saat layar mati — dan karena pemutaran menahan wakelock, CPU
     * memang terjaga untuk melayaninya. Sekarang ticker terikat ke lifecycle
     * Activity dan berhenti sendiri setiap kali pemutaran dijeda.
     */
    fun onUiVisible() {
        uiVisible = true
        controller?.let {
            syncFrom(it)
            startPositionTicker()
        }
    }

    fun onUiHidden() {
        uiVisible = false
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun startPositionTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            // collectLatest membatalkan loop begitu pemutaran berhenti, jadi
            // saat dijeda tidak ada bangun-tidur sama sekali.
            _isPlaying.collectLatest { playing ->
                if (!playing) return@collectLatest
                while (isActive) {
                    controller?.let { player ->
                        _position.value = player.currentPosition.coerceAtLeast(0L)
                        _duration.value = player.duration.takeIf { it > 0 } ?: 0L
                        crossfade.checkCrossfade(_position.value, _duration.value)
                    }
                    delay(500)
                }
            }
        }
    }

    fun openEqualizer(context: Context) {
        runCatching {
            val intent = android.content.Intent(android.media.audiofx.AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                putExtra(android.media.audiofx.AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                putExtra(android.media.audiofx.AudioEffect.EXTRA_CONTENT_TYPE, android.media.audiofx.AudioEffect.CONTENT_TYPE_MUSIC)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            }
        }
    }

    private companion object {
        const val WARM_UP_COUNT = 3
        const val WARM_UP_DELAY_MS = 400L
    }
}
