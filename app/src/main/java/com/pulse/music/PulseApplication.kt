package com.pulse.music

import android.app.Application
import com.pulse.music.data.LyricsRepository
import com.pulse.music.data.MetadataRepository
import com.pulse.music.data.MusicRepository
import com.pulse.music.data.PulseDatabase
import com.pulse.music.data.UserPreferences
import com.pulse.music.scanner.MusicScanner
import com.pulse.music.update.UpdateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application class. Holds singleton instances of the database and repositories
 * so they can be shared across ViewModels without a DI framework.
 */
class PulseApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: PulseDatabase by lazy { PulseDatabase.create(this) }
    val scanner: MusicScanner by lazy { MusicScanner(this) }
    val repository: MusicRepository by lazy {
        MusicRepository(
            dao = database.musicDao(),
            scanner = scanner,
            metadataDao = database.metadataDao(),
            lyricsDao = database.lyricsDao(),
        )
    }
    val userPreferences: UserPreferences by lazy { UserPreferences(this) }

    val metadataRepository: MetadataRepository by lazy {
        MetadataRepository(database.metadataDao())
    }
    val lyricsRepository: LyricsRepository by lazy {
        LyricsRepository(
            lyricsDao = database.lyricsDao(),
            metadataDao = database.metadataDao(),
        )
    }

    val updateRepository: UpdateRepository by lazy { UpdateRepository(this) }

    override fun onCreate() {
        super.onCreate()
        INSTANCE = this
        appScope.launch {
            updateRepository.syncBackgroundChecks()
        }
    }

    companion object {
        private var INSTANCE: PulseApplication? = null
        fun get(): PulseApplication =
            INSTANCE ?: error("PulseApplication not initialized")
    }
}
