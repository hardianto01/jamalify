package com.jamalsquad.jamalify.data.spotify

import android.util.Base64
import com.jamalsquad.jamalify.data.SpotifyTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Playlist Spotify yang berhasil dibaca: judul + daftar track mentah. */
data class SpotifyPlaylist(
    val name: String,
    val owner: String,
    val tracks: List<SpotifyTrack>,
    val truncated: Boolean
)

/**
 * Spotify tidak memberi file audio, jadi yang diambil hanya daftar lagunya.
 *
 * Dua jalur dipakai:
 *  - Halaman embed publik, tanpa perlu kredensial apa pun, tapi jumlah track
 *    yang dikembalikan dibatasi Spotify (biasanya ~100 pertama).
 *  - Web API resmi lewat Client Credentials, dipakai kalau pengguna mengisi
 *    Client ID dan Secret di layar Impor. Jalur ini mengambil playlist penuh.
 */
object SpotifyClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0 Safari/537.36"

    /** Tipe dan ID dari URL/URI Spotify apa pun. */
    fun parseLink(input: String): Pair<String, String>? {
        val trimmed = input.trim()
        Regex("""spotify:(playlist|album|track):([A-Za-z0-9]+)""").find(trimmed)?.let {
            return it.groupValues[1] to it.groupValues[2]
        }
        Regex("""open\.spotify\.com/(?:intl-[a-z]{2}/)?(playlist|album|track)/([A-Za-z0-9]+)""")
            .find(trimmed)?.let {
                return it.groupValues[1] to it.groupValues[2]
            }
        return null
    }

    fun isSpotifyLink(input: String) = parseLink(input) != null

    suspend fun fetch(
        input: String,
        clientId: String? = null,
        clientSecret: String? = null
    ): SpotifyPlaylist = withContext(Dispatchers.IO) {
        val (type, id) = parseLink(input)
            ?: throw IllegalArgumentException("Link Spotify tidak dikenali")

        if (!clientId.isNullOrBlank() && !clientSecret.isNullOrBlank()) {
            runCatching { fetchViaApi(type, id, clientId, clientSecret) }
                .getOrElse { fetchViaEmbed(type, id) }
        } else {
            fetchViaEmbed(type, id)
        }
    }

    // --- jalur embed (tanpa kredensial) ---

    private fun fetchViaEmbed(type: String, id: String): SpotifyPlaylist {
        val url = "https://open.spotify.com/embed/$type/$id"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()

        val html = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Spotify menolak permintaan (HTTP ${response.code})")
            }
            response.body?.string().orEmpty()
        }

        val json = extractNextData(html)
            ?: throw IllegalStateException("Struktur halaman Spotify berubah, daftar lagu tidak ditemukan")

        val entity = json
            .optJSONObject("props")
            ?.optJSONObject("pageProps")
            ?.optJSONObject("state")
            ?.optJSONObject("data")
            ?.optJSONObject("entity")
            ?: throw IllegalStateException("Playlist tidak dapat dibaca. Pastikan playlist-nya publik.")

        val name = entity.optString("name").ifBlank { "Playlist Spotify" }
        val owner = entity.optJSONObject("owner")?.optString("name")
            ?: entity.optString("subtitle")

        val trackArray: JSONArray = entity.optJSONArray("trackList")
            ?: entity.optJSONObject("tracks")?.optJSONArray("items")
            ?: JSONArray()

        val tracks = buildList {
            for (i in 0 until trackArray.length()) {
                val item = trackArray.optJSONObject(i) ?: continue
                val track = item.optJSONObject("track") ?: item
                val title = track.optString("title").ifBlank { track.optString("name") }
                val artist = track.optString("subtitle").ifBlank { artistsOf(track) }
                if (title.isNotBlank()) add(SpotifyTrack(title, artist))
            }
        }

        if (tracks.isEmpty()) {
            throw IllegalStateException("Tidak ada lagu yang terbaca. Playlist mungkin privat.")
        }

        val total = entity.optJSONObject("tracks")?.optInt("totalCount", tracks.size) ?: tracks.size
        return SpotifyPlaylist(name, owner, tracks, truncated = total > tracks.size)
    }

    private fun extractNextData(html: String): JSONObject? {
        val marker = "id=\"__NEXT_DATA__\""
        val markerIndex = html.indexOf(marker)
        if (markerIndex < 0) return null
        val start = html.indexOf('>', markerIndex).takeIf { it > 0 }?.plus(1) ?: return null
        val end = html.indexOf("</script>", start).takeIf { it > start } ?: return null
        return runCatching { JSONObject(html.substring(start, end)) }.getOrNull()
    }

    // --- jalur Web API resmi ---

    private fun fetchViaApi(
        type: String,
        id: String,
        clientId: String,
        clientSecret: String
    ): SpotifyPlaylist {
        val token = requestToken(clientId, clientSecret)
        return when (type) {
            "playlist" -> fetchPlaylistApi(id, token)
            "album" -> fetchAlbumApi(id, token)
            else -> fetchTrackApi(id, token)
        }
    }

    private fun requestToken(clientId: String, clientSecret: String): String {
        val credentials = Base64.encodeToString(
            "$clientId:$clientSecret".toByteArray(), Base64.NO_WRAP
        )
        val request = Request.Builder()
            .url("https://accounts.spotify.com/api/token")
            .header("Authorization", "Basic $credentials")
            .post(FormBody.Builder().add("grant_type", "client_credentials").build())
            .build()

        return client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Kredensial Spotify ditolak (HTTP ${response.code})")
            }
            JSONObject(body).getString("access_token")
        }
    }

    private fun apiGet(url: String, token: String): JSONObject {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .build()
        return client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Spotify API gagal (HTTP ${response.code})")
            }
            JSONObject(body)
        }
    }

    private fun fetchPlaylistApi(id: String, token: String): SpotifyPlaylist {
        val head = apiGet("https://api.spotify.com/v1/playlists/$id?market=ID", token)
        val name = head.optString("name").ifBlank { "Playlist Spotify" }
        val owner = head.optJSONObject("owner")?.optString("display_name").orEmpty()

        val tracks = mutableListOf<SpotifyTrack>()
        var next: String? =
            "https://api.spotify.com/v1/playlists/$id/tracks?limit=100&market=ID"

        while (next != null) {
            val page = apiGet(next, token)
            val items = page.optJSONArray("items") ?: JSONArray()
            for (i in 0 until items.length()) {
                val track = items.optJSONObject(i)?.optJSONObject("track") ?: continue
                val title = track.optString("name")
                if (title.isNotBlank()) tracks += SpotifyTrack(title, artistsOf(track))
            }
            next = page.optString("next").takeIf { it.isNotBlank() && it != "null" }
        }

        return SpotifyPlaylist(name, owner, tracks, truncated = false)
    }

    private fun fetchAlbumApi(id: String, token: String): SpotifyPlaylist {
        val head = apiGet("https://api.spotify.com/v1/albums/$id?market=ID", token)
        val name = head.optString("name").ifBlank { "Album Spotify" }
        val owner = artistsOf(head)

        val tracks = mutableListOf<SpotifyTrack>()
        var next: String? = "https://api.spotify.com/v1/albums/$id/tracks?limit=50&market=ID"
        while (next != null) {
            val page = apiGet(next, token)
            val items = page.optJSONArray("items") ?: JSONArray()
            for (i in 0 until items.length()) {
                val track = items.optJSONObject(i) ?: continue
                val title = track.optString("name")
                if (title.isNotBlank()) tracks += SpotifyTrack(title, artistsOf(track))
            }
            next = page.optString("next").takeIf { it.isNotBlank() && it != "null" }
        }
        return SpotifyPlaylist(name, owner, tracks, truncated = false)
    }

    private fun fetchTrackApi(id: String, token: String): SpotifyPlaylist {
        val track = apiGet("https://api.spotify.com/v1/tracks/$id?market=ID", token)
        val title = track.optString("name")
        return SpotifyPlaylist(
            name = title.ifBlank { "Lagu Spotify" },
            owner = artistsOf(track),
            tracks = listOf(SpotifyTrack(title, artistsOf(track))),
            truncated = false
        )
    }

    private fun artistsOf(obj: JSONObject): String {
        val artists = obj.optJSONArray("artists") ?: return ""
        return (0 until artists.length())
            .mapNotNull { artists.optJSONObject(it)?.optString("name") }
            .filter { it.isNotBlank() }
            .joinToString(", ")
    }
}
