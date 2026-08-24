package com.jamalsquad.jamalify.data.youtube

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NpRequest
import org.schabi.newpipe.extractor.downloader.Response as NpResponse
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.util.concurrent.TimeUnit

/**
 * Jembatan antara NewPipeExtractor dan OkHttp. NewPipeExtractor tidak membawa
 * HTTP client sendiri, jadi implementasi ini wajib didaftarkan sebelum
 * ekstraktor dipakai.
 */
class JamalifyDownloader private constructor(
    private val client: OkHttpClient
) : Downloader() {

    override fun execute(request: NpRequest): NpResponse {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val body = dataToSend?.toRequestBody(null, 0, dataToSend.size)

        val builder = Request.Builder()
            .method(httpMethod, body)
            .url(url)
            .addHeader("User-Agent", USER_AGENT)

        headers.forEach { (name, values) ->
            builder.removeHeader(name)
            values.forEach { value -> builder.addHeader(name, value) }
        }

        val response = client.newCall(builder.build()).execute()

        if (response.code == 429) {
            response.close()
            throw ReCaptchaException("reCaptcha Challenge requested", url)
        }

        val responseBodyToReturn = response.body?.string()
        val latestUrl = response.request.url.toString()

        return NpResponse(
            response.code,
            response.message,
            response.headers.toMultimap(),
            responseBodyToReturn,
            latestUrl
        )
    }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:129.0) Gecko/20100101 Firefox/129.0"

        @Volatile
        private var instance: JamalifyDownloader? = null

        fun init(client: OkHttpClient? = null): JamalifyDownloader =
            instance ?: synchronized(this) {
                instance ?: JamalifyDownloader(
                    client ?: OkHttpClient.Builder()
                        .readTimeout(30, TimeUnit.SECONDS)
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .build()
                ).also { instance = it }
            }
    }
}
