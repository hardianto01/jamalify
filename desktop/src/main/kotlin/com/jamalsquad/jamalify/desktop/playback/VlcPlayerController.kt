package com.jamalsquad.jamalify.desktop.playback

import com.jamalsquad.jamalify.data.Song
import com.jamalsquad.jamalify.data.repo.RecommendationEngine
import com.jamalsquad.jamalify.data.youtube.YouTubeService
import com.jamalsquad.jamalify.desktop.core.LibraryStore
import com.jamalsquad.jamalify.desktop.core.PlayerController
import com.jamalsquad.jamalify.desktop.core.PlayerUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import java.io.File
import java.util.concurrent.Executors

/**
 * Pemutar desktop berbasis libVLC (lewat vlcj), khusus audio.
 *
 * Semua perubahan antrean dan perintah ke VLC dijalankan di satu thread
 * [playerThread]: event VLC datang dari thread native, dan vlcj melarang
 * memanggil balik pemutar dari dalam callback-nya sendiri.
 */
class VlcPlayerController(
    private val library: LibraryStore? = null,
    private val recommendations: RecommendationEngine? = null
) : PlayerController {

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "jamalify-player").apply { isDaemon = true }
    }
    private val playerThread = executor.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + playerThread)

    private val _state = MutableStateFlow(PlayerUiState())
    override val state: StateFlow<PlayerUiState> = _state.asStateFlow()
    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    override val queue: StateFlow<List<Song>> = _queue.asStateFlow()
    private val _queueIndex = MutableStateFlow(-1)
    override val queueIndex: StateFlow<Int> = _queueIndex.asStateFlow()

    private var factory: MediaPlayerFactory? = null
    private var player: MediaPlayer? = null

    private var loadJob: Job? = null
    /** Berkas yang sedang dimuat VLC; event dari media lama diabaikan dengan membandingkannya. */
    private var loadedUrl: String? = null
    private var streamingMedia: ProgressiveMedia? = null
    private var retriedCurrent = false
    private var consecutiveErrors = 0
    /** Riwayat indeks untuk tombol sebelumnya saat acak aktif. */
    private val shuffleHistory = ArrayDeque<Int>()

    private var historyId: Long? = null
    private var historyStartedFor: String? = null

    init {
        try {
            // --no-video: stream YouTube kadang berisi trek video; jangan sampai VLC membuka jendela.
            val f = MediaPlayerFactory("--no-video", "--quiet", "--intf=dummy")
            val mp = f.mediaPlayers().newMediaPlayer()
            mp.events().addMediaPlayerEventListener(VlcEvents())
            factory = f
            player = mp
        } catch (t: Throwable) {
            // UnsatisfiedLinkError / RuntimeException dari NativeDiscovery: libVLC tidak ada.
            factory = null
            player = null
            _state.update { it.copy(error = VLC_MISSING) }
        }
        scope.launch { pollPosition() }
    }

    // --- antrean ---

    override fun playSongs(songs: List<Song>, startIndex: Int) {
        scope.launch {
            if (songs.isEmpty()) return@launch
            _queue.value = songs
            shuffleHistory.clear()
            startAt(startIndex.coerceIn(songs.indices))
        }
    }

    override fun playNext(song: Song) {
        scope.launch {
            val q = _queue.value
            if (q.isEmpty()) {
                _queue.value = listOf(song)
                startAt(0)
                return@launch
            }
            val insertAt = (_queueIndex.value + 1).coerceIn(0, q.size)
            _queue.value = q.toMutableList().apply { add(insertAt, song) }
        }
    }

    override fun addToQueue(song: Song) {
        scope.launch {
            val wasEmpty = _queue.value.isEmpty()
            _queue.value = _queue.value + song
            if (wasEmpty) startAt(0)
        }
    }

    override fun removeFromQueue(index: Int) {
        scope.launch {
            val q = _queue.value
            if (index !in q.indices) return@launch
            val current = _queueIndex.value
            _queue.value = q.toMutableList().apply { removeAt(index) }
            shuffleHistory.clear()
            when {
                index < current -> _queueIndex.value = current - 1
                index == current -> {
                    if (_queue.value.isEmpty()) stopPlayback(clearCurrent = true)
                    else startAt(index.coerceAtMost(_queue.value.lastIndex))
                }
            }
        }
    }

    override fun skipTo(index: Int) {
        scope.launch { if (index in _queue.value.indices) startAt(index) }
    }

    // --- kontrol ---

    override fun togglePlay() {
        scope.launch {
            val mp = player ?: return@launch
            val idx = _queueIndex.value
            when {
                idx !in _queue.value.indices -> Unit
                // Setelah error atau lagu habis tidak ada media yang bisa dilanjutkan; muat ulang.
                loadedUrl == null && loadJob?.isActive != true -> startAt(idx)
                mp.status().isPlaying -> mp.controls().setPause(true)
                else -> mp.controls().play()
            }
        }
    }

    override fun next() {
        scope.launch { advance(userInitiated = true) }
    }

    override fun previous() {
        scope.launch {
            // Seperti pemutar pada umumnya: lewat 3 detik, tombol ini mengulang lagu.
            if (_state.value.positionMs > 3_000) {
                player?.controls()?.setTime(0)
                _state.update { it.copy(positionMs = 0) }
                return@launch
            }
            val target = if (_state.value.shuffle) shuffleHistory.removeLastOrNull()
            else _queueIndex.value - 1
            if (target != null && target in _queue.value.indices) startAt(target, pushHistory = false)
            else player?.controls()?.setTime(0)
        }
    }

    override fun seekTo(positionMs: Long) {
        scope.launch {
            player?.controls()?.setTime(positionMs.coerceAtLeast(0))
            _state.update { it.copy(positionMs = positionMs.coerceAtLeast(0)) }
        }
    }

    override fun setVolume(volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        _state.update { it.copy(volume = v) }
        scope.launch { applyVolume() }
    }

    override fun toggleShuffle() {
        _state.update { it.copy(shuffle = !it.shuffle) }
        scope.launch { shuffleHistory.clear() }
    }

    override fun toggleRepeatOne() {
        _state.update { it.copy(repeatOne = !it.repeatOne) }
    }

    override fun release() {
        runBlocking(playerThread) {
            finishHistory(completed = false)
            loadJob?.cancel()
            runCatching { player?.controls()?.stop() }
            runCatching { player?.release() }
            runCatching { factory?.release() }
            player = null
            factory = null
        }
        scope.cancel()
        executor.shutdown()
    }

    // --- inti ---

    private fun startAt(index: Int, pushHistory: Boolean = true) {
        val q = _queue.value
        if (index !in q.indices) return
        val prev = _queueIndex.value
        if (pushHistory && prev in q.indices && prev != index) shuffleHistory.addLast(prev)
        if (shuffleHistory.size > 200) shuffleHistory.removeFirst()

        finishHistory(completed = false)
        val song = q[index]
        _queueIndex.value = index
        retriedCurrent = false
        _state.update {
            it.copy(
                current = song, isPlaying = false, isBuffering = true, positionMs = 0,
                durationMs = song.durationSec * 1000, error = if (player == null) VLC_MISSING else null
            )
        }
        load(song)
    }

    private fun load(song: Song) {
        loadJob?.cancel()
        val mp = player
        if (mp == null) {
            _state.update { it.copy(isBuffering = false, error = VLC_MISSING) }
            return
        }
        loadedUrl = null
        runCatching { mp.controls().stop() }
        loadJob = scope.launch {
            // Berkas utuh diputar langsung; yang belum selesai diumpankan sambil diunduh.
            val cached = StreamCache.cached(song.id)
            val progress = if (cached != null) null else try {
                withContext(Dispatchers.IO) { StreamCache.begin(song.id, YouTubeService.resolveAudioUrl(song.id)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onStreamError(song)
                return@launch
            }
            if (cached != null) {
                loadedUrl = cached.absolutePath
                mp.media().play(cached.absolutePath, ":no-video")
            } else {
                loadedUrl = STREAMING
                // Disimpan di field: callback JNA yang tersapu GC saat VLC masih membaca = crash native.
                streamingMedia = ProgressiveMedia(progress!!)
                mp.media().play(streamingMedia, ":no-video")
            }
            prefetchNext()
        }
    }

    private var warmUpJob: Job? = null

    /**
     * Sama dengan PlayerConnection.warmUp di Android: URL beberapa lagu teratas
     * disiapkan, lagu pertama sekalian diunduh, jadi saat ditekan langsung bunyi.
     */
    override fun warmUp(songs: List<Song>) {
        val ids = songs.asSequence()
            .map { it.id }
            .filter { it.isNotBlank() && it != _state.value.current?.id }
            .take(WARM_UP_COUNT)
            .toList()
        if (ids.isEmpty()) return
        warmUpJob?.cancel()
        warmUpJob = scope.launch(Dispatchers.IO) {
            // Tunda sebentar: daftar yang cuma dilewati tidak perlu disiapkan.
            delay(WARM_UP_DELAY_MS)
            ids.forEachIndexed { index, id ->
                runCatching { YouTubeService.prefetchAudioUrl(id) }
                if (index == 0 && StreamCache.cached(id) == null) {
                    runCatching { StreamCache.begin(id, YouTubeService.resolveAudioUrl(id)) }
                }
            }
        }
    }

    private suspend fun localFile(videoId: String): File =
        StreamCache.cached(videoId) ?: StreamCache.fetch(videoId, YouTubeService.resolveAudioUrl(videoId))

    /** Lagu berikutnya diunduh di belakang supaya pergantian lagu langsung bunyi. */
    private fun prefetchNext() {
        if (_state.value.shuffle) return
        val next = _queue.value.getOrNull(_queueIndex.value + 1) ?: return
        scope.launch(Dispatchers.IO) { runCatching { localFile(next.id) } }
    }

    /**
     * URL googlevideo sering sudah kedaluwarsa atau terikat klien lain; satu
     * kali dibuang dari cache lalu diminta ulang biasanya cukup. Kalau tetap
     * gagal, lompat ke lagu berikutnya — tapi berhenti setelah beberapa kali
     * beruntun supaya tidak menyapu seluruh antrean saat internet putus.
     */
    private fun onStreamError(song: Song) {
        if (_state.value.current?.id != song.id) return
        YouTubeService.invalidateUrl(song.id)
        StreamCache.evict(song.id)
        if (!retriedCurrent) {
            retriedCurrent = true
            load(song)
            return
        }
        consecutiveErrors++
        loadedUrl = null
        _state.update {
            it.copy(isPlaying = false, isBuffering = false, error = "Gagal memutar \"${song.title}\". Cek koneksi internet.")
        }
        if (consecutiveErrors < MAX_CONSECUTIVE_ERRORS && _queueIndex.value < _queue.value.lastIndex) {
            scope.launch {
                delay(1_500)
                if (_state.value.current?.id == song.id) advance(userInitiated = false)
            }
        }
    }

    private suspend fun onFinished() {
        finishHistory(completed = true)
        loadedUrl = null
        if (_state.value.repeatOne) {
            _queueIndex.value.takeIf { it in _queue.value.indices }?.let { startAt(it, pushHistory = false) }
            return
        }
        advance(userInitiated = false)
    }

    private suspend fun advance(userInitiated: Boolean) {
        val q = _queue.value
        if (q.isEmpty()) return
        val idx = _queueIndex.value
        val target = when {
            _state.value.shuffle && q.size > 1 -> q.indices.filter { it != idx }.random()
            idx < q.lastIndex -> idx + 1
            else -> null
        }
        if (target != null) {
            startAt(target)
            return
        }
        // Antrean habis: lanjutkan seperti mode radio di Android — pilihan mesin
        // rekomendasi (sesuai selera) dulu, lagu terkait biasa sebagai cadangan.
        val current = q.getOrNull(idx) ?: return
        val existing = q.map { it.id }.toSet()
        val related = runCatching {
            withContext(Dispatchers.IO) {
                recommendations?.autoplay(current, existing, limit = RADIO_BATCH)?.takeIf { it.isNotEmpty() }
                    ?: YouTubeService.related(current.id)
            }
        }
            .getOrDefault(emptyList())
            .filter { s -> q.none { it.id == s.id } }
            .take(RADIO_BATCH)
        if (related.isNotEmpty() && _queue.value === q) {
            _queue.value = q + related
            startAt(idx + 1)
        } else if (!userInitiated) {
            stopPlayback(clearCurrent = false)
        }
    }

    private fun stopPlayback(clearCurrent: Boolean) {
        loadJob?.cancel()
        finishHistory(completed = false)
        loadedUrl = null
        runCatching { player?.controls()?.stop() }
        _state.update {
            it.copy(
                current = if (clearCurrent) null else it.current,
                isPlaying = false, isBuffering = false, positionMs = 0
            )
        }
        if (clearCurrent) _queueIndex.value = -1
    }

    private fun applyVolume() {
        player?.audio()?.setVolume((_state.value.volume * 100).toInt())
    }

    private suspend fun pollPosition() {
        while (scope.isActive) {
            val mp = player
            if (mp != null && loadedUrl != null) {
                val pos = mp.status().time()
                val len = mp.status().length()
                if (pos >= 0) {
                    _state.update {
                        it.copy(positionMs = pos, durationMs = if (len > 0) len else it.durationMs)
                    }
                }
            }
            delay(POLL_MS)
        }
    }

    private fun startHistory(song: Song) {
        val store = library ?: return
        if (historyStartedFor == song.id && historyId != null) return
        historyStartedFor = song.id
        historyId = null
        scope.launch {
            val id = runCatching { store.recordPlayStart(song) }.getOrNull()
            if (historyStartedFor == song.id) historyId = id
        }
    }

    private fun finishHistory(completed: Boolean) {
        val store = library ?: return
        val id = historyId ?: run { historyStartedFor = null; return }
        val listened = _state.value.positionMs
        historyId = null
        historyStartedFor = null
        scope.launch { runCatching { store.recordPlayEnd(id, listened, completed) } }
    }

    /** Callback datang dari thread native; semua kerja dilempar ke [playerThread]. */
    private inner class VlcEvents : MediaPlayerEventAdapter() {
        /** MRL dari VLC berbentuk file:///… ter-encode, jadi cukup cocokkan nama berkasnya. */
        private fun mrlOf(mp: MediaPlayer): String? = runCatching { mp.media().info()?.mrl() }.getOrNull()

        private fun isCurrent(mrl: String?): Boolean {
            val loaded = loadedUrl ?: return false
            // Media callback tidak punya MRL berkas (imem://); event lama sudah disaring oleh stop() sebelum play.
            if (loaded == STREAMING) return mrl == null || mrl.startsWith("imem")
            return mrl == null || mrl.endsWith(File(loaded).name)
        }

        override fun playing(mediaPlayer: MediaPlayer) {
            scope.launch {
                // Sebagian keluaran audio mengabaikan volume yang diset sebelum stream terbuka.
                applyVolume()
                consecutiveErrors = 0
                _state.update { it.copy(isPlaying = true, isBuffering = false, error = null) }
                _state.value.current?.let { startHistory(it) }
            }
        }

        override fun paused(mediaPlayer: MediaPlayer) {
            scope.launch { _state.update { it.copy(isPlaying = false) } }
        }

        override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) {
            scope.launch { _state.update { it.copy(isBuffering = newCache < 100f) } }
        }

        override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
            if (newLength > 0) scope.launch { _state.update { it.copy(durationMs = newLength) } }
        }

        override fun finished(mediaPlayer: MediaPlayer) {
            val mrl = mrlOf(mediaPlayer)
            scope.launch {
                if (!isCurrent(mrl)) return@launch
                // Stream dari server lokal berakhir lebih awal kalau unduhannya putus.
                val song = _state.value.current
                if (song != null && StreamCache.failedRecently(song.id)) onStreamError(song) else onFinished()
            }
        }

        override fun error(mediaPlayer: MediaPlayer) {
            val mrl = mrlOf(mediaPlayer)
            scope.launch {
                if (!isCurrent(mrl)) return@launch
                _state.value.current?.let { onStreamError(it) }
            }
        }
    }

    companion object {
        const val VLC_MISSING =
            "VLC belum terpasang. Pasang VLC 3.x (videolan.org) lalu buka ulang Jamalify."
        private const val POLL_MS = 250L
        private const val MAX_CONSECUTIVE_ERRORS = 3
        private const val RADIO_BATCH = 20
        private const val WARM_UP_COUNT = 3
        private const val WARM_UP_DELAY_MS = 400L
        private const val STREAMING = "stream:"
    }
}
