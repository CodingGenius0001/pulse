package com.pulse.music.data

import com.pulse.music.network.GeniusApi
import com.pulse.music.network.GeniusSearchOutcome
import com.pulse.music.network.LrcLibApi
import com.pulse.music.network.SongDetails
import kotlinx.coroutines.flow.Flow

/**
 * Mediates between the UI and remote metadata providers, backed by Room.
 *
 * Genius is still the only art source, but LRCLIB can recover artist/album
 * text for badly tagged files and give Genius a second, better-informed try.
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
    ): SongMetadata {
        metadataDao.get(song.id)?.let { cached ->
            if (input.matches(song) && !shouldRetry(song, cached)) return cached
            metadataDao.delete(song.id)
        }

        val primaryOutcome = GeniusApi.searchForMetadata(
            title = input.title,
            artist = input.artist,
            album = input.album,
        )
        val fallbackInfo = if (primaryOutcome is GeniusSearchOutcome.Found) {
            null
        } else {
            LrcLibApi.findBestTrackInfo(
                trackName = input.title,
                artistName = input.artist,
                albumName = input.album,
                durationSeconds = song.durationMs / 1000,
            )
        }

        val primaryHit = when (primaryOutcome) {
            is GeniusSearchOutcome.Found -> primaryOutcome.hit
            GeniusSearchOutcome.NoMatch -> null
            GeniusSearchOutcome.Unavailable -> null
        }

        val retriedHit = if (primaryHit == null && fallbackInfo.hasUsefulArtistFor(input) && fallbackInfo.matchesSongContext(input)) {
            when (val retried = GeniusApi.searchForMetadata(input.title, fallbackInfo?.artist.orEmpty(), input.album)) {
                is GeniusSearchOutcome.Found -> retried.hit
                else -> null
            }
        } else {
            null
        }

        val finalHit = retriedHit ?: primaryHit
        if (finalHit != null) {
            val details = GeniusApi.getSong(finalHit.id)
            if (!details.matchesAlbumContext(input.album) && !input.album.isUnknownAlbum()) {
                val fallbackOnly = fallbackInfo?.toMetadata(song.id)
                    ?: input.toTextOnlyMetadata(song.id)
                metadataDao.upsert(fallbackOnly)
                return fallbackOnly
            }
            val record = SongMetadata(
                songId = song.id,
                geniusId = finalHit.id,
                geniusUrl = details?.url ?: finalHit.url,
                resolvedTitle = details?.title ?: fallbackInfo?.title ?: finalHit.title ?: input.title,
                resolvedArtist = details?.artist ?: fallbackInfo?.artist ?: finalHit.artist ?: input.artist,
                resolvedAlbum = details?.album ?: fallbackInfo?.album ?: input.album.takeIf { it.isNotBlank() },
                artworkUrl = details?.artworkUrl ?: finalHit.artworkUrl,
                releaseDate = details?.releaseDate,
            )
            metadataDao.upsert(record)
            return record
        }

        if (fallbackInfo != null) {
            val record = fallbackInfo.toMetadata(song.id)
            metadataDao.upsert(record)
            return record
        }

        return when (primaryOutcome) {
            GeniusSearchOutcome.Unavailable -> {
                if (input.matches(song)) {
                    SongMetadata(songId = song.id)
                } else {
                    val textOnly = input.toTextOnlyMetadata(song.id)
                    metadataDao.upsert(textOnly)
                    textOnly
                }
            }
            else -> {
                val textOnly = input.toTextOnlyMetadata(song.id)
                metadataDao.upsert(textOnly)
                textOnly
            }
        }
    }

    suspend fun refresh(song: Song): SongMetadata {
        metadataDao.delete(song.id)
        return resolve(song)
    }

    private fun shouldRetry(song: Song, cached: SongMetadata): Boolean {
        val noResolvedInfo = cached.geniusId == null &&
            cached.geniusUrl.isNullOrBlank() &&
            cached.resolvedTitle.isNullOrBlank() &&
            cached.resolvedArtist.isNullOrBlank() &&
            cached.resolvedAlbum.isNullOrBlank() &&
            cached.artworkUrl.isNullOrBlank()
        if (!noResolvedInfo) return false
        return song.artist.isUnknownArtist()
    }

    suspend fun correctMatch(song: Song, title: String, artist: String, album: String): SongMetadata {
        metadataDao.delete(song.id)
        return resolve(
            song = song,
            input = MatchInput(
                title = title.trim().ifBlank { song.title },
                artist = artist.trim().ifBlank { song.artist },
                album = album.trim().ifBlank { song.album },
            ),
        )
    }

    private fun LrcLibApi.TrackInfo?.hasUsefulArtistFor(input: MatchInput): Boolean =
        this != null &&
            !artist.isNullOrBlank() &&
            !artist.isUnknownArtist() &&
            (input.artist.isUnknownArtist() || !artist.equals(input.artist, ignoreCase = true))

    private fun LrcLibApi.TrackInfo?.matchesSongContext(input: MatchInput): Boolean {
        if (this == null) return false

        val titleMatches = title.cleanKey() == input.title.cleanKey()
        if (!titleMatches) return false

        val albumTrusted = input.album.isUnknownAlbum() || album.cleanKey().isNotBlank() && album.cleanKey() == input.album.cleanKey()
        if (!albumTrusted) return false

        if (!input.artist.isUnknownArtist()) {
            return artist.cleanKey() == input.artist.cleanKey()
        }

        return !artist.isNullOrBlank() && !artist.isUnknownArtist()
    }

    private fun LrcLibApi.TrackInfo.toMetadata(songId: Long): SongMetadata =
        SongMetadata(
            songId = songId,
            resolvedTitle = title.takeIf { it.isNotBlank() },
            resolvedArtist = artist.takeIf { !it.isNullOrBlank() },
            resolvedAlbum = album.takeIf { !it.isNullOrBlank() },
        )

    private fun MatchInput.matches(song: Song): Boolean =
        title == song.title && artist == song.artist && album == song.album

    private fun MatchInput.toTextOnlyMetadata(songId: Long): SongMetadata =
        SongMetadata(
            songId = songId,
            resolvedTitle = title.takeIf { it.isNotBlank() },
            resolvedArtist = artist.takeIf { it.isNotBlank() },
            resolvedAlbum = album.takeIf { it.isNotBlank() },
        )

    private fun SongDetails?.matchesAlbumContext(expectedAlbum: String): Boolean {
        if (expectedAlbum.isUnknownAlbum()) return true
        val actual = this?.album.cleanKey()
        val expected = expectedAlbum.cleanKey()
        if (actual.isBlank() || expected.isBlank()) return true
        return actual == expected
    }

    private fun String?.isUnknownArtist(): Boolean =
        isNullOrBlank() ||
            equals("Unknown artist", ignoreCase = true) ||
            equals("<unknown>", ignoreCase = true)

    private fun String?.isUnknownAlbum(): Boolean =
        isNullOrBlank() ||
            equals("Unknown album", ignoreCase = true) ||
            equals("<unknown>", ignoreCase = true)

    private fun String?.cleanKey(): String =
        this.orEmpty()
            .lowercase()
            .replace(Regex("""\([^)]*\)|\[[^]]*]"""), " ")
            .replace(Regex("""[^a-z0-9 ]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
}
