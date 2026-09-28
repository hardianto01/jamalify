package com.jamalsquad.jamalify.util

/**
 * URL sampul YouTube bisa diminta dalam berbagai ukuran. Sampul dimuat sesuai
 * ukuran tampilnya: sampul 52dp di daftar lagu cukup belasan KB, bukan
 * gambar 800px yang ~100KB lalu dikecilkan lagi saat didekode.
 */
object Thumbnails {

    private val YTIMG = Regex("""^https?://i\d?\.ytimg\.com/vi(?:_webp)?/([\w-]{11})/[^/?]+""")
    private val SIZE_WH = Regex("""=w\d+-h\d+""")
    private val SIZE_S = Regex("""=s\d+""")

    /** Batas antara sampul kecil (daftar) dan besar (pemutar), dalam piksel. */
    private const val SMALL_MAX_PX = 320

    /**
     * Sampul untuk ukuran [px]. Juga memperbaiki URL rusak yang sudah
     * tersimpan di database, seperti `hqmaxresdefault.jpg` yang selalu 404.
     */
    fun sized(url: String?, px: Int): String? {
        if (url.isNullOrBlank()) return url
        YTIMG.find(url)?.let { match ->
            // mqdefault 320x180 tanpa bingkai hitam; hqdefault 480x360 untuk
            // tampilan besar. maxresdefault tidak ada untuk banyak video.
            val variant = if (px <= SMALL_MAX_PX) "mqdefault.jpg" else "hqdefault.jpg"
            return "https://i.ytimg.com/vi/${match.groupValues[1]}/$variant"
        }
        // Sampul album YouTube Music (lh3/yt3) menerima ukuran di URL.
        val target = px.coerceIn(120, 800)
        return when {
            SIZE_WH.containsMatchIn(url) -> url.replace(SIZE_WH, "=w$target-h$target")
            SIZE_S.containsMatchIn(url) -> url.replace(SIZE_S, "=s$target")
            else -> url
        }
    }

    /** Bentuk yang disimpan: ukuran besar, supaya pemutar dan notifikasi tetap tajam. */
    fun normalize(url: String?): String? = sized(url, 544)
}
