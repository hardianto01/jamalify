package com.jamalsquad.jamalify.desktop.playback

import uk.co.caprica.vlcj.media.callback.DefaultCallbackMedia
import java.io.FileNotFoundException
import java.io.RandomAccessFile

/**
 * Umpan audio ke VLC langsung dari berkas [StreamCache] yang sedang diunduh.
 *
 * VLC berhenti di ujung berkas lokal yang masih tumbuh, dan lewat HTTP lokal
 * ia butuh ±4 detik per koneksi. Lewat callback, byte yang belum tiba cukup
 * ditunggu di [onRead] — lagu bunyi begitu potongan pertama masuk.
 */
class ProgressiveMedia(private val progress: StreamCache.Progress) : DefaultCallbackMedia(true) {

    @Volatile private var pos = 0L
    @Volatile private var closed = false

    override fun onGetSize(): Long = progress.total

    override fun onOpen(): Boolean {
        pos = 0
        closed = false
        return true
    }

    override fun onRead(buffer: ByteArray, bufferSize: Int): Int {
        while (!closed) {
            val available = if (progress.done) progress.total else progress.written
            if (pos >= progress.total) return 0
            if (pos < available) {
                val n = readAt(pos, buffer, minOf(bufferSize.toLong(), available - pos).toInt())
                if (n > 0) {
                    pos += n
                    return n
                }
            } else if (progress.failed) {
                // Unduhan putus: akhiri stream; pemutar membedakannya lewat StreamCache.failedRecently.
                return -1
            }
            Thread.sleep(15)
        }
        return -1
    }

    override fun onSeek(offset: Long): Boolean {
        pos = offset
        return true
    }

    override fun onClose() {
        closed = true
    }

    private fun readAt(at: Long, buf: ByteArray, len: Int): Int {
        // Dibuka per potongan: .part bisa berganti nama jadi .audio kapan saja.
        for (f in listOf(progress.part, progress.target)) {
            try {
                RandomAccessFile(f, "r").use { raf ->
                    raf.seek(at)
                    return raf.read(buf, 0, len)
                }
            } catch (_: FileNotFoundException) {
            }
        }
        return -1
    }
}
