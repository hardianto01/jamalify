package com.jamalsquad.jamalify.desktop.catalog

import com.jamalsquad.jamalify.data.Song
import com.jamalsquad.jamalify.data.lyrics.LyricsService
import com.jamalsquad.jamalify.data.repo.RecommendationEngine
import com.jamalsquad.jamalify.data.youtube.YouTubeService
import com.jamalsquad.jamalify.desktop.core.HomeSection
import com.jamalsquad.jamalify.desktop.core.MusicCatalog
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/** Katalog online desktop; logika YouTube-nya sama persis dengan versi Android. */
/** [recommendations] null berarti beranda hanya berisi rak umum (mis. saat pratinjau). */
class YouTubeCatalog(private val recommendations: RecommendationEngine? = null) : MusicCatalog {

    override suspend fun search(query: String): List<Song> {
        if (query.isBlank()) return emptyList()
        return YouTubeService.search(query.trim())
    }

    /**
     * Rak diambil berbarengan dengan batas waktu masing-masing, seperti
     * HomeViewModel di Android: satu rak yang lambat tidak boleh menahan beranda.
     */
    override suspend fun home(): List<HomeSection> = coroutineScope {
        val trending = async {
            HomeSection("Sedang tren", withTimeoutOrNull(10_000L) { YouTubeService.trending() }.orEmpty())
        }
        val shelves = SHELF_QUERIES.map { (title, query) ->
            async {
                HomeSection(title, withTimeoutOrNull(8_000L) { YouTubeService.shelf(query) }.orEmpty())
            }
        }
        val personal = async { withTimeoutOrNull(15_000L) { personalSections() }.orEmpty() }
        personal.await() + (listOf(trending) + shelves).awaitAll().filter { it.songs.isNotEmpty() }
    }

    /** Rak dari kebiasaan dengar, judul dan ambangnya sama dengan HomeViewModel Android. */
    private suspend fun personalSections(): List<HomeSection> = coroutineScope {
        val engine = recommendations ?: return@coroutineScope emptyList()
        val quick = async { runCatching { engine.quickPicks(limit = 20) }.getOrDefault(emptyList()) }
        val forgotten = async { runCatching { engine.forgottenFavorites() }.getOrDefault(emptyList()) }
        val mixes = async { runCatching { engine.similarTo(count = 3, perShelf = 25) }.getOrDefault(emptyList()) }
        buildList {
            quick.await().takeIf { it.isNotEmpty() }?.let { add(HomeSection("Pilihan cepat untukmu", it)) }
            mixes.await().forEachIndexed { i, (_, songs) ->
                if (songs.isNotEmpty()) add(HomeSection("Mix Harian ${i + 1}", songs))
            }
            forgotten.await().takeIf { it.size >= 5 }?.let { add(HomeSection("Favorit yang lama tak diputar", it)) }
        }
    }

    override suspend fun related(song: Song): List<Song> =
        YouTubeService.related(song.id).filterNot { it.id == song.id }

    /** Lirik sinkron dikembalikan sebagai LRC supaya UI cukup punya satu parser. */
    override suspend fun lyrics(song: Song): String? {
        val result = LyricsService.fetchLyrics(song.title, song.artist, song.durationSec.toInt())
            ?: return null
        if (result.syncedLines.isNotEmpty()) {
            return result.syncedLines.joinToString("\n") { line ->
                val totalSec = line.timestampMs / 1000
                val centis = (line.timestampMs % 1000) / 10
                "[%02d:%02d.%02d]%s".format(totalSec / 60, totalSec % 60, centis, line.text)
            }
        }
        return result.plainLyrics
    }

    private companion object {
        val SHELF_QUERIES = listOf(
            "Pop Indonesia" to "lagu pop indonesia terbaru",
            "Santai sore" to "lagu akustik indonesia santai",
            "Indie Indonesia" to "indie indonesia",
            "Dangdut" to "dangdut terbaru",
            "K-Pop" to "kpop hits",
            "Fokus kerja" to "lofi instrumental focus"
        )
    }
}
