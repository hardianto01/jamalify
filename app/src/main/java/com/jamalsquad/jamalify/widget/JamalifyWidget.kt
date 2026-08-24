package com.jamalsquad.jamalify.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import android.widget.RemoteViews
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaButtonReceiver
import coil.ImageLoader
import coil.request.ImageRequest
import com.jamalsquad.jamalify.MainActivity
import com.jamalsquad.jamalify.R
import com.jamalsquad.jamalify.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Widget home screen: sampul, judul, dan tiga tombol.
 *
 * Sengaja TANPA progress bar. Progress yang bergerak memaksa widget di-refresh
 * setiap detik, dan tiap refresh membangunkan proses launcher plus satu putaran
 * IPC — pemborosan baterai yang berjalan terus-menerus. Di sini widget hanya
 * digambar ulang ketika status pemutaran benar-benar berubah: putar, jeda,
 * atau ganti lagu.
 */
@UnstableApi
object JamalifyWidget {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private const val PREFS = "jamalsquad_widget"
    private const val KEY_TITLE = "title"
    private const val KEY_ARTIST = "artist"
    private const val KEY_ART = "art"
    private const val KEY_PLAYING = "playing"

    /** Gambar ulang semua widget yang terpasang dengan status terbaru. */
    fun push(context: Context, song: Song?, isPlaying: Boolean) {
        remember(context, song, isPlaying)
        render(context, song, isPlaying)
    }

    /** Dipakai saat sistem meminta update dan pemutar sedang tidak hidup. */
    fun renderFromCache(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val title = prefs.getString(KEY_TITLE, null)
        val song = title?.let {
            Song(
                id = "",
                title = it,
                artist = prefs.getString(KEY_ARTIST, "").orEmpty(),
                thumbnail = prefs.getString(KEY_ART, null),
                durationSec = 0
            )
        }
        render(context, song, prefs.getBoolean(KEY_PLAYING, false))
    }

    private fun remember(context: Context, song: Song?, isPlaying: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            putString(KEY_TITLE, song?.title)
            putString(KEY_ARTIST, song?.artist)
            putString(KEY_ART, song?.thumbnail)
            putBoolean(KEY_PLAYING, isPlaying)
        }.apply()
    }

    private fun render(context: Context, song: Song?, isPlaying: Boolean) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, JamalifyWidgetProvider::class.java))
        if (ids.isEmpty()) return

        val views = buildViews(context, song, isPlaying, artwork = null)
        manager.updateAppWidget(ids, views)

        // Sampul menyusul supaya teks dan tombol muncul seketika.
        val artUrl = song?.thumbnail ?: return
        scope.launch {
            val bitmap = runCatching {
                val request = ImageRequest.Builder(context)
                    .data(artUrl)
                    .size(256, 256)
                    // RemoteViews tidak bisa membawa hardware bitmap lintas proses.
                    .allowHardware(false)
                    .build()
                ImageLoader(context).execute(request).drawable?.toBitmap()
            }.getOrNull() ?: return@launch

            val withArt = buildViews(context, song, isPlaying, artwork = bitmap)
            manager.updateAppWidget(ids, withArt)
        }
    }

    private fun buildViews(
        context: Context,
        song: Song?,
        isPlaying: Boolean,
        artwork: android.graphics.Bitmap?
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_player)

        views.setTextViewText(R.id.widget_title, song?.title ?: "Belum ada lagu")
        views.setTextViewText(
            R.id.widget_artist,
            song?.artist?.takeIf { it.isNotBlank() } ?: "Ketuk untuk membuka Jamalify"
        )

        if (artwork != null) {
            views.setImageViewBitmap(R.id.widget_art, artwork)
        } else {
            views.setImageViewResource(R.id.widget_art, R.drawable.ic_widget_note)
        }

        views.setImageViewResource(
            R.id.widget_play,
            if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
        )

        // Membuka aplikasi selalu aman, jadi itu aksi untuk badan widget.
        views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))

        if (song == null) {
            // Tidak ada sesi yang hidup: tombol transport tidak ada tujuannya,
            // jadi semuanya diarahkan membuka aplikasi daripada menembak
            // service yang belum tentu berjalan.
            val open = openAppIntent(context)
            views.setOnClickPendingIntent(R.id.widget_prev, open)
            views.setOnClickPendingIntent(R.id.widget_play, open)
            views.setOnClickPendingIntent(R.id.widget_next, open)
        } else {
            views.setOnClickPendingIntent(
                R.id.widget_prev,
                mediaKeyIntent(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            )
            views.setOnClickPendingIntent(
                R.id.widget_play,
                mediaKeyIntent(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            )
            views.setOnClickPendingIntent(
                R.id.widget_next,
                mediaKeyIntent(context, KeyEvent.KEYCODE_MEDIA_NEXT)
            )
        }

        return views
    }

    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    /**
     * Tombol transport dikirim sebagai media button ke MediaButtonReceiver
     * milik Media3, bukan sebagai startService langsung — receiver itu yang
     * tahu cara membangunkan sesi tanpa melanggar batasan start service dari
     * latar belakang.
     */
    private fun mediaKeyIntent(context: Context, keyCode: Int): PendingIntent {
        val intent = Intent(Intent.ACTION_MEDIA_BUTTON)
            .setComponent(ComponentName(context, MediaButtonReceiver::class.java))
            .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))

        return PendingIntent.getBroadcast(
            context,
            keyCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
