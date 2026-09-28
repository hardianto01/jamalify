package com.jamalsquad.jamalify.playback

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
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
 * Crossfade antar lagu: saat lagu mendekati akhir (sisanya <= crossfadeDuration),
 * volume di-fade out, dan saat lagu baru mulai, volume di-fade in dari 0.
 *
 * Tidak ada jeda keheningan antar lagu — transisi terasa mulus seperti
 * DJ mixing dua track secara bersamaan.
 */
@UnstableApi
class CrossfadeManager(
    private val playerProvider: () -> Player?
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _crossfadeDurationMs = MutableStateFlow(DEFAULT_CROSSFADE_MS)
    val crossfadeDurationMs: StateFlow<Long> = _crossfadeDurationMs.asStateFlow()

    private var fadeJob: Job? = null
    private var isFadingOut = false
    private var isFadingIn = false

    private val fadeTickMs = 50L

    fun setDuration(durationMs: Long) {
        _crossfadeDurationMs.value = durationMs.coerceIn(0L, MAX_CROSSFADE_MS)
    }

    fun isEnabled(): Boolean = _crossfadeDurationMs.value > 0

    /**
     * Dipanggil setiap 500ms dari position ticker.
     * Cek apakah sudah waktunya fade out.
     */
    fun checkCrossfade(currentPositionMs: Long, durationMs: Long) {
        if (!isEnabled() || isFadingOut) return
        if (durationMs <= 0) return

        val remaining = durationMs - currentPositionMs
        val fadeStart = _crossfadeDurationMs.value + 2000L // mulai fade 2 detik + durasi crossfade sebelum habis

        if (remaining <= fadeStart) {
            startFadeOut()
        }
    }

    private fun startFadeOut() {
        val player = playerProvider() ?: return
        if (isFadingOut) return
        isFadingOut = true

        fadeJob = scope.launch {
            val steps = (_crossfadeDurationMs.value / fadeTickMs).toInt().coerceAtLeast(1)
            val initialVolume = player.volume

            for (i in steps downTo 0) {
                val vol = initialVolume * (i.toFloat() / steps)
                player.volume = vol.coerceIn(0f, 1f)
                delay(fadeTickMs)
            }

            isFadingOut = false
            player.volume = initialVolume
        }
    }

    /**
     * Dipanggil saat lagu baru mulai — fade in dari volume rendah.
     */
    fun startFadeIn() {
        val player = playerProvider() ?: return
        if (!isEnabled()) return

        fadeJob?.cancel()
        isFadingIn = true

        fadeJob = scope.launch {
            val steps = (_crossfadeDurationMs.value / fadeTickMs).toInt().coerceAtLeast(1)
            val targetVolume = 1f
            player.volume = 0f

            for (i in 0..steps) {
                val vol = targetVolume * (i.toFloat() / steps)
                player.volume = vol.coerceIn(0f, 1f)
                delay(fadeTickMs)
            }

            player.volume = targetVolume
            isFadingIn = false
        }
    }

    fun reset() {
        fadeJob?.cancel()
        fadeJob = null
        isFadingOut = false
        isFadingIn = false
        playerProvider()?.volume = 1f
    }

    private companion object {
        const val DEFAULT_CROSSFADE_MS = 3000L
        const val MAX_CROSSFADE_MS = 12000L
    }
}