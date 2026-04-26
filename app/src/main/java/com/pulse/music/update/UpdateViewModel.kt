package com.pulse.music.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pulse.music.PulseApplication
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel shared between the Settings "Check for updates" row and the
 * Home banner. There's only one logical update flow at a time across the
 * whole app, so we use a process-singleton VM (not a per-screen one) — the
 * Settings screen and Home screen both observe the same state.
 */
class UpdateViewModel(
    private val repository: UpdateRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private var activeJob: Job? = null

    /**
     * Hit GitHub. Cancels any in-flight check or download — the user pressed
     * Check again, so we restart fresh.
     */
    fun checkForUpdate() {
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            repository.check().collect { _state.value = it }
        }
    }

    /**
     * Start downloading the available update. Only valid when state is
     * Available; no-op otherwise.
     */
    fun downloadUpdate() {
        val current = _state.value
        if (current !is UpdateState.Available) return
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            repository.download(current.info).collect { _state.value = it }
        }
    }

    /**
     * Hand the downloaded APK to the system installer. Only valid when state
     * is Ready.
     */
    fun installUpdate() {
        val current = _state.value
        if (current !is UpdateState.Ready) return
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            repository.prepareInstall(current.info)
            repository.launchInstall(current.file)
        }
    }

    /**
     * Reset to Idle — useful when dismissing an error or clearing a stale
     * Ready state.
     */
    fun dismiss() {
        activeJob?.cancel()
        _state.value = UpdateState.Idle
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = PulseApplication.get()
                return UpdateViewModel(app.updateRepository) as T
            }
        }
    }
}
