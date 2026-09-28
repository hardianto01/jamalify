package com.jamalsquad.jamalify.data.repo

import com.jamalsquad.jamalify.data.Song
import com.jamalsquad.jamalify.data.db.HistoryEntity
import com.jamalsquad.jamalify.data.db.PlayEvent
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.exp
import kotlin.math.ln
import kotlin.random.Random

/**
 * Rekomendasi personal yang dihitung sepenuhnya di HP, meniru cara kerja
 * "Quick picks" di Metrolist / InnerTune:
 *
 * 1. Dari riwayat dengar, pilih beberapa lagu ACUAN — lagu yang sering dan
 *    tuntas didengar belakangan ini (bukan cuma satu lagu terakhir).
 * 2. Ambil lagu terkait dari tiap acuan. Lagu yang muncul di daftar terkait
 *    beberapa acuan sekaligus hampir pasti cocok selera.
 * 3. Buang yang baru saja didengar dan yang pernah di-skip, turunkan artis
 *    yang sering di-skip, batasi jumlah per artis, sisakan ruang untuk
 *    artis yang belum pernah didengar.
 *
 * Acuan dipilih acak berbobot, jadi setiap refresh hasilnya berbeda tanpa
 * kehilangan arah selera.
 */
class RecommendationEngine(private val repository: MusicRepository) {

    /** Ringkasan kebiasaan dengar satu lagu. */
    private data class SongStats(
        val song: Song,
        val affinity: Double,
        val plays: Int,
        val skips: Int,
        val lastPlayedAt: Long,
        val isFavorite: Boolean
    )

    /** Semua yang perlu diketahui tentang selera, dihitung sekali per permintaan. */
    private class Taste(
        val songs: Map<String, SongStats>,
        val artistSkipRate: Map<String, Double>,
        val knownArtists: Set<String>
    ) {
        val isEmpty get() = songs.isEmpty()
    }

    private data class CachedRelated(val songs: List<Song>, val fetchedAt: Long)

    /** Lagu terkait jarang berubah; menyimpannya menghemat kuota dan menghindari rate limit YouTube. */
    private val relatedCache = ConcurrentHashMap<String, CachedRelated>()

    /** Rak utama "Pilihan cepat untukmu". Kosong kalau riwayat belum cukup. */
    suspend fun quickPicks(limit: Int = 20): List<Song> {
        val taste = loadTaste()
        if (taste.isEmpty) return emptyList()
        val seeds = pickSeeds(taste, count = SEED_COUNT)
        return rankCandidates(taste, seeds, exclude = emptySet(), limit = limit)
    }

    /**
     * Rak "Mirip dengan …": satu lagu acuan per rak, seperti Metrolist.
     * Acuannya berbeda dari yang tampil paling atas, supaya rak tidak kembar.
     */
    suspend fun similarTo(count: Int = 2, perShelf: Int = 15): List<Pair<Song, List<Song>>> {
        val taste = loadTaste()
        if (taste.isEmpty) return emptyList()
        return coroutineScope {
            pickSeeds(taste, count).map { seed ->
                async {
                    val songs = rankCandidates(taste, listOf(seed), exclude = setOf(seed.song.id), limit = perShelf)
                    seed.song to songs
                }
            }.awaitAll().filter { it.second.size >= MIN_SHELF_SIZE }
        }
    }

    /**
     * Lagu yang dulu sering diputar sampai habis tapi sudah lama tidak
     * didengar. Tanpa jaringan sama sekali — murni dari riwayat.
     */
    suspend fun forgottenFavorites(limit: Int = 15): List<Song> {
        val taste = loadTaste()
        val now = System.currentTimeMillis()
        return taste.songs.values
            .filter { it.plays >= 3 && it.skips * 2 < it.plays && now - it.lastPlayedAt > FORGOTTEN_AFTER_MS }
            .sortedByDescending { it.affinity * jitter() }
            .map { it.song }
            .take(limit)
    }

    /**
     * Lanjutan antrean: sebagian besar dari lagu terkait lagu yang sedang
     * diputar (menjaga suasana), sisanya dari selera umum (menjaga variasi).
     */
    suspend fun autoplay(current: Song?, excludeIds: Set<String>, limit: Int = 10): List<Song> {
        val taste = loadTaste()
        val seeds = buildList {
            if (current != null && current.id.isNotBlank()) {
                add(Seed(current, CURRENT_SONG_WEIGHT))
            }
            addAll(pickSeeds(taste, count = 2).filter { it.song.id != current?.id })
        }
        if (seeds.isEmpty()) return emptyList()
        return rankCandidates(taste, seeds, exclude = excludeIds, limit = limit)
    }

    /** Playlist yang dibentuk dari kebiasaan dengar, bukan sekadar urutan riwayat. */
    data class PersonalPlaylist(
        val id: String,
        val title: String,
        val subtitle: String,
        val songs: List<Song>
    )

