package com.pulse.music.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalMetadataParserTest {

    @Test
    fun `artist title filename stays artist title`() {
        val identity = LocalMetadataParser.normalizeSong(
            rawTitle = "",
            rawArtist = "",
            rawAlbum = "",
            displayName = "Zara Larsson - Lush Life.mp3",
        )

        assertEquals("Lush Life", identity.title)
        assertEquals("Zara Larsson", identity.artist)
        assertEquals("", identity.album)
    }

    @Test
    fun `title artist filename resolves using title tag`() {
        val identity = LocalMetadataParser.normalizeSong(
            rawTitle = "Lush Life",
            rawArtist = "",
            rawAlbum = "",
            displayName = "Lush Life - Zara Larsson.mp3",
        )

        assertEquals("Lush Life", identity.title)
        assertEquals("Zara Larsson", identity.artist)
    }

    @Test
    fun `title artist filename resolves using artist tag`() {
        val identity = LocalMetadataParser.normalizeSong(
            rawTitle = "Shape of You",
            rawArtist = "Ed Sheeran",
            rawAlbum = "Unknown album",
            displayName = "Shape of You - Ed Sheeran.mp3",
        )

        assertEquals("Shape of You", identity.title)
        assertEquals("Ed Sheeran", identity.artist)
        assertEquals("", identity.album)
    }

    @Test
    fun `ambiguous dashed split falls back conservatively`() {
        val analysis = LocalMetadataParser.analyzeSong(
            rawTitle = "",
            rawArtist = "",
            rawAlbum = "",
            displayName = "Paradise - Yellow.mp3",
        )

        assertTrue(analysis.dashedAssessment?.ambiguous == true)
        assertEquals("Unknown artist", analysis.identity.artist)
        assertTrue(analysis.identity.title in setOf("Paradise", "Yellow"))
        assertNull(analysis.dashedAssessment?.confidentCandidate)
    }

    @Test
    fun `one republic filename does not store counting stars as artist`() {
        val identity = LocalMetadataParser.normalizeSong(
            rawTitle = "",
            rawArtist = "",
            rawAlbum = "",
            displayName = "OneRepublic - Counting Stars.mp3",
        )

        assertEquals("Counting Stars", identity.title)
        assertEquals("OneRepublic", identity.artist)
        assertFalse(identity.artist.equals("Counting Stars", ignoreCase = true))
    }

    @Test
    fun `counting stars artist title direction stays correct`() {
        val identity = LocalMetadataParser.normalizeSong(
            rawTitle = "",
            rawArtist = "",
            rawAlbum = "",
            displayName = "Counting Stars - OneRepublic.mp3",
        )

        assertEquals("Counting Stars", identity.title)
        assertEquals("OneRepublic", identity.artist)
    }

    @Test
    fun `ranked candidates include both dashed directions`() {
        val candidates = LocalMetadataParser.rankedIdentityCandidates(
            rawTitle = "",
            rawArtist = "",
            displayName = "Lush Life - Zara Larsson.mp3",
        )

        val identities = candidates.map { "${it.artist}|${it.title}" }
        assertTrue(identities.contains("Lush Life|Zara Larsson"))
        assertTrue(identities.contains("Zara Larsson|Lush Life"))
    }

    @Test
    fun `pulseapp artist and album are treated as folder noise`() {
        val identity = LocalMetadataParser.normalizeSong(
            rawTitle = "Apna Bana Le",
            rawArtist = "PulseApp",
            rawAlbum = "PulseApp",
            displayName = "Apna Bana Le.mp3",
        )

        assertEquals("Apna Bana Le", identity.title)
        assertEquals("Unknown artist", identity.artist)
        assertEquals("", identity.album)
    }
}
