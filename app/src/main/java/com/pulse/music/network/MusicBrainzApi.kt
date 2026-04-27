package com.pulse.music.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

object MusicBrainzApi {

    data class SearchCandidate(
        val recordingId: String,
        val title: String,
        val artist: String?,
        val lengthMs: Long?,
    )

    data class RecordingDetails(
        val recordingId: String,
        val url: String,
        val title: String,
        val artist: String?,
        val album: String?,
        val releaseGroupId: String?,
        val releaseId: String?,
        val releaseDate: String?,
        val artworkUrl: String?,
    )

    sealed interface SearchOutcome {
        data class Found(val candidates: List<SearchCandidate>) : SearchOutcome
        data object NoMatch : SearchOutcome
        data object Unavailable : SearchOutcome
    }

    private const val BASE_URL = "https://musicbrainz.org/ws/2"
    private const val USER_AGENT = "Pulse-Android/0.5.13 (github.com/CodingGenius0001/pulse)"

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    suspend fun searchRecordings(
        title: String,
        artist: String,
        album: String = "",
    ): SearchOutcome = withContext(Dispatchers.IO) {
        val cleanTitle = title.cleanSearchText()
        if (cleanTitle.isBlank()) return@withContext SearchOutcome.NoMatch

        val query = buildString {
            append("recording:\"").append(cleanTitle.escapeLucene()).append('"')
            if (artist.isKnownArtist()) {
                append(" AND artist:\"").append(artist.cleanSearchText().escapeLucene()).append('"')
            }
            if (album.isKnownAlbum()) {
                append(" AND release:\"").append(album.cleanSearchText().escapeLucene()).append('"')
            }
        }

        val url = "$BASE_URL/recording".toHttpUrl()
            .newBuilder()
            .addQueryParameter("fmt", "json")
            .addQueryParameter("limit", "10")
            .addQueryParameter("query", query)
            .build()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .get()
            .build()

        try {
            HttpClient.instance.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext SearchOutcome.Unavailable
                val body = response.body?.string() ?: return@withContext SearchOutcome.Unavailable
                val parsed = json.decodeFromString(RecordingSearchResponse.serializer(), body)
                val candidates = parsed.recordings
                    .mapNotNull { recording ->
                        SearchCandidate(
                            recordingId = recording.id,
                            title = recording.title.orEmpty(),
                            artist = recording.artistCredit?.joinToString(" ") { it.name.orEmpty() }?.trim(),
                            lengthMs = recording.length,
                        ).takeIf { it.title.isNotBlank() }
                    }
                if (candidates.isEmpty()) SearchOutcome.NoMatch else SearchOutcome.Found(candidates)
            }
        } catch (_: Exception) {
            SearchOutcome.Unavailable
        }
    }

    suspend fun getRecordingDetails(
        recordingId: String,
        preferredAlbum: String = "",
    ): RecordingDetails? = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/recording/$recordingId".toHttpUrl()
            .newBuilder()
            .addQueryParameter("fmt", "json")
            .addQueryParameter("inc", "releases+release-groups+artist-credits")
            .build()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .get()
            .build()

        try {
            HttpClient.instance.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val parsed = json.decodeFromString(RecordingLookupResponse.serializer(), body)
                val artistName = parsed.artistCredit?.joinToString(" ") { it.name.orEmpty() }?.trim()
                val releaseCandidates = rankReleases(parsed.releases.orEmpty(), preferredAlbum)
                var chosenRelease = releaseCandidates.firstOrNull()
                var artworkUrl: String? = null
                for (release in releaseCandidates.take(5)) {
                    val resolvedArtwork = resolveArtwork(
                        release = release,
                        artistName = artistName,
                    )
                    if (!resolvedArtwork.isNullOrBlank()) {
                        chosenRelease = release
                        artworkUrl = resolvedArtwork
                        break
                    }
                }
                if (artworkUrl.isNullOrBlank()) {
                    artworkUrl = chosenRelease?.let { release ->
                        resolveArtwork(
                            release = release,
                            artistName = artistName,
                        )
                    }
                }
                val releaseGroupId = chosenRelease?.releaseGroup?.id
                val releaseId = chosenRelease?.id

                RecordingDetails(
                    recordingId = parsed.id,
                    url = "https://musicbrainz.org/recording/${parsed.id}",
                    title = parsed.title.orEmpty(),
                    artist = artistName,
                    album = chosenRelease?.title,
                    releaseGroupId = releaseGroupId,
                    releaseId = releaseId,
                    releaseDate = chosenRelease?.date,
                    artworkUrl = artworkUrl,
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun hasCoverArt(entityType: String, mbid: String): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://coverartarchive.org/$entityType/$mbid/front")
            .header("User-Agent", USER_AGENT)
            .head()
            .build()

        try {
            HttpClient.instance.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun resolveArtwork(
        release: ReleaseDto,
        artistName: String?,
    ): String? {
        val releaseGroupId = release.releaseGroup?.id
        val releaseId = release.id
        return when {
            !releaseGroupId.isNullOrBlank() && hasCoverArt("release-group", releaseGroupId) ->
                "https://coverartarchive.org/release-group/$releaseGroupId/front"
            !releaseId.isNullOrBlank() && hasCoverArt("release", releaseId) ->
                "https://coverartarchive.org/release/$releaseId/front"
            !releaseGroupId.isNullOrBlank() ->
                TheAudioDbApi.findAlbumArtByReleaseGroup(releaseGroupId)
                    ?: TheAudioDbApi.findAlbumArt(artistName.orEmpty(), release.title.orEmpty())
            else ->
                TheAudioDbApi.findAlbumArt(artistName.orEmpty(), release.title.orEmpty())
        }
    }

    private fun rankReleases(
        releases: List<ReleaseDto>,
        preferredAlbum: String,
    ): List<ReleaseDto> {
        if (releases.isEmpty()) return emptyList()
        val cleanPreferredAlbum = preferredAlbum.cleanSearchText()
        return releases
            .sortedByDescending { release ->
                when {
                    cleanPreferredAlbum.isNotBlank() &&
                        release.title.orEmpty().cleanSearchText() == cleanPreferredAlbum -> 4
                    release.status.equals("official", ignoreCase = true) -> 3
                    !release.date.isNullOrBlank() -> 2
                    else -> 1
                }
            }
    }

    private fun String.isKnownArtist(): Boolean =
        isNotBlank() && !equals("Unknown artist", ignoreCase = true) && !equals("<unknown>", ignoreCase = true)

    private fun String.isKnownAlbum(): Boolean =
        isNotBlank() && !equals("Unknown album", ignoreCase = true) && !equals("<unknown>", ignoreCase = true)

    private fun String.cleanSearchText(): String =
        lowercase()
            .replace(Regex("""\([^)]*\)|\[[^]]*]"""), " ")
            .replace(Regex("""\b(remaster(ed)?|explicit|clean|audio|official|video|lyrics?|feat\.?|ft\.?)\b"""), " ")
            .replace(Regex("""[^\p{L}\p{N}& ]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun String.escapeLucene(): String =
        replace(Regex("""([+\-!(){}\[\]^"~*?:\\/]|&&|\|\|)"""), """\\$1""")

    @Serializable
    private data class RecordingSearchResponse(
        @SerialName("recordings")
        val recordings: List<RecordingSearchDto> = emptyList(),
    )

    @Serializable
    private data class RecordingSearchDto(
        val id: String,
        val title: String? = null,
        val length: Long? = null,
        @SerialName("artist-credit")
        val artistCredit: List<ArtistCreditDto>? = null,
    )

    @Serializable
    private data class RecordingLookupResponse(
        val id: String,
        val title: String? = null,
        @SerialName("artist-credit")
        val artistCredit: List<ArtistCreditDto>? = null,
        val releases: List<ReleaseDto>? = null,
    )

    @Serializable
    private data class ArtistCreditDto(
        val name: String? = null,
    )

    @Serializable
    private data class ReleaseDto(
        val id: String,
        val title: String? = null,
        val date: String? = null,
        val status: String? = null,
        @SerialName("release-group")
        val releaseGroup: ReleaseGroupDto? = null,
    )

    @Serializable
    private data class ReleaseGroupDto(
        val id: String,
    )
}
