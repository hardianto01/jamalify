package com.jamalsquad.jamalify.data.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.jamalsquad.jamalify.R
import com.jamalsquad.jamalify.data.db.DownloadEntity
import com.jamalsquad.jamalify.data.db.DownloadState
import com.jamalsquad.jamalify.data.db.JamalifyDatabase
import com.jamalsquad.jamalify.data.repo.MusicRepository
import com.jamalsquad.jamalify.data.youtube.YouTubeService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Mengunduh audio satu per satu dari antrean di tabel `downloads`.
 * Berjalan sebagai foreground service supaya tidak dimatikan sistem saat
 * aplikasi ditutup.
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Menyiapkan unduhan…", 0, false))
        if (!running) {
            running = true
            scope.launch { drainQueue() }
        }
        return START_NOT_STICKY
    }

    private suspend fun drainQueue() {
        val dao = JamalifyDatabase.get(this).musicDao()
        val repository = MusicRepository.get(this)

        while (true) {
            val pending = dao.nextPendingDownload() ?: break
            val song = dao.getSong(pending.songId)
            val title = song?.title ?: pending.songId

            dao.upsertDownload(pending.copy(state = DownloadState.RUNNING, progress = 0))
            notify("Mengunduh $title", 0, true)

            val target = repository.downloadFileFor(pending.songId)
            val result = runCatching { downloadTo(pending.songId, target, title) }

            if (result.isSuccess) {
                dao.upsertDownload(
                    DownloadEntity(
                        songId = pending.songId,
                        filePath = target.absolutePath,
                        state = DownloadState.COMPLETED,
                        progress = 100,
                        sizeBytes = target.length()
                    )
                )
            } else {
                target.delete()
                dao.upsertDownload(
                    DownloadEntity(
                        songId = pending.songId,
                        state = DownloadState.FAILED,
                        error = result.exceptionOrNull()?.message ?: "Gagal mengunduh"
                    )
                )
            }
        }

        running = false
        stopForegroundCompat()
        stopSelf()
    }

    private suspend fun downloadTo(songId: String, target: File, title: String) {
        val url = YouTubeService.resolveAudioUrl(songId)
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0")
            .build()

        val dao = JamalifyDatabase.get(this).musicDao()
        target.parentFile?.mkdirs()
        val partial = File(target.absolutePath + ".part")

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body ?: error("Respons kosong")
            val total = body.contentLength()

            body.byteStream().use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = 0L
                    var lastReported = -1

                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloaded += read

                        if (total > 0) {
                            val percent = ((downloaded * 100) / total).toInt()
                            if (percent != lastReported && percent % 2 == 0) {
                                lastReported = percent
                                dao.upsertDownload(
                                    DownloadEntity(
                                        songId = songId,
                                        state = DownloadState.RUNNING,
                                        progress = percent,
                                        sizeBytes = total
                                    )
                                )
                                notify("Mengunduh $title", percent, false)
                            }
                        }
                    }
                }
            }
        }

        if (!partial.renameTo(target)) {
            partial.copyTo(target, overwrite = true)
            partial.delete()
        }
    }

    // --- notifikasi ---

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.download_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    private fun buildNotification(text: String, progress: Int, indeterminate: Boolean) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Jamalify")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, indeterminate)
            .build()

    private fun notify(text: String, progress: Int, indeterminate: Boolean) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text, progress, indeterminate))
    }

    @Suppress("DEPRECATION")
    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "jamalsquad_downloads"
        private const val NOTIFICATION_ID = 4201

        fun start(context: Context) {
            val intent = Intent(context, DownloadService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
