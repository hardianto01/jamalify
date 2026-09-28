package com.jamalsquad.jamalify.data.youtube

import okhttp3.Interceptor
import okhttp3.Response
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper

/**
 * URL googlevideo terikat ke klien YouTube yang memintanya (web, Android,
 * iOS, visionOS). Server menolak dengan 403 kalau User-Agent pengunduhnya
 * tidak cocok — itu sebabnya sebagian lagu dulu gagal diputar sementara
 * yang lain lancar: tergantung klien mana yang dipakai NewPipe untuk lagu itu.
 *
 * Dipakai oleh pemutar dan pengunduh supaya keduanya mengirim header yang sama.
 */
class StreamHeadersInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.url.host.endsWith("googlevideo.com")) return chain.proceed(request)

        val url = request.url.toString()
        val builder = request.newBuilder()
        when {
            YoutubeParsingHelper.isAndroidStreamingUrl(url) ->
                builder.header("User-Agent", YoutubeParsingHelper.getAndroidUserAgent(Localization.DEFAULT))
            YoutubeParsingHelper.isIosStreamingUrl(url) ->
                builder.header("User-Agent", YoutubeParsingHelper.getIosUserAgent(Localization.DEFAULT))
            YoutubeParsingHelper.isVisionOsStreamingUrl(url) ->
                builder.header("User-Agent", YoutubeParsingHelper.getVisionOsUserAgent(Localization.DEFAULT))
            else -> builder
                .header("User-Agent", JamalifyDownloader.USER_AGENT)
                .header("Origin", "https://www.youtube.com")
                .header("Referer", "https://www.youtube.com/")
        }
        return chain.proceed(builder.build())
    }
}
