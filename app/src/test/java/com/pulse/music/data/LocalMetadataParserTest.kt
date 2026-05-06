package com.pulse.music.data

import org.junit.Assert.assertEquals
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
}