    /**
     * Mengelompokkan lagu yang didengar jadi playlist: yang disukai, yang
     * sering diulang sampai habis, satu mix per artis teratas, dan temuan
     * baru dari artis yang belum pernah didengar.
     *
     * Dengan [enrich] false tidak ada permintaan jaringan sama sekali —
     * beranda langsung terisi dari database, lalu diganti versi lengkapnya
     * begitu lagu serupa dari YouTube selesai diambil.
     */
    suspend fun personalPlaylists(enrich: Boolean): List<PersonalPlaylist> {
        val taste = loadTaste()
        if (taste.isEmpty) return emptyList()
        val enjoyed = enjoyed(taste)

        val liked = taste.songs.values.filter { it.isFavorite }
            .sortedByDescending { it.affinity }
            .map { it.song }
        val onRepeat = enjoyed.filter { it.plays >= 2 }.map { it.song }.take(30)
        val recent = runCatching { repository.getRecentlyPlayedList(50) }.getOrDefault(emptyList())

        val (mixes, discovery) = coroutineScope {
            val mixes = topArtists(enjoyed, ARTIST_MIX_COUNT).map { async { artistMix(taste, it, enrich) } }
            val discovery = async { if (enrich) discoveries(taste) else emptyList() }
            mixes.awaitAll().filterNotNull() to discovery.await()
        }

        return buildList {
            if (liked.isNotEmpty()) {
                add(PersonalPlaylist("liked", "Lagu yang Disukai", "${liked.size} lagu", liked))
            }
            if (onRepeat.size >= 4) {
                add(PersonalPlaylist("on_repeat", "Sering Kamu Ulang", "Paling sering kamu dengar sampai habis", onRepeat))
            }
            addAll(mixes)
            if (discovery.size >= MIN_SHELF_SIZE) {
                add(PersonalPlaylist("discover", "Temuan Baru", "Artis baru yang mirip seleramu", discovery))
            }
            if (recent.isNotEmpty()) {
                add(PersonalPlaylist("recent", "Baru Diputar", "${recent.size} lagu terakhir", recent))
            }
        }
    }

    /** Kata kunci pencarian playlist YouTube: artis yang paling disukai. */
    suspend fun favoriteArtists(count: Int = ARTIST_MIX_COUNT): List<String> {
        val taste = loadTaste()
        if (taste.isEmpty) return emptyList()
        return topArtists(enjoyed(taste), count).map { it.name }
    }

    private data class ArtistTaste(val name: String, val score: Double, val songs: List<SongStats>)

    /** Lagu yang disukai: skor positif dan tidak lebih sering di-skip daripada didengar. */
    private fun enjoyed(taste: Taste): List<SongStats> =
        taste.songs.values
            .filter { it.affinity > 0 && !(it.skips > 0 && it.skips >= it.plays) }
            .sortedByDescending { it.affinity }

    private fun topArtists(enjoyed: List<SongStats>, count: Int): List<ArtistTaste> =
        enjoyed.groupBy { artistKey(it.song.artist) }
            // Kanal label/lirik berisi banyak artis; "Mix HITS Records" tidak berarti apa-apa.
            .filterKeys { key -> key.isNotBlank() && LABEL_HINTS.none { key.contains(it) } }
            .map { (_, songs) ->
                ArtistTaste(songs.first().song.artist.removeSuffix(" - Topic").trim(), songs.sumOf { it.affinity }, songs)
            }
            .filter { it.songs.size >= 2 || it.songs.sumOf { s -> s.plays } >= 3 }
            .sortedByDescending { it.score }
            .take(count)

    private suspend fun artistMix(taste: Taste, artist: ArtistTaste, enrich: Boolean): PersonalPlaylist? {
        val own = artist.songs.map { it.song }
        val similar = if (enrich) {
            val seeds = artist.songs.take(2).map { Seed(it.song, it.affinity) }
            rankCandidates(taste, seeds, exclude = own.map { it.id }.toSet(), limit = 20)
        } else {
            emptyList()
        }
        // Satu lagu artisnya, dua lagu serupa — seperti mix artis di Spotify.
        val songs = buildList {
            val ownIt = own.take(10).iterator()
            val similarIt = similar.iterator()
            while (ownIt.hasNext() || similarIt.hasNext()) {
                if (ownIt.hasNext()) add(ownIt.next())
                repeat(2) { if (similarIt.hasNext()) add(similarIt.next()) }
            }
        }
        if (songs.size < 3) return null
        return PersonalPlaylist(
            id = "artist_${artistKey(artist.name)}",
            title = "Mix ${artist.name}",
            subtitle = "${artist.name} dan artis serupa",
            songs = songs
        )
    }

