package com.jamalsquad.jamalify.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.jamalsquad.jamalify.data.repo.RecommendationEngine
import com.jamalsquad.jamalify.desktop.catalog.YouTubeCatalog
import com.jamalsquad.jamalify.desktop.data.DesktopLibraryStore
import com.jamalsquad.jamalify.desktop.data.DesktopSettings
import com.jamalsquad.jamalify.desktop.playback.VlcPlayerController
import com.jamalsquad.jamalify.desktop.ui.JamalifyDesktopApp
import com.jamalsquad.jamalify.data.youtube.YouTubeService
import java.awt.Dimension
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(DelicateCoroutinesApi::class)
fun main() {
    // Harus sebelum VLC dibuat: libVLC bawaan installer Windows didahulukan dari VLC sistem.
    NativeVlc.configure()

    val settings = DesktopSettings()
    val library = DesktopLibraryStore()
    // Satu mesin untuk beranda dan radio, supaya cache lagu terkaitnya dipakai bersama.
    val recommendations = RecommendationEngine(library)
    val catalog = YouTubeCatalog(recommendations)
    val player = VlcPlayerController(library, recommendations).apply { setVolume(settings.volume.value) }

    // Seperti warmUpExtractor di Android: lagu terakhir kemungkinan besar diputar lagi,
    // jadi URL-nya disiapkan selagi jendela dibuka. Riwayat kosong = tanpa permintaan jaringan.
    GlobalScope.launch(Dispatchers.IO) {
        val last = runCatching { library.recentlyPlayed.first().firstOrNull() }.getOrNull() ?: return@launch
        runCatching { YouTubeService.prefetchAudioUrl(last.id) }
    }

    application {
        Window(
            onCloseRequest = {
                settings.setVolume(player.state.value.volume)
                player.release()
                library.close()
                exitApplication()
            },
            title = "Jamalify",
            state = rememberWindowState(width = 1280.dp, height = 800.dp)
        ) {
            window.minimumSize = Dimension(900, 600)
            JamalifyDesktopApp(player, catalog, library, settings)
        }
    }
}
