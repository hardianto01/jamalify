package com.jamalsquad.jamalify.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import androidx.media3.common.util.UnstableApi

/**
 * Provider ini nyaris tidak melakukan apa-apa: pembaruan sesungguhnya didorong
 * oleh PlaybackService setiap kali status berubah. Sistem hanya memanggil ini
 * saat widget baru dipasang atau perangkat baru menyala, dan status terakhir
 * dibaca dari cache supaya widget tidak kosong sebelum ada yang diputar.
 */
@UnstableApi
class JamalifyWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        JamalifyWidget.renderFromCache(context)
    }

    override fun onEnabled(context: Context) {
        JamalifyWidget.renderFromCache(context)
    }
}
