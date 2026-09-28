package com.jamalsquad.jamalify.playback

import android.content.Context
import android.media.audiofx.Equalizer
import android.media.audiofx.PresetReverb
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.jamalsquad.jamalify.data.youtube.YouTubeService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Mode suara: kecepatan, nada, dan gema. Kecepatan dan nada diatur terpisah
 * oleh ExoPlayer (Sonic), jadi "tupai" bisa bernada tinggi tanpa ikut ngebut.
 *
 * Slowed dan speed up sengaja menggeser nada bersama kecepatan, seperti
 * piringan hitam diputar pelan/cepat — itulah bunyi khas versi TikTok-nya.
 */
enum class SoundMode(
    val label: String,
    val emoji: String,
    val speed: Float,
    val pitch: Float,
    val reverb: Short? = null,
    val troll: Boolean = false,
    /** Stream bitrate tertinggi; berlaku untuk lagu yang dimuat setelahnya. */
    val hiRes: Boolean = false,
    /** Hanya meloloskan frekuensi tengah, seperti speaker radio kecil. */
    val lofiRadio: Boolean = false
) {
    NORMAL("Normal", "🎵", 1f, 1f),
    HI_RES("Hi-Res", "", 1f, 1f, hiRes = true),
    SLOWED("Slowed", "🌙", 0.85f, 0.85f),
    SLOWED_REVERB("Slowed + Reverb", "🌌", 0.85f, 0.85f, PresetReverb.PRESET_LARGEHALL),
    REVERB("Reverb", "🏛️", 1f, 1f, PresetReverb.PRESET_LARGEHALL),
    SPEED_UP("Speed Up", "⚡", 1.2f, 1.2f),
    NIGHTCORE("Nightcore", "🌸", 1.3f, 1.35f),

    CHIPMUNK("Tupai", "🐿️", 1f, 1.8f, troll = true),
    MONSTER("Raksasa", "👹", 1f, 0.55f, troll = true),
    BATHROOM("Kamar Mandi", "🚿", 1f, 1f, PresetReverb.PRESET_SMALLROOM, troll = true),
    KASET_KUSUT("Kaset Kusut", "📼", 0.7f, 0.65f, troll = true),
    NGEBUT("Ngebut", "🏎️", 1.8f, 1f, troll = true),
    HANTU("Hantu", "👻", 0.8f, 0.7f, PresetReverb.PRESET_PLATE, troll = true),
    OM_OM("Suara Om-om", "🧔", 0.97f, 0.8f, troll = true),
    RADIO_JADUL("Radio Jadul", "📻", 1f, 1f, PresetReverb.PRESET_SMALLROOM, troll = true, lofiRadio = true)
}

/**
 * Pilihan disimpan supaya pendengar setia slowed tidak perlu memilih ulang
 * tiap membuka aplikasi. Mode troll sengaja tidak disimpan — lelucon yang
 * terbawa ke esok hari tidak lucu lagi.
 */
object SoundModeManager {

    private const val PREFS = "sound_mode"
    private const val KEY_MODE = "mode"

    private val _mode = MutableStateFlow(SoundMode.NORMAL)
    val mode: StateFlow<SoundMode> = _mode.asStateFlow()

    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        val store = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = store
        val saved = store.getString(KEY_MODE, null)
            ?.let { name -> SoundMode.entries.firstOrNull { it.name == name } }
            ?: SoundMode.NORMAL
        YouTubeService.hiRes = saved.hiRes
        _mode.value = saved
    }

    fun select(mode: SoundMode) {
        YouTubeService.hiRes = mode.hiRes
        _mode.value = mode
        prefs?.edit()?.putString(KEY_MODE, if (mode.troll) SoundMode.NORMAL.name else mode.name)?.apply()
    }
}

/**
 * Menerapkan [SoundMode] ke ExoPlayer. Hidup di [PlaybackService] karena
 * gema butuh ID sesi audio pemutar, yang hanya ada di sana.
 */
@UnstableApi
class SoundModeApplier(private val player: ExoPlayer) {

