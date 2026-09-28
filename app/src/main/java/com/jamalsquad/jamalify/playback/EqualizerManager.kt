package com.jamalsquad.jamalify.playback

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.media.audiofx.Equalizer
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BandInfo(
    val bandIndex: Short,
    val centerFreqHz: Int,
    val minLevelMilliBel: Short,
    val maxLevelMilliBel: Short,
    val currentLevelMilliBel: Short
)

/**
 * Pengelola Equalizer bawaan (In-App Hardware Equalizer).
 * Mengontrol pita frekuensi audio secara langsung menggunakan API [Equalizer] Android.
 */
object EqualizerManager {

    private var equalizer: Equalizer? = null
    private var activeSessionId: Int = 0

    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _bands = MutableStateFlow<List<BandInfo>>(emptyList())
    val bands: StateFlow<List<BandInfo>> = _bands.asStateFlow()

    private val _presets = MutableStateFlow<List<String>>(emptyList())
    val presets: StateFlow<List<String>> = _presets.asStateFlow()

    private val _currentPreset = MutableStateFlow<Short>(-1)
    val currentPreset: StateFlow<Short> = _currentPreset.asStateFlow()

    fun init(sessionId: Int) {
        if (activeSessionId == sessionId && equalizer != null) return
        release()
        activeSessionId = sessionId
        runCatching {
            val eq = Equalizer(0, sessionId).apply { enabled = true }
            equalizer = eq
            _isEnabled.value = eq.enabled
            updateBands(eq)
            updatePresets(eq)
        }
    }

    private fun updateBands(eq: Equalizer) {
        val numBands = eq.numberOfBands
        val range = eq.bandLevelRange
        val min = range.getOrNull(0) ?: -1500
        val max = range.getOrNull(1) ?: 1500

        val list = mutableListOf<BandInfo>()
        for (i in 0 until numBands) {
            val bandIndex = i.toShort()
            val freqHz = eq.getCenterFreq(bandIndex) / 1000
            val curLevel = eq.getBandLevel(bandIndex)
            list.add(
                BandInfo(
                    bandIndex = bandIndex,
                    centerFreqHz = freqHz,
                    minLevelMilliBel = min,
                    maxLevelMilliBel = max,
                    currentLevelMilliBel = curLevel
                )
            )
        }
        _bands.value = list
    }

    private fun updatePresets(eq: Equalizer) {
        val numPresets = eq.numberOfPresets
        val list = mutableListOf<String>()
        for (i in 0 until numPresets) {
            list.add(eq.getPresetName(i.toShort()) ?: "Preset ${i + 1}")
        }
        _presets.value = list
        _currentPreset.value = eq.currentPreset
    }

    fun setEnabled(enabled: Boolean) {
        equalizer?.let {
            it.enabled = enabled
            _isEnabled.value = enabled
        }
    }

    fun setBandLevel(bandIndex: Short, levelMilliBel: Short) {
        equalizer?.let { eq ->
            runCatching {
                eq.setBandLevel(bandIndex, levelMilliBel)
                _currentPreset.value = -1 // Mode kustom
                updateBands(eq)
            }
        }
    }

    fun usePreset(presetIndex: Short) {
        equalizer?.let { eq ->
            runCatching {
                eq.usePreset(presetIndex)
                _currentPreset.value = presetIndex
                updateBands(eq)
            }
        }
    }

    fun launchSystemEqualizer(context: Context) {
        val sessionId = activeSessionId
        val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
            if (sessionId != 0) {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
            }
            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
            putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val launched = runCatching {
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                true
            } else false
        }.getOrDefault(false)

        if (!launched) {
            Toast.makeText(
                context,
                "Equalizer sistem bawaan HP tidak tersedia. Gunakan Equalizer internal Jamalify di atas.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun release() {
        runCatching { equalizer?.release() }
        equalizer = null
    }
}
