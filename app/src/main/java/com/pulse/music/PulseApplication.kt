package com.pulse.music

import android.app.Application
import com.pulse.music.data.PulseDatabase
import com.pulse.music.data.MusicRepository
import com.pulse.music.scanner.MusicScanner

/**
 * Application class. Holds singleton instances of the database and repository
 * so they can be shared across ViewModels without a DI framework.
 */
class PulseApplication : Application() {

    val database: PulseDatabase by lazy { PulseDatabase.create(this) }
    val scanner: MusicScanner by lazy { MusicScanner(this) }
    val repository: MusicRepository by lazy {
        MusicRepository(database.musicDao(), scanner)
    }

    override fun onCreate() {
        super.onCreate()
        INSTANCE = this
    }

    companion object {
        private var INSTANCE: PulseApplication? = null
        fun get(): PulseApplication =
            INSTANCE ?: error("PulseApplication not initialized")
    }
}
