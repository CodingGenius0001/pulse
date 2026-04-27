package com.pulse.music.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

object TheAudioDbApi {

    private const val BASE_URL = "https://www.theaudiodb.com/api/v1/json/123"
    private const val USER_AGENT = "Pulse-Android/0.5.11 (github.com/CodingGenius0001/pulse)"

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    suspend fun findAlbumArtByReleaseGroup(releaseGroupId: String): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(
                "$BASE_URL/album-mb.php".toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("i", releaseGroupId)
                    .build()
            )
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .get()
            .build()

        try {
            HttpClient.instance.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val parsed = json.decodeFromString(AlbumLookupResponse.serializer(), body)
                parsed.albums
                    ?.firstOrNull()
                    ?.albumThumb
                    ?.takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) {
            null
        }
    }

    @Serializable
    private data class AlbumLookupResponse(
        @SerialName("album")
        val albums: List<AlbumDto>? = null,
    )

    @Serializable
    private data class AlbumDto(
        @SerialName("strAlbumThumb")
        val albumThumb: String? = null,
    )
}
