package com.pulse.music.data

import com.pulse.music.network.GeniusApi
import com.pulse.music.network.GeniusSearchOutcome
import com.pulse.music.network.LrcLibApi
import com.pulse.music.network.MusicBrainzApi
import com.pulse.music.network.SearchHit
import com.pulse.music.network.SongDetails
import com.pulse.music.network.TheAudioDbApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Mediates between the UI and remote metadata providers, backed by Room.
 *
 * LRCLIB is the primary identity source when the user has not manually fixed a
 * track. MusicBrainz + Cover Art Archive is the primary artwork path after the
 * identity is resolved, then TheAudioDB, with Genius as the final metadata /
 * artwork fallback.
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

    fun observeAll(): Flow<List<SongMetadata>> = metadataDao.observeAll()

    // Plain lookups for the same song (background queue, Now Playing, player
    // session) share one in-flight request instead of each hitting every provider.
    private val inFlightLookups = ConcurrentHashMap<Long, Deferred<SongMetadata>>()
    private val lookupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
        if (force || persistOverride || !input.matches(song)) {
            return resolveNow(song, input, persistOverride, force)
        }
        val lookup = inFlightLookups.computeIfAbsent(song.id) {
            lookupScope.async(start = CoroutineStart.LAZY) {
                try {
                    resolveNow(song, input, persistOverride = false, force = false)
                } finally {
                    inFlightLookups.remove(song.id)
                }
            }
        }
        return lookup.await()
    }

    private suspend fun resolveNow(
        song: Song,
        input: MatchInput,
        persistOverride: Boolean,
        force: Boolean,
    ): SongMetadata = coroutineScope {
        val existing = metadataDao.get(song.id)
        val now = System.currentTimeMillis()
        val storedOverride = existing?.overrideInput()
        val requestedInput = input.normalizedFor(song).input
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
            return@coroutineScope existing
        }

        val candidateResolution = chooseBestInputCandidate(
            song = song,
            requestedInput = effectiveInput,
            allowAmbiguity = overrideToPersist == null,
        )
        val chosenInput = candidateResolution.input

        val fallbackInfo = if (overrideToPersist == null) {
            candidateResolution.fallbackInfo ?: LrcLibApi.findBestTrackInfo(
                trackName = chosenInput.title,
                artistName = chosenInput.artist,
                albumName = chosenInput.album,
                durationSeconds = song.durationMs / 1000,
            ) ?: searchLrcLibIdentity(
                title = chosenInput.title,
                durationSeconds = song.durationMs / 1000,
            )
        } else {
            null
        }
        val resolvedIdentityInput = fallbackInfo?.toMatchInput(chosenInput) ?: chosenInput

        val searchInputs = buildMetadataSearchInputs(
            input = resolvedIdentityInput,
            fallbackInfo = fallbackInfo,
            alternateInputs = candidateResolution.searchSeeds,
        )
        currentCoroutineContext().ensureActive()
        val musicBrainzOutcomes = searchInputs.map { candidateInput ->
            async {
                MusicBrainzApi.searchRecordings(
                    title = candidateInput.title,
                    artist = candidateInput.artist,
                    album = candidateInput.album,
                )
            }
        }.awaitAll()
        val primaryOutcome = musicBrainzOutcomes.firstOrNull {
            it is MusicBrainzApi.SearchOutcome.Found || it == MusicBrainzApi.SearchOutcome.Unavailable
        } ?: MusicBrainzApi.SearchOutcome.NoMatch
        val primaryCandidates = musicBrainzOutcomes.flatMap { outcome ->
            when (outcome) {
                is MusicBrainzApi.SearchOutcome.Found -> outcome.candidates
                else -> emptyList()
            }
        }

        val bestMusicBrainzMatch = pickBestMusicBrainzMatch(
            candidates = primaryCandidates.distinctBy { it.recordingId },
            input = resolvedIdentityInput,
            durationMs = song.durationMs,
        )
        currentCoroutineContext().ensureActive()
        val bestGeniusMatch = if (
            bestMusicBrainzMatch == null ||
            bestMusicBrainzMatch.details?.artworkUrl.isNullOrBlank() ||
            bestMusicBrainzMatch.details?.artist.isUnknownArtist()
        ) {
            pickBestGeniusMatch(
                candidates = searchGeniusCandidates(
                    input = resolvedIdentityInput,
                    fallbackInfo = fallbackInfo,
                    alternateInputs = candidateResolution.searchSeeds,
                ),
                input = resolvedIdentityInput,
            )
        } else {
            null
        }
        val providerResolvedInput = when {
            overrideToPersist != null -> overrideToPersist
            fallbackInfo != null -> fallbackInfo.toMatchInput(effectiveInput)
            bestMusicBrainzMatch != null -> bestMusicBrainzMatch.toMatchInput(fallbackInfo, resolvedIdentityInput)
            bestGeniusMatch != null -> bestGeniusMatch.toMatchInput(resolvedIdentityInput)
            else -> resolvedIdentityInput
        }
        val fallbackArtwork = resolveArtworkFallback(
            input = providerResolvedInput,
            fallbackInfo = fallbackInfo,
            bestMusicBrainzMatch = bestMusicBrainzMatch,
            bestGeniusMatch = bestGeniusMatch,
        )

        val localIdentityImproved = requestedInput.isMeaningfulImprovementOver(song)
        val resolvedRecord = when {
            bestMusicBrainzMatch != null -> {
                val details = bestMusicBrainzMatch.details
                SongMetadata(
                    songId = song.id,
                    geniusUrl = details?.url ?: bestGeniusMatch?.details?.url ?: bestGeniusMatch?.hit?.url,
                    resolvedTitle = providerResolvedInput.title.takeIf { it.isNotBlank() },
                    resolvedArtist = providerResolvedInput.artist.takeIf { it.isNotBlank() },
                    resolvedAlbum = providerResolvedInput.album.takeIf { it.isNotBlank() },
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
                    resolvedTitle = providerResolvedInput.title.takeIf { it.isNotBlank() },
                    resolvedArtist = providerResolvedInput.artist.takeIf { it.isNotBlank() },
                    resolvedAlbum = providerResolvedInput.album.takeIf { it.isNotBlank() },
                    artworkUrl = details?.artworkUrl ?: bestGeniusMatch.hit.artworkUrl ?: fallbackArtwork,
                    releaseDate = details?.releaseDate,
                    overrideTitle = overrideToPersist?.title,
                    overrideArtist = overrideToPersist?.artist,
                    overrideAlbum = overrideToPersist?.album,
                    overrideAppliedAt = if (overrideToPersist != null) System.currentTimeMillis() else 0,
                )
            }

            fallbackInfo != null -> {
                SongMetadata(
                    songId = song.id,
                    resolvedTitle = providerResolvedInput.title.takeIf { it.isNotBlank() },
                    resolvedArtist = providerResolvedInput.artist.takeIf { it.isNotBlank() },
                    resolvedAlbum = providerResolvedInput.album.takeIf { it.isNotBlank() },
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

            primaryOutcome == MusicBrainzApi.SearchOutcome.Unavailable -> SongMetadata(
                songId = song.id,
                resolvedTitle = requestedInput.title.takeIf { localIdentityImproved && it.isNotBlank() },
                resolvedArtist = requestedInput.artist.takeIf { localIdentityImproved && it.isKnownArtist() },
                resolvedAlbum = requestedInput.album.takeIf { localIdentityImproved && it.isKnownAlbum() },
                overrideTitle = overrideToPersist?.title,
                overrideArtist = overrideToPersist?.artist,
                overrideAlbum = overrideToPersist?.album,
                overrideAppliedAt = if (overrideToPersist != null) System.currentTimeMillis() else 0,
            )
            else -> SongMetadata(
                songId = song.id,
                resolvedTitle = requestedInput.title.takeIf { localIdentityImproved && it.isNotBlank() },
                resolvedArtist = requestedInput.artist.takeIf { localIdentityImproved && it.isKnownArtist() },
                resolvedAlbum = requestedInput.album.takeIf { localIdentityImproved && it.isKnownAlbum() },
                overrideTitle = overrideToPersist?.title,
                overrideArtist = overrideToPersist?.artist,
                overrideAlbum = overrideToPersist?.album,
                overrideAppliedAt = if (overrideToPersist != null) System.currentTimeMillis() else 0,
            )
        }

        val finalRecord = resolvedRecord
            .preserveUsefulExisting(existing)
            .withEnrichmentState(existing, now)
        metadataDao.upsert(finalRecord)
        return@coroutineScope finalRecord
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
        alternateInputs: List<MatchInput>,
    ): List<SearchHit> = coroutineScope {
        val searchInputs = buildMetadataSearchInputs(input, fallbackInfo, alternateInputs)
        val outcomes = searchInputs.map { candidateInput ->
            async {
                GeniusApi.searchForMetadata(
                    title = candidateInput.title,
                    artist = candidateInput.artist,
                    album = candidateInput.album,
                )
            }
        }.awaitAll()
        return@coroutineScope outcomes
            .flatMap { outcome ->
                when (outcome) {
                    is GeniusSearchOutcome.Found -> outcome.hits
                    else -> emptyList()
                }
            }
            .distinctBy { "${it.id}|${it.title.cleanKey()}|${it.artist.cleanKey()}" }
    }

    private suspend fun pickBestMusicBrainzMatch(
        candidates: List<MusicBrainzApi.SearchCandidate>,
        input: MatchInput,
        durationMs: Long,
    ): MusicBrainzMatch? = coroutineScope {
        if (candidates.isEmpty()) return@coroutineScope null

        val shortlist = candidates
            .map { hit -> hit to preliminaryScore(hit, input, durationMs) }
            .filter { (_, score) -> score >= minimumMusicBrainzShortlistScore(input) }
            .sortedByDescending { (_, score) -> score }
            .take(if (input.artist.isKnownArtist()) 4 else 8)
            .map { it.first }

        if (shortlist.isEmpty()) return@coroutineScope null

        val scored = shortlist.map { hit ->
            async {
                currentCoroutineContext().ensureActive()
                val details = MusicBrainzApi.getRecordingDetails(hit.recordingId, preferredAlbum = input.album)
                MusicBrainzMatch(
                    hit = hit,
                    details = details,
                    score = finalScore(hit, details, input, durationMs),
                )
            }
        }.awaitAll()

        val minimumScore = minimumMusicBrainzAcceptScore(input)
        return@coroutineScope scored
            .filter { it.score >= minimumScore }
            .maxByOrNull { it.score }
    }

    private suspend fun pickBestGeniusMatch(
        candidates: List<SearchHit>,
        input: MatchInput,
    ): GeniusMatch? = coroutineScope {
        if (candidates.isEmpty()) return@coroutineScope null

        val shortlist = candidates
            .map { hit -> hit to preliminaryScore(hit, input) }
            .filter { (_, score) -> score >= minimumGeniusShortlistScore(input) }
            .sortedByDescending { (_, score) -> score }
            .take(5)
            .map { it.first }

        if (shortlist.isEmpty()) return@coroutineScope null

        val scored = shortlist.map { hit ->
            async {
                currentCoroutineContext().ensureActive()
                val details = GeniusApi.getSong(hit.id)
                GeniusMatch(
                    hit = hit,
                    details = details,
                    score = finalScore(hit, details, input),
                )
            }
        }.awaitAll()

        val minimumScore = minimumGeniusAcceptScore(input)
        return@coroutineScope scored
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

        val missingArtistButMatched = cached.resolvedArtist.isUnknownArtist() &&
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

    private fun minimumGeniusShortlistScore(input: MatchInput): Int = when {
        input.artist.isKnownArtist() -> 56
        input.album.isKnownAlbum() -> 48
        else -> 40
    }

    private fun minimumGeniusAcceptScore(input: MatchInput): Int = when {
        input.artist.isKnownArtist() && input.album.isKnownAlbum() -> 84
        input.artist.isKnownArtist() -> 68
        input.album.isKnownAlbum() -> 58
        else -> 48
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
        geniusId != null ||
            !geniusUrl.isNullOrBlank() ||
            (
                !resolvedTitle.isNullOrBlank() &&
                    resolvedArtist.isKnownArtist()
                ) ||
            (
                resolvedArtist.isKnownArtist() &&
                    resolvedAlbum.isKnownAlbum()
                )

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

    private suspend fun chooseBestInputCandidate(
        song: Song,
        requestedInput: MatchInput,
        allowAmbiguity: Boolean,
    ): CandidateResolution {
        val candidates = if (allowAmbiguity) {
            requestedInput.candidateInputsFor(song)
        } else {
            listOf(
                MatchCandidate(
                    input = requestedInput,
                    localBias = 0,
                    preserveAsSearchSeed = false,
                )
            )
        }
        if (candidates.size <= 1) {
            return CandidateResolution(
                input = candidates.first().input,
                fallbackInfo = null,
                searchSeeds = candidates.filter { it.preserveAsSearchSeed }.map { it.input },
            )
        }

        val durationSeconds = song.durationMs / 1000
        var bestResolution = CandidateResolution(
            input = candidates.first().input,
            fallbackInfo = null,
            searchSeeds = candidates.filter { it.preserveAsSearchSeed }.map { it.input },
        )
        var bestScore = Int.MIN_VALUE
        for (candidate in candidates.take(MAX_IDENTITY_CANDIDATES)) {
            currentCoroutineContext().ensureActive()
            val fallbackInfo = LrcLibApi.findBestTrackInfo(
                trackName = candidate.input.title,
                artistName = candidate.input.artist,
                albumName = candidate.input.album,
                durationSeconds = durationSeconds,
            )
            val score = candidate.scoreAgainstFallback(fallbackInfo)
            if (score > bestScore) {
                bestScore = score
                bestResolution = CandidateResolution(
                    input = candidate.input,
                    fallbackInfo = fallbackInfo,
                    searchSeeds = candidates
                        .filter { it.preserveAsSearchSeed }
                        .map { it.input }
                        .filterNot { it.sameIdentityAs(candidate.input) },
                )
            }
            if (score >= STRONG_IDENTITY_SCORE) break
        }
        return bestResolution
    }

    private fun MatchInput.normalizedFor(song: Song): NormalizedMatchInput =
        LocalMetadataParser.analyzeSong(
            rawTitle = title.trim().ifBlank { song.title },
            rawArtist = LocalMetadataParser.cleanArtist(artist).ifBlank { song.artist },
            rawAlbum = album.trim().ifBlank { song.album },
            displayName = song.dataPath.substringAfterLast('/').substringAfterLast('\\'),
        ).let { parsed ->
            NormalizedMatchInput(
                input = MatchInput(
                    title = parsed.identity.title,
                    artist = parsed.identity.artist,
                    album = parsed.identity.album,
                ),
                parserAnalysis = parsed,
            )
        }

    internal fun MatchInput.candidateInputsFor(song: Song): List<MatchCandidate> {
        val normalizedTitle = title.trim().ifBlank { song.title }
        val normalizedArtist = LocalMetadataParser.cleanArtist(artist).ifBlank { song.artist }
        val displayName = song.dataPath.substringAfterLast('/').substringAfterLast('\\')
        val analysis = LocalMetadataParser.analyzeSong(
            rawTitle = normalizedTitle,
            rawArtist = normalizedArtist,
            rawAlbum = album,
            displayName = displayName,
        )
        val rankedCandidates = analysis.rankedCandidates
        val filenameCandidates = LocalMetadataParser.rankedIdentityCandidates(
            rawTitle = "",
            rawArtist = "",
            displayName = displayName,
        ).filter { it.fromDash }

        return buildList {
            add(
                MatchCandidate(
                    input = analysis.inputWithAlbum(album),
                    localBias = if (analysis.dashedAssessment?.ambiguous == true) AMBIGUOUS_LOCAL_IDENTITY_PENALTY else 0,
                    preserveAsSearchSeed = false,
                )
            )
            rankedCandidates.forEach { candidate ->
                add(
                    MatchCandidate(
                        input = MatchInput(
                            title = candidate.title,
                            artist = candidate.artist,
                            album = album,
                        ).normalizedFor(song).input,
                        localBias = if (candidate.fromDash && analysis.dashedAssessment?.ambiguous == true) 0 else 4,
                        preserveAsSearchSeed = candidate.fromDash && analysis.dashedAssessment?.ambiguous == true,
                    )
                )
            }
            filenameCandidates.forEach { candidate ->
                add(
                    MatchCandidate(
                        input = MatchInput(
                            title = candidate.title,
                            artist = candidate.artist,
                            album = album,
                        ).normalizedFor(song).input,
                        localBias = 0,
                        preserveAsSearchSeed = true,
                    )
                )
            }
        }
            .reversed()
            .distinctBy { "${it.input.title.cleanKey()}|${it.input.artist.cleanKey()}|${it.input.album.cleanKey()}" }
            .reversed()
    }

    internal fun repositorySearchInputsFor(
        input: MatchInput,
        fallbackInfo: LrcLibApi.TrackInfo?,
        alternateInputs: List<MatchInput> = emptyList(),
    ): List<MatchInput> = buildMetadataSearchInputs(input, fallbackInfo, alternateInputs)

    private fun MatchInput.matches(song: Song): Boolean =
        title == song.title && artist == song.artist && album == song.album

    private fun MatchInput.isMeaningfulImprovementOver(song: Song): Boolean {
        val rawTitle = song.title.cleanKey()
        val rawArtist = song.artist.cleanKey()
        val rawAlbum = song.album.cleanKey()
        return title.cleanKey() != rawTitle ||
            (
                artist.isKnownArtist() &&
                    (song.artist.isUnknownArtist() || artist.cleanKey() != rawArtist)
                ) ||
            (
                album.isKnownAlbum() &&
                    (song.album.isUnknownAlbum() || album.cleanKey() != rawAlbum)
                )
    }

    private fun LrcLibApi.TrackInfo?.hasUsefulArtistFor(input: MatchInput): Boolean =
        this != null &&
            !artist.isNullOrBlank() &&
            !artist.isUnknownArtist() &&
            (input.artist.isUnknownArtist() || !artist.equals(input.artist, ignoreCase = true))

    private suspend fun searchLrcLibIdentity(
        title: String,
        durationSeconds: Long,
    ): LrcLibApi.TrackInfo? {
        currentCoroutineContext().ensureActive()
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
        fallbackInfo: LrcLibApi.TrackInfo?,
        bestMusicBrainzMatch: MusicBrainzMatch?,
        bestGeniusMatch: GeniusMatch?,
    ): String? {
        bestMusicBrainzMatch?.details?.artworkUrl?.takeIf { it.isNotBlank() }?.let { return it }

        val artworkPairs = buildList {
            val artistAlbumSeeds = listOf(
                input.artist to input.album,
                (bestMusicBrainzMatch?.details?.artist ?: bestMusicBrainzMatch?.hit?.artist).orEmpty() to
                    bestMusicBrainzMatch?.details?.album.orEmpty(),
                fallbackInfo?.artist.orEmpty() to fallbackInfo?.album.orEmpty(),
                bestGeniusMatch?.details?.artist.orEmpty() to bestGeniusMatch?.details?.album.orEmpty(),
                bestGeniusMatch?.hit?.artist.orEmpty() to input.title,
                input.artist to input.title,
            )
            artistAlbumSeeds.forEach { (artist, album) ->
                LocalMetadataParser.artistVariants(artist).ifEmpty { listOf(artist) }
                    .forEach { variant -> add(variant to album) }
            }
        }
            .distinct()

        artworkPairs.take(MAX_ARTWORK_PAIRS).forEach { (artist, album) ->
            if (artist.isKnownArtist() && album.isKnownAlbum()) {
                TheAudioDbApi.findAlbumArt(artist, album)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { return it }
            }
        }

        return bestGeniusMatch?.details?.artworkUrl
            ?: bestGeniusMatch?.hit?.artworkUrl
    }

    private fun buildMetadataSearchInputs(
        input: MatchInput,
        fallbackInfo: LrcLibApi.TrackInfo?,
        alternateInputs: List<MatchInput> = emptyList(),
    ): List<MatchInput> {
        val titleCandidates = buildList {
            add(input.title)
            add(fallbackInfo?.title.orEmpty())
            alternateInputs.forEach { add(it.title) }
        }
            .map { LocalMetadataParser.cleanTitle(it, input.artist) }
            .filter { it.isNotBlank() }
            .distinctBy { it.cleanKey() }

        val artistCandidates = buildList {
            addAll(LocalMetadataParser.artistVariants(input.artist))
            addAll(LocalMetadataParser.artistVariants(fallbackInfo?.artist.orEmpty()))
            alternateInputs.forEach { addAll(LocalMetadataParser.artistVariants(it.artist)) }
            if (input.artist.isNotBlank()) add(input.artist)
            if (!fallbackInfo?.artist.isNullOrBlank()) add(fallbackInfo?.artist.orEmpty())
            alternateInputs.forEach { if (it.artist.isNotBlank()) add(it.artist) }
            add("")
        }
            .distinctBy { it.cleanKey() }

        val albumCandidates = buildList {
            add(input.album)
            add(fallbackInfo?.album.orEmpty())
            alternateInputs.forEach { add(it.album) }
            add("")
        }
            .filter { it.isBlank() || it.isKnownAlbum() }
            .distinctBy { it.cleanKey() }

        return buildList {
            add(input)
            alternateInputs.forEach { add(it) }
            fallbackInfo?.toMatchInput(input)?.let { add(it) }
            titleCandidates.forEach { title ->
                artistCandidates.forEach { artist ->
                    albumCandidates.forEach { album ->
                        add(MatchInput(title = title, artist = artist, album = album))
                    }
                }
            }
        }
            .distinctBy { "${it.title.cleanKey()}|${it.artist.cleanKey()}|${it.album.cleanKey()}" }
            .take(MAX_SEARCH_INPUTS)
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

    private data class CandidateResolution(
        val input: MatchInput,
        val fallbackInfo: LrcLibApi.TrackInfo?,
        val searchSeeds: List<MatchInput>,
    )

    internal data class MatchCandidate(
        val input: MatchInput,
        val localBias: Int,
        val preserveAsSearchSeed: Boolean,
    )

    private data class NormalizedMatchInput(
        val input: MatchInput,
        val parserAnalysis: LocalIdentityAnalysis,
    )

    private fun GeniusMatch.toMatchInput(fallbackInput: MatchInput): MatchInput = MatchInput(
        title = details?.title ?: hit.title.takeIf { it.isNotBlank() } ?: fallbackInput.title,
        artist = details?.artist ?: hit.artist ?: fallbackInput.artist,
        album = details?.album ?: fallbackInput.album,
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
        const val MAX_SEARCH_INPUTS = 6
        const val MAX_ARTWORK_PAIRS = 8
        const val MAX_IDENTITY_CANDIDATES = 3
        const val STRONG_IDENTITY_SCORE = 92
        const val AMBIGUOUS_LOCAL_IDENTITY_PENALTY = -16
    }

    private fun MatchCandidate.scoreAgainstFallback(fallbackInfo: LrcLibApi.TrackInfo?): Int {
        if (fallbackInfo == null) return 0
        var score = 0
        if (fallbackInfo.title.cleanKey() == input.title.cleanKey()) score += 44
        if (input.artist.isKnownArtist() && fallbackInfo.artist.cleanKey() == input.artist.cleanKey()) score += 34
        if (input.album.isKnownAlbum() && fallbackInfo.album.cleanKey() == input.album.cleanKey()) score += 16
        if (fallbackInfo.artist.isKnownArtist() && input.artist.isUnknownArtist()) score += 18
        if (fallbackInfo.plainLyrics?.isNotBlank() == true || fallbackInfo.syncedLyrics?.isNotBlank() == true) score += 8
        return score + localBias
    }

    private fun MatchInput.sameIdentityAs(other: MatchInput): Boolean =
        title.cleanKey() == other.title.cleanKey() &&
            artist.cleanKey() == other.artist.cleanKey() &&
            album.cleanKey() == other.album.cleanKey()

    private fun LocalIdentityAnalysis.inputWithAlbum(albumOverride: String): MatchInput = MatchInput(
        title = identity.title,
        artist = identity.artist,
        album = LocalMetadataParser.cleanAlbum(albumOverride).ifBlank { identity.album },
    )
}
