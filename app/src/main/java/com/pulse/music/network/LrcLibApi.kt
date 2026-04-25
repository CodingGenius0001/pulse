package com.pulse.music.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

/**
 * LRCLIB API — fetches synced or plain lyrics keyed by track+artist+album+duration.
 *
 * LRCLIB is a free, open, crowd-sourced lyrics database (https://lrclib.net).
 * No token required, but they ask nicely for a distinctive User-Agent so they
 * can attribute traffic and contact the app author if something goes wrong.
 *
 * Returned payload:
 *   - syncedLyrics: LRC-format string like `[00:12.34] Line 1\n[00:15.67] Line 2`
 *   - plainLyrics: fallback if no synced version exists for this exact track
 *   - instrumental: true when the track is flagged as having no lyrics at all
 *
 * If the track isn't in the DB, /api/get returns 404. We treat that as
 * "NotFound" rather than an error so we can cache the miss and avoid
 * hammering the network on every dialog open.
 */
object LrcLibApi {

    private const val BASE_URL = "https://lrclib.net/api/get"
    private const val USER_AGENT = "Pulse-Android/0.4 (github.com/CodingGenius0001/pulse)"

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    sealed interface LrcLibResponse {
        data class Found(
            val plainLyrics: String?,
            val syncedLyrics: String?,
            val instrumental: Boolean,
        ) : LrcLibResponse
        data object NotFound : LrcLibResponse
        data object Error : LrcLibResponse
    }

    /**
     * Fetch lyrics for a track. All four params are used by LRCLIB to find
     * the exact right version of the track (important for albums where the
     * album cut and single cut have different timings).
     *
     * [durationSeconds] can be approximate — LRCLIB allows a small tolerance
     * internally, so passing durationMs/1000 is fine.
     */
    suspend fun fetch(
        trackName: String,
        artistName: String,
        albumName: String,
        durationSeconds: Long,
    ): LrcLibResponse = withContext(Dispatchers.IO) {
        val url = BASE_URL.toHttpUrl()
            .newBuilder()
            .addQueryParameter("track_name", trackName)
            .addQueryParameter("artist_name", artistName)
            .addQueryParameter("album_name", albumName)
            .addQueryParameter("duration", durationSeconds.toString())
            .build()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        try {
            HttpClient.instance.newCall(request).execute().use { response ->
                when {
                    response.code == 404 -> LrcLibResponse.NotFound
                    !response.isSuccessful -> LrcLibResponse.Error
                    else -> {
                        val body = response.body?.string()
                            ?: return@use LrcLibResponse.Error
                        val parsed = json.decodeFromString(GetResponse.serializer(), body)
                        LrcLibResponse.Found(
                            plainLyrics = parsed.plainLyrics?.takeIf { it.isNotBlank() },
                            syncedLyrics = parsed.syncedLyrics?.takeIf { it.isNotBlank() },
                            instrumental = parsed.instrumental,
                        )
                    }
                }
            }
        } catch (e: Exception) {
            LrcLibResponse.Error
        }
    }

    @Serializable
    private data class GetResponse(
        val id: Long = 0,
        val name: String? = null,
        val trackName: String? = null,
        val artistName: String? = null,
        val albumName: String? = null,
        val duration: Double? = null,
        val instrumental: Boolean = false,
        val plainLyrics: String? = null,
        val syncedLyrics: String? = null,
    )
}
