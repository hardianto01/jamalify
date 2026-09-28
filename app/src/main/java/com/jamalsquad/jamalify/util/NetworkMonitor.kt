package com.jamalsquad.jamalify.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pemantau jaringan DEFAULT perangkat, dipakai PlaybackService untuk
 * memulihkan pemutaran.
 *
 * Yang dipantau sengaja pergantian jaringan, bukan sekadar offline → online.
 * Pindah Wi-Fi ke data seluler (atau sebaliknya) hampir selalu terjadi tanpa
 * jeda offline sama sekali — jaringan baru sudah siap sebelum yang lama
 * hilang. Kalau hanya menunggu "kembali online", event itu tidak pernah
 * datang dan lagu berhenti selamanya.
 */
class NetworkMonitor(context: Context) {

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isOnline = MutableStateFlow(checkOnline())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    /** Dipancarkan setiap kali jaringan default berganti ke jaringan baru yang siap dipakai. */
    private val _networkChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val networkChanged: SharedFlow<Unit> = _networkChanged.asSharedFlow()

    @Volatile
    private var usableNetwork: Network? = null

    /**
     * Jaringan pertama setelah [start] bukan pergantian — itu jaringan yang
     * sudah dipakai. Menganggapnya pergantian akan membuang URL yang baru
     * saja disiapkan dan membuat lagu pertama memuat dari nol.
     */
    @Volatile
    private var seenFirstNetwork = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val usable = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            _isOnline.value = usable
            if (usable && network != usableNetwork) {
                usableNetwork = network
                if (seenFirstNetwork) _networkChanged.tryEmit(Unit)
                seenFirstNetwork = true
            }
        }

        override fun onLost(network: Network) {
            // Callback default: onLost hanya datang kalau tidak ada pengganti.
            // Kalau ada, onCapabilitiesChanged untuk jaringan baru yang datang.
            if (network == usableNetwork) usableNetwork = null
            _isOnline.value = checkOnline()
        }
    }

    fun start() {
        runCatching { connectivityManager.registerDefaultNetworkCallback(networkCallback) }
    }

    fun stop() {
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
    }

    private fun checkOnline(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
