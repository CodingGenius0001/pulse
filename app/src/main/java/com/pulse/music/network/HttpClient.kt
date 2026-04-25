package com.pulse.music.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Shared OkHttp client used by all network services. Kept as a singleton
 * because OkHttp manages its own connection/thread pools internally — spinning
 * up a new client per request defeats caching and costs memory.
 */
object HttpClient {
    val instance: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
}
