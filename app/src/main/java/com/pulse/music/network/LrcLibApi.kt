package com.pulse.music.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * LRCLIB API wrapper. Matching is deliberately forgiving because local files
 * often have missing artist tags, folder names as album tags, or slightly
 * different durations from the LRCLIB canonical record.
 */
object LrcLibApi {

    private const val BASE_URL = "https://lrclib.net/api"
    private const val USER_AGENT = "Pulse-Android/0.5.1 (github.com/CodingGenius0001/pulse)"

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

    suspend fun fetch(
        trackName: String,
        artistName: String,
        albumName: String,
        durationSeconds: Long,
    ): LrcLibResponse = withContext(Dispatchers.IO) {
        val cleanTrack = trackName.cleanForLyricsQuery()
        if (cleanTrack.isBlank()) return@withContext LrcLibResponse.NotFound

        val knownArtist = artistName.isKnownArtist()
        if (knownArtist) {
            val exact = exactGet(cleanTrack, artistName, albumName, durationSeconds)
            if (exact is LrcLibResponse.Found && exact.hasContent()) {
                return@withContext exact
            }
        }

        val primary = searchFallback(
            trackName = cleanTrack,
            artistName = artistName.takeIf { knownArtist },
            durationSeconds = durationSeconds,
        )
        if (primary is LrcLibResponse.Found && primary.hasContent()) {
            return@withContext primary
        }

        if (knownArtist) {
            val broad = queryFallback(
                query = "$cleanTrack ${artistName.cleanForLyricsQuery()}",
                expectedTrack = cleanTrack,
                expectedArtist = artistName,
                durationSeconds = durationSeconds,
            )
            if (broad is LrcLibResponse.Found && broad.hasContent()) {
                return@withContext broad
            }
        }

        queryFallback(
            query = cleanTrack,
            expectedTrack = cleanTrack,
            expectedArtist = artistName.takeIf { knownArtist },
            durationSeconds = durationSeconds,
        )
    }

    private fun LrcLibResponse.Found.hasContent(): Boolean =
        instrumental || !syncedLyrics.isNullOrBlank() || !plainLyrics.isNullOrBlank()

