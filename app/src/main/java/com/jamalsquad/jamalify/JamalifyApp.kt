package com.jamalsquad.jamalify

import android.app.Application
import androidx.media3.common.util.UnstableApi
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.jamalsquad.jamalify.data.repo.MusicRepository
import com.jamalsquad.jamalify.data.youtube.YouTubeService
import com.jamalsquad.jamalify.playback.PlayerConnection
import com.jamalsquad.jamalify.playback.SoundModeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@UnstableApi
class JamalifyApp : Application(), ImageLoaderFactory {

    lateinit var repository: MusicRepository
        private set

    lateinit var playerConnection: PlayerConnection
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        repository = MusicRepository.get(this)
        // Sebelum antrean pertama dibuat: kualitas Hi-Res ikut tertulis di URI lagu.
        SoundModeManager.init(this)
        playerConnection = PlayerConnection(this)

        // Inisialisasi ekstraktor di latar supaya tidak menahan layar pertama.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { YouTubeService.ensureInit() }
            warmUpExtractor()
        }
    }

    /**
     * Resolve pertama selalu paling mahal: `base.js` harus diunduh dan
     * JavaScript-nya dijalankan sebelum URL apa pun bisa dipakai. Hasil
     * dekripsi itu dipakai bersama untuk seluruh sesi, jadi mengerjakannya
     * lebih awal membuat lagu pertama yang ditekan pengguna tidak menanggung
     * ongkos penuh.
     *
     * Sasarannya lagu terakhir dari riwayat — kalau ada, kemungkinan besar
     * lagu itu juga yang akan dilanjutkan, jadi hasilnya bukan sekadar
     * pemanasan. Kalau riwayat kosong, tidak ada permintaan jaringan yang
     * dikeluarkan sama sekali.
     */
    private suspend fun warmUpExtractor() {
        val last = runCatching {
            repository.recentlyPlayed().first().firstOrNull()
        }.getOrNull() ?: return
        runCatching { YouTubeService.prefetchAudioUrl(last.id) }
    }

    /**
     * Satu ImageLoader untuk seluruh aplikasi, jadi cache memori dan disknya
     * dipakai bersama oleh daftar lagu, pemutar, palet warna, dan widget.
     * Header cache i.ytimg pendek, padahal sampul video tidak pernah berubah —
     * diabaikan supaya sampul yang sudah pernah tampil tidak diunduh ulang.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache {
            MemoryCache.Builder(this).maxSizePercent(0.25).build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("artwork"))
                .maxSizeBytes(128L * 1024 * 1024)
                .build()
        }
        .respectCacheHeaders(false)
        .crossfade(150)
        .build()

    companion object {
        lateinit var instance: JamalifyApp
            private set
    }
}
