package com.jamalsquad.jamalify.data.repo

import com.jamalsquad.jamalify.data.Song
import com.jamalsquad.jamalify.data.db.PlayEvent

/**
 * Data yang dibutuhkan [RecommendationEngine]. Dipisah dari [MusicRepository]
 * supaya mesin rekomendasi yang sama bisa dipakai versi desktop.
 */
interface RecommendationSource {
    suspend fun playEventsSince(since: Long): List<PlayEvent>
    suspend fun getFavoritesList(limit: Int = 20): List<Song>
    suspend fun getRecentlyPlayedList(limit: Int = 20): List<Song>
    suspend fun related(videoId: String, limit: Int = 50): List<Song>
}
