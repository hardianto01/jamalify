package com.jamalsquad.jamalify.desktop.playback

import com.jamalsquad.jamalify.data.youtube.StreamHeadersInterceptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * Lagu diunduh ke disk per potongan Range berujung, dan VLC membacanya lewat
 * [ProgressiveMedia] sambil unduhan berjalan.
 *
 * googlevideo memperlambat permintaan Range terbuka (`bytes=0-`) sampai
 * ±30 KB/s — persis yang dikirim VLC, sehingga lagu baru bunyi setelah ±8
 * detik. Permintaan dengan ujung jelas dilayani penuh (3 MB < 2 detik).
 * Lagu bisa mulai begitu puluhan KB pertama tiba, lagu berikutnya yang sudah
 * di-prefetch langsung bunyi, dan seek ke bagian yang sudah ada tidak menunggu
 * jaringan.
 */
object StreamCache {

    private val client = OkHttpClient.Builder()
        .addInterceptor(StreamHeadersInterceptor())
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val dir: File by lazy {
        val base = System.getenv("XDG_CACHE_HOME")?.let(::File)
            ?: System.getenv("LOCALAPPDATA")?.let(::File)
            ?: File(System.getProperty("user.home"), ".cache")
        File(base, "jamalify/stream").apply { mkdirs() }
    }

    /** Kemajuan unduhan yang sedang berjalan; dibaca [ProgressiveMedia] dari thread VLC. */
    class Progress(val part: File, val target: File) {
        @Volatile var total = -1L
        @Volatile var written = 0L
        @Volatile var done = false
        @Volatile var failed = false
    }

    private val inFlight = ConcurrentHashMap<String, Deferred<File>>()
    private val progress = ConcurrentHashMap<String, Progress>()

    fun progressOf(videoId: String): Progress? = progress[videoId]

    /** Unduhan terakhir lagu ini putus di tengah — akhir stream bukan berarti lagu selesai. */
    fun failedRecently(videoId: String) = progress[videoId]?.failed == true

    /**
     * Mulai (atau ikut) unduhan [videoId] tanpa menunggu selesai. Kembali setelah
     * ukuran berkas diketahui, supaya VLC tahu ukuran stream sejak awal.
     */
    suspend fun begin(videoId: String, url: String): Progress {
        cached(videoId)?.let { f ->
            return Progress(f, f).apply { total = f.length(); written = total; done = true }
        }
        val p = progress.compute(videoId) { _, old ->
            old?.takeUnless { it.failed } ?: Progress(File(dir, "$videoId.part"), File(dir, "$videoId.audio"))
        }!!
        scope.async { runCatching { fetch(videoId, url) } }
        while (p.total < 0) {
            if (p.failed) throw IOException("Unduhan $videoId gagal")
            kotlinx.coroutines.delay(20)
        }
        return p
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Berkas lokal untuk [videoId]; dua pemanggil untuk lagu yang sama berbagi satu unduhan. */
    suspend fun fetch(videoId: String, url: String): File {
        cached(videoId)?.let { return it }
        val job = inFlight.computeIfAbsent(videoId) {
            scope.async(start = CoroutineStart.LAZY) {
                try {
                    download(videoId, url)
                } finally {
                    inFlight.remove(videoId)
                }
            }
        }
        job.start()
        return job.await()
    }

    fun cached(videoId: String): File? =
        File(dir, "$videoId.audio").takeIf { it.isFile && it.length() > 0 }
            ?.also { it.setLastModified(System.currentTimeMillis()) }

    fun evict(videoId: String) {
        progress.remove(videoId)
        File(dir, "$videoId.audio").delete()
    }

    private suspend fun download(videoId: String, url: String): File {
        val p = progress.compute(videoId) { _, old ->
            old?.takeUnless { it.failed || it.done } ?: Progress(File(dir, "$videoId.part"), File(dir, "$videoId.audio"))
        }!!
        try {
            return downloadInto(p, videoId, url)
        } catch (e: Throwable) {
            p.failed = true
            throw e
        }
    }

    private suspend fun downloadInto(p: Progress, videoId: String, url: String): File {
        val total = CLEN.find(url)?.groupValues?.get(1)?.toLongOrNull() ?: probeLength(url)
        p.written = 0
        p.total = total
        val part = p.part
        part.outputStream().use { out ->
            var offset = 0L
            while (offset < total) {
                coroutineContext.ensureActive()
                val end = minOf(offset + CHUNK_BYTES, total) - 1
                val request = Request.Builder().url(url).header("Range", "bytes=$offset-$end").build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code} untuk $videoId")
                    val body = response.body ?: throw IOException("Respons kosong untuk $videoId")
                    body.byteStream().use { input ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            out.flush()
                            offset += n
                            p.written = offset
                        }
                    }
                }
            }
        }
        val target = p.target
        if (!part.renameTo(target)) {
            // Windows menolak rename selama VLC masih membaca .part.
            part.copyTo(target, overwrite = true)
            part.delete()
        }
        p.done = true
        trim()
        return target
    }

    /** URL tanpa `clen`: tanya ukuran lewat Range satu byte. */
    private fun probeLength(url: String): Long {
        val request = Request.Builder().url(url).header("Range", "bytes=0-0").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            return response.header("Content-Range")?.substringAfter('/')?.toLongOrNull()
                ?: throw IOException("Ukuran stream tidak diketahui")
        }
    }

    /** Simpan beberapa lagu terakhir saja; cache ini bukan fitur unduhan. */
    private fun trim() {
        val files = dir.listFiles { f -> f.name.endsWith(".audio") }?.sortedByDescending { it.lastModified() }
            ?: return
        files.drop(MAX_FILES).forEach { it.delete() }
    }

    private val CLEN = Regex("""[?&]clen=(\d+)""")
    // Di bawah ambang yang dikenal memicu pelambatan untuk satu permintaan besar.
    private const val CHUNK_BYTES = 4L * 1024 * 1024
    private const val MAX_FILES = 12
}
