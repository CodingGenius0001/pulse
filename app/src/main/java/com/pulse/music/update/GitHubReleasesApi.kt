package com.pulse.music.update

import com.pulse.music.network.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Request

/**
 * Information about the latest release pulled from GitHub.
 *
 * [buildNumber] is parsed out of the release name, which our CI workflow
 * formats as either "Latest debug build" (no number directly) or
 * "Build #123 (abc1234)". For the floating "latest" release we look at the
 * release body, which we control to embed the run number explicitly.
 */
data class UpdateInfo(
    val buildNumber: Int,
    val commitSha: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val releaseNotes: String,
)

/**
 * Thin wrapper over the GitHub Releases API. Public repos don't need auth,
 * which keeps the in-app updater zero-config for the user.
 */
object GitHubReleasesApi {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    /**
     * Fetch metadata for the release tagged [tag] in repo [repo] (e.g.
     * "CodingGenius0001/pulse"). Returns null on any failure.
     *
     * Prefers an asset named `pulse-debug.apk`; falls back to any .apk if
     * the canonical name isn't present.
     */
    suspend fun fetchTaggedRelease(repo: String, tag: String): UpdateInfo? = withContext(Dispatchers.IO) {
        val url = "https://api.github.com/repos/$repo/releases/tags/$tag"

        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "Pulse-Android-Updater")
            .get()
            .build()

        try {
            HttpClient.instance.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val parsed = json.decodeFromString(ReleaseDto.serializer(), body)

                val apkAsset = parsed.assets.firstOrNull { it.name.equals("pulse-debug.apk", ignoreCase = true) }
                    ?: parsed.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                    ?: return@withContext null

                UpdateInfo(
                    buildNumber = parseBuildNumber(parsed.name, parsed.body, parsed.targetCommitish),
                    commitSha = parsed.targetCommitish,
                    downloadUrl = apkAsset.browserDownloadUrl,
                    sizeBytes = apkAsset.size,
                    releaseNotes = parsed.body.orEmpty(),
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * The build number of the release. We try three sources, in order:
     *  1. Release name like "Build #42 (abc1234)" — the per-run versioned tags
     *  2. Release body containing "build #42" — our floating "latest" release
     *     embeds this in its description
     *  3. Fall back to 0 (treated as "unknown / always-newer-than-installed")
     */
    private fun parseBuildNumber(name: String?, body: String?, sha: String): Int {
        val regex = Regex("""[Bb]uild\s+#(\d+)""")
        name?.let { regex.find(it)?.groupValues?.get(1)?.toIntOrNull()?.let { return it } }
        body?.let { regex.find(it)?.groupValues?.get(1)?.toIntOrNull()?.let { return it } }
        return 0
    }

    @Serializable
    private data class ReleaseDto(
        val name: String? = null,
        val body: String? = null,
        @kotlinx.serialization.SerialName("target_commitish")
        val targetCommitish: String = "",
        val assets: List<AssetDto> = emptyList(),
    )

    @Serializable
    private data class AssetDto(
        val name: String,
        val size: Long = 0,
        @kotlinx.serialization.SerialName("browser_download_url")
        val browserDownloadUrl: String,
    )
}
