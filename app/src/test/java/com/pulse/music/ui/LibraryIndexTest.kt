package com.pulse.music.ui

import com.pulse.music.data.Song
import com.pulse.music.data.SongMetadata
import com.pulse.music.data.displayWith
import com.pulse.music.util.formatTotalDuration
import com.pulse.music.util.songCountLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryIndexTest {

    private fun song(
        id: Long,
        title: String,
        artist: String = "Artist",
        album: String = "Album",
        track: Int = 0,
    ) = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        albumId = 1,
        durationMs = 180_000,
        dataPath = "/Music/PulseApp/$id.mp3",
        dateAdded = id,
        trackNumber = track,
    )

    @Test
    fun `album songs follow track numbers, untagged tracks last`() {
        val index = LibraryIndex.build(
            songs = listOf(
                song(1, "Zeta", track = 0),
                song(2, "Beta", track = 2),
                song(3, "Alpha", track = 1),
            ),
            metadata = emptyMap(),
        )

        val album = index.album(LibraryIndex.albumKey("Album"))
        assertNotNull(album)
        assertEquals(listOf(3L, 2L, 1L), album!!.songs.map { it.id })
    }

    @Test
    fun `resolved metadata decides album and artist grouping`() {
        val songs = listOf(
            song(1, "One", artist = "Unknown artist", album = "<unknown>"),
            song(2, "Two", artist = "Real Artist", album = "Real Album"),
        )
        val metadata = mapOf(
            1L to SongMetadata(songId = 1, resolvedArtist = "Real Artist", resolvedAlbum = "Real Album"),
        )

        val index = LibraryIndex.build(songs, metadata)

        assertEquals(1, index.albums.size)
        assertEquals(listOf(1L, 2L), index.albums.single().songs.map { it.id }.sorted())
        assertEquals("Real Artist", index.albums.single().artist)
        assertEquals(1, index.artists.size)
    }

    @Test
    fun `tagged songs stay together even when providers resolve album variants`() {
        val songs = listOf(
            song(1, "Blinding Lights", artist = "The Weeknd", album = "After Hours", track = 9),
            song(2, "Alone Again", artist = "The Weeknd", album = "After Hours", track = 1),
        )
        val metadata = mapOf(
            1L to SongMetadata(songId = 1, resolvedAlbum = "After Hours (Explicit)"),
            2L to SongMetadata(songId = 2, resolvedAlbum = "after hours"),
        )

        val index = LibraryIndex.build(songs, metadata)

        assertEquals(listOf("After Hours"), index.albums.map { it.title })
        assertEquals(listOf(2L, 1L), index.albums.single().songs.map { it.id })
    }

    @Test
    fun `unknown albums are left out and unknown artists sort last`() {
        val index = LibraryIndex.build(
            songs = listOf(
                song(1, "One", artist = "Unknown artist", album = ""),
                song(2, "Two", artist = "Abba", album = "Gold"),
            ),
            metadata = emptyMap(),
        )

        assertEquals(listOf("Gold"), index.albums.map { it.title })
        assertEquals(listOf("Abba", "Unknown artist"), index.artists.map { it.name })
        assertNull(index.album(LibraryIndex.albumKey("")))
    }

    @Test
    fun `albums with no majority artist are various artists`() {
        val index = LibraryIndex.build(
            songs = listOf(
                song(1, "One", artist = "A", album = "Mix"),
                song(2, "Two", artist = "B", album = "Mix"),
                song(3, "Three", artist = "C", album = "Mix"),
            ),
            metadata = emptyMap(),
        )

        assertEquals("Various artists", index.albums.single().artist)
    }

    @Test
    fun `resolved names that only differ in case keep the tag spelling`() {
        val display = song(1, "Bohemian Rhapsody", artist = "Queen", album = "A Night at the Opera")
            .displayWith(
                SongMetadata(
                    songId = 1,
                    resolvedTitle = "bohemian rhapsody",
                    resolvedArtist = "queen",
                    resolvedAlbum = "A Night At The Opera (2011 Remaster)",
                ),
            )

        assertEquals("Bohemian Rhapsody", display.title)
        assertEquals("Queen", display.artist)
        assertEquals("A Night At The Opera (2011 Remaster)", display.album)
    }

    @Test
    fun `album keys ignore case and surrounding spaces`() {
        assertEquals(LibraryIndex.albumKey("Gold "), LibraryIndex.albumKey("gold"))
    }

    @Test
    fun `duration and count labels read naturally`() {
        assertEquals("1 min", formatTotalDuration(20_000))
        assertEquals("42 min", formatTotalDuration(42 * 60_000L))
        assertEquals("1 hr", formatTotalDuration(60 * 60_000L))
        assertEquals("1 hr 5 min", formatTotalDuration(65 * 60_000L))
        assertEquals("1 song", songCountLabel(1))
        assertEquals("3 songs", songCountLabel(3))
    }
}
