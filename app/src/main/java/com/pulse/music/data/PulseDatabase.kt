package com.pulse.music.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Song::class,
        Playlist::class,
        PlaylistSongCrossRef::class,
        SongMetadata::class,
        SongLyrics::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class PulseDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao
    abstract fun metadataDao(): MetadataDao
    abstract fun lyricsDao(): LyricsDao

    companion object {
        fun create(context: Context): PulseDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                PulseDatabase::class.java,
                "pulse.db",
            )
                // Destructive migration is fine at this stage — if the schema
                // version bumps, we wipe the DB and rescan. Users lose likes
                // and playlists on app updates; acceptable tradeoff for pre-1.0.
                // Swap to proper Migration objects before a real release.
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
