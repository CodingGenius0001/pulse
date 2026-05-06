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

data class LocalIdentityCandidate(
    val title: String,
    val artist: String,
    val score: Int,
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
        val candidates = rankedIdentityCandidates(
            rawTitle = rawTitle,
            rawArtist = rawArtist,
            displayName = displayName,
        )
        val bestCandidate = candidates.firstOrNull()
        val cleanedArtist = cleanArtist(bestCandidate?.artist).ifBlank {
            artistFromTag.ifBlank { "Unknown artist" }
        }
        val cleanedTitle = cleanTitle(bestCandidate?.title, cleanedArtist)
            .ifBlank { cleanTitle(titleSource, cleanedArtist) }
            .ifBlank { fileBase.ifBlank { "Unknown" } }

        return LocalSongIdentity(
            title = cleanedTitle,
            artist = cleanedArtist,
            album = cleanAlbum(rawAlbum),
        )
    }

    fun rankedIdentityCandidates(
        rawTitle: String?,
        rawArtist: String?,
        displayName: String?,
    ): List<LocalIdentityCandidate> {
        val fileBase = displayName.orEmpty().substringBeforeLast(".").trim()
        val artistFromTag = cleanArtist(rawArtist)
        val titleFromTag = cleanTitleCandidate(rawTitle)
        val dashedInputs = listOf(rawTitle.orEmpty().trim(), fileBase)
            .filter { it.isNotBlank() }
            .distinct()

        val candidates = buildList {
            if (titleFromTag.isNotBlank()) {
                add(
                    LocalIdentityCandidate(
                        title = cleanTitle(titleFromTag, artistFromTag),
                        artist = artistFromTag.ifBlank { "Unknown artist" },
                        score = scoreCandidate(
                            title = titleFromTag,
                            artist = artistFromTag,
                            titleFromTag = titleFromTag,
                            artistFromTag = artistFromTag,
                            fileBase = fileBase,
                            fromDash = false,
                        ),
                    )
                )
            }

            dashedInputs.forEach { source ->
                parseArtistTitleCandidates(source).forEachIndexed { index, parsed ->
                    val candidateArtist = cleanArtist(parsed.first)
                    val candidateTitle = cleanTitle(parsed.second, candidateArtist)
                    if (candidateArtist.isNotBlank() && candidateTitle.isNotBlank()) {
                        add(
                            LocalIdentityCandidate(
                                title = candidateTitle,
                                artist = candidateArtist,
                                score = scoreCandidate(
                                    title = candidateTitle,
                                    artist = candidateArtist,
                                    titleFromTag = titleFromTag,
                                    artistFromTag = artistFromTag,
                                    fileBase = fileBase,
                                    fromDash = true,
                                    reversed = index == 1,
                                ),
                            )
                        )
                    }
                }
            }
        }

        return candidates
            .filter { it.title.isNotBlank() }
            .distinctBy { "${it.title.cleanKey()}|${it.artist.cleanKey()}" }
            .sortedByDescending { it.score }
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

    private fun parseArtistTitleCandidates(value: String?): List<Pair<String, String>> {
        val candidate = value.orEmpty().trim()
        if (candidate.isBlank()) return emptyList()
        val match = ARTIST_TITLE_SEPARATOR_REGEX.find(candidate) ?: return emptyList()
        val left = candidate.substring(0, match.range.first).trim()
        val right = candidate.substring(match.range.last + 1).trim()
        if (left.isBlank() || right.isBlank()) return emptyList()
        return listOf(left to right, right to left)
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

    private fun scoreCandidate(
        title: String,
        artist: String,
        titleFromTag: String,
        artistFromTag: String,
        fileBase: String,
        fromDash: Boolean,
        reversed: Boolean = false,
    ): Int {
        var score = 0
        val titleKey = title.cleanKey()
        val artistKey = artist.cleanKey()
        val titleTagKey = titleFromTag.cleanKey()
        val artistTagKey = artistFromTag.cleanKey()
        val fileKey = fileBase.cleanKey()

        if (artistTagKey.isNotBlank()) {
            score += when {
                artistKey == artistTagKey -> 40
                artistKey.contains(artistTagKey) || artistTagKey.contains(artistKey) -> 28
                else -> -30
            }
        }

        if (titleTagKey.isNotBlank()) {
            score += when {
                titleKey == titleTagKey -> 34
                titleKey.contains(titleTagKey) || titleTagKey.contains(titleKey) -> 20
                else -> -12
            }
        }

        if (fromDash) score += 10
        if (reversed) score -= 4
        if (fileKey.contains(titleKey) && fileKey.contains(artistKey)) score += 8
        if (artist.looksLikeArtistName()) score += 10 else score -= 12
        if (title.looksLikeTrackTitle()) score += 8 else score -= 8
        if (titleKey == artistKey) score -= 24
        if (artistKey in TITLE_NOISE_WORDS) score -= 20
        return score
    }

    private fun String.looksLikeArtistName(): Boolean {
        val cleaned = cleanArtist(this)
        if (cleaned.isBlank()) return false
        val key = cleaned.cleanKey()
        if (key.isBlank() || key in TITLE_NOISE_WORDS) return false
        if (Regex("""(?i)\b(track|song|official|lyrics?|audio|video)\b""").containsMatchIn(cleaned)) return false
        return true
    }

    private fun String.looksLikeTrackTitle(): Boolean {
        val cleaned = cleanTitleCandidate(this)
        if (cleaned.isBlank()) return false
        return !Regex("""(?i)\b(topic|official|lyrics?|audio|video)\b""").containsMatchIn(cleaned)
    }

    private val TITLE_NOISE_WORDS = setOf(
        "official", "video", "audio", "lyrics", "lyric", "topic", "visualizer", "music", "hd", "4k"
    )
}
