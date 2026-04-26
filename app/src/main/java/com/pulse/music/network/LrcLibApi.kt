package com.pulse.music.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

/**
 * LRCLIB API — fetches synced or plain lyrics by track + artist + album + duration.
 *
 * LRCLIB has TWO relevant endpoints and we use both:
 *
 *  1. /api/get — exact match. All four fields must line up with what's in LRCLIB's
 *     database. Returns 404 when any field doesn't match (album mismatch is the
 *     most common cause — users' files often have generic album names like the
 *     folder name, but LRCLIB has the canonical album).
 *
 *  2. /api/search — fuzzy. Takes track_name + artist_name (no album, no duration)
 *     and returns a list of candidate matches with their lyrics already populated.
 *     We use this as a fallback when /get 404s.
 *
 * The fallback chain looks like:
 *   try /get with all four fields → if 404 →
 *   try /search with track + artist → pick best match by closest duration →
 *   if still no result → NotFound.
 *
 * This is what makes "Anyone" by Seventeen work: the file's album is "Pulse"
 * (folder leaking in) but LRCLIB has it as "Your Choice", so /get 404s, and
 * /search rescues the lookup.
 */
object LrcLibApi {

    private const val BASE_URL = "https://lrclib.net/api"
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
     * Try to find lyrics for a track. Hits /get first; if that 404s, falls
     * back to /search and picks the best candidate by closest duration to
     * what we expected.
     */
    suspend fun fetch(
        trackName: String,
        artistName: String,
        albumName: String,
        durationSeconds: Long,
    ): LrcLibResponse = withContext(Dispatchers.IO) {
        // Don't even bother if the artist is unknown — LRCLIB's matching
        // gets very loose and we'll grab the wrong song.
        if (artistName.isBlank() || artistName.equals("Unknown artist", ignoreCase = true)) {
            return@withContext LrcLibResponse.NotFound
        }

        val exact = exactGet(trackName, artistName, albumName, durationSeconds)

        // BUG FIX (v0.5): /api/get sometimes returns 200 with both lyric
        // fields null (and not flagged as instrumental). My old code treated
        // that as a "Found but empty" result, which cascaded up as NotFound
        // without ever trying /search. Now we explicitly check that we got
        // SOMETHING usable; if not, fall through to /search like a 404.
        if (exact is LrcLibResponse.Found && exact.hasContent()) {
            return@withContext exact
        }
        // Also fall through on Error — better to retry against /search than
        // give up because of a transient hiccup on /get.
        searchFallback(trackName, artistName, durationSeconds)
    }

    /** True if we actually have lyrics or a confirmed instrumental flag. */
    private fun LrcLibResponse.Found.hasContent(): Boolean =
        instrumental || !syncedLyrics.isNullOrBlank() || !plainLyrics.isNullOrBlank()

    /**
     * Hit /api/get with all four fields. Returns Found, NotFound (on 404), or Error.
     */
    private suspend fun exactGet(
        trackName: String,
        artistName: String,
        albumName: String,
        durationSeconds: Long,
    ): LrcLibResponse {
        val url = "$BASE_URL/get".toHttpUrl()
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

        return try {
            HttpClient.instance.newCall(request).execute().use { response ->
                when {
                    response.code == 404 -> LrcLibResponse.NotFound
                    !response.isSuccessful -> LrcLibResponse.Error
                    else -> {
                        val body = response.body?.string() ?: return@use LrcLibResponse.Error
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

    /**
     * Fall back to /api/search with just track + artist. Picks the candidate
     * whose duration is closest to ours, within a 5-second tolerance — beyond
     * that, we'd risk grabbing a different song with the same name.
     *
     * /search returns a JSON array. Each entry already has plainLyrics +
     * syncedLyrics populated, so a single roundtrip is enough.
     */
    private suspend fun searchFallback(
        trackName: String,
        artistName: String,
        durationSeconds: Long,
    ): LrcLibResponse {
        val url = "$BASE_URL/search".toHttpUrl()
            .newBuilder()
            .addQueryParameter("track_name", trackName)
            .addQueryParameter("artist_name", artistName)
            .build()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        return try {
            HttpClient.instance.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use LrcLibResponse.Error
                val body = response.body?.string() ?: return@use LrcLibResponse.Error
                val candidates = json.decodeFromString(
                    kotlinx.serialization.builtins.ListSerializer(SearchResultDto.serializer()),
                    body,
                )
                if (candidates.isEmpty()) return@use LrcLibResponse.NotFound

                // Strategy:
                //  1. Prefer candidates within 10s of our duration AND that
                //     have actual lyric content (synced or plain).
                //  2. If none match, try ANY candidate within 10s.
                //  3. If none match at all, give up (NotFound) — we don't want
                //     to grab a 5-minute remix when the user has the 30-second clip.
                val withContent = candidates.filter { c ->
                    kotlin.math.abs(c.duration - durationSeconds) <= 10 &&
                        (!c.syncedLyrics.isNullOrBlank() || !c.plainLyrics.isNullOrBlank())
                }
                val best = withContent
                    .minByOrNull { kotlin.math.abs(it.duration - durationSeconds) }
                    ?: candidates
                        .filter { kotlin.math.abs(it.duration - durationSeconds) <= 10 }
                        .minByOrNull { kotlin.math.abs(it.duration - durationSeconds) }
                    ?: return@use LrcLibResponse.NotFound

                LrcLibResponse.Found(
                    plainLyrics = best.plainLyrics?.takeIf { it.isNotBlank() },
                    syncedLyrics = best.syncedLyrics?.takeIf { it.isNotBlank() },
                    instrumental = best.instrumental,
                )
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

    @Serializable
    private data class SearchResultDto(
        val id: Long = 0,
        val name: String? = null,
        val trackName: String? = null,
        val artistName: String? = null,
        val albumName: String? = null,
        val duration: Long = 0,
        val instrumental: Boolean = false,
        val plainLyrics: String? = null,
        val syncedLyrics: String? = null,
    )
}
