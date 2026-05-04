package com.pulse.music.importer

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.pulse.music.data.MusicRepository
import com.pulse.music.network.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume

data class ImportCandidate(
    val title: String,
    val artist: String,
    val durationMs: Long,
    val url: String,
)

data class ImportedSong(
    val title: String,
    val artist: String,
    val fileName: String,
    val uri: Uri,
)

class SongImportManager(
    private val context: Context,
    private val repository: MusicRepository,
) {

    suspend fun search(query: String): List<ImportCandidate> = withContext(Dispatchers.IO) {
        val normalized = query.trim()
        if (normalized.isBlank()) return@withContext emptyList()

        val service = ServiceList.YouTube
        val handler = service.searchQHFactory.fromQuery(normalized, emptyList(), "")
        val searchInfo = SearchInfo.getInfo(service, handler)

        searchInfo.relatedItems
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull { item ->
                val url = item.url?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                ImportCandidate(
                    title = item.name?.trim().orEmpty().ifBlank { "Unknown title" },
                    artist = item.uploaderName?.trim().orEmpty().ifBlank { "Unknown artist" },
                    durationMs = (item.duration.takeIf { it > 0 } ?: 0L) * 1000L,
                    url = url,
                )
            }
            .filter { it.durationMs == 0L || it.durationMs >= MIN_IMPORT_DURATION_MS }
            .take(MAX_RESULTS)
    }

    suspend fun importSong(
        candidate: ImportCandidate,
        onProgress: suspend (Float) -> Unit,
    ): ImportedSong = withContext(Dispatchers.IO) {
        val streamInfo = StreamInfo.getInfo(ServiceList.YouTube, candidate.url)
        val bestAudio = pickBestAudioStream(streamInfo.audioStreams)
            ?: throw IOException("Pulse couldn't find a downloadable audio stream for that result.")

        val suffix = bestAudio.format?.suffix?.ifBlank { null } ?: DEFAULT_AUDIO_SUFFIX
        val baseName = sanitizeFileName("${candidate.title} - ${candidate.artist}")
        val fileName = buildUniqueDisplayName(baseName, suffix)
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val relativePath = "${Environment.DIRECTORY_MUSIC}${File.separator}PulseApp"

        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.TITLE, candidate.title)
            put(MediaStore.Audio.Media.ARTIST, candidate.artist)
            put(MediaStore.Audio.Media.ALBUM, IMPORT_ALBUM_NAME)
            put(MediaStore.Audio.Media.MIME_TYPE, bestAudio.format?.mimeType ?: "audio/*")
            put(MediaStore.Audio.Media.RELATIVE_PATH, relativePath)
            put(MediaStore.Audio.Media.IS_MUSIC, 1)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values)
            ?: throw IOException("Pulse couldn't create a destination file in the music folder.")

        try {
            val audioUrl = bestAudio.content.takeIf { bestAudio.isUrl }
                ?: throw IOException("This audio stream isn't directly downloadable.")
            downloadToUri(uri, audioUrl, onProgress)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val readyValues = ContentValues().apply {
                    put(MediaStore.Audio.Media.IS_PENDING, 0)
                }
                resolver.update(uri, readyValues, null, null)
            } else {
                scanMedia(uri)
            }

            repository.rescan()

            ImportedSong(
                title = candidate.title,
                artist = candidate.artist,
                fileName = fileName,
                uri = uri,
            )
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            throw t
        }
    }

    private suspend fun downloadToUri(
        targetUri: Uri,
        sourceUrl: String,
        onProgress: suspend (Float) -> Unit,
    ) {
        val request = Request.Builder()
            .url(sourceUrl)
            .header("User-Agent", DOWNLOADER_USER_AGENT)
            .get()
            .build()

        HttpClient.instance.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Download failed with HTTP ${response.code}.")
            }

            val body = response.body ?: throw IOException("Download returned an empty response.")
            val totalBytes = body.contentLength()
            context.contentResolver.openOutputStream(targetUri)?.use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                    var downloaded = 0L
                    var lastProgressBucket = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (totalBytes > 0) {
                            val progress = (downloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                            val bucket = (progress * 100).toInt()
                            if (bucket != lastProgressBucket) {
                                onProgress(progress)
                                lastProgressBucket = bucket
                            }
                        }
                    }
                    output.flush()
                }
            } ?: throw IOException("Pulse couldn't open the destination file for writing.")
        }

        onProgress(1f)
    }

    private suspend fun scanMedia(uri: Uri) {
        val path = resolvePathFromUri(uri) ?: return
        suspendCancellableCoroutine<Unit> { continuation ->
            MediaScannerConnection.scanFile(context, arrayOf(path), null) { _, _ ->
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
    }

    private fun resolvePathFromUri(uri: Uri): String? {
        val projection = arrayOf(MediaStore.Audio.Media.DATA)
        return context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val dataIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
            if (dataIndex >= 0 && cursor.moveToFirst()) cursor.getString(dataIndex) else null
        }
    }

    private fun buildUniqueDisplayName(baseName: String, suffix: String): String {
        var attempt = 0
        while (attempt < 50) {
            val name = if (attempt == 0) {
                "$baseName.$suffix"
            } else {
                "$baseName ($attempt).$suffix"
            }
            if (!displayNameExists(name)) return name
            attempt += 1
        }
        return "$baseName ${System.currentTimeMillis()}.$suffix"
    }

    private fun displayNameExists(displayName: String): Boolean {
        val selection = "${MediaStore.Audio.Media.DISPLAY_NAME} = ? AND ${MediaStore.Audio.Media.RELATIVE_PATH} = ?"
        val args = arrayOf(displayName, "${Environment.DIRECTORY_MUSIC}${File.separator}PulseApp${File.separator}")
        return context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media._ID),
            selection,
            args,
            null,
        )?.use { it.moveToFirst() } == true
    }

    private fun pickBestAudioStream(streams: List<AudioStream>): AudioStream? =
        streams
            .filter { it.isUrl }
            .maxWithOrNull(
                compareBy<AudioStream>(
                    { audioFormatRank(it.format) },
                    { it.averageBitrate },
                    { it.bitrate },
                ),
            )

    private fun audioFormatRank(format: MediaFormat?): Int = when (format) {
        MediaFormat.M4A -> 5
        MediaFormat.MP3 -> 4
        MediaFormat.OPUS -> 3
        MediaFormat.WEBMA,
        MediaFormat.WEBMA_OPUS -> 2
        MediaFormat.OGG,
        MediaFormat.FLAC,
        MediaFormat.ALAC,
        MediaFormat.WAV,
        MediaFormat.AIFF,
        MediaFormat.AIF,
        MediaFormat.MP2 -> 1
        null -> 0
        else -> 0
    }

    private fun sanitizeFileName(raw: String): String =
        raw.replace(Regex("[\\\\/:*?\"<>|]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_BASE_NAME_LENGTH)
            .ifBlank { "Pulse import" }

    companion object {
        fun initializeExtractor() {
            try {
                NewPipe.init(PulseExtractorDownloader())
            } catch (_: IllegalStateException) {
                // Ignore repeated init calls across process recreation edge cases.
            } catch (_: ExtractionException) {
                // Ignore if NewPipe internals reject a late re-init; search/import will surface errors.
            }
        }

        private const val MAX_RESULTS = 8
        private const val MAX_BASE_NAME_LENGTH = 80
        private const val MIN_IMPORT_DURATION_MS = 30_000L
        private const val DOWNLOAD_BUFFER_SIZE = 64 * 1024
        private const val DEFAULT_AUDIO_SUFFIX = "m4a"
        private const val IMPORT_ALBUM_NAME = "Pulse Imports"
        private const val DOWNLOADER_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
    }
}
