package com.pulse.music.update

import android.content.Context
import androidx.work.Data
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class UpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val force = inputData.getBoolean(KEY_FORCE_CHECK, false)
            if (force) {
                UpdateRepository(applicationContext).checkForAppOpenUpdate(force = true)
            } else {
                UpdateRepository(applicationContext).checkForScheduledUpdate()
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val KEY_FORCE_CHECK = "force_check"

        fun forceInput(): Data = Data.Builder()
            .putBoolean(KEY_FORCE_CHECK, true)
            .build()
    }
}
