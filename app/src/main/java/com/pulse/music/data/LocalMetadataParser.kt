package com.pulse.music.data

private val UNKNOWN_ARTIST_VALUES = setOf("unknown artist", "<unknown>")
private val UNKNOWN_ALBUM_VALUES = setOf("unknown album", "<unknown>")
private val TITLE_NOISE_REGEX = Regex(
    """(?i)\s*[\(\[][^)\]]*\b(official|video|audio|lyrics?|visualizer|music video|hd|4k|topic|remaster(?:ed)?|clean|explicit)\b[^)\]]*[\)\]]\s*""",
)
private val INLINE_TITLE_NOISE_REGEX = Regex(
    """(?i)\s*-\s*(official|video|audio|lyrics?|visualizer|music video|hd|4k|topic|remaster(?:ed)?|clean|explicit)\b.*$""",
)
private val ARTIST_TITLE_SEPARATOR_REGEX = Regex("""\s(?:-|–|—|:)\s""")
private val FEATURE_SPLIT_REGEX = Regex("""(?i)\s*(?:,|&| feat\.? | ft\.? | x | with |/)\s*""")

data class LocalSongIdentity(
    val title: String,
    val artist: String,
    val album: String,
)

object LocalMetadataParser {
    fun normalizeSong(
        rawTitle: String?,
        rawArtist: String?,
        rawAlbum: String?,
        displayName: String?,
    ): LocalSongIdentity {
        val fileBase = displayName.orEmpty().substringBeforeLast(".").trim()
        val artistFromTag = cleanArtist(rawArtist)
        val titleSource = cleanTitleCandidate(rawTitle).ifBlank { cleanTitleCandidate(fileBase) }
        val parsedFromTitle = parseArtistTitle(rawTitle)
        val parsedFromFile = parseArtistTitle(fileBase)

        val artist = when {
            artistFromTag.isNotBlank() -> artistFromTag
            parsedFromTitle?.first?.isNotBlank() == true -> cleanArtist(parsedFromTitle.first)
            parsedFromFile?.first?.isNotBlank() == true -> cleanArtist(parsedFromFile.first)
            else -> ""
        }

        val parsedTitle = when {
            artistFromTag.isBlank() && parsedFromTitle?.second?.isNotBlank() == true -> parsedFromTitle.second
            artistFromTag.isBlank() && parsedFromFile?.second?.isNotBlank() == true -> parsedFromFile.second
            artistFromTag.isNotBlank() && parsedFromTitle?.first?.cleanKey() == artistFromTag.cleanKey() -> parsedFromTitle.second
            artistFromTag.isNotBlank() && parsedFromFile?.first?.cleanKey() == artistFromTag.cleanKey() -> parsedFromFile.second
            else -> titleSource
        }

        val cleanedArtist = artist.ifBlank { "Unknown artist" }
        val cleanedTitle = cleanTitle(parsedTitle, cleanedArtist)
            .ifBlank { cleanTitle(titleSource, cleanedArtist) }
            .ifBlank { fileBase.ifBlank { "Unknown" } }

        return LocalSongIdentity(
            title = cleanedTitle,
            artist = cleanedArtist,
            album = cleanAlbum(rawAlbum),
        )
    }

    fun cleanAlbum(value: String?): String {
        val trimmed = value.orEmpty().trim()
        if (trimmed.isBlank()) return ""
        if (trimmed.lowercase() in UNKNOWN_ALBUM_VALUES) return ""
        return trimmed
    }

    fun cleanArtist(value: String?): String {
        val trimmed = value.orEmpty().trim()
        if (trimmed.isBlank()) return ""
        if (trimmed.lowercase() in UNKNOWN_ARTIST_VALUES) return ""
        return trimmed
            .replace(Regex("""(?i)\s*-\s*topic$"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    fun cleanTitle(value: String?, artistHint: String = ""): String {
        var cleaned = cleanTitleCandidate(value)
        if (cleaned.isBlank()) return ""

        val artist = cleanArtist(artistHint)
        if (artist.isNotBlank()) {
            val artistKey = artist.cleanKey()
            val cleanedKey = cleaned.cleanKey()
            if (cleanedKey.startsWith("$artistKey ")) {
                cleaned = cleaned.substringAfterFirstSeparator().ifBlank { cleaned }
            }
            cleaned = cleaned.replace(
                Regex("""(?i)\s(?:-|–|—|:)\s${Regex.escape(artist)}$"""),
                "",
            ).trim()
        }

        return cleaned
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    fun artistVariants(value: String): List<String> {
        val cleaned = cleanArtist(value)
        if (cleaned.isBlank()) return emptyList()

        val split = cleaned
            .split(FEATURE_SPLIT_REGEX)
            .map { it.trim() }
            .filter { it.isNotBlank() }

        return buildList {
            add(cleaned)
            if (split.isNotEmpty()) {
                add(split.first())
            }
            if (split.size >= 2) {
                add(split.take(2).joinToString(", "))
                split.forEach { add(it) }
            }
        }.distinctBy { it.cleanKey() }
    }

    private fun cleanTitleCandidate(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return value
            .replace(TITLE_NOISE_REGEX, " ")
            .replace(INLINE_TITLE_NOISE_REGEX, "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun parseArtistTitle(value: String?): Pair<String, String>? {
        val candidate = value.orEmpty().trim()
        if (candidate.isBlank()) return null
        val match = ARTIST_TITLE_SEPARATOR_REGEX.find(candidate) ?: return null
        val left = candidate.substring(0, match.range.first).trim()
        val right = candidate.substring(match.range.last + 1).trim()
        if (left.isBlank() || right.isBlank()) return null
        return left to right
    }

    private fun String.substringAfterFirstSeparator(): String {
        val match = ARTIST_TITLE_SEPARATOR_REGEX.find(this) ?: return this
        return substring(match.range.last + 1).trim()
    }

    private fun String.cleanKey(): String =
        lowercase()
            .replace(Regex("""[^\p{L}\p{N}& ]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
}
