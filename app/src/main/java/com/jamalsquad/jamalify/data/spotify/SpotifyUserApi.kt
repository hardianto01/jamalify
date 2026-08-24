package com.jamalsquad.jamalify.data.spotify

import com.jamalsquad.jamalify.data.SpotifyTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Satu playlist milik pengguna, sebelum isinya ditarik. */
data class SpotifyUserPlaylist(
    val id: String,
    val name: String,
    val trackCount: Int
)

/**
 * Bagian API Spotify yang butuh identitas pengguna. Berbeda dari
 * [SpotifyClient] yang hanya membaca halaman publik, semua panggilan di sini
 * memakai access token hasil login.
 */
object SpotifyUserApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun displayName(token: String): String = withContext(Dispatchers.IO) {
        val me = get("https://api.spotify.com/v1/me", token)
        me.optString("display_name").ifBlank { me.optString("id") }
    }

    suspend fun playlists(token: String): List<SpotifyUserPlaylist> =
        withContext(Dispatchers.IO) {
            val result = mutableListOf<SpotifyUserPlaylist>()
            var next: String? = "https://api.spotify.com/v1/me/playlists?limit=50"
            while (next != null) {
                val page = get(next, token)
                val items = page.optJSONArray("items") ?: JSONArray()
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val id = item.optString("id")
                    val name = item.optString("name")
                    if (id.isBlank() || name.isBlank()) continue
                    result += SpotifyUserPlaylist(
                        id = id,
                        name = name,
                        trackCount = item.optJSONObject("tracks")?.optInt("total") ?: 0
                    )
                }
                next = page.optString("next").takeIf { it.isNotBlank() && it != "null" }
            }
            result
        }

    suspend fun playlistTracks(token: String, playlistId: String): List<SpotifyTrack> =
        withContext(Dispatchers.IO) {
            collectTracks(
                start = "https://api.spotify.com/v1/playlists/$playlistId/tracks?limit=100&market=from_token",
                token = token,
                unwrap = { it.optJSONObject("track") }
            )
        }

    /** Lagu yang pengguna tandai suka (ikon hati di Spotify). */
    suspend fun savedTracks(token: String): List<SpotifyTrack> = withContext(Dispatchers.IO) {
        collectTracks(
            start = "https://api.spotify.com/v1/me/tracks?limit=50&market=from_token",
            token = token,
            unwrap = { it.optJSONObject("track") }
        )
    }

    /** Artis yang paling sering didengar — dipakai untuk personalisasi beranda. */
    suspend fun topArtists(token: String, limit: Int = 8): List<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val page = get(
                    "https://api.spotify.com/v1/me/top/artists?limit=$limit&time_range=medium_term",
                    token
                )
                val items = page.optJSONArray("items") ?: JSONArray()
                (0 until items.length())
                    .mapNotNull { items.optJSONObject(it)?.optString("name") }
                    .filter { it.isNotBlank() }
            }.getOrDefault(emptyList())
        }

    // --- internal ---

    private fun collectTracks(
        start: String,
        token: String,
        unwrap: (JSONObject) -> JSONObject?
    ): List<SpotifyTrack> {
        val tracks = mutableListOf<SpotifyTrack>()
        var next: String? = start
        while (next != null) {
            val page = get(next, token)
            val items = page.optJSONArray("items") ?: JSONArray()
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val track = unwrap(item) ?: continue
                val title = track.optString("name")
                if (title.isBlank()) continue
                tracks += SpotifyTrack(title, artistsOf(track))
            }
            next = page.optString("next").takeIf { it.isNotBlank() && it != "null" }
        }
        return tracks
    }

    private fun get(url: String, token: String): JSONObject {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .build()
        return client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error(
                    when (response.code) {
                        401 -> "Sesi Spotify habis. Masuk ulang."
                        403 -> "Akun ini belum diizinkan di aplikasi Spotify Developer kamu."
                        429 -> "Terlalu banyak permintaan ke Spotify. Coba lagi sebentar lagi."
                        else -> "Spotify API gagal (HTTP ${response.code})"
                    }
                )
            }
            JSONObject(text)
        }
    }

    private fun artistsOf(obj: JSONObject): String {
        val artists = obj.optJSONArray("artists") ?: return ""
        return (0 until artists.length())
            .mapNotNull { artists.optJSONObject(it)?.optString("name") }
            .filter { it.isNotBlank() }
            .joinToString(", ")
    }
}
