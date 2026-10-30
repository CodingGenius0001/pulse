package com.pulse.music.data

import com.pulse.music.network.LrcLibApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataRepositoryTest {

    @Test
    fun `ambiguous basename keeps both filename directions available`() {
        val repository = MetadataRepository(FakeMetadataDao())
        val song = Song(
            id = 1L,
            title = "Counting Stars",
            artist = "Paradise",
            album = "",
            albumId = 0L,
            durationMs = 240_000L,
            dataPath = """C:\Music\Counting Stars - Paradise.mp3""",
            dateAdded = 0L,
        )

        val candidates = repository.run {
            MetadataRepository.MatchInput(song.title, song.artist, song.album).candidateInputsFor(song)
        }

        val searchSeeds = candidates.filter { it.preserveAsSearchSeed }.map { it.input }
        assertTrue(searchSeeds.any { it.title == "Counting Stars" && it.artist == "Paradise" })
        assertTrue(searchSeeds.any { it.title == "Paradise" && it.artist == "Counting Stars" })
    }

    @Test
    fun `repository search inputs include opposite filename candidate for ambiguous file`() {
        val repository = MetadataRepository(FakeMetadataDao())
        val fallback = LrcLibApi.TrackInfo(
            title = "Paradise",
            artist = "Coldplay",
            album = null,
            durationSeconds = 278L,
            plainLyrics = null,
            syncedLyrics = null,
            instrumental = false,
        )

        val searchInputs = repository.repositorySearchInputsFor(
            input = MetadataRepository.MatchInput(
                title = "Counting Stars",
                artist = "Paradise",
                album = "",
            ),
            fallbackInfo = fallback,
            alternateInputs = listOf(
                MetadataRepository.MatchInput(
                    title = "Paradise",
                    artist = "Counting Stars",
                    album = "",
                )
            ),
        )

        assertTrue(searchInputs.any { it.title == "Paradise" && it.artist == "Counting Stars" })
        assertTrue(searchInputs.any { it.title == "Counting Stars" && it.artist == "Paradise" })
    }

    private class FakeMetadataDao : MetadataDao {
        private val state = MutableStateFlow<SongMetadata?>(null)

        override suspend fun upsert(metadata: SongMetadata) {
            state.value = metadata
        }

        override suspend fun get(songId: Long): SongMetadata? = state.value?.takeIf { it.songId == songId }

        override fun observe(songId: Long): Flow<SongMetadata?> = state

        override fun observeAll(): Flow<List<SongMetadata>> = state.map { listOfNotNull(it) }

        override suspend fun delete(songId: Long) {
            if (state.value?.songId == songId) {
                state.value = null
            }
        }

        override suspend fun moveSongId(oldSongId: Long, newSongId: Long) {
            state.value = state.value?.takeIf { it.songId == oldSongId }?.copy(songId = newSongId)
        }
    }
}
