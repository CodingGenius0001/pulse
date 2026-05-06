package com.pulse.music.scanner

import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import com.pulse.music.data.LocalMetadataParser
import com.pulse.music.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Scans the device's MediaStore for audio files inside the dedicated Pulse folder.
 *
 * Key design decision: we only scan /Music/PulseApp/ plus legacy Pulse folders
 * for upgrades. There is no fallback to "all music", which keeps the library
 * free of unrelated recordings and notification sounds.
 */
class MusicScanner(private val context: Context) {

    /**
     * Resolves the expected Pulse folder locations on this device. We check
     * /Music/PulseApp/ first (our preferred location going forward) and fall
     * back to /Music/Pulse/ and /Pulse/ for users upgrading from older builds.
     */
    private fun pulseFolderCandidates(): List<File> {
        val musicDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_MUSIC
        )
        val externalRoot = Environment.getExternalStorageDirectory()
        return listOf(
            File(musicDir, "PulseApp"),
            File(musicDir, "Pulse"),
            File(externalRoot, "Pulse"),
        )
    }

    /** The folder we should use: first one that exists, or the default Music/PulseApp path. */
    fun preferredPulseFolder(): File = pulseFolderCandidates().firstOrNull { it.exists() }
        ?: pulseFolderCandidates().first()

    /**
     * Pretty-printed version of the preferred folder: strips the
     * /storage/emulated/0/ prefix so users see "Music/PulseApp" instead of the
     * full Android internal storage path.
     */
    fun shortDisplayPath(): String {
        val full = preferredPulseFolder().absolutePath
        return full
            .removePrefix("/storage/emulated/0/")
            .removePrefix(Environment.getExternalStorageDirectory().absolutePath + "/")
            .removePrefix("/")
            .ifBlank { "Music/PulseApp" }
    }

    /** Whether any Pulse folder exists on the device right now. */
    fun pulseFolderExists(): Boolean = pulseFolderCandidates().any { it.exists() }

    /**
     * Creates the default Pulse folder at /Music/PulseApp/. Safe to call even
     * if it already exists. Returns true if the folder is now usable.
     */
    fun createPulseFolder(): Boolean {
        val target = pulseFolderCandidates().first()
        return try {
            target.exists() || target.mkdirs()
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Scans all audio files inside the Pulse folder. Returns an empty list if
     * the folder doesn't exist or is empty.
     */
    suspend fun scanAll(): List<Song> = withContext(Dispatchers.IO) {
        if (!pulseFolderExists()) return@withContext emptyList()

        val folders = pulseFolderCandidates().filter { it.exists() }
        val allResults = mutableListOf<Song>()
        val seenPaths = mutableSetOf<String>()
        val seenContentKeys = mutableSetOf<String>()

        for (folder in folders) {
            val folderPath = folder.absolutePath
            queryAudioInFolder(folderPath).forEach { song ->
                val pathKey = song.dataPath.takeIf { it.isNotBlank() }
                val normalizedArtist = song.artist
                    .takeUnless { it.equals("Unknown artist", ignoreCase = true) || it.isBlank() }
                    ?: ""
                val contentKey = "${song.title.lowercase()}|$normalizedArtist|${song.durationMs / 1000}"

                val isDuplicate = (pathKey != null && pathKey in seenPaths) ||
                    contentKey in seenContentKeys
                if (!isDuplicate) {
                    if (pathKey != null) seenPaths.add(pathKey)
                    seenContentKeys.add(contentKey)
                    allResults.add(song)
                }
            }
        }
        allResults
    }

    suspend fun scanByUri(uri: android.net.Uri): Song? = withContext(Dispatchers.IO) {
        context.contentResolver.query(
            uri,
            projection(),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val song = songFromCursor(cursor)
            if (song != null && isInsidePulseFolder(song.dataPath)) song else null
        }
    }

    private fun queryAudioInFolder(folderPath: String): List<Song> {
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} = 1 AND " +
            "${MediaStore.Audio.Media.DURATION} >= 30000 AND " +
            "${MediaStore.Audio.Media.DATA} LIKE ?"
        val args = arrayOf("$folderPath/%")
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        val results = mutableListOf<Song>()
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection(),
            selection,
            args,
            sortOrder,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                songFromCursor(cursor)?.let(results::add)
            }
        }
        return results
    }

    private fun projection() = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.ALBUM_ID,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.DATA,
        MediaStore.Audio.Media.DATE_ADDED,
        MediaStore.Audio.Media.DISPLAY_NAME,
    )

    private fun songFromCursor(cursor: android.database.Cursor): Song? {
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
        val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
        val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
        val displayNameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
        val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)

        val rawPath = cursor.getString(dataCol).orEmpty()
        if (rawPath.isBlank()) return null

        val displayName = cursor.getString(displayNameCol)
        val identity = LocalMetadataParser.normalizeSong(
            rawTitle = cursor.getString(titleCol),
            rawArtist = cursor.getString(artistCol),
            rawAlbum = cursor.getString(albumCol),
            displayName = displayName,
        )

        return Song(
            id = cursor.getLong(idCol),
            title = identity.title,
            artist = identity.artist,
            album = identity.album,
            albumId = cursor.getLong(albumIdCol),
            durationMs = cursor.getLong(durationCol),
            dataPath = rawPath,
            dateAdded = cursor.getLong(dateAddedCol),
        )
    }

    private fun isInsidePulseFolder(path: String): Boolean =
        pulseFolderCandidates()
            .filter { it.exists() }
            .ifEmpty { pulseFolderCandidates().take(1) }
            .any { candidate ->
                path.startsWith(candidate.absolutePath + File.separator) || path == candidate.absolutePath
            }
}
