package com.jamalsquad.jamalify.playback

import android.media.audiofx.Equalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Mode Karaoke: meredam frekuensi vokal utama (1-4 kHz) via system equalizer.
 * Equalizer(0, 0) menggunakan output mix default — berlaku untuk semua audio
 * yang diputar di perangkat, termasuk ExoPlayer.
 */
class KaraokeModeManager {
    private val _isKaraokeMode = MutableStateFlow(false)
    val isKaraokeMode: StateFlow<Boolean> = _isKaraokeMode.asStateFlow()

    private var equalizer: Equalizer? = null

    fun toggleKaraoke() {
        val newState = !_isKaraokeMode.value
        _isKaraokeMode.value = newState
        if (newState) {
            setupEqualizer()
        } else {
            disableEqualizer()
        }
    }

    private fun setupEqualizer() {
        runCatching {
            releaseEqualizer()
            val eq = Equalizer(0, 0)
            eq.enabled = true

            val numBands = eq.numberOfBands
            if (numBands >= 5) {
                val minLevel = eq.bandLevelRange[0]
                // Reduksi band tengah (vokal utama 1-4 kHz)
                val band3 = (numBands * 0.4).toInt().coerceIn(0, numBands - 1)
                val band4 = (numBands * 0.6).toInt().coerceIn(0, numBands - 1)
                eq.setBandLevel(band3.toShort(), (minLevel * 0.3).toInt().toShort())
                eq.setBandLevel(band4.toShort(), (minLevel * 0.2).toInt().toShort())
            }
            equalizer = eq
        }
    }

    private fun disableEqualizer() {
        runCatching { equalizer?.enabled = false }
        releaseEqualizer()
    }

    private fun releaseEqualizer() {
        runCatching { equalizer?.release() }
        equalizer = null
    }

    fun release() {
        disableEqualizer()
    }
}