    private suspend fun exactGet(
        trackName: String,
        artistName: String,
        albumName: String,
        durationSeconds: Long,
    ): LrcLibResponse {
        val builder = "$BASE_URL/get".toHttpUrl()
            .newBuilder()
            .addQueryParameter("track_name", trackName)
            .addQueryParameter("artist_name", artistName)
            .addQueryParameter("duration", durationSeconds.toString())

        albumName
            .takeIf { it.isKnownAlbum() }
            ?.let { builder.addQueryParameter("album_name", it) }

        val request = Request.Builder()
            .url(builder.build())
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
                        json.decodeFromString(GetResponse.serializer(), body).toFound()
                    }
                }
            }
        } catch (e: Exception) {
            LrcLibResponse.Error
        }
    }

    private suspend fun searchFallback(
        trackName: String,
        artistName: String?,
        durationSeconds: Long,
    ): LrcLibResponse {
        val builder = "$BASE_URL/search".toHttpUrl()
            .newBuilder()
            .addQueryParameter("track_name", trackName)

        artistName?.let { builder.addQueryParameter("artist_name", it) }

        val request = Request.Builder()
            .url(builder.build())
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        return runSearchRequest(
            request = request,
            expectedTrack = trackName,
            expectedArtist = artistName,
            durationSeconds = durationSeconds,
        )
    }

    private suspend fun queryFallback(
        query: String,
        expectedTrack: String,
        expectedArtist: String?,
        durationSeconds: Long,
    ): LrcLibResponse {
        val request = Request.Builder()
            .url(
                "$BASE_URL/search".toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("query", query)
                    .build()
            )
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        return runSearchRequest(request, expectedTrack, expectedArtist, durationSeconds)
    }

    private suspend fun runSearchRequest(
        request: Request,
        expectedTrack: String,
        expectedArtist: String?,
        durationSeconds: Long,
    ): LrcLibResponse {
        return try {
            HttpClient.instance.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use LrcLibResponse.Error
                val body = response.body?.string() ?: return@use LrcLibResponse.Error
                val candidates = json.decodeFromString(
                    ListSerializer(SearchResultDto.serializer()),
                    body,
                )
                val best = pickBestCandidate(candidates, expectedTrack, expectedArtist, durationSeconds)
                    ?: return@use LrcLibResponse.NotFound
                best.toFound()
            }
        } catch (e: Exception) {
            LrcLibResponse.Error
        }
    }

    private fun pickBestCandidate(
        candidates: List<SearchResultDto>,
        expectedTrack: String,
        expectedArtist: String?,
        durationSeconds: Long,
    ): SearchResultDto? {
        val useful = candidates.filter { it.hasContent() }
        if (useful.isEmpty()) return null

        return useful
            .map { it to scoreCandidate(it, expectedTrack, expectedArtist, durationSeconds) }
            .filter { (_, score) -> score >= 45 }
            .maxByOrNull { (_, score) -> score }
            ?.first
    }

    private fun scoreCandidate(
        candidate: SearchResultDto,
        expectedTrack: String,
        expectedArtist: String?,
        durationSeconds: Long,
    ): Int {
        val candidateTrack = (candidate.trackName ?: candidate.name.orEmpty()).cleanForLyricsQuery()
        val titleScore = textScore(candidateTrack, expectedTrack, exact = 42, contains = 34, overlap = 24)
        if (titleScore == 0) return 0

        val artistScore = if (expectedArtist.isKnownArtist()) {
            textScore(
                candidate.artistName.orEmpty().cleanForLyricsQuery(),
                expectedArtist.orEmpty().cleanForLyricsQuery(),
                exact = 24,
                contains = 18,
                overlap = 12,
            )
        } else {
            10
        }
        if (expectedArtist.isKnownArtist() && artistScore == 0) return 0

        val candidateDuration = candidate.duration.roundToLong()
        val tolerance = max(12L, (durationSeconds * 0.08).roundToLong())
        val diff = abs(candidateDuration - durationSeconds)
        val durationScore = when {
            candidateDuration <= 0L || durationSeconds <= 0L -> 8
            diff <= 2L -> 20
            diff <= tolerance -> 20 - ((diff.toFloat() / tolerance) * 12).roundToLong().toInt()
            diff <= max(25L, tolerance * 2) -> 4
            else -> 0
        }

        val contentScore = when {
            !candidate.syncedLyrics.isNullOrBlank() -> 12
            !candidate.plainLyrics.isNullOrBlank() -> 8
            candidate.instrumental -> 6
            else -> 0
        }

        return titleScore + artistScore + durationScore + contentScore
    }

    private fun textScore(actual: String, expected: String, exact: Int, contains: Int, overlap: Int): Int {
        if (actual.isBlank() || expected.isBlank()) return 0
        if (actual == expected) return exact
        if (actual.contains(expected) || expected.contains(actual)) return contains
        val actualTokens = actual.split(" ").filter { it.length > 2 }.toSet()
        val expectedTokens = expected.split(" ").filter { it.length > 2 }.toSet()
        if (actualTokens.isEmpty() || expectedTokens.isEmpty()) return 0
        val shared = actualTokens.intersect(expectedTokens).size
        return if (shared >= expectedTokens.size.coerceAtMost(2)) overlap else 0
    }

    private fun SearchResultDto.hasContent(): Boolean =
        instrumental || !syncedLyrics.isNullOrBlank() || !plainLyrics.isNullOrBlank()

    private fun GetResponse.toFound(): LrcLibResponse.Found =
        LrcLibResponse.Found(
            plainLyrics = plainLyrics?.takeIf { it.isNotBlank() },
            syncedLyrics = syncedLyrics?.takeIf { it.isNotBlank() },
            instrumental = instrumental,
        )

    private fun SearchResultDto.toFound(): LrcLibResponse.Found =
        LrcLibResponse.Found(
            plainLyrics = plainLyrics?.takeIf { it.isNotBlank() },
            syncedLyrics = syncedLyrics?.takeIf { it.isNotBlank() },
            instrumental = instrumental,
        )

    private fun String?.isKnownArtist(): Boolean =
        !isNullOrBlank() && !equals("Unknown artist", ignoreCase = true) && !equals("<unknown>", ignoreCase = true)

    private fun String.isKnownAlbum(): Boolean =
        isNotBlank() && !equals("Unknown album", ignoreCase = true) && !equals("<unknown>", ignoreCase = true)

    private fun String.cleanForLyricsQuery(): String =
        replace(Regex("""\([^)]*\)|\[[^]]*]"""), " ")
            .replace(Regex("""\b(remaster(ed)?|explicit|clean|audio|official|video|lyrics?)\b""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .lowercase()

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
        val duration: Double = 0.0,
        val instrumental: Boolean = false,
        val plainLyrics: String? = null,
        val syncedLyrics: String? = null,
    )
}