    private suspend fun discoveries(taste: Taste): List<Song> =
        rankCandidates(taste, pickSeeds(taste, SEED_COUNT), exclude = emptySet(), limit = 80)
            .filter { artistKey(it.artist) !in taste.knownArtists }
            .take(25)

    // --- inti ---

    private data class Seed(val song: Song, val weight: Double)

    private suspend fun loadTaste(): Taste {
        val now = System.currentTimeMillis()
        val events = runCatching { repository.playEventsSince(now - HISTORY_WINDOW_MS) }
            .getOrDefault(emptyList())

        val songs = events.groupBy { it.songId }.mapValues { (_, plays) ->
            val first = plays.first()
            var affinity = 0.0
            var listened = 0
            var skipped = 0
            for (event in plays) {
                val recency = exp(-(now - event.playedAt).toDouble() / RECENCY_HALF_LIFE_MS * ln(2.0))
                when (classify(event)) {
                    Outcome.LISTENED -> { affinity += recency; listened++ }
                    Outcome.PARTIAL -> affinity += 0.4 * recency
                    Outcome.SKIPPED -> { affinity -= 0.5 * recency; skipped++ }
                    Outcome.UNKNOWN -> affinity += 0.6 * recency
                }
            }
            if (first.isFavorite) affinity += FAVORITE_BONUS
            SongStats(
                song = first.toSong(),
                affinity = affinity,
                plays = listened + plays.count { classify(it) == Outcome.UNKNOWN },
                skips = skipped,
                lastPlayedAt = plays.maxOf { it.playedAt },
                isFavorite = first.isFavorite
            )
        }

        // Favorit yang belum pernah diputar lewat app tetap sinyal selera.
        val favorites = runCatching { repository.getFavoritesList(30) }.getOrDefault(emptyList())
        val merged = songs.toMutableMap()
        favorites.forEach { fav ->
            merged.putIfAbsent(fav.id, SongStats(fav, FAVORITE_BONUS, 0, 0, 0L, true))
        }

        val artistSkipRate = events.groupBy { artistKey(it.artist) }.mapValues { (_, plays) ->
            val judged = plays.filter { classify(it) != Outcome.UNKNOWN }
            if (judged.size < 3) 0.0 else judged.count { classify(it) == Outcome.SKIPPED }.toDouble() / judged.size
        }

        return Taste(merged, artistSkipRate, merged.values.map { artistKey(it.song.artist) }.toSet())
    }

    private enum class Outcome { LISTENED, PARTIAL, SKIPPED, UNKNOWN }

    private fun classify(event: PlayEvent): Outcome {
        if (event.listenedMs == HistoryEntity.UNKNOWN_LISTENED) return Outcome.UNKNOWN
        val durationMs = event.durationSec * 1000
        val ratio = if (durationMs > 0) event.listenedMs.toDouble() / durationMs else null
        return when {
            event.listenedMs < SKIP_THRESHOLD_MS && (ratio == null || ratio < 0.5) -> Outcome.SKIPPED
            ratio == null && event.listenedMs >= LISTENED_THRESHOLD_MS -> Outcome.LISTENED
            ratio != null && ratio >= 0.6 -> Outcome.LISTENED
            else -> Outcome.PARTIAL
        }
    }

    /** Acak berbobot tanpa pengembalian: yang paling disukai paling mungkin terpilih, tapi tidak selalu. */
    private fun pickSeeds(taste: Taste, count: Int): List<Seed> {
        val pool = taste.songs.values.filter { it.affinity > 0 }.toMutableList()
        val picked = mutableListOf<Seed>()
        val usedArtists = mutableSetOf<String>()
        while (picked.size < count && pool.isNotEmpty()) {
            val total = pool.sumOf { it.affinity }
            var roll = Random.nextDouble() * total
            val chosen = pool.firstOrNull { roll -= it.affinity; roll <= 0 } ?: pool.last()
            pool.remove(chosen)
            // Acuan dari artis berbeda, supaya rekomendasi tidak satu warna.
            if (!usedArtists.add(artistKey(chosen.song.artist)) && pool.any { artistKey(it.song.artist) !in usedArtists }) continue
            picked += Seed(chosen.song, chosen.affinity)
        }
        return picked
    }

