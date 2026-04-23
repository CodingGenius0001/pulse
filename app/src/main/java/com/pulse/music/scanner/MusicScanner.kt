package com.pulse.music.scanner

import android.content.Context
import android.provider.MediaStore
import com.pulse.music.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Scans the device's MediaStore for audio files.
 *
 * MediaStore is the right mechanism on modern Android — it respects scoped storage,
 * updates automatically when files are added/removed, and gives us access to any
 * audio file regardless of where it's stored.
 *
 * If [folderFilter] is non-null, only songs whose absolute path contains that
 * substring will be returned. Default folder: "/Pulse/" — so dropping files into
 * any `Pulse` folder on your device will surface them.
 */
class MusicScanner(private val context: Context) {

    /**
     * Scans all audio files. If the Pulse folder has nothing in it, falls back
     * to showing everything — so a first-time user isn't greeted by an empty screen.
     */
    suspend fun scanAll(folderFilter: String? = "/Pulse/"): List<Song> =
        withContext(Dispatchers.IO) {
            val filtered = queryAudio(folderFilter)
            if (filtered.isNotEmpty() || folderFilter == null) {
                filtered
            } else {
                // Folder empty — show the user everything so the app isn't useless
                queryAudio(null)
            }
        }

    private fun queryAudio(folderFilter: String?): List<Song> {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED,
        )

        // Only real music tracks longer than 30 seconds (skip voicemails, ringtones, etc.)
        val baseSelection = "${MediaStore.Audio.Media.IS_MUSIC} = 1 AND " +
                "${MediaStore.Audio.Media.DURATION} >= 30000"
        val selection: String
        val args: Array<String>?
        if (folderFilter != null) {
            selection = "$baseSelection AND ${MediaStore.Audio.Media.DATA} LIKE ?"
            args = arrayOf("%$folderFilter%")
        } else {
            selection = baseSelection
            args = null
        }

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

            while (cursor.moveToNext()) {
                results += Song(
                    id = cursor.getLong(idCol),
                    title = cursor.getString(titleCol) ?: "Unknown",
                    artist = cursor.getString(artistCol)?.takeUnless { it == "<unknown>" }
                        ?: "Unknown artist",
                    album = cursor.getString(albumCol) ?: "Unknown album",
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