    private var reverb: PresetReverb? = null
    private var equalizer: Equalizer? = null
    private var reverbSessionId = 0
    private var current: SoundMode = SoundMode.NORMAL

    fun apply(mode: SoundMode) {
        val qualityChanged = mode.hiRes != current.hiRes
        current = mode
        player.playbackParameters = PlaybackParameters(mode.speed, mode.pitch)
        applyReverb(mode.reverb)
        applyRadioFilter(mode.lofiRadio)
        if (qualityChanged) switchQuality(mode.hiRes)
    }

    /** ExoPlayer bisa berganti sesi audio; efek lama tidak ikut pindah. */
    fun onAudioSessionIdChanged() {
        releaseReverb()
        releaseEqualizer()
        applyReverb(current.reverb)
        applyRadioFilter(current.lofiRadio)
    }

    /**
     * Antrean dibangun dengan kualitas saat lagu ditambahkan. Ganti URI-nya
     * supaya kualitas baru langsung terasa: lagu sekarang dimuat ulang dari
     * posisi yang sama, sisanya menyusul saat giliran diputar.
     */
    private fun switchQuality(hiRes: Boolean) {
        val count = player.mediaItemCount
        if (count == 0) return
        val currentIndex = player.currentMediaItemIndex
        val position = player.currentPosition
        val items = (0 until count).map { index ->
            val item = player.getMediaItemAt(index)
            item.buildUpon().setUri(StreamResolver.uriFor(item.mediaId, hiRes)).build()
        }
        player.replaceMediaItems(0, count, items)
        if (player.currentMediaItemIndex != currentIndex || player.currentPosition < position - SEEK_TOLERANCE_MS) {
            player.seekTo(currentIndex, position)
        }
    }

    private fun applyRadioFilter(enabled: Boolean) {
        if (!enabled) {
            releaseEqualizer()
            return
        }
        if (equalizer != null) return
        equalizer = runCatching {
            Equalizer(0, player.audioSessionId).apply {
                val (min, max) = bandLevelRange.let { it[0] to it[1] }
                for (band in 0 until numberOfBands) {
                    val hz = getCenterFreq(band.toShort()) / 1000
                    val level = when {
                        hz < RADIO_LOW_CUT_HZ || hz > RADIO_HIGH_CUT_HZ -> min
                        else -> (max * 0.6f).toInt().toShort()
                    }
                    setBandLevel(band.toShort(), level)
                }
                this.enabled = true
            }
        }.getOrNull()
    }

    private fun releaseEqualizer() {
        val eq = equalizer ?: return
        runCatching { eq.enabled = false }
        runCatching { eq.release() }
        equalizer = null
    }

    private fun applyReverb(preset: Short?) {
        if (preset == null) {
            releaseReverb()
            return
        }
        val sessionId = player.audioSessionId
        val existing = reverb
        if (existing != null && reverbSessionId == sessionId) {
            runCatching { existing.preset = preset }
            return
        }
        releaseReverb()
        // Utamakan efek sisipan di sesi pemutar sendiri. Kalau perangkat
        // tidak mendukung, pakai efek bantu di output mix lalu sambungkan.
        reverb = runCatching {
            PresetReverb(0, sessionId).apply {
                this.preset = preset
                enabled = true
            }.also { reverbSessionId = sessionId }
        }.recoverCatching {
            PresetReverb(0, 0).apply {
                this.preset = preset
                enabled = true
            }.also {
                reverbSessionId = sessionId
                player.setAuxEffectInfo(AuxEffectInfo(it.id, 1f))
            }
        }.getOrNull()
    }

    private fun releaseReverb() {
        val effect = reverb ?: return
        runCatching { player.setAuxEffectInfo(AuxEffectInfo(AuxEffectInfo.NO_AUX_EFFECT_ID, 0f)) }
        runCatching { effect.enabled = false }
        runCatching { effect.release() }
        reverb = null
    }

    fun release() {
        releaseReverb()
        releaseEqualizer()
    }

    private companion object {
        const val RADIO_LOW_CUT_HZ = 400
        const val RADIO_HIGH_CUT_HZ = 4_000
        const val SEEK_TOLERANCE_MS = 1_500L
    }
}
