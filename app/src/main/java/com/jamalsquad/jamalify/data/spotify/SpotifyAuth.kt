package com.jamalsquad.jamalify.data.spotify

import android.content.Context
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * Login Spotify dengan Authorization Code + PKCE.
 *
 * Aplikasi Android tidak bisa menyimpan client secret dengan aman — siapa pun
 * bisa membongkar APK dan membacanya. PKCE menggantikan secret dengan pasangan
 * verifier/challenge yang dibuat baru setiap kali login, jadi kode otorisasi
 * yang dicuri di tengah jalan pun tidak bisa ditukar jadi token.
 *
 * Client ID tetap harus diisi pengguna: Spotify mengikat redirect URI ke satu
 * aplikasi terdaftar, jadi tidak ada Client ID universal yang bisa dibundel.
 */
object SpotifyAuth {

    const val REDIRECT_URI = "jamalify://spotify-callback"

    private const val PREFS = "jamalify_spotify"
    private const val KEY_CLIENT_ID = "client_id"
    private const val KEY_VERIFIER = "code_verifier"
    private const val KEY_ACCESS = "access_token"
    private const val KEY_REFRESH = "refresh_token"
    private const val KEY_EXPIRES = "expires_at"
    private const val KEY_DISPLAY_NAME = "display_name"

    private val SCOPES = listOf(
        "playlist-read-private",
        "playlist-read-collaborative",
        "user-library-read",
        "user-top-read"
    ).joinToString(" ")

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // --- konfigurasi ---

    fun clientId(context: Context): String =
        prefs(context).getString(KEY_CLIENT_ID, "").orEmpty()

    fun setClientId(context: Context, value: String) {
        prefs(context).edit().putString(KEY_CLIENT_ID, value.trim()).apply()
    }

    fun isLoggedIn(context: Context): Boolean =
        !prefs(context).getString(KEY_REFRESH, null).isNullOrBlank()

    fun displayName(context: Context): String =
        prefs(context).getString(KEY_DISPLAY_NAME, "").orEmpty()

    fun setDisplayName(context: Context, name: String) {
        prefs(context).edit().putString(KEY_DISPLAY_NAME, name).apply()
    }

    fun logout(context: Context) {
        prefs(context).edit()
            .remove(KEY_ACCESS)
            .remove(KEY_REFRESH)
            .remove(KEY_EXPIRES)
            .remove(KEY_DISPLAY_NAME)
            .remove(KEY_VERIFIER)
            .apply()
    }

    // --- langkah 1: buka halaman izin Spotify ---

    /** URL otorisasi, sekaligus menyimpan code_verifier untuk penukaran nanti. */
    fun buildAuthorizeUrl(context: Context): String? {
        val id = clientId(context).ifBlank { return null }

        val verifier = randomVerifier()
        prefs(context).edit().putString(KEY_VERIFIER, verifier).apply()

        return Uri.parse("https://accounts.spotify.com/authorize").buildUpon()
            .appendQueryParameter("client_id", id)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challengeOf(verifier))
            .appendQueryParameter("scope", SCOPES)
            .build()
            .toString()
    }

    // --- langkah 2: tukar kode dari redirect jadi token ---

    fun isRedirect(uri: Uri?): Boolean =
        uri != null && "$uri".startsWith(REDIRECT_URI)

    suspend fun completeLogin(context: Context, uri: Uri): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                uri.getQueryParameter("error")?.let { error ->
                    error("Spotify menolak login: $error")
                }
                val code = uri.getQueryParameter("code")
                    ?: error("Redirect tidak membawa kode otorisasi")
                val verifier = prefs(context).getString(KEY_VERIFIER, null)
                    ?: error("Sesi login kedaluwarsa, coba masuk lagi")

                val body = FormBody.Builder()
                    .add("grant_type", "authorization_code")
                    .add("code", code)
                    .add("redirect_uri", REDIRECT_URI)
                    .add("client_id", clientId(context))
                    .add("code_verifier", verifier)
                    .build()

                storeTokens(context, postToken(body))
                prefs(context).edit().remove(KEY_VERIFIER).apply()
            }
        }

    // --- langkah 3: token yang selalu segar ---

    /** Access token yang masih berlaku, memperbarui sendiri kalau sudah lewat. */
    suspend fun accessToken(context: Context): String? = withContext(Dispatchers.IO) {
        val p = prefs(context)
        val current = p.getString(KEY_ACCESS, null)
        val expiresAt = p.getLong(KEY_EXPIRES, 0L)

        if (!current.isNullOrBlank() && System.currentTimeMillis() < expiresAt - 60_000) {
            return@withContext current
        }

        val refresh = p.getString(KEY_REFRESH, null) ?: return@withContext null
        runCatching {
            val body = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refresh)
                .add("client_id", clientId(context))
                .build()
            storeTokens(context, postToken(body))
        }.getOrElse { return@withContext null }

        p.getString(KEY_ACCESS, null)
    }

    // --- internal ---

    private fun postToken(body: FormBody): JSONObject {
        val request = Request.Builder()
            .url("https://accounts.spotify.com/api/token")
            .post(body)
            .build()
        return client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = runCatching {
                    JSONObject(text).optString("error_description")
                }.getOrNull().orEmpty()
                error(
                    if (detail.isNotBlank()) detail
                    else "Spotify menolak permintaan token (HTTP ${response.code})"
                )
            }
            JSONObject(text)
        }
    }

    private fun storeTokens(context: Context, json: JSONObject) {
        val editor = prefs(context).edit()
        json.optString("access_token").takeIf { it.isNotBlank() }?.let {
            editor.putString(KEY_ACCESS, it)
        }
        // Spotify tidak selalu mengirim refresh token baru saat refresh;
        // kalau kosong, yang lama tetap berlaku.
        json.optString("refresh_token").takeIf { it.isNotBlank() }?.let {
            editor.putString(KEY_REFRESH, it)
        }
        val expiresIn = json.optInt("expires_in", 3600).toLong()
        editor.putLong(KEY_EXPIRES, System.currentTimeMillis() + expiresIn * 1000)
        editor.apply()
    }

    private fun randomVerifier(): String {
        val allowed = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
        val random = SecureRandom()
        return (1..96).map { allowed[random.nextInt(allowed.length)] }.joinToString("")
    }

    private fun challengeOf(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
        return Base64.encodeToString(
            digest,
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
    }
}
