package com.jamalsquad.jamalify.playback

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.jamalsquad.jamalify.data.Song
import com.jamalsquad.jamalify.data.repo.MusicRepository
import com.jamalsquad.jamalify.data.youtube.YouTubeService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Cerdas: hanya pre-cache (resolve URL + download stream) saat terhubung Wi-Fi.
 * Mengisi cache ExoPlayer dengan 5 lagu berikutnya di antrean sehingga pemutaran
 * berikutnya langsung dari cache tanpa buffer.
 */
class WiFiPreCacheManager(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectivityManager =
        context.getSystemContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun preCacheUpcoming(songs: List<Song>, startIndex: Int, count: Int = 5) {
        if (!isOnWifi()) return

        val upcoming = songs.drop(startIndex + 1).take(count)
        scope.launch {
            for (song in upcoming) {
                try {
                    if (song.id.isBlank() || YouTubeService.isUrlCached(song.id)) continue
                    val local = MusicRepository.get(context).localFileFor(song.id)
                    if (local == null) {
                        YouTubeService.resolveAudioUrl(song.id)
                    }
                } catch (_: Exception) { }
            }
        }
    }

    private fun isOnWifi(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun Context.getSystemContext(): Context = applicationContext
}