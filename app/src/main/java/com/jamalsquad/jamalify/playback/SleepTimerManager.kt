package com.jamalsquad.jamalify.playback

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manajer Sleep Timer untuk mematikan pemutaran musik secara otomatis.
 * Mendukung timer durasi (15m, 30m, 45m, 60m) dan mode "Akhir Lagu".
 */
@UnstableApi
class SleepTimerManager(private val playerProvider: () -> Player?) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _remainingMillis = MutableStateFlow<Long?>(null)
    val remainingMillis: StateFlow<Long?> = _remainingMillis.asStateFlow()

    private val _isEndOfSongMode = MutableStateFlow(false)
    val isEndOfSongMode: StateFlow<Boolean> = _isEndOfSongMode.asStateFlow()

    private var timerJob: Job? = null

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            if (_isEndOfSongMode.value && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                stopPlaybackWithFadeOut()
                cancelTimer()
            }
        }
    }

    fun startTimer(minutes: Int) {
        cancelTimer()
        _isEndOfSongMode.value = false
        val durationMs = minutes * 60 * 1000L
        _remainingMillis.value = durationMs

        timerJob = scope.launch {
            var left = durationMs
            while (left > 0) {
                delay(1000L)
                left -= 1000L
                _remainingMillis.value = left
            }
            stopPlaybackWithFadeOut()
            _remainingMillis.value = null
        }
    }

    fun extendTimer(additionalMinutes: Int = 15) {
        val currentLeft = _remainingMillis.value ?: 0L
        val newDuration = currentLeft + (additionalMinutes * 60 * 1000L)
        startTimer((newDuration / (60 * 1000L)).toInt().coerceAtLeast(1))
    }

    fun setEndOfSongMode() {
        cancelTimer()
        _isEndOfSongMode.value = true
        _remainingMillis.value = null
        playerProvider()?.addListener(playerListener)
    }

    fun cancelTimer() {
        timerJob?.cancel()
        timerJob = null
        _remainingMillis.value = null
        _isEndOfSongMode.value = false
        runCatching { playerProvider()?.removeListener(playerListener) }
    }

    private fun stopPlaybackWithFadeOut() {
        val player = playerProvider() ?: return
        scope.launch {
            val initialVolume = player.volume
            val steps = 10
            val fadeDelay = 300L
            for (i in steps downTo 0) {
                val vol = (initialVolume * (i.toFloat() / steps)).coerceIn(0f, 1f)
                player.volume = vol
                delay(fadeDelay)
            }
            player.pause()
            player.volume = initialVolume
        }
    }
}
