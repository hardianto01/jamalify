package com.jamalsquad.jamalify.data.youtube

import com.jamalsquad.jamalify.data.RemotePlaylist
import com.jamalsquad.jamalify.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.kiosk.KioskExtractor
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Semua akses ke YouTube lewat NewPipeExtractor dikumpulkan di sini supaya
 * sisa aplikasi tidak pernah menyentuh API ekstraktor secara langsung.
 */
object YouTubeService {

    private val service = ServiceList.YouTube

    @Volatile
    private var initialized = false

    fun ensureInit() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            NewPipe.init(JamalifyDownloader.init())
            initialized = true
        }
    }

    /**
     * Cari lagu. Filter YouTube Music dipakai lebih dulu karena hasilnya jauh
     * lebih relevan untuk musik; kalau kosong atau gagal, jatuh ke video biasa.
     */
    suspend fun search(query: String, limit: Int = 30): List<Song> = withContext(Dispatchers.IO) {
        ensureInit()
        val musicResults = runCatching {
            searchWithFilter(query, YoutubeSearchQueryHandlerFactory.MUSIC_SONGS, limit)
        }.getOrDefault(emptyList())

        if (musicResults.isNotEmpty()) return@withContext musicResults

        runCatching {
            searchWithFilter(query, YoutubeSearchQueryHandlerFactory.VIDEOS, limit)
        }.getOrDefault(emptyList())
    }

    private fun searchWithFilter(query: String, filter: String, limit: Int): List<Song> {
        val handler = service.searchQHFactory.fromQuery(query, listOf(filter), "")
        val extractor = service.getSearchExtractor(handler)
        extractor.fetchPage()
        return extractor.initialPage.items.toSongs(limit)
    }

    private var trendingExtractor: KioskExtractor<*>? = null
    private var trendingNextPage: Page? = null
    private val trendingLock = Mutex()

    /**
     * Trending YouTube. Extractor dan halaman berikutnya disimpan supaya
     * [trendingMore] bisa melanjutkan dari titik yang sama.
     */
    suspend fun trending(limit: Int = 30): List<Song> = withContext(Dispatchers.IO) {
        ensureInit()
        trendingLock.withLock {
            runCatching {
                val extractor = service.kioskList.defaultKioskExtractor
                extractor.forceContentCountry(ContentCountry("ID"))
                extractor.fetchPage()
                val page = extractor.initialPage
                trendingExtractor = extractor
                trendingNextPage = page.nextPage
                page.items.toSongs(limit)
            }.getOrDefault(emptyList())
        }
    }

    /** Halaman trending berikutnya; kosong kalau sudah habis. */
    suspend fun trendingMore(limit: Int = 30): List<Song> = withContext(Dispatchers.IO) {
        ensureInit()
        trendingLock.withLock {
            val extractor = trendingExtractor ?: return@withLock emptyList()
            val next = trendingNextPage ?: return@withLock emptyList()
            runCatching {
                val page = extractor.getPage(next)
                trendingNextPage = page.nextPage
                page.items.toSongs(limit)
            }.getOrDefault(emptyList())
        }
    }

    fun hasMoreTrending(): Boolean = trendingNextPage != null

    /**
     * Satu rak rekomendasi bertema. Selalu lewat filter YouTube Music supaya
     * yang muncul lagu, bukan vlog atau reaksi yang kebetulan cocok kata kunci.
     */
    suspend fun shelf(query: String, limit: Int = 20): List<Song> = withContext(Dispatchers.IO) {
        ensureInit()
        runCatching {
            searchWithFilter(query, YoutubeSearchQueryHandlerFactory.MUSIC_SONGS, limit)
        }.getOrDefault(emptyList())
    }

    /** Lagu terkait — dipakai untuk mode radio / lanjut otomatis. */
    suspend fun related(videoId: String, limit: Int = 20): List<Song> = withContext(Dispatchers.IO) {
        ensureInit()
        runCatching {
            val info = StreamInfo.getInfo(service, watchUrl(videoId))
            info.relatedItems.toSongs(limit)
        }.getOrDefault(emptyList())
    }

    /** Ambil seluruh isi playlist/album YouTube, termasuk halaman berikutnya. */
    suspend fun playlist(url: String, maxSongs: Int = 500): RemotePlaylist =
        withContext(Dispatchers.IO) {
            ensureInit()
            var info = PlaylistInfo.getInfo(service, url)
            val songs = info.relatedItems.toSongs(maxSongs).toMutableList()

            var nextPage = info.nextPage
            while (nextPage != null && songs.size < maxSongs) {
                val page = PlaylistInfo.getMoreItems(service, url, nextPage)
                songs += page.items.toSongs(maxSongs - songs.size)
                nextPage = page.nextPage
                if (page.items.isEmpty()) break
            }

            RemotePlaylist(
                title = info.name ?: "Playlist",
                author = info.uploaderName ?: "",
                thumbnail = info.thumbnails.bestUrl(),
                songs = songs
            )
        }

    private data class CachedUrl(val url: String, val expiresAt: Long)

    private val urlCache = ConcurrentHashMap<String, CachedUrl>()

    /**
     * URL audio langsung untuk sebuah video.
     *
     * Menyelesaikan satu URL itu mahal: unduh halaman watch, unduh `base.js`,
     * lalu jalankan JavaScript-nya untuk memecahkan parameter `n`. Hasilnya
     * disimpan sampai URL kedaluwarsa supaya memutar ulang lagu yang sama
     * tidak membangunkan radio sama sekali.
     */
    suspend fun resolveAudioUrl(videoId: String): String = withContext(Dispatchers.IO) {
        cachedUrl(videoId)?.let { return@withContext it }

        ensureInit()
        val info = StreamInfo.getInfo(service, watchUrl(videoId))
        val stream = info.audioStreams.pickBest()
            ?: throw IllegalStateException("Tidak ada stream audio untuk $videoId")
        val url = stream.content
            ?: throw IllegalStateException("URL stream kosong untuk $videoId")

        rememberUrl(videoId, url)
        url
    }

    /**
     * Isi cache tanpa memutar apa pun. Dipakai untuk lagu berikutnya di
     * antrean, dijalankan saat lagu sekarang mulai memuat — radio sudah
     * menyala di titik itu, jadi ongkos tambahannya nyaris nol.
     */
    suspend fun prefetchAudioUrl(videoId: String) {
        if (cachedUrl(videoId) != null) return
        runCatching { resolveAudioUrl(videoId) }
    }

    fun isUrlCached(videoId: String) = cachedUrl(videoId) != null

    private fun cachedUrl(videoId: String): String? =
        urlCache[videoId]?.takeIf { it.expiresAt > System.currentTimeMillis() }?.url

    private fun rememberUrl(videoId: String, url: String) {
        if (urlCache.size > MAX_CACHED_URLS) {
            val now = System.currentTimeMillis()
            urlCache.entries.removeAll { it.value.expiresAt <= now }
            if (urlCache.size > MAX_CACHED_URLS) urlCache.clear()
        }
        urlCache[videoId] = CachedUrl(url, expiryOf(url))
    }

    /** YouTube menaruh waktu kedaluwarsa di URL-nya; pakai itu kalau ada. */
    private fun expiryOf(url: String): Long {
        val now = System.currentTimeMillis()
        val fromUrl = EXPIRE_PARAM.find(url)
            ?.groupValues?.getOrNull(1)
            ?.toLongOrNull()
            ?.let { it * 1000 - EXPIRY_MARGIN_MS }
        return fromUrl?.takeIf { it > now } ?: (now + DEFAULT_TTL_MS)
    }

    /** Metadata lengkap satu lagu, dipakai saat impor dari ID mentah. */
    suspend fun songInfo(videoId: String): Song = withContext(Dispatchers.IO) {
        ensureInit()
        val info = StreamInfo.getInfo(service, watchUrl(videoId))
        Song(
            id = videoId,
            title = info.name ?: videoId,
            artist = info.uploaderName ?: "",
            thumbnail = info.thumbnails.bestUrl(),
            durationSec = info.duration
        )
    }

    fun watchUrl(videoId: String) = "https://www.youtube.com/watch?v=$videoId"

    /**
     * Ambil videoId dari berbagai bentuk URL YouTube, atau kembalikan apa
     * adanya kalau yang diberikan memang sudah berupa ID.
     */
    fun extractVideoId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.matches(Regex("[A-Za-z0-9_-]{11}"))) return trimmed
        val patterns = listOf(
            Regex("""(?:v=|/v/|youtu\.be/|/embed/|/shorts/)([A-Za-z0-9_-]{11})""")
        )
        for (p in patterns) {
            p.find(trimmed)?.groupValues?.getOrNull(1)?.let { return it }
        }
        return null
    }

    fun isPlaylistUrl(input: String): Boolean =
        input.contains("list=") || input.contains("/playlist")

    // --- helper internal ---

    private fun List<*>.toSongs(limit: Int): List<Song> =
        asSequence()
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull { it.toSongOrNull() }
            .distinctBy { it.id }
            .take(limit)
            .toList()

    private fun StreamInfoItem.toSongOrNull(): Song? {
        val id = extractVideoId(url ?: return null) ?: return null
        return Song(
            id = id,
            title = name ?: return null,
            artist = uploaderName.orEmpty().removeSuffix(" - Topic"),
            thumbnail = thumbnails.bestUrl(),
            durationSec = duration.coerceAtLeast(0)
        )
    }

    private fun List<Image>?.bestUrl(): String? =
        this?.maxByOrNull { it.height }?.url ?: this?.firstOrNull()?.url

    /**
     * Prioritaskan m4a supaya kompatibel dengan ExoPlayer di semua perangkat.
     *
     * Bitrate tertinggi bukan pilihan yang baik di HP: byte-nya paling banyak,
     * jadi radio menyala paling lama dan buffer awal paling lambat penuh.
     * ~128 kbps sudah transparan lewat speaker atau earphone biasa.
     */
    private fun List<AudioStream>?.pickBest(): AudioStream? {
        if (this.isNullOrEmpty()) return null
        val m4a = filter { it.format?.name?.contains("M4A", ignoreCase = true) == true }
        val pool = m4a.ifEmpty { this }

        val rated = pool.filter { it.averageBitrate > 0 }
        if (rated.isEmpty()) return pool.firstOrNull()

        val acceptable = rated.filter { it.averageBitrate >= MIN_BITRATE_KBPS }
        return (acceptable.ifEmpty { rated })
            .minByOrNull { abs(it.averageBitrate - TARGET_BITRATE_KBPS) }
    }

    private const val TARGET_BITRATE_KBPS = 128
    private const val MIN_BITRATE_KBPS = 96
    private const val MAX_CACHED_URLS = 150
    private const val DEFAULT_TTL_MS = 2 * 60 * 60 * 1000L
    private const val EXPIRY_MARGIN_MS = 5 * 60 * 1000L
    private val EXPIRE_PARAM = Regex("""[?&]expire=(\d+)""")
}
