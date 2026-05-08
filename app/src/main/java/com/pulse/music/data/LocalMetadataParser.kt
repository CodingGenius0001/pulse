package com.pulse.music.data

private val UNKNOWN_ARTIST_VALUES = setOf("unknown artist", "<unknown>")
private val UNKNOWN_ALBUM_VALUES = setOf("unknown album", "<unknown>")
private val APP_FOLDER_NOISE_VALUES = setOf("pulseapp", "pulse app")
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
    val fromDash: Boolean = false,
    val reversed: Boolean = false,
)

data class LocalIdentityAnalysis(
    val identity: LocalSongIdentity,
    val rankedCandidates: List<LocalIdentityCandidate>,
    val dashedAssessment: DashedIdentityAssessment?,
)

data class DashedIdentityAssessment(
    val candidates: List<LocalIdentityCandidate>,
    val confidentCandidate: LocalIdentityCandidate?,
    val ambiguous: Boolean,
)

object LocalMetadataParser {
    fun normalizeSong(
        rawTitle: String?,
        rawArtist: String?,
        rawAlbum: String?,
        displayName: String?,
    ): LocalSongIdentity = analyzeSong(rawTitle, rawArtist, rawAlbum, displayName).identity

    fun analyzeSong(
        rawTitle: String?,
        rawArtist: String?,
        rawAlbum: String?,
        displayName: String?,
    ): LocalIdentityAnalysis {
        val fileBase = displayName.orEmpty().substringBeforeLast(".").trim()
        val artistFromTag = cleanArtist(rawArtist)
        val taggedTitle = cleanTitleCandidate(rawTitle)
        val titleSource = taggedTitle.ifBlank { cleanTitleCandidate(fileBase) }
        val candidates = rankedIdentityCandidates(
            rawTitle = rawTitle,
            rawArtist = rawArtist,
            displayName = displayName,
        )
        val dashedAssessment = assessDashedIdentity(rawTitle, rawArtist, displayName)
        val bestCandidate = when {
            dashedAssessment?.ambiguous == true -> candidates.firstOrNull { !it.fromDash }
                ?: dashedAssessment.confidentCandidate
            else -> dashedAssessment?.confidentCandidate ?: candidates.firstOrNull()
        }
        val cleanedArtist = when {
            dashedAssessment?.ambiguous == true -> artistFromTag.ifBlank { "Unknown artist" }
            else -> cleanArtist(bestCandidate?.artist).ifBlank { artistFromTag.ifBlank { "Unknown artist" } }
        }
        val cleanedTitle = when {
            dashedAssessment?.ambiguous == true -> {
                val safeTitle = chooseSafeTitle(
                    taggedTitle = taggedTitle,
                    artistFromTag = artistFromTag,
                    cleanedArtist = cleanedArtist,
                    dashedAssessment = dashedAssessment,
                )
                cleanTitle(safeTitle, cleanedArtist)
            }
            else -> cleanTitle(bestCandidate?.title, cleanedArtist)
        }
            .ifBlank { cleanTitle(titleSource, cleanedArtist) }
            .ifBlank { fileBase.ifBlank { "Unknown" } }

        return LocalIdentityAnalysis(
            identity = LocalSongIdentity(
                title = cleanedTitle,
                artist = cleanedArtist,
                album = cleanAlbum(rawAlbum),
            ),
            rankedCandidates = candidates,
            dashedAssessment = dashedAssessment,
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
                                fromDash = true,
                                reversed = index == 1,
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

    fun assessDashedIdentity(
        rawTitle: String?,
        rawArtist: String?,
        displayName: String?,
    ): DashedIdentityAssessment? {
        val dashedCandidates = rankedIdentityCandidates(
            rawTitle = rawTitle,
            rawArtist = rawArtist,
            displayName = displayName,
        ).filter { it.fromDash }
        if (dashedCandidates.size < 2) return null
        val best = dashedCandidates.first()
        val runnerUp = dashedCandidates[1]
        val ambiguous = best.score - runnerUp.score < DASHED_CONFIDENCE_GAP &&
            !best.artist.hasStrongArtistSignal()
        return DashedIdentityAssessment(
            candidates = dashedCandidates,
            confidentCandidate = if (ambiguous) null else best,
            ambiguous = ambiguous,
        )
    }

    fun cleanAlbum(value: String?): String {
        val trimmed = value.orEmpty().trim()
        if (trimmed.isBlank()) return ""
        if (trimmed.lowercase() in UNKNOWN_ALBUM_VALUES) return ""
        if (trimmed.cleanKey() in APP_FOLDER_NOISE_VALUES) return ""
        return trimmed
    }

    fun cleanArtist(value: String?): String {
        val trimmed = value.orEmpty().trim()
        if (trimmed.isBlank()) return ""
        if (trimmed.lowercase() in UNKNOWN_ARTIST_VALUES) return ""
        val cleaned = trimmed
            .replace(Regex("""(?i)\s*-\s*topic$"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (cleaned.cleanKey() in APP_FOLDER_NOISE_VALUES) return ""
        return cleaned
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

    private fun chooseSafeTitle(
        taggedTitle: String,
        artistFromTag: String,
        cleanedArtist: String,
        dashedAssessment: DashedIdentityAssessment,
    ): String {
        if (taggedTitle.isNotBlank()) return taggedTitle
        return dashedAssessment.candidates
            .map { candidate ->
                candidate.title to safeTitleScore(
                    title = candidate.title,
                    artistFromTag = artistFromTag,
                    cleanedArtist = cleanedArtist,
                )
            }
            .maxByOrNull { it.second }
            ?.first
            .orEmpty()
    }

    private fun safeTitleScore(
        title: String,
        artistFromTag: String,
        cleanedArtist: String,
    ): Int {
        var score = 0
        val titleKey = title.cleanKey()
        if (title.looksLikeTrackTitle()) score += 10
        if (title.contains(' ')) score += 4
        if (title.isLikelyArtistToken()) score -= 12
        if (artistFromTag.isNotBlank() && titleKey == artistFromTag.cleanKey()) score -= 20
        if (cleanedArtist.isKnownArtistValue() && titleKey == cleanedArtist.cleanKey()) score -= 20
        return score
    }

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
        score += artistLikelihoodScore(artist)
        score += titleLikelihoodScore(title)
        if (titleKey == artistKey) score -= 24
        if (artistKey in TITLE_NOISE_WORDS) score -= 20
        return score
    }

    private fun artistLikelihoodScore(value: String): Int {
        var score = if (value.looksLikeArtistName()) 10 else -12
        if (value.isLikelyArtistToken()) score += 12
        if (value.looksLikePersonalName()) score += 6
        if (value.looksLikeTrackTitle()) score -= 10
        return score
    }

    private fun titleLikelihoodScore(value: String): Int {
        var score = if (value.looksLikeTrackTitle()) 8 else -8
        if (value.contains(' ')) score += 4
        if (value.isLikelyArtistToken()) score -= 10
        if (value.looksLikePersonalName()) score -= 12
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

    private fun String.isLikelyArtistToken(): Boolean {
        val cleaned = cleanArtist(this)
        if (cleaned.isBlank()) return false
        if (cleaned.contains('&') || cleaned.contains('/')) return true
        val tokens = cleaned.split(" ").filter { it.isNotBlank() }
        if (tokens.size == 1) {
            val token = tokens.first()
            if (token.any(Char::isDigit)) return true
            if (Regex("""[a-z][A-Z]""").containsMatchIn(token)) return true
        }
        return false
    }

    private fun String.looksLikePersonalName(): Boolean {
        val tokens = cleanArtist(this)
            .split(" ")
            .filter { it.isNotBlank() }
        if (tokens.size !in 2..3) return false
        return tokens.all { token ->
            token.all { it.isLetter() } &&
                token.first().isUpperCase() &&
                token.drop(1).all { it.isLowerCase() }
        }
    }

    private fun String.hasStrongArtistSignal(): Boolean =
        isLikelyArtistToken() || looksLikePersonalName()

    private fun String.isKnownArtistValue(): Boolean =
        isNotBlank() && lowercase() !in UNKNOWN_ARTIST_VALUES

    private val TITLE_NOISE_WORDS = setOf(
        "official", "video", "audio", "lyrics", "lyric", "topic", "visualizer", "music", "hd", "4k"
    )

    private const val DASHED_CONFIDENCE_GAP = 12
}
