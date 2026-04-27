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
    version = 6,
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
                .addMigrations(MIGRATION_1_5, MIGRATION_2_5, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .build()
        }

        private val MIGRATION_1_5 = object : Migration(1, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                migrateLegacySchemaToV5(db)
            }
        }

        private val MIGRATION_2_5 = object : Migration(2, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                migrateLegacySchemaToV5(db)
            }
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

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!db.hasColumn("song_metadata", "identityResolvedAt")) {
                    db.execSQL("ALTER TABLE song_metadata ADD COLUMN identityResolvedAt INTEGER NOT NULL DEFAULT 0")
                }
                if (!db.hasColumn("song_metadata", "artworkAttemptedAt")) {
                    db.execSQL("ALTER TABLE song_metadata ADD COLUMN artworkAttemptedAt INTEGER NOT NULL DEFAULT 0")
                }
                if (!db.hasColumn("song_metadata", "artworkResolvedAt")) {
                    db.execSQL("ALTER TABLE song_metadata ADD COLUMN artworkResolvedAt INTEGER NOT NULL DEFAULT 0")
                }
                if (!db.hasColumn("song_metadata", "lyricsAttemptedAt")) {
                    db.execSQL("ALTER TABLE song_metadata ADD COLUMN lyricsAttemptedAt INTEGER NOT NULL DEFAULT 0")
                }
                if (!db.hasColumn("song_metadata", "lyricsResolvedAt")) {
                    db.execSQL("ALTER TABLE song_metadata ADD COLUMN lyricsResolvedAt INTEGER NOT NULL DEFAULT 0")
                }
                db.execSQL(
                    """
                    UPDATE song_metadata
                    SET identityResolvedAt = CASE
                        WHEN identityResolvedAt = 0 AND (
                            COALESCE(resolvedTitle, '') != '' OR
                            COALESCE(resolvedArtist, '') != '' OR
                            COALESCE(resolvedAlbum, '') != ''
                        ) THEN fetchedAt
                        ELSE identityResolvedAt
                    END
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    UPDATE song_metadata
                    SET artworkAttemptedAt = CASE
                        WHEN artworkAttemptedAt = 0 THEN fetchedAt
                        ELSE artworkAttemptedAt
                    END,
                    artworkResolvedAt = CASE
                        WHEN artworkResolvedAt = 0 AND COALESCE(artworkUrl, '') != '' THEN fetchedAt
                        ELSE artworkResolvedAt
                    END
                    """.trimIndent()
                )
            }
        }

        private fun migrateLegacySchemaToV5(db: SupportSQLiteDatabase) {
            if (db.hasTable("songs")) {
                if (!db.hasColumn("songs", "playCount")) {
                    db.execSQL("ALTER TABLE songs ADD COLUMN playCount INTEGER NOT NULL DEFAULT 0")
                }
                if (!db.hasColumn("songs", "lastPlayedAt")) {
                    db.execSQL("ALTER TABLE songs ADD COLUMN lastPlayedAt INTEGER NOT NULL DEFAULT 0")
                }
                if (!db.hasColumn("songs", "liked")) {
                    db.execSQL("ALTER TABLE songs ADD COLUMN liked INTEGER NOT NULL DEFAULT 0")
                }
            }

            if (db.hasTable("playlists") && !db.hasColumn("playlists", "systemType")) {
                db.execSQL("ALTER TABLE playlists ADD COLUMN systemType TEXT")
            }

            if (db.hasTable("playlist_song_cross_ref") && !db.hasColumn("playlist_song_cross_ref", "addedAt")) {
                db.execSQL("ALTER TABLE playlist_song_cross_ref ADD COLUMN addedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "UPDATE playlist_song_cross_ref SET addedAt = strftime('%s','now') * 1000 WHERE addedAt = 0"
                )
            }

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS song_metadata (
                    songId INTEGER NOT NULL PRIMARY KEY,
                    geniusId INTEGER,
                    geniusUrl TEXT,
                    resolvedTitle TEXT,
                    resolvedArtist TEXT,
                    resolvedAlbum TEXT,
                    artworkUrl TEXT,
                    releaseDate TEXT,
                    overrideTitle TEXT,
                    overrideArtist TEXT,
                    overrideAlbum TEXT,
                    overrideAppliedAt INTEGER NOT NULL DEFAULT 0,
                    identityResolvedAt INTEGER NOT NULL DEFAULT 0,
                    artworkAttemptedAt INTEGER NOT NULL DEFAULT 0,
                    artworkResolvedAt INTEGER NOT NULL DEFAULT 0,
                    lyricsAttemptedAt INTEGER NOT NULL DEFAULT 0,
                    lyricsResolvedAt INTEGER NOT NULL DEFAULT 0,
                    fetchedAt INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            db.execSQL("UPDATE song_metadata SET fetchedAt = strftime('%s','now') * 1000 WHERE fetchedAt = 0")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS song_lyrics (
                    songId INTEGER NOT NULL PRIMARY KEY,
                    syncedLyrics TEXT,
                    plainLyrics TEXT,
                    instrumental INTEGER NOT NULL DEFAULT 0,
                    notFound INTEGER NOT NULL DEFAULT 0,
                    fetchedAt INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            db.execSQL("UPDATE song_lyrics SET fetchedAt = strftime('%s','now') * 1000 WHERE fetchedAt = 0")
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

        private fun SupportSQLiteDatabase.hasTable(table: String): Boolean {
            query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                arrayOf(table),
            ).use { cursor ->
                return cursor.moveToFirst()
            }
        }
    }
}
