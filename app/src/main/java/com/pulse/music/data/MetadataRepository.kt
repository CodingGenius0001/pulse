package com.pulse.music.data

import com.pulse.music.network.GeniusApi
import com.pulse.music.network.GeniusSearchOutcome
import com.pulse.music.network.LrcLibApi
import com.pulse.music.network.SearchHit
import com.pulse.music.network.SongDetails
import kotlinx.coroutines.flow.Flow

/**
 * Mediates between the UI and remote metadata providers, backed by Room.
 *
 * The key rule now is conservative matching: if we are not confident that a
 * remote result is the same song, we keep the track unresolved instead of
 * showing wrong artwork or wrong metadata.
 */
class MetadataRepository(
    private val metadataDao: MetadataDao,
) {

    data class MatchInput(
        val title: String,
        val artist: String,
        val album: String,
    )

    suspend fun getCached(songId: Long): SongMetadata? = metadataDao.get(songId)

    fun observe(songId: Long): Flow<SongMetadata?> = metadataDao.observe(songId)

    suspend fun resolve(
        song: Song,
        input: MatchInput = MatchInput(
            title = song.title,
            artist = song.artist,
            album = song.album,
        ),
        persistOverride: Boolean = false,
    ): SongMetadata {
        val existing = metadataDao.get(song.id)
        val storedOverride = existing?.overrideInput()
        val requestedInput = input.normalizedFor(song)
        val effectiveInput = when {
            persistOverride -> requestedInput
            input.matches(song) && storedOverride != null -> storedOverride
            else -> requestedInput
        }
        val overrideToPersist = when {
            persistOverride -> effectiveInput
            storedOverride != null -> storedOverride
            else -> null
        }

        if (input.matches(song) && existing != null && !shouldRetry(song, existing, effectiveInput)) {
            return existing
        }

        val primaryOutcome = GeniusApi.searchForMetadata(
            title = effectiveInput.title,
            artist = effectiveInput.artist,
            album = effectiveInput.album,
        )
        val fallbackInfo = if (primaryOutcome is GeniusSearchOutcome.Found) {
            null
        } else {
            LrcLibApi.findBestTrackInfo(
                trackName = effectiveInput.title,
                artistName = effectiveInput.artist,
                albumName = effectiveInput.album,
                durationSeconds = song.durationMs / 1000,
            )
        }

        val primaryHits = when (primaryOutcome) {
            is GeniusSearchOutcome.Found -> primaryOutcome.hits
            GeniusSearchOutcome.NoMatch -> emptyList()
            GeniusSearchOutcome.Unavailable -> emptyList()
        }

        val retriedHits = if (
            primaryHits.isEmpty() &&
            fallbackInfo.hasUsefulArtistFor(effectiveInput) &&
            fallbackInfo.matchesSongContext(effectiveInput)
        ) {
            when (
                val retried = GeniusApi.searchForMetadata(
                    title = effectiveInput.title,
                    artist = fallbackInfo?.artist.orEmpty(),
                    album = effectiveInput.album,
                )
            ) {
                is GeniusSearchOutcome.Found -> retried.hits
                else -> emptyList()
            }
        } else {
            emptyList()
        }

        val bestGeniusMatch = pickBestGeniusMatch(
            candidates = (primaryHits + retriedHits).distinctBy { it.id },
            input = effectiveInput,
        )

        val resolvedRecord = when {
            bestGeniusMatch != null -> {
                val details = bestGeniusMatch.details
                SongMetadata(
                    songId = song.id,
                    geniusId = bestGeniusMatch.hit.id,
                    geniusUrl = details?.url ?: bestGeniusMatch.hit.url,
                    resolvedTitle = details?.title ?: bestGeniusMatch.hit.title ?: effectiveInput.title,
                    resolvedArtist = details?.artist ?: bestGeniusMatch.hit.artist ?: effectiveInput.artist,
                    resolvedAlbum = details?.album ?: effectiveInput.album.takeIf { it.isNotBlank() },
                    artworkUrl = details?.artworkUrl ?: bestGeniusMatch.hit.artworkUrl,
                    releaseDate = details?.releaseDate,
                    overrideTitle = overrideToPersist?.title,
                    overrideArtist = overrideToPersist?.artist,
                    overrideAlbum = overrideToPersist?.album,
                    overrideAppliedAt = if (overrideToPersist != null) System.currentTimeMillis() else 0,
                )
            }

            fallbackInfo != null && fallbackInfo.matchesSongContext(effectiveInput) -> {
                fallbackInfo.toMetadata(
                    songId = song.id,
                    override = overrideToPersist,
                )
            }

            overrideToPersist != null -> {
                SongMetadata(
                    songId = song.id,
                    resolvedTitle = overrideToPersist.title.takeIf { it.isNotBlank() },
                    resolvedArtist = overrideToPersist.artist.takeIf { it.isNotBlank() },
                    resolvedAlbum = overrideToPersist.album.takeIf { it.isNotBlank() },
                    overrideTitle = overrideToPersist.title,
                    overrideArtist = overrideToPersist.artist,
                    overrideAlbum = overrideToPersist.album,
                    overrideAppliedAt = System.currentTimeMillis(),
                )
            }

            primaryOutcome == GeniusSearchOutcome.Unavailable -> SongMetadata(songId = song.id)
            else -> SongMetadata(songId = song.id)
        }

        metadataDao.upsert(resolvedRecord)
        return resolvedRecord
    }

    suspend fun refresh(song: Song): SongMetadata {
        val existing = metadataDao.get(song.id)
        metadataDao.delete(song.id)
        return resolve(
            song = song,
            input = existing?.overrideInput() ?: MatchInput(song.title, song.artist, song.album),
            persistOverride = existing?.hasOverride() == true,
        )
    }

    suspend fun correctMatch(song: Song, title: String, artist: String, album: String): SongMetadata {
        return resolve(
            song = song,
            input = MatchInput(title, artist, album),
            persistOverride = true,
        )
    }

    private suspend fun pickBestGeniusMatch(
        candidates: List<SearchHit>,
        input: MatchInput,
    ): GeniusMatch? {
        if (candidates.isEmpty()) return null

        val shortlist = candidates
            .map { hit -> hit to preliminaryScore(hit, input) }
            .filter { (_, score) -> score >= 56 }
            .sortedByDescending { (_, score) -> score }
            .take(5)
            .map { it.first }

        if (shortlist.isEmpty()) return null

        val scored = shortlist.map { hit ->
            val details = GeniusApi.getSong(hit.id)
            GeniusMatch(
                hit = hit,
                details = details,
                score = finalScore(hit, details, input),
            )
        }

        val minimumScore = if (input.album.isKnownAlbum()) 84 else 70
        return scored
            .filter { it.score >= minimumScore }
            .maxByOrNull { it.score }
    }

    private fun preliminaryScore(hit: SearchHit, input: MatchInput): Int {
        val titleScore = textScore(
            actual = hit.title.cleanKey(),
            expected = input.title.cleanKey(),
            exact = 42,
            contains = 30,
            overlap = 20,
        )
        if (titleScore == 0) return 0

        val artistScore = if (input.artist.isKnownArtist()) {
            textScore(
                actual = hit.artist.cleanKey(),
                expected = input.artist.cleanKey(),
                exact = 28,
                contains = 18,
                overlap = 12,
            )
        } else {
            0
        }
        if (input.artist.isKnownArtist() && artistScore == 0) return 0

        return titleScore + artistScore
    }

    private fun finalScore(
        hit: SearchHit,
        details: SongDetails?,
        input: MatchInput,
    ): Int {
        val titleScore = textScore(
            actual = (details?.title ?: hit.title).cleanKey(),
            expected = input.title.cleanKey(),
            exact = 42,
            contains = 30,
            overlap = 20,
        )
        if (titleScore == 0) return 0

        val artistScore = if (input.artist.isKnownArtist()) {
            textScore(
                actual = (details?.artist ?: hit.artist).cleanKey(),
                expected = input.artist.cleanKey(),
                exact = 28,
                contains = 18,
                overlap = 12,
            )
        } else {
            0
        }
        if (input.artist.isKnownArtist() && artistScore == 0) return 0

        val albumScore = if (input.album.isKnownAlbum()) {
            textScore(
                actual = details?.album.cleanKey(),
                expected = input.album.cleanKey(),
                exact = 24,
                contains = 16,
                overlap = 10,
            )
        } else {
            8
        }

        val artScore = when {
            !details?.artworkUrl.isNullOrBlank() -> 10
            !hit.artworkUrl.isNullOrBlank() -> 6
            else -> 0
        }

        return titleScore + artistScore + albumScore + artScore
    }

    private fun shouldRetry(song: Song, cached: SongMetadata, input: MatchInput): Boolean {
        val noResolvedInfo = cached.geniusId == null &&
            cached.geniusUrl.isNullOrBlank() &&
            cached.resolvedTitle.isNullOrBlank() &&
            cached.resolvedArtist.isNullOrBlank() &&
            cached.resolvedAlbum.isNullOrBlank() &&
            cached.artworkUrl.isNullOrBlank()
        if (noResolvedInfo) return song.artist.isUnknownArtist() || input.artist.isKnownArtist()

        val hasManualOverrideWithoutArtwork = cached.hasOverride() &&
            cached.geniusId == null &&
            cached.artworkUrl.isNullOrBlank() &&
            cached.resolvedTitle.cleanKey() == input.title.cleanKey() &&
            cached.resolvedArtist.cleanKey() == input.artist.cleanKey()
        return hasManualOverrideWithoutArtwork
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

    private fun SongMetadata.overrideInput(): MatchInput? {
        if (!hasOverride()) return null
        return MatchInput(
            title = overrideTitle.orEmpty(),
            artist = overrideArtist.orEmpty(),
            album = overrideAlbum.orEmpty(),
        )
    }

    private fun SongMetadata.hasOverride(): Boolean =
        !overrideTitle.isNullOrBlank() || !overrideArtist.isNullOrBlank() || !overrideAlbum.isNullOrBlank()

    private fun MatchInput.normalizedFor(song: Song): MatchInput =
        MatchInput(
            title = title.trim().ifBlank { song.title },
            artist = artist.trim().ifBlank { song.artist },
            album = album.trim().ifBlank { song.album },
        )

    private fun MatchInput.matches(song: Song): Boolean =
        title == song.title && artist == song.artist && album == song.album

    private fun LrcLibApi.TrackInfo?.hasUsefulArtistFor(input: MatchInput): Boolean =
        this != null &&
            !artist.isNullOrBlank() &&
            !artist.isUnknownArtist() &&
            (input.artist.isUnknownArtist() || !artist.equals(input.artist, ignoreCase = true))

    private fun LrcLibApi.TrackInfo?.matchesSongContext(input: MatchInput): Boolean {
        if (this == null) return false

        val titleMatches = title.cleanKey() == input.title.cleanKey()
        if (!titleMatches) return false

        val albumTrusted = input.album.isUnknownAlbum() ||
            (album.cleanKey().isNotBlank() && album.cleanKey() == input.album.cleanKey())
        if (!albumTrusted) return false

        if (!input.artist.isUnknownArtist()) {
            return artist.cleanKey() == input.artist.cleanKey()
        }

        return !artist.isNullOrBlank() && !artist.isUnknownArtist()
    }

    private fun LrcLibApi.TrackInfo.toMetadata(songId: Long, override: MatchInput?): SongMetadata =
        SongMetadata(
            songId = songId,
            resolvedTitle = title.takeIf { it.isNotBlank() },
            resolvedArtist = artist.takeIf { !it.isNullOrBlank() },
            resolvedAlbum = album.takeIf { !it.isNullOrBlank() },
            overrideTitle = override?.title,
            overrideArtist = override?.artist,
            overrideAlbum = override?.album,
            overrideAppliedAt = if (override != null) System.currentTimeMillis() else 0,
        )

    private fun String?.isUnknownArtist(): Boolean =
        isNullOrBlank() ||
            equals("Unknown artist", ignoreCase = true) ||
            equals("<unknown>", ignoreCase = true)

    private fun String?.isKnownArtist(): Boolean = !isUnknownArtist()

    private fun String?.isUnknownAlbum(): Boolean =
        isNullOrBlank() ||
            equals("Unknown album", ignoreCase = true) ||
            equals("<unknown>", ignoreCase = true)

    private fun String?.isKnownAlbum(): Boolean = !isUnknownAlbum()

    private fun String?.cleanKey(): String =
        this.orEmpty()
            .lowercase()
            .replace(Regex("""\([^)]*\)|\[[^]]*]"""), " ")
            .replace(Regex("""\b(remaster(ed)?|explicit|clean|audio|official|video|lyrics?|feat\.?|ft\.?)\b"""), " ")
            .replace(Regex("""[^\p{L}\p{N}& ]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private data class GeniusMatch(
        val hit: SearchHit,
        val details: SongDetails?,
        val score: Int,
    )
}
