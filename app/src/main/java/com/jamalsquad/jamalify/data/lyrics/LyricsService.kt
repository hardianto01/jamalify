package com.jamalsquad.jamalify.data.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class LyricLine(
    val timestampMs: Long,
    val text: String
)

data class LyricsResult(
    val trackName: String,
    val artistName: String,
    val isPlainOnly: Boolean,
    val plainLyrics: String?,
    val syncedLines: List<LyricLine>
)

object LyricsService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun fetchLyrics(trackName: String, artistName: String, durationSec: Int = 0): LyricsResult? =
        withContext(Dispatchers.IO) {
            val directResult = getDirect(trackName, artistName, durationSec)
            if (directResult != null) return@withContext directResult

            // Fallback ke search
            searchFallback(trackName, artistName)
        }

    private fun getDirect(trackName: String, artistName: String, durationSec: Int): LyricsResult? {
        val cleanTrack = cleanTitle(trackName)
        val cleanArtist = cleanArtist(artistName)

        val urlBuilder = "https://lrclib.net/api/get".toHttpUrlOrNull()?.newBuilder() ?: return null
        urlBuilder.addQueryParameter("track_name", cleanTrack)
        urlBuilder.addQueryParameter("artist_name", cleanArtist)
        if (durationSec > 0) {
            urlBuilder.addQueryParameter("duration", durationSec.toString())
        }

        val request = Request.Builder()
            .url(urlBuilder.build())
            .header("User-Agent", "JamalifyApp/3.1 (https://github.com/jamalify)")
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string() ?: return@use null
                parseLyricsJson(JSONObject(body))
            }
        }.getOrNull()
    }

    private fun searchFallback(trackName: String, artistName: String): LyricsResult? {
        val query = "${cleanTitle(trackName)} ${cleanArtist(artistName)}".trim()
        val urlBuilder = "https://lrclib.net/api/search".toHttpUrlOrNull()?.newBuilder() ?: return null
        urlBuilder.addQueryParameter("q", query)

        val request = Request.Builder()
            .url(urlBuilder.build())
            .header("User-Agent", "JamalifyApp/3.1 (https://github.com/jamalify)")
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string() ?: return@use null
                val jsonArray = JSONArray(body)
                if (jsonArray.length() == 0) return@use null
                parseLyricsJson(jsonArray.getJSONObject(0))
            }
        }.getOrNull()
    }

    private fun parseLyricsJson(json: JSONObject): LyricsResult? {
        val syncedLyrics = json.optString("syncedLyrics", "").takeIf { it.isNotBlank() }
        val plainLyrics = json.optString("plainLyrics", "").takeIf { it.isNotBlank() }

        if (syncedLyrics == null && plainLyrics == null) return null

        val lines = if (syncedLyrics != null) parseSyncedLrc(syncedLyrics) else emptyList()

        return LyricsResult(
            trackName = json.optString("trackName", ""),
            artistName = json.optString("artistName", ""),
            isPlainOnly = syncedLyrics == null,
            plainLyrics = plainLyrics,
            syncedLines = lines
        )
    }

    private fun parseSyncedLrc(lrcContent: String): List<LyricLine> {
        val result = mutableListOf<LyricLine>()
        val regex = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})\](.*)""")

        lrcContent.lineSequence().forEach { line ->
            val match = regex.find(line.trim())
            if (match != null) {
                val min = match.groupValues[1].toLongOrNull() ?: 0L
                val sec = match.groupValues[2].toLongOrNull() ?: 0L
                val millisRaw = match.groupValues[3]
                val millis = if (millisRaw.length == 2) millisRaw.toLong() * 10 else millisRaw.toLong()
                val text = match.groupValues[4].trim()

                val totalMs = (min * 60 + sec) * 1000 + millis
                if (text.isNotEmpty()) {
                    result.add(LyricLine(totalMs, text))
                }
            }
        }
        return result.sortedBy { it.timestampMs }
    }

    private fun cleanTitle(title: String): String =
        title.replace(Regex("""(?i)\(official.*?\)|\[official.*?\]|ft\..*?|feat\..*?"""), "").trim()

    private fun cleanArtist(artist: String): String =
        artist.replace(Regex("""(?i) - Topic|Official"""), "").trim()
}
