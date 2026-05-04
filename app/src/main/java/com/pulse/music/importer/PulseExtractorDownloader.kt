package com.pulse.music.importer

import com.pulse.music.network.HttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException

internal class PulseExtractorDownloader : Downloader() {

    override fun execute(request: Request): Response {
        val requestBody = request.dataToSend()?.toRequestBody()
        val okhttpRequest = okhttp3.Request.Builder()
            .url(request.url())
            .method(request.httpMethod(), requestBody)
            .addHeader("User-Agent", USER_AGENT)

        request.headers().forEach { (name, values) ->
            okhttpRequest.removeHeader(name)
            values.forEach { value -> okhttpRequest.addHeader(name, value) }
        }

        try {
            HttpClient.instance.newCall(okhttpRequest.build()).execute().use { response ->
                if (response.code == 429) {
                    throw ReCaptchaException("reCaptcha challenge requested", request.url())
                }

                val bodyString = response.body?.string() ?: ""
                return Response(
                    response.code,
                    response.message,
                    response.headers.toMultimap().mapValues { (_, values) -> values.toList() },
                    bodyString,
                    response.request.url.toString(),
                )
            }
        } catch (e: ReCaptchaException) {
            throw e
        } catch (e: IOException) {
            throw e
        }
    }

    private companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
    }
}
