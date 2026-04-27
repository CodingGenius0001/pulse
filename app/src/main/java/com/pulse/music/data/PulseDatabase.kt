package com.pulse.music.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Song::class,
        Playlist::class,
        PlaylistSongCrossRef::class,
        SongMetadata::class,
        SongLyrics::class,
    ],
    version = 5,
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
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                .build()
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE song_metadata ADD COLUMN overrideTitle TEXT")
                db.execSQL("ALTER TABLE song_metadata ADD COLUMN overrideArtist TEXT")
                db.execSQL("ALTER TABLE song_metadata ADD COLUMN overrideAlbum TEXT")
                db.execSQL("ALTER TABLE song_metadata ADD COLUMN overrideAppliedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!db.hasColumn("song_metadata", "fetchedAt")) {
                    db.execSQL("ALTER TABLE song_metadata ADD COLUMN fetchedAt INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("UPDATE song_metadata SET fetchedAt = strftime('%s','now') * 1000 WHERE fetchedAt = 0")
                }
            }
        }

        private fun SupportSQLiteDatabase.hasColumn(table: String, column: String): Boolean {
            query("PRAGMA table_info($table)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (nameIndex >= 0 && cursor.getString(nameIndex) == column) {
                        return true
                    }
                }
            }
            return false
        }
    }
}
