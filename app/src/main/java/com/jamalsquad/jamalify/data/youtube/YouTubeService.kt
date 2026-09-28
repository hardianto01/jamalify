package com.jamalsquad.jamalify.data.youtube

import com.jamalsquad.jamalify.data.PlaylistSummary
import com.jamalsquad.jamalify.data.RemotePlaylist
import com.jamalsquad.jamalify.data.Song
import com.jamalsquad.jamalify.util.Thumbnails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
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
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
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
    suspend fun search(query: String, limit: Int = 50): List<Song> = withContext(Dispatchers.IO) {
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
     * Trending YouTube Musik. Menggunakan pencarian YouTube Music (MUSIC_SONGS)
     * untuk memastikan konten yang tampil murni lagu/musik populer, bukan video live/game.
     */
    suspend fun trending(limit: Int = 50): List<Song> = withContext(Dispatchers.IO) {
        ensureInit()
        trendingLock.withLock {
            val musicTrending = runCatching {
                kotlinx.coroutines.withTimeoutOrNull(7000L) {
                    searchWithFilter("Trending Musik Indonesia Hits", YoutubeSearchQueryHandlerFactory.MUSIC_SONGS, limit)
                }
            }.getOrNull() ?: emptyList()

            if (musicTrending.isNotEmpty()) return@withLock musicTrending

            // Fallback: Cari lagu musik populer Indonesia via YouTube Music filter
            runCatching {
                searchWithFilter("Lagu Pop Indonesia Hits Terbaru", YoutubeSearchQueryHandlerFactory.MUSIC_SONGS, limit)
            }.getOrDefault(emptyList())
        }
    }

    /** Halaman trending berikutnya; kosong kalau sudah habis. */
    suspend fun trendingMore(limit: Int = 50): List<Song> = withContext(Dispatchers.IO) {
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
    suspend fun shelf(query: String, limit: Int = 50): List<Song> = withContext(Dispatchers.IO) {
        ensureInit()
        runCatching {
            searchWithFilter(query, YoutubeSearchQueryHandlerFactory.MUSIC_SONGS, limit)
        }.getOrDefault(emptyList())
    }

    /** Lagu terkait — dipakai untuk mode radio / lanjut otomatis. */
    suspend fun related(videoId: String, limit: Int = 50): List<Song> = withContext(Dispatchers.IO) {
        ensureInit()
        runCatching {
            val info = StreamInfo.getInfo(service, watchUrl(videoId))
            info.relatedItems.toSongs(limit)
        }.getOrDefault(emptyList())
    }

    /** Playlist YouTube Music yang cocok dengan [query], tanpa isinya. */
    suspend fun searchPlaylists(query: String, limit: Int = 10): List<PlaylistSummary> =
        withContext(Dispatchers.IO) {
            ensureInit()
            runCatching {
                val handler = service.searchQHFactory.fromQuery(
                    query, listOf(YoutubeSearchQueryHandlerFactory.MUSIC_PLAYLISTS), ""
                )
                val extractor = service.getSearchExtractor(handler)
                extractor.fetchPage()
                extractor.initialPage.items
                    .filterIsInstance<PlaylistInfoItem>()
                    .mapNotNull { item ->
                        PlaylistSummary(
                            url = item.url ?: return@mapNotNull null,
                            title = item.name ?: return@mapNotNull null,
                            author = item.uploaderName.orEmpty(),
                            thumbnail = item.thumbnails.bestUrl()
                        )
                    }
                    .take(limit)
            }.getOrDefault(emptyList())
        }

    /** Ambil seluruh isi playlist/album YouTube, termasuk halaman berikutnya. */
    suspend fun playlist(url: String, maxSongs: Int = 5000): RemotePlaylist =
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
    suspend fun resolveAudioUrl(videoId: String, hiRes: Boolean = this.hiRes): String {
        val urlKey = urlKey(videoId, hiRes)
        cachedUrl(urlKey)?.let { return it }
        // Lagu yang sedang di-prefetch lalu ditekan pengguna: tunggu hasil
        // yang sudah setengah jalan, jangan mulai dari awal lagi.
        // LAZY: kalau langsung jalan, job bisa selesai (dan menghapus dirinya
        // dari map) sebelum sempat dimasukkan — lalu tertinggal selamanya.
        val job = inFlight.computeIfAbsent(urlKey) {
            resolveScope.async(start = CoroutineStart.LAZY) {
                try {
                    resolveUncached(videoId, hiRes)
                } finally {
                    inFlight.remove(urlKey)
                }
            }
        }
        job.start()
        return job.await()
    }

    /**
     * Hanya stream audio yang diambil, bukan [StreamInfo] lengkap. StreamInfo
     * juga memproses semua stream video — masing-masing URL-nya didekripsi
     * lewat JavaScript — padahal tidak satu pun dipakai.
     */
    private fun resolveUncached(videoId: String, hiRes: Boolean): String {
        ensureInit()
        // Sesekali YouTube menjawab tanpa stream audio sama sekali, padahal
        // permintaan berikutnya normal. Satu kali coba lagi sebelum menyerah.
        val stream = (1..RESOLVE_ATTEMPTS).firstNotNullOfOrNull {
            val extractor = service.getStreamExtractor(watchUrl(videoId))
            extractor.fetchPage()
            extractor.audioStreams.pickBest(hiRes)
        } ?: throw IllegalStateException("Tidak ada stream audio untuk $videoId")
        val url = stream.content

        rememberUrl(urlKey(videoId, hiRes), url)
        return url
    }

    private val inFlight = ConcurrentHashMap<String, Deferred<String>>()
    private val resolveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Isi cache tanpa memutar apa pun. Dipakai untuk lagu berikutnya di
     * antrean, dijalankan saat lagu sekarang mulai memuat — radio sudah
     * menyala di titik itu, jadi ongkos tambahannya nyaris nol.
     */
    suspend fun prefetchAudioUrl(videoId: String) {
        if (isUrlCached(videoId)) return
        runCatching { resolveAudioUrl(videoId) }
    }

    fun isUrlCached(videoId: String) = cachedUrl(urlKey(videoId, hiRes)) != null

    /** Hapus URL dari cache jika terjadi error pemutaran / stream kedaluwarsa. */
    fun invalidateUrl(videoId: String) {
        urlCache.remove(urlKey(videoId, hiRes = false))
        urlCache.remove(urlKey(videoId, hiRes = true))
    }

    /**
     * Kualitas yang dipakai lagu-lagu berikutnya. Hi-Res mengambil stream
     * dengan bitrate tertinggi (biasanya Opus ±160 kbps) alih-alih M4A 128.
     */
    @Volatile
    var hiRes: Boolean = false

    private fun urlKey(videoId: String, hiRes: Boolean) = if (hiRes) "$videoId#hi" else videoId

    /** Bersihkan seluruh cache URL stream. */
    fun clearAllCachedUrls() {
        urlCache.clear()
    }

    private fun cachedUrl(urlKey: String): String? =
        urlCache[urlKey]?.takeIf { it.expiresAt > System.currentTimeMillis() }?.url

    private fun rememberUrl(urlKey: String, url: String) {
        if (urlCache.size > MAX_CACHED_URLS) {
            val now = System.currentTimeMillis()
            urlCache.entries.removeAll { it.value.expiresAt <= now }
            if (urlCache.size > MAX_CACHED_URLS) urlCache.clear()
        }
        urlCache[urlKey] = CachedUrl(url, expiryOf(url))
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

        val itemTitle = name ?: return null
        val titleLower = itemTitle.lowercase()
        val uploaderLower = uploaderName.orEmpty().lowercase()

        // Filter out live streams
        if (streamType?.name?.contains("LIVE", ignoreCase = true) == true) {
            return null
        }

        // Filter out duration: Songs are strictly between 30 seconds and 12 minutes (720 seconds)
        // This strictly excludes long podcasts (30m-2h), talk shows, full movies, and long vlogs
        if (duration > 0 && (duration < 30 || duration > 720)) {
            return null
        }

        // Filter out non-music keywords (podcasts, games, sports, news, vlogs, talk shows, etc.)
        val nonMusicKeywords = listOf(
            "podcast", "podkes", "episode", "eps.", "eps ", "eps#", "ep.", "ep ", "talkshow", "talk show",
            "interview", "wawancara", "bincang", "curhat", "obrolan", "ceramah", "khotbah", "kajian",
            "vlog", "gameplay", "mabar", "push rank", "reaction", "react", "review", "unboxing",
            "tutorial", "cara ", "tips ", "trailer", "teaser", "full movie", "film", "sinopsis",
            "dokumenter", "documentary", "highlight", "full match", "news", "berita", "live stream",
            "live streaming", "asmr", "shorts", "stand up", "komedi", "comedy", "mobile legends",
            "free fire", "roblox", "pubg", "street food", "persib", "persija", "fc seoul", "gtv sports"
        )

        if (nonMusicKeywords.any { titleLower.contains(it) || uploaderLower.contains(it) }) {
            return null
        }

        return Song(
            id = id,
            title = itemTitle,
            artist = uploaderName.orEmpty().removeSuffix(" - Topic"),
            thumbnail = thumbnails.bestUrl(),
            durationSec = duration.coerceAtLeast(0)
        )
    }

    private fun List<Image>?.bestUrl(): String? {
        val raw = this?.maxByOrNull { it.height }?.url ?: this?.firstOrNull()?.url ?: return null
        return Thumbnails.normalize(raw)
    }

    /**
     * Prioritaskan m4a supaya kompatibel dengan ExoPlayer di semua perangkat.
     *
     * Hanya stream progresif yang dipertimbangkan: stream DASH/HLS isinya
     * manifest, bukan berkas audio, dan dulu membuat sebagian lagu gagal
     * diputar. Trek dubbing juga dihindari kalau ada trek aslinya.
     *
     * Bitrate tertinggi bukan pilihan yang baik di HP: byte-nya paling banyak,
     * jadi radio menyala paling lama dan buffer awal paling lambat penuh.
     * ~128 kbps sudah transparan lewat speaker atau earphone biasa.
     */
    private fun List<AudioStream>?.pickBest(hiRes: Boolean): AudioStream? {
        val progressive = this.orEmpty().filter {
            it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && !it.content.isNullOrBlank()
        }
        if (progressive.isEmpty()) return null
        val original = progressive.filter {
            it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL
        }.ifEmpty { progressive }
        if (hiRes) return original.maxByOrNull { it.averageBitrate }
        val m4a = original.filter { it.format?.name?.contains("M4A", ignoreCase = true) == true }
        val pool = m4a.ifEmpty { original }

        val rated = pool.filter { it.averageBitrate > 0 }
        if (rated.isEmpty()) return pool.firstOrNull()

        val acceptable = rated.filter { it.averageBitrate >= MIN_BITRATE_KBPS }
        return (acceptable.ifEmpty { rated })
            .minByOrNull { abs(it.averageBitrate - TARGET_BITRATE_KBPS) }
    }

    private const val TARGET_BITRATE_KBPS = 128
    private const val MIN_BITRATE_KBPS = 96
    private const val RESOLVE_ATTEMPTS = 2
    private const val MAX_CACHED_URLS = 150
    private const val DEFAULT_TTL_MS = 2 * 60 * 60 * 1000L
    private const val EXPIRY_MARGIN_MS = 5 * 60 * 1000L
    private val EXPIRE_PARAM = Regex("""[?&]expire=(\d+)""")
}
