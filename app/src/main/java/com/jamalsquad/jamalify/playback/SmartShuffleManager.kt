package com.jamalsquad.jamalify.playback

import android.content.Context
import com.jamalsquad.jamalify.data.Song
import com.jamalsquad.jamalify.data.repo.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Smart Shuffle: mengacak lagu yang ada di playlist TAPI juga menyisipkan 1-2 lagu
 * rekomendasi sejenis di antara urutan acak, sehingga pemutaran terasa lebih
 * bervariasi dan tidak membosankan seperti shuffle biasa.
 */
class SmartShuffleManager(private val context: Context) {
    private val repository = MusicRepository.get(context)

    fun shuffleWithRecommendations(
        songs: List<Song>,
        insertEvery: Int = 4
    ): List<Song> {
        if (songs.size <= 2) return songs.shuffled()

        val shuffled = songs.shuffled().toMutableList()
        val result = mutableListOf<Song>()
        val usedIds = songs.map { it.id }.toMutableSet()

        val artists = songs.map { it.artist }.distinct().take(3)
        val recommendations = runBlocking(Dispatchers.IO) {
            val recs = mutableListOf<Song>()
            for (artist in artists) {
                if (recs.size >= 5) break
                val cleanArtist = artist.removeSuffix(" - Topic").trim()
                if (cleanArtist.isBlank()) continue
                val found = runCatching {
                    repository.search("$cleanArtist songs audio", limit = 3)
                }.getOrDefault(emptyList())
                recs.addAll(found.filter { it.id !in usedIds && it.title.isNotBlank() && (it.durationSec in 30L..720L) })
            }
            recs.take(5)
        }

        var insertCounter = 0
        for (song in shuffled) {
            result.add(song)
            insertCounter++
            if (insertCounter >= insertEvery && recommendations.isNotEmpty()) {
                result.add(recommendations.random())
                insertCounter = 0
            }
        }

        return result.distinctBy { it.id }
    }
}