package com.pulse.music.scanner

import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import com.pulse.music.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Scans the device's MediaStore for audio files inside the dedicated Pulse folder.
 *
 * Key design decision: we ONLY scan /Music/Pulse/ (or /Pulse/ on the root of
 * external storage). No fallback to "all music" — that was picking up WhatsApp
 * voice notes, Discord call recordings, ringtones, and every other random audio
 * file on the device. Strict folder scoping keeps the user's library curated.
 *
 * If the Pulse folder doesn't exist yet, the UI shows an empty state with a
 * "Create Pulse folder" button that calls [createPulseFolder].
 */
class MusicScanner(private val context: Context) {

    /**
     * Resolves the expected Pulse folder locations on this device. We check
     * /Music/PulseApp/ first (our preferred location going forward) and fall
     * back to /Music/Pulse/ and /Pulse/ for users upgrading from v0.2.
     */
    private fun pulseFolderCandidates(): List<File> {
        val musicDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_MUSIC
        )
        val externalRoot = Environment.getExternalStorageDirectory()
        return listOf(
            File(musicDir, "PulseApp"),   // primary — new in v0.4
            File(musicDir, "Pulse"),      // legacy fallback
            File(externalRoot, "Pulse"),  // legacy fallback
        )
    }

    /** The folder we should use — first one that exists, or the default Music/Pulse path. */
    fun preferredPulseFolder(): File = pulseFolderCandidates().firstOrNull { it.exists() }
        ?: pulseFolderCandidates().first() // default: /Music/Pulse/

    /**
     * Pretty-printed version of the preferred folder — strips the
     * /storage/emulated/0/ prefix so users see "Music/Pulse" instead of the
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
     * if it already exists. Returns true if folder is now usable.
     */
    fun createPulseFolder(): Boolean {
        val target = pulseFolderCandidates().first() // /Music/PulseApp/
        return try {
            target.exists() || target.mkdirs()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Scans all audio files inside the Pulse folder. Returns empty list if
     * the folder doesn't exist or is empty — no fallback scanning.
     */
    suspend fun scanAll(): List<Song> = withContext(Dispatchers.IO) {
        if (!pulseFolderExists()) return@withContext emptyList()

        val folders = pulseFolderCandidates().filter { it.exists() }
        val allResults = mutableListOf<Song>()

        // Dedupe by: absolute file path (primary), and by a content key of
        // (title|artist|duration-rounded-to-seconds) as a secondary safety net.
        // Same physical file returned by two queries will collide on path;
        // near-identical files from different scans will collide on content key.
        val seenPaths = mutableSetOf<String>()
        val seenContentKeys = mutableSetOf<String>()

        for (folder in folders) {
            val folderPath = folder.absolutePath
            queryAudioInFolder(folderPath).forEach { song ->
                val pathKey = song.dataPath.takeIf { it.isNotBlank() }
                // Normalise "Unknown artist" / blank to empty so two copies of
                // the same song — one tagged and one not — still deduplicate on
                // title + duration alone.
                val normalisedArtist = song.artist
                    .takeUnless { it.equals("Unknown artist", ignoreCase = true) || it.isBlank() }
                    ?: ""
                val contentKey = "${song.title.lowercase()}|$normalisedArtist|${song.durationMs / 1000}"

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

    private fun queryAudioInFolder(folderPath: String): List<Song> {
        val projection = arrayOf(
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

        // Only real music tracks >= 30s. The IS_MUSIC=1 filter excludes ringtones,
        // notifications, and alarms that MediaStore classifies differently.
        // We match the path prefix so files living in subfolders of Pulse also count.
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} = 1 AND " +
                "${MediaStore.Audio.Media.DURATION} >= 30000 AND " +
                "${MediaStore.Audio.Media.DATA} LIKE ?"
        val args = arrayOf("$folderPath/%")

        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        val results = mutableListOf<Song>()
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            args,
            sortOrder,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val displayNameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val rawTitle = cursor.getString(titleCol)
                val rawArtist = cursor.getString(artistCol)
                val rawAlbum = cursor.getString(albumCol)
                val displayName = cursor.getString(displayNameCol)

                results += Song(
                    id = cursor.getLong(idCol),
                    // Prefer the real title; fall back to filename without extension
                    title = rawTitle?.takeUnless { it.isBlank() }
                        ?: displayName?.substringBeforeLast(".")
                        ?: "Unknown",
                    artist = rawArtist?.takeUnless { it == "<unknown>" || it.isBlank() }
                        ?: "Unknown artist",
                    album = rawAlbum?.takeUnless { it == "<unknown>" || it.isBlank() }
                        ?: "Unknown album",
                    albumId = cursor.getLong(albumIdCol),
                    durationMs = cursor.getLong(durationCol),
                    dataPath = cursor.getString(dataCol) ?: "",
                    dateAdded = cursor.getLong(dateAddedCol),
                )
            }
        }
        return results
    }
}
