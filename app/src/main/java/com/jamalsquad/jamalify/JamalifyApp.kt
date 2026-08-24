package com.jamalsquad.jamalify

import android.app.Application
import androidx.media3.common.util.UnstableApi
import com.jamalsquad.jamalify.data.repo.MusicRepository
import com.jamalsquad.jamalify.data.youtube.YouTubeService
import com.jamalsquad.jamalify.playback.PlayerConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@UnstableApi
class JamalifyApp : Application() {

    lateinit var repository: MusicRepository
        private set

    lateinit var playerConnection: PlayerConnection
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        repository = MusicRepository.get(this)
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

    companion object {
        lateinit var instance: JamalifyApp
            private set
    }
}
