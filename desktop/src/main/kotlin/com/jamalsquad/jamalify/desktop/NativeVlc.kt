package com.jamalsquad.jamalify.desktop

import java.io.File

/**
 * Menunjukkan libVLC yang dibundel ke vlcj.
 *
 * Installer Windows (msi/exe) membawa libvlc.dll, libvlccore.dll dan plugins/
 * dari zip resmi VLC win64 di `<resources>/vlc`, di mana `<resources>` adalah
 * direktori yang diisi Compose dari `desktop/resources/<os>-<arch>/` dan
 * diumumkan lewat properti sistem `compose.application.resources.dir`
 * (berlaku untuk aplikasi terpasang maupun `./gradlew :desktop:run`).
 *
 * Direktori itu ditambahkan ke depan `jna.library.path`. NativeDiscovery
 * vlcj memeriksa jna.library.path sebelum lokasi instalasi sistem, dan begitu
 * libvlc ditemukan di sana ia sendiri yang menyetel VLC_PLUGIN_PATH ke
 * `<dir>/plugins`. Jadi kode playback cukup:
 *
 * ```
 * NativeVlc.configure()
 * NativeDiscovery().discover()   // atau langsung MediaPlayerFactory()
 * ```
 *
 * Bila tidak ada bundel (Linux, atau build Windows lokal tanpa unduhan VLC),
 * fungsi ini tidak melakukan apa-apa dan vlcj memakai VLC yang terpasang.
 * Lokasi lain bisa dipaksa dengan `-Djamalify.vlc.dir=...` atau variabel
 * lingkungan `JAMALIFY_VLC_DIR`.
 */
object NativeVlc {

    /** Direktori libVLC yang dipakai, atau null bila memakai VLC sistem. */
    @Volatile
    var bundledDir: File? = null
        private set

    @Synchronized
    fun configure(): File? {
        bundledDir?.let { return it }
        val dir = candidates().firstOrNull(::containsLibVlc) ?: return null
        val path = dir.absolutePath
        val existing = System.getProperty("jna.library.path").orEmpty()
        if (existing.split(File.pathSeparator).none { it == path }) {
            System.setProperty(
                "jna.library.path",
                if (existing.isBlank()) path else path + File.pathSeparator + existing,
            )
        }
        bundledDir = dir
        return dir
    }

    private fun candidates(): List<File> = buildList {
        System.getProperty("jamalify.vlc.dir")?.takeIf { it.isNotBlank() }?.let { add(File(it)) }
        System.getenv("JAMALIFY_VLC_DIR")?.takeIf { it.isNotBlank() }?.let { add(File(it)) }
        System.getProperty("compose.application.resources.dir")
            ?.takeIf { it.isNotBlank() }
            ?.let { add(File(it, "vlc")) }
    }

    private fun containsLibVlc(dir: File): Boolean {
        val os = System.getProperty("os.name").orEmpty().lowercase()
        val lib = when {
            os.contains("win") -> "libvlc.dll"
            os.contains("mac") -> "libvlc.dylib"
            else -> "libvlc.so"
        }
        return File(dir, lib).isFile
    }
}
