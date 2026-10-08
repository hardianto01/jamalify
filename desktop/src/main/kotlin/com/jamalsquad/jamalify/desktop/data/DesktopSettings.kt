package com.jamalsquad.jamalify.desktop.data

import com.jamalsquad.jamalify.desktop.core.AppSettings
import com.jamalsquad.jamalify.desktop.core.DesktopTheme
import com.jamalsquad.jamalify.ui.components.PetKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.Properties

/**
 * Preferensi di berkas .properties: cukup untuk tiga nilai, dan bisa diedit
 * tangan kalau perlu. Nilai yang tidak dikenali jatuh ke bawaan, bukan crash.
 */
class DesktopSettings(private val file: File = DataPaths.settingsFile) : AppSettings {

    private val props = Properties().apply {
        runCatching { if (file.exists()) file.inputStream().use { load(it) } }
    }

    private val _pet = MutableStateFlow(
        props.getProperty(KEY_PET)?.let { runCatching { PetKind.valueOf(it) }.getOrNull() } ?: PetKind.CAT
    )
    private val _theme = MutableStateFlow(
        props.getProperty(KEY_THEME)?.let { runCatching { DesktopTheme.valueOf(it) }.getOrNull() } ?: DesktopTheme.SYSTEM
    )
    private val _volume = MutableStateFlow(
        props.getProperty(KEY_VOLUME)?.toFloatOrNull()?.coerceIn(0f, 1f) ?: DEFAULT_VOLUME
    )

    override val pet: StateFlow<PetKind> = _pet
    override val theme: StateFlow<DesktopTheme> = _theme
    override val volume: StateFlow<Float> = _volume

    override fun setPet(kind: PetKind) {
        _pet.value = kind
        save(KEY_PET, kind.name)
    }

    override fun setTheme(theme: DesktopTheme) {
        _theme.value = theme
        save(KEY_THEME, theme.name)
    }

    override fun setVolume(volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        _volume.value = v
        save(KEY_VOLUME, v.toString())
    }

    /*
     * Tulis ke berkas sementara lalu rename: kalau app mati di tengah jalan
     * (mis. volume digeser lalu jendela ditutup), berkas lama tetap utuh.
     */
    @Synchronized
    private fun save(key: String, value: String) {
        props.setProperty(key, value)
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.outputStream().use { props.store(it, "Jamalify desktop") }
            java.nio.file.Files.move(
                tmp.toPath(), file.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE
            )
        }
    }

    private companion object {
        const val KEY_PET = "pet"
        const val KEY_THEME = "theme"
        const val KEY_VOLUME = "volume"
        const val DEFAULT_VOLUME = 0.6f
    }
}
