package com.pulse.music.data

import com.pulse.music.network.GeniusApi
import com.pulse.music.network.GeniusSearchOutcome
import com.pulse.music.network.LrcLibApi
import com.pulse.music.network.MusicBrainzApi
import com.pulse.music.network.SearchHit
import com.pulse.music.network.SongDetails
import com.pulse.music.network.TheAudioDbApi
import kotlinx.coroutines.flow.Flow
import kotlin.math.abs

/**
 * Mediates between the UI and remote metadata providers, backed by Room.
 *
 * Matching is conservative by design: we prefer "unresolved" over wrong data.
 * MusicBrainz + Cover Art Archive is the primary path, then TheAudioDB, with
 * Genius used as the final remote artwork / metadata fallback.
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
        force: Boolean = false,
    ): SongMetadata {
        val existing = metadataDao.get(song.id)
        val now = System.currentTimeMillis()
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

        if (!force && input.matches(song) && existing != null && !shouldRetry(song, existing, effectiveInput)) {
            return existing
        }

        val fallbackInfo = LrcLibApi.findBestTrackInfo(
            trackName = effectiveInput.title,
            artistName = effectiveInput.artist,
            albumName = effectiveInput.album,
            durationSeconds = song.durationMs / 1000,
        ) ?: searchLrcLibIdentity(
            title = effectiveInput.title,
            durationSeconds = song.durationMs / 1000,
        )
        val enrichedInput = effectiveInput.mergedWith(fallbackInfo)

        val primaryOutcome = MusicBrainzApi.searchRecordings(
            title = enrichedInput.title,
            artist = enrichedInput.artist,
            album = enrichedInput.album,
        )
        val primaryCandidates = when (primaryOutcome) {
            is MusicBrainzApi.SearchOutcome.Found -> primaryOutcome.candidates
            MusicBrainzApi.SearchOutcome.NoMatch -> emptyList()
            MusicBrainzApi.SearchOutcome.Unavailable -> emptyList()
        }

        val retriedCandidates = buildList {
            val shouldRetryWithFallbackIdentity =
                fallbackInfo != null &&
                    fallbackInfo.matchesSongContext(enrichedInput) &&
                    (
                        primaryCandidates.isEmpty() ||
                            fallbackInfo.hasUsefulArtistFor(effectiveInput) ||
                            effectiveInput.album.isUnknownAlbum()
                        )
            if (shouldRetryWithFallbackIdentity) {
                when (
                    val retried = MusicBrainzApi.searchRecordings(
                        title = enrichedInput.title,
                        artist = enrichedInput.artist,
                        album = enrichedInput.album,
                    )
                ) {
                    is MusicBrainzApi.SearchOutcome.Found -> addAll(retried.candidates)
                    else -> Unit
                }
            }
            if (primaryCandidates.isEmpty() || enrichedInput.artist.isUnknownArtist()) {
                when (
                    val titleOnly = MusicBrainzApi.searchRecordings(
                        title = enrichedInput.title,
                        artist = "",
                        album = enrichedInput.album,
                    )
                ) {
                    is MusicBrainzApi.SearchOutcome.Found -> addAll(titleOnly.candidates)
                    else -> Unit
                }
            }
        }

        val bestMusicBrainzMatch = pickBestMusicBrainzMatch(
            candidates = (primaryCandidates + retriedCandidates).distinctBy { it.recordingId },
            input = enrichedInput,
            durationMs = song.durationMs,
        )
        val resolvedInput = bestMusicBrainzMatch
            ?.toMatchInput(fallbackInfo, enrichedInput)
            ?: fallbackInfo?.toMatchInput(enrichedInput)
            ?: enrichedInput

        val bestGeniusMatch = if (
            bestMusicBrainzMatch == null ||
            bestMusicBrainzMatch.details?.artworkUrl.isNullOrBlank() ||
            bestMusicBrainzMatch.details?.artist.isUnknownArtist()
        ) {
            pickBestGeniusMatch(
                candidates = searchGeniusCandidates(resolvedInput, fallbackInfo),
                input = resolvedInput,
            )
        } else {
            null
        }
        val fallbackArtwork = resolveArtworkFallback(
            input = resolvedInput,
            bestGeniusMatch = bestGeniusMatch,
        )

        val resolvedRecord = when {
            bestMusicBrainzMatch != null -> {
                val details = bestMusicBrainzMatch.details
                SongMetadata(
                    songId = song.id,
                    geniusUrl = details?.url ?: bestGeniusMatch?.details?.url ?: bestGeniusMatch?.hit?.url,
                    resolvedTitle = details?.title
                        ?: bestMusicBrainzMatch.hit.title.takeIf { it.isNotBlank() }
                        ?: fallbackInfo?.title?.takeIf { it.isNotBlank() }
                        ?: resolvedInput.title,
                    resolvedArtist = details?.artist
                        ?: bestMusicBrainzMatch.hit.artist
                        ?: fallbackInfo?.artist?.takeIf { !it.isNullOrBlank() }
                        ?: resolvedInput.artist,
                    resolvedAlbum = details?.album
                        ?: fallbackInfo?.album?.takeIf { !it.isNullOrBlank() }
                        ?: resolvedInput.album.takeIf { it.isNotBlank() },
                    artworkUrl = details?.artworkUrl ?: fallbackArtwork,
                    releaseDate = details?.releaseDate ?: bestGeniusMatch?.details?.releaseDate,
                    overrideTitle = overrideToPersist?.title,
                    overrideArtist = overrideToPersist?.artist,
                    overrideAlbum = overrideToPersist?.album,
                    overrideAppliedAt = if (overrideToPersist != null) System.currentTimeMillis() else 0,
                )
            }

            bestGeniusMatch != null -> {
                val details = bestGeniusMatch.details
                SongMetadata(
                    songId = song.id,
                    geniusId = bestGeniusMatch.hit.id,
                    geniusUrl = details?.url ?: bestGeniusMatch.hit.url,
                    resolvedTitle = details?.title ?: bestGeniusMatch.hit.title ?: resolvedInput.title,
                    resolvedArtist = details?.artist
                        ?: bestGeniusMatch.hit.artist
                        ?: fallbackInfo?.artist?.takeIf { !it.isNullOrBlank() }
                        ?: resolvedInput.artist,
                    resolvedAlbum = details?.album
                        ?: fallbackInfo?.album?.takeIf { !it.isNullOrBlank() }
                        ?: resolvedInput.album.takeIf { it.isNotBlank() },
                    artworkUrl = details?.artworkUrl ?: bestGeniusMatch.hit.artworkUrl ?: fallbackArtwork,
                    releaseDate = details?.releaseDate,
                    overrideTitle = overrideToPersist?.title,
                    overrideArtist = overrideToPersist?.artist,
                    overrideAlbum = overrideToPersist?.album,
                    overrideAppliedAt = if (overrideToPersist != null) System.currentTimeMillis() else 0,
                )
            }

            fallbackInfo != null && fallbackInfo.matchesSongContext(enrichedInput) -> {
                SongMetadata(
                    songId = song.id,
                    resolvedTitle = fallbackInfo.title.takeIf { it.isNotBlank() },
                    resolvedArtist = fallbackInfo.artist.takeIf { !it.isNullOrBlank() },
                    resolvedAlbum = fallbackInfo.album.takeIf { !it.isNullOrBlank() },
                    artworkUrl = fallbackArtwork,
                    overrideTitle = overrideToPersist?.title,
                    overrideArtist = overrideToPersist?.artist,
                    overrideAlbum = overrideToPersist?.album,
                    overrideAppliedAt = if (overrideToPersist != null) System.currentTimeMillis() else 0,
                )
            }

            overrideToPersist != null -> {
                SongMetadata(
                    songId = song.id,
                    resolvedTitle = overrideToPersist.title.takeIf { it.isNotBlank() },
                    resolvedArtist = overrideToPersist.artist.takeIf { it.isNotBlank() },
                    resolvedAlbum = overrideToPersist.album.takeIf { it.isNotBlank() },
                    artworkUrl = fallbackArtwork,
                    overrideTitle = overrideToPersist.title,
                    overrideArtist = overrideToPersist.artist,
                    overrideAlbum = overrideToPersist.album,
                    overrideAppliedAt = System.currentTimeMillis(),
                )
            }

            primaryOutcome == MusicBrainzApi.SearchOutcome.Unavailable -> SongMetadata(songId = song.id)
            else -> SongMetadata(songId = song.id)
        }

        val finalRecord = resolvedRecord
            .preserveUsefulExisting(existing)
            .withEnrichmentState(existing, now)
        metadataDao.upsert(finalRecord)
        return finalRecord
    }

    suspend fun refresh(song: Song): SongMetadata {
        val existing = metadataDao.get(song.id)
        return resolve(
            song = song,
            input = existing?.overrideInput() ?: MatchInput(song.title, song.artist, song.album),
            persistOverride = existing?.hasOverride() == true,
            force = true,
        )
    }

    suspend fun correctMatch(song: Song, title: String, artist: String, album: String): SongMetadata {
        return resolve(
            song = song,
            input = MatchInput(title, artist, album),
            persistOverride = true,
            force = true,
        )
    }

    suspend fun needsBackgroundEnrichment(song: Song): Boolean {
        val cached = metadataDao.get(song.id) ?: return true
        if (!cached.hasResolvedIdentity()) return true
        if (!cached.hasResolvedArtwork()) {
            val attemptedAt = cached.artworkAttemptedAt
            return attemptedAt == 0L || System.currentTimeMillis() - attemptedAt >= ARTWORK_RETRY_WINDOW_MS
        }
        return false
    }

    private suspend fun searchGeniusCandidates(
        input: MatchInput,
        fallbackInfo: LrcLibApi.TrackInfo?,
    ): List<SearchHit> {
        val primaryHits = when (
            val outcome = GeniusApi.searchForMetadata(
                title = input.title,
                artist = input.artist,
                album = input.album,
            )
        ) {
            is GeniusSearchOutcome.Found -> outcome.hits
            else -> emptyList()
        }
        if (primaryHits.isNotEmpty()) return primaryHits

        if (fallbackInfo.hasUsefulArtistFor(input) && fallbackInfo.matchesSongContext(input)) {
            return when (
                val retried = GeniusApi.searchForMetadata(
                    title = input.title,
                    artist = fallbackInfo?.artist.orEmpty(),
                    album = fallbackInfo?.album.orEmpty().ifBlank { input.album },
                )
            ) {
                is GeniusSearchOutcome.Found -> retried.hits
                else -> emptyList()
            }
        }
        return emptyList()
    }

    private suspend fun pickBestMusicBrainzMatch(
        candidates: List<MusicBrainzApi.SearchCandidate>,
        input: MatchInput,
        durationMs: Long,
    ): MusicBrainzMatch? {
        if (candidates.isEmpty()) return null

        val shortlist = candidates
            .map { hit -> hit to preliminaryScore(hit, input, durationMs) }
            .filter { (_, score) -> score >= minimumMusicBrainzShortlistScore(input) }
            .sortedByDescending { (_, score) -> score }
            .take(if (input.artist.isKnownArtist()) 4 else 8)
            .map { it.first }

        if (shortlist.isEmpty()) return null

        val scored = shortlist.map { hit ->
            val details = MusicBrainzApi.getRecordingDetails(hit.recordingId, preferredAlbum = input.album)
            MusicBrainzMatch(
                hit = hit,
                details = details,
                score = finalScore(hit, details, input, durationMs),
            )
        }

        val minimumScore = minimumMusicBrainzAcceptScore(input)
        return scored
            .filter { it.score >= minimumScore }
            .maxByOrNull { it.score }
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

    private fun preliminaryScore(
        hit: MusicBrainzApi.SearchCandidate,
        input: MatchInput,
        durationMs: Long,
    ): Int {
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

        val durationScore = durationScore(
            actualMs = hit.lengthMs,
            expectedMs = durationMs,
        )

        return titleScore + artistScore + durationScore
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
        hit: MusicBrainzApi.SearchCandidate,
        details: MusicBrainzApi.RecordingDetails?,
        input: MatchInput,
        durationMs: Long,
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

        val artScore = if (!details?.artworkUrl.isNullOrBlank()) 12 else 0
        val durationScore = durationScore(
            actualMs = hit.lengthMs,
            expectedMs = durationMs,
        )
        return titleScore + artistScore + albumScore + artScore + durationScore
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
        if (!cached.hasResolvedIdentity()) {
            return true
        }

        val noResolvedInfo = cached.geniusId == null &&
            cached.geniusUrl.isNullOrBlank() &&
            cached.resolvedTitle.isNullOrBlank() &&
            cached.resolvedArtist.isNullOrBlank() &&
            cached.resolvedAlbum.isNullOrBlank() &&
            cached.artworkUrl.isNullOrBlank()
        if (noResolvedInfo) {
            return true
        }

        val hasManualOverrideWithoutArtwork = cached.hasOverride() &&
            cached.artworkUrl.isNullOrBlank() &&
            cached.resolvedTitle.cleanKey() == input.title.cleanKey() &&
            cached.resolvedArtist.cleanKey() == input.artist.cleanKey()
        if (hasManualOverrideWithoutArtwork) return true

        val missingArtistButMatched = cached.resolvedArtist.isNullOrBlank() &&
            cached.resolvedTitle.cleanKey() == input.title.cleanKey()
        if (missingArtistButMatched) {
            return true
        }

        val missingArtworkButMatched = cached.artworkUrl.isNullOrBlank() &&
            cached.resolvedTitle.cleanKey() == input.title.cleanKey() &&
            (
                input.artist.isUnknownArtist() ||
                    cached.resolvedArtist.cleanKey() == input.artist.cleanKey()
                ) &&
            (
                cached.artworkAttemptedAt == 0L ||
                    System.currentTimeMillis() - cached.artworkAttemptedAt >= ARTWORK_RETRY_WINDOW_MS
                )
        return missingArtworkButMatched
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

    private fun durationScore(actualMs: Long?, expectedMs: Long): Int {
        if (actualMs == null || actualMs <= 0L || expectedMs <= 0L) return 0
        val diff = abs(actualMs - expectedMs)
        return when {
            diff <= 2_000L -> 18
            diff <= 5_000L -> 14
            diff <= 10_000L -> 8
            diff <= 15_000L -> 4
            else -> 0
        }
    }

    private fun minimumMusicBrainzShortlistScore(input: MatchInput): Int = when {
        input.artist.isKnownArtist() -> 56
        input.album.isKnownAlbum() -> 48
        else -> 30
    }

    private fun minimumMusicBrainzAcceptScore(input: MatchInput): Int = when {
        input.artist.isKnownArtist() && input.album.isKnownAlbum() -> 82
        input.artist.isKnownArtist() -> 68
        input.album.isKnownAlbum() -> 60
        else -> 52
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

    private fun SongMetadata.hasResolvedIdentity(): Boolean =
        identityResolvedAt > 0L ||
            !resolvedTitle.isNullOrBlank() ||
            resolvedArtist.isKnownArtist() ||
            resolvedAlbum.isKnownAlbum()

    private fun SongMetadata.hasResolvedArtwork(): Boolean =
        artworkResolvedAt > 0L || !artworkUrl.isNullOrBlank()

    private fun SongMetadata.hasAnyResolvedFields(): Boolean =
        geniusId != null ||
            !geniusUrl.isNullOrBlank() ||
            !resolvedTitle.isNullOrBlank() ||
            !resolvedArtist.isNullOrBlank() ||
            !resolvedAlbum.isNullOrBlank() ||
            !artworkUrl.isNullOrBlank() ||
            !releaseDate.isNullOrBlank()

    private fun SongMetadata.preserveUsefulExisting(existing: SongMetadata?): SongMetadata {
        if (existing == null) return this
        if (!hasAnyResolvedFields()) return existing.copy(
            overrideTitle = overrideTitle ?: existing.overrideTitle,
            overrideArtist = overrideArtist ?: existing.overrideArtist,
            overrideAlbum = overrideAlbum ?: existing.overrideAlbum,
            overrideAppliedAt = maxOf(overrideAppliedAt, existing.overrideAppliedAt),
        )
        val sameIdentity = sameResolvedIdentityAs(existing)
        return if (sameIdentity) {
            copy(
                geniusId = geniusId ?: existing.geniusId,
                geniusUrl = geniusUrl ?: existing.geniusUrl,
                resolvedTitle = resolvedTitle ?: existing.resolvedTitle,
                resolvedArtist = resolvedArtist ?: existing.resolvedArtist,
                resolvedAlbum = resolvedAlbum ?: existing.resolvedAlbum,
                artworkUrl = artworkUrl ?: existing.artworkUrl,
                releaseDate = releaseDate ?: existing.releaseDate,
                overrideTitle = overrideTitle ?: existing.overrideTitle,
                overrideArtist = overrideArtist ?: existing.overrideArtist,
                overrideAlbum = overrideAlbum ?: existing.overrideAlbum,
                overrideAppliedAt = maxOf(overrideAppliedAt, existing.overrideAppliedAt),
            )
        } else {
            copy(
                overrideTitle = overrideTitle ?: existing.overrideTitle,
                overrideArtist = overrideArtist ?: existing.overrideArtist,
                overrideAlbum = overrideAlbum ?: existing.overrideAlbum,
                overrideAppliedAt = maxOf(overrideAppliedAt, existing.overrideAppliedAt),
            )
        }
    }

    private fun SongMetadata.sameResolvedIdentityAs(other: SongMetadata): Boolean {
        val thisTitle = resolvedTitle.cleanKey()
        val otherTitle = other.resolvedTitle.cleanKey()
        val thisArtist = resolvedArtist.cleanKey()
        val otherArtist = other.resolvedArtist.cleanKey()
        return thisTitle.isNotBlank() &&
            otherTitle.isNotBlank() &&
            thisTitle == otherTitle &&
            (
                thisArtist.isBlank() ||
                    otherArtist.isBlank() ||
                    thisArtist == otherArtist
                )
    }

    private fun SongMetadata.withEnrichmentState(
        existing: SongMetadata?,
        attemptedAt: Long,
    ): SongMetadata {
        val identityAt = when {
            hasResolvedIdentity() -> existing?.identityResolvedAt?.takeIf { it > 0L } ?: attemptedAt
            else -> existing?.identityResolvedAt ?: 0L
        }
        val artResolvedAt = when {
            hasResolvedArtwork() -> existing?.artworkResolvedAt?.takeIf { it > 0L } ?: attemptedAt
            else -> existing?.artworkResolvedAt ?: 0L
        }
        return copy(
            identityResolvedAt = identityAt,
            artworkAttemptedAt = attemptedAt,
            artworkResolvedAt = artResolvedAt,
            lyricsAttemptedAt = existing?.lyricsAttemptedAt ?: lyricsAttemptedAt,
            lyricsResolvedAt = existing?.lyricsResolvedAt ?: lyricsResolvedAt,
            fetchedAt = attemptedAt,
        )
    }

    private fun MatchInput.normalizedFor(song: Song): MatchInput =
        MatchInput(
            title = title.trim().ifBlank { song.title },
            artist = artist.trim().ifBlank { song.artist },
            album = album.trim().ifBlank { song.album },
        )

    private fun MatchInput.mergedWith(fallback: LrcLibApi.TrackInfo?): MatchInput =
        MatchInput(
            title = fallback?.title?.takeIf { it.isNotBlank() } ?: title,
            artist = if (artist.isKnownArtist()) artist else fallback?.artist.orEmpty().ifBlank { artist },
            album = if (album.isKnownAlbum()) album else fallback?.album.orEmpty().ifBlank { album },
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

    private suspend fun searchLrcLibIdentity(
        title: String,
        durationSeconds: Long,
    ): LrcLibApi.TrackInfo? {
        val candidates = LrcLibApi.searchCandidates(
            trackName = title,
            durationSeconds = durationSeconds,
        )
        val exactDurationMatch = candidates.firstOrNull { candidate ->
            candidate.title.cleanKey() == title.cleanKey() &&
                candidate.artist.isKnownArtist() &&
                (
                    candidate.durationSeconds == null ||
                        abs(candidate.durationSeconds - durationSeconds) <= 12
                    )
        }
        if (exactDurationMatch != null) return exactDurationMatch

        val broaderCandidates = LrcLibApi.searchCandidates(trackName = title)
        return broaderCandidates.firstOrNull { candidate ->
            candidate.title.cleanKey() == title.cleanKey() && candidate.artist.isKnownArtist()
        } ?: candidates.firstOrNull() ?: broaderCandidates.firstOrNull()
    }

    private suspend fun resolveArtworkFallback(
        input: MatchInput,
        bestGeniusMatch: GeniusMatch?,
    ): String? {
        if (input.artist.isKnownArtist() && input.album.isKnownAlbum()) {
            TheAudioDbApi.findAlbumArt(input.artist, input.album)
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
        }
        return bestGeniusMatch?.details?.artworkUrl
            ?: bestGeniusMatch?.hit?.artworkUrl
    }

    private fun LrcLibApi.TrackInfo.toMatchInput(fallbackInput: MatchInput): MatchInput =
        MatchInput(
            title = title.takeIf { it.isNotBlank() } ?: fallbackInput.title,
            artist = artist?.takeIf { it.isNotBlank() } ?: fallbackInput.artist,
            album = album?.takeIf { it.isNotBlank() } ?: fallbackInput.album,
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

    private data class MusicBrainzMatch(
        val hit: MusicBrainzApi.SearchCandidate,
        val details: MusicBrainzApi.RecordingDetails?,
        val score: Int,
    )

    private fun MusicBrainzMatch.toMatchInput(
        fallback: LrcLibApi.TrackInfo?,
        fallbackInput: MatchInput,
    ): MatchInput = MatchInput(
        title = details?.title ?: hit.title.takeIf { it.isNotBlank() } ?: fallback?.title ?: fallbackInput.title,
        artist = details?.artist ?: hit.artist ?: fallback?.artist ?: fallbackInput.artist,
        album = details?.album ?: fallback?.album ?: fallbackInput.album,
    )

    private companion object {
        const val ARTWORK_RETRY_WINDOW_MS = 30 * 60 * 1000L
    }
}
