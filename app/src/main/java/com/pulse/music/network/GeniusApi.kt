package com.pulse.music.network

import com.pulse.music.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

/**
 * Thin wrapper over the Genius API. Does two things:
 *
 *  1. [search] — find a song by "title artist" and return the top hit's Genius ID
 *  2. [getSong] — fetch full metadata for that Genius ID (album art URL, release date)
 *
 * Auth is a Bearer token loaded at build time from local.properties →
 * BuildConfig.GENIUS_ACCESS_TOKEN. If the token is blank (no config), every
 * call short-circuits to null and the app behaves as if Genius is offline.
 *
 * All methods run on [Dispatchers.IO] internally. Callers don't need to wrap.
 */
object GeniusApi {

    private const val BASE_URL = "https://api.genius.com"

    // Lenient so a single surprise field in Genius's response doesn't break the parse.
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    /**
     * Search Genius for a song. Returns the top hit's ID and URL, or null if
     * no hit matched, the artist is unknown (matching is too loose without
     * one), or auth is missing.
     *
     * Sanity-checks the matched result's artist against ours: if Genius's
     * top hit is by a wildly different artist (e.g. searching "Animals" by
     * Maroon 5 returns "Animals" by some random producer), we reject the
     * match rather than show wrong artwork. Empirical threshold: lowercase
     * substring match in either direction is good enough for legitimate
     * matches like "Sia" vs "Sia feat. Sean Paul".
     */
    suspend fun search(title: String, artist: String): SearchHit? = withContext(Dispatchers.IO) {
        val token = BuildConfig.GENIUS_ACCESS_TOKEN
        if (token.isBlank()) return@withContext null

        // Without a real artist name, Genius matching is too loose — we'd
        // grab whatever's popular under that title and end up with the
        // wrong artwork. Better to show the gradient fallback than a wrong
        // album cover.
        if (artist.isBlank() || artist.equals("Unknown artist", ignoreCase = true)) {
            return@withContext null
        }

        val url = "$BASE_URL/search".toHttpUrl()
            .newBuilder()
            .addQueryParameter("q", "$title $artist")
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        try {
            HttpClient.instance.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val parsed = json.decodeFromString(SearchResponse.serializer(), body)

                // Walk hits in relevance order, return the first one whose
                // artist is plausibly the same as ours.
                val expectedArtistLower = artist.lowercase()
                val firstGoodHit = parsed.response.hits.firstOrNull { hit ->
                    val resultArtistLower = hit.result.primaryArtist?.name?.lowercase() ?: return@firstOrNull false
                    // Substring either way handles "Sia" vs "Sia feat. Sean Paul",
                    // "The Beatles" vs "Beatles", etc., while still rejecting
                    // unrelated artists like "Maroon 5" vs "Architects".
                    resultArtistLower.contains(expectedArtistLower) ||
                        expectedArtistLower.contains(resultArtistLower)
                } ?: return@withContext null

                val r = firstGoodHit.result
                SearchHit(
                    id = r.id,
                    url = r.url,
                    title = r.title,
                    artist = r.primaryArtist?.name,
                    // Prefer song_art_image_url (square album cover) over
                    // header_image_url (banner image, often unrelated to the
                    // album — that's where the LOSTWAVE picture came from).
                    artworkUrl = r.songArtImageUrl,
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Fetch the full song record for a Genius ID. Adds richer fields like
     * album name and release date on top of what /search returns.
     */
    suspend fun getSong(geniusId: Long): SongDetails? = withContext(Dispatchers.IO) {
        val token = BuildConfig.GENIUS_ACCESS_TOKEN
        if (token.isBlank()) return@withContext null

        val request = Request.Builder()
            .url("$BASE_URL/songs/$geniusId")
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        try {
            HttpClient.instance.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val parsed = json.decodeFromString(SongResponse.serializer(), body)
                val s = parsed.response.song
                SongDetails(
                    id = s.id,
                    url = s.url,
                    title = s.title,
                    artist = s.primaryArtist?.name,
                    album = s.album?.name,
                    // song_art_image_url is the square album cover.
                    // header_image_url is a banner that's often unrelated
                    // and/or generic ("LOSTWAVE", random K-pop poster).
                    // Always prefer the album art.
                    artworkUrl = s.songArtImageUrl,
                    releaseDate = s.releaseDateForDisplay,
                )
            }
        } catch (e: Exception) {
            null
        }
    }
}

// ---------- Simplified public types our app actually consumes ----------

data class SearchHit(
    val id: Long,
    val url: String,
    val title: String,
    val artist: String?,
    val artworkUrl: String?,
)

data class SongDetails(
    val id: Long,
    val url: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val artworkUrl: String?,
    val releaseDate: String?,
)

// ---------- Serialization DTOs matching Genius's response shape ----------
// Kept @Serializable + internal so they're parse-only and not leaked into the
// rest of the app. The public-facing types above are what UI code touches.

@Serializable
private data class SearchResponse(
    val response: SearchResponseBody,
)

@Serializable
private data class SearchResponseBody(
    val hits: List<Hit>,
)

@Serializable
private data class Hit(
    val result: ResultDto,
)

@Serializable
private data class ResultDto(
    val id: Long,
    val title: String,
    val url: String,
    @kotlinx.serialization.SerialName("primary_artist") val primaryArtist: ArtistDto? = null,
    @kotlinx.serialization.SerialName("song_art_image_url") val songArtImageUrl: String? = null,
    @kotlinx.serialization.SerialName("header_image_url") val headerImageUrl: String? = null,
)

@Serializable
private data class ArtistDto(
    val name: String,
)

@Serializable
private data class SongResponse(
    val response: SongResponseBody,
)

@Serializable
private data class SongResponseBody(
    val song: SongDto,
)

@Serializable
private data class SongDto(
    val id: Long,
    val title: String,
    val url: String,
    @kotlinx.serialization.SerialName("primary_artist") val primaryArtist: ArtistDto? = null,
    val album: AlbumDto? = null,
    @kotlinx.serialization.SerialName("song_art_image_url") val songArtImageUrl: String? = null,
    @kotlinx.serialization.SerialName("header_image_url") val headerImageUrl: String? = null,
    @kotlinx.serialization.SerialName("release_date_for_display") val releaseDateForDisplay: String? = null,
)

@Serializable
private data class AlbumDto(
    val name: String,
)
