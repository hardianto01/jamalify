package com.jamalsquad.jamalify.ui.components

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Peliharaan pilihan pengguna di beranda, disimpan seperti [com.jamalsquad.jamalify.ui.theme.ThemeManager]. */
object PetManager {

    private const val PREFS = "pet"
    private const val KEY_KIND = "kind"

    private val _kind = MutableStateFlow(PetKind.CAT)
    val kind: StateFlow<PetKind> = _kind.asStateFlow()

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        val store = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = store
        _kind.value = store.getString(KEY_KIND, null)
            ?.let { name -> PetKind.entries.firstOrNull { it.name == name } }
            ?: PetKind.CAT
    }

    fun select(kind: PetKind) {
        _kind.value = kind
        prefs?.edit()?.putString(KEY_KIND, kind.name)?.apply()
    }
}
