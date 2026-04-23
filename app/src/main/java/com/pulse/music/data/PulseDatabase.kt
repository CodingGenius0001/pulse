package com.pulse.music.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Song::class, Playlist::class, PlaylistSongCrossRef::class],
    version = 1,
    exportSchema = false,
)
abstract class PulseDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao

    companion object {
        fun create(context: Context): PulseDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                PulseDatabase::class.java,
                "pulse.db",
            )
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
