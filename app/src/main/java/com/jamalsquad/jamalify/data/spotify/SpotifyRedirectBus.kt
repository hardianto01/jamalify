package com.jamalsquad.jamalify.data.spotify

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Titik temu antara Activity dan layar Impor.
 *
 * Redirect login mendarat di [android.app.Activity.onNewIntent], sementara
 * yang perlu menanganinya adalah ImportViewModel yang baru dibuat ketika layar
 * Impor tampil. Nilainya ditahan di sini sampai ada yang mengambilnya, jadi
 * urutan mana yang lebih dulu siap tidak jadi masalah.
 */
object SpotifyRedirectBus {
    val redirect = MutableStateFlow<Uri?>(null)

    fun post(uri: Uri) {
        redirect.value = uri
    }

    fun consume() {
        redirect.value = null
    }
}