    private suspend fun rankCandidates(
        taste: Taste,
        seeds: List<Seed>,
        exclude: Set<String>,
        limit: Int
    ): List<Song> {
        val seedIds = seeds.map { it.song.id }.toSet()
        val relatedLists = coroutineScope {
            seeds.map { seed -> async { seed to relatedOf(seed.song.id) } }.awaitAll()
        }

        data class Candidate(val song: Song, var score: Double, var hits: Int)

        val candidates = LinkedHashMap<String, Candidate>()
        for ((seed, related) in relatedLists) {
            related.forEachIndexed { index, song ->
                // Urutan awal daftar terkait paling relevan; makin ke bawah makin melenceng.
                val positional = 1.0 / (1 + index * 0.15)
                val gain = seed.weight.coerceAtLeast(0.2) * positional
                val candidate = candidates.getOrPut(song.id) { Candidate(song, 0.0, 0) }
                candidate.score += gain
                candidate.hits++
            }
        }

        val now = System.currentTimeMillis()
        val scored = candidates.values.mapNotNull { candidate ->
            val id = candidate.song.id
            if (id in exclude || id in seedIds) return@mapNotNull null
            val stats = taste.songs[id]
            var score = candidate.score * (1 + 0.5 * (candidate.hits - 1))

            if (stats != null) {
                // Lagu yang pernah di-skip tidak disodorkan lagi.
                if (stats.skips > 0 && stats.skips >= stats.plays) return@mapNotNull null
                val sinceLast = now - stats.lastPlayedAt
                when {
                    sinceLast < BLOCK_RECENT_MS -> return@mapNotNull null
                    sinceLast < DAMPEN_RECENT_MS -> score *= 0.3
                }
            }
            val artist = artistKey(candidate.song.artist)
            score *= 1 - 0.7 * (taste.artistSkipRate[artist] ?: 0.0)
            Triple(candidate.song, score * jitter(), artist in taste.knownArtists)
        }.sortedByDescending { it.second }

        // Campur yang sudah akrab dengan penemuan baru, maks. 2 lagu per artis.
        val result = mutableListOf<Song>()
        val perArtist = mutableMapOf<String, Int>()
        val discoveryTarget = (limit * DISCOVERY_SHARE).toInt()
        val discovery = scored.filter { !it.third }.iterator()
        val familiar = scored.filter { it.third }.iterator()
        var discoveryAdded = 0

        fun tryAdd(song: Song): Boolean {
            val artist = artistKey(song.artist)
            if ((perArtist[artist] ?: 0) >= MAX_PER_ARTIST) return false
            perArtist[artist] = (perArtist[artist] ?: 0) + 1
            result += song
            return true
        }

        while (result.size < limit && (discovery.hasNext() || familiar.hasNext())) {
            val wantDiscovery = discoveryAdded < discoveryTarget &&
                result.size % (1 / DISCOVERY_SHARE).toInt() == DISCOVERY_SLOT
            val source = when {
                wantDiscovery && discovery.hasNext() -> discovery
                familiar.hasNext() -> familiar
                else -> discovery
            }
            val next = source.next()
            if (tryAdd(next.first) && source === discovery) discoveryAdded++
        }
        return result
    }

    private suspend fun relatedOf(videoId: String): List<Song> {
        val now = System.currentTimeMillis()
        relatedCache[videoId]?.takeIf { now - it.fetchedAt < RELATED_TTL_MS }?.let { return it.songs }
        val songs = runCatching { repository.related(videoId, limit = 30) }.getOrDefault(emptyList())
            .filter { it.durationSec == 0L || it.durationSec in 30L..720L }
        if (songs.isNotEmpty()) {
            if (relatedCache.size > MAX_RELATED_CACHE) relatedCache.clear()
            relatedCache[videoId] = CachedRelated(songs, now)
        }
        return songs
    }

    private fun artistKey(artist: String) = artist.removeSuffix(" - Topic").trim().lowercase()

    /** Sedikit acak supaya dua refresh berturut-turut tidak identik. */
    private fun jitter() = 0.85 + Random.nextDouble() * 0.3

    private companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L
        const val HISTORY_WINDOW_MS = 60 * DAY_MS
        const val RECENCY_HALF_LIFE_MS = 14 * DAY_MS
        const val FORGOTTEN_AFTER_MS = 21 * DAY_MS
        const val BLOCK_RECENT_MS = 2 * DAY_MS
        const val DAMPEN_RECENT_MS = 7 * DAY_MS
        const val RELATED_TTL_MS = 6 * 60 * 60 * 1000L
        const val MAX_RELATED_CACHE = 100

        const val SKIP_THRESHOLD_MS = 30_000L
        const val LISTENED_THRESHOLD_MS = 90_000L
        const val FAVORITE_BONUS = 1.5
        const val CURRENT_SONG_WEIGHT = 3.0

        const val SEED_COUNT = 5
        const val ARTIST_MIX_COUNT = 3
        val LABEL_HINTS = listOf("records", "entertainment", "official", "lirik", "lyric", "musikindo", "production")
        const val MIN_SHELF_SIZE = 5
        const val MAX_PER_ARTIST = 2
        const val DISCOVERY_SHARE = 0.25
        /** Penemuan baru di urutan ke-4, 8, …; lagu pembuka tetap yang paling pasti cocok. */
        const val DISCOVERY_SLOT = 3
    }
}
