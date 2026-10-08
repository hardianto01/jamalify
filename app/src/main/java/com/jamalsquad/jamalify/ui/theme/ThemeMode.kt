package com.jamalsquad.jamalify.ui.theme

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val label: String) {
    SYSTEM("Ikuti sistem"),
    LIGHT("Terang"),
    DARK("Gelap")
}

/**
 * Pilihan tema pengguna. Bawaannya mengikuti sistem; pilihan manual ada
 * untuk OEM yang mode gelapnya tidak menyetel `uiMode=night`, sehingga
 * aplikasi tidak tahu ponselnya sedang gelap.
 */
object ThemeManager {

    private const val PREFS = "theme"
    private const val KEY_MODE = "mode"

    private val _mode = MutableStateFlow(ThemeMode.SYSTEM)
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        val store = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = store
        _mode.value = store.getString(KEY_MODE, null)
            ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
            ?: ThemeMode.SYSTEM
    }

    fun select(mode: ThemeMode) {
        _mode.value = mode
        prefs?.edit()?.putString(KEY_MODE, mode.name)?.apply()
    }
}
