package com.pulse.music.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import com.pulse.music.BuildConfig
import com.pulse.music.MainActivity
import com.pulse.music.R
import com.pulse.music.data.PendingUpdateInfo
import com.pulse.music.network.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.Request
import java.io.File

/**
 * Top-level state of the in-app updater. Used by Settings + the Home banner
 * to drive their UI.
 */
sealed interface UpdateState {
    /** No check has been run yet (this session). */
    data object Idle : UpdateState

    /** A network check is in progress. */
    data object Checking : UpdateState

    /** Latest release is the one we're already running. */
    data object UpToDate : UpdateState

    /** A newer build is available — show details, allow Download. */
    data class Available(val info: UpdateInfo) : UpdateState

    /** APK is downloading. [progress] is 0.0–1.0. */
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState

    /** Download finished; APK is at [file] and ready to hand to PackageInstaller. */
    data class Ready(val info: UpdateInfo, val file: File) : UpdateState

    /** Something went wrong. */
    data class Error(val message: String) : UpdateState
}

/**
 * Coordinates the update lifecycle. Pure logic + IO; the UI subscribes to
 * the [stateFlow]-style returns.
 *
 * Why we don't keep an internal MutableStateFlow: the UI flow is short-lived
 * (the user opens Settings, taps "Check", sees a result) and the
 * download-with-progress is a one-shot per tap. Modeling each operation as
 * its own Flow is simpler than fighting state-machine reentrance.
 */
class UpdateRepository(private val context: Context) {

    data class PendingChangelog(
        val versionName: String,
        val buildNumber: Int,
        val body: String,
    )

    suspend fun checkForAppOpenUpdateNotification() {
        val prefs = PulseApplicationHolder.preferences(context)
        if (!prefs.updateNotificationsEnabled.first()) return

        val now = System.currentTimeMillis()
        val lastCheck = prefs.updateNotificationsLastCheck.first()
        if (now - lastCheck < UPDATE_CHECK_INTERVAL_MS) return
        prefs.setUpdateNotificationsLastCheck(now)

        val info = GitHubReleasesApi.fetchTaggedRelease(
            repo = BuildConfig.GITHUB_REPO,
            tag = BuildConfig.RELEASE_TAG,
        ) ?: return

        val installedBuild = BuildConfig.BUILD_NUMBER
        val alreadyNotifiedBuild = prefs.updateNotificationsLastNotifiedBuild.first()
        if (info.buildNumber <= 0 || info.buildNumber <= installedBuild || info.buildNumber <= alreadyNotifiedBuild) {
            return
        }
        if (!canPostNotifications()) return

        postUpdateNotification(info)
        prefs.setUpdateNotificationsLastNotifiedBuild(info.buildNumber)
    }

    /**
     * Hits the GitHub API, compares the release's build number to the one
     * baked into this APK at build time, and emits the resolved state.
     *
     * Emits [UpdateState.Checking] first, then exactly one terminal state.
     */
    fun check(): Flow<UpdateState> = flow {
        emit(UpdateState.Checking)

        val info = GitHubReleasesApi.fetchTaggedRelease(
            repo = BuildConfig.GITHUB_REPO,
            tag = BuildConfig.RELEASE_TAG,
        )

        if (info == null) {
            emit(UpdateState.Error("Couldn't reach GitHub. Check your connection and try again."))
            return@flow
        }

        // Compare against the build number baked into THIS APK. If the remote
        // build is newer, offer the update; otherwise we're up-to-date.
        // Build number 0 means "unknown" (e.g. local debug build with no CI
        // context) — we treat that as always-allow-update so people running
        // hand-built debug APKs can still upgrade to the canonical CI build.
        val installedBuild = BuildConfig.BUILD_NUMBER
        when {
            installedBuild == 0 -> emit(UpdateState.Available(info))
            info.buildNumber > installedBuild -> emit(UpdateState.Available(info))
            else -> emit(UpdateState.UpToDate)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Downloads the APK to <cache>/updates/pulse-debug.apk, emitting progress
     * along the way. The cache dir is the right home for this:
     *  - It's app-private (can't be tampered with by other apps)
     *  - It's a child of the path declared in file_provider_paths.xml so we
     *    can hand a content:// URI to PackageInstaller
     *  - It auto-clears when the OS needs space, so a stale download won't
     *    haunt us forever
     *
     * Emits Downloading(progress=…) repeatedly, then exactly one Ready or Error.
     */
    fun download(info: UpdateInfo): Flow<UpdateState> = flow {
        emit(UpdateState.Downloading(info, 0f))

        val updatesDir = File(context.cacheDir, "updates")
        if (!updatesDir.exists() && !updatesDir.mkdirs()) {
            emit(UpdateState.Error("Couldn't prepare update folder."))
            return@flow
        }
        val outputFile = File(updatesDir, "pulse-debug.apk")
        // Wipe any previous download — fresh state every time.
        outputFile.delete()

        val request = Request.Builder()
            .url(info.downloadUrl)
            .header("User-Agent", "Pulse-Android-Updater")
            .get()
            .build()

        try {
            HttpClient.instance.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    emit(UpdateState.Error("Download failed (HTTP ${response.code})."))
                    return@flow
                }
                val body = response.body ?: run {
                    emit(UpdateState.Error("Empty response body."))
                    return@flow
                }

                // Total size: prefer Content-Length when present, fall back
                // to the size we already read from the API (info.sizeBytes).
                val total = body.contentLength().takeIf { it > 0 } ?: info.sizeBytes

                body.byteStream().use { input ->
                    outputFile.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var downloaded = 0L
                        var lastEmitted = -1f
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read

                            if (total > 0) {
                                val progress = (downloaded.toFloat() / total).coerceIn(0f, 1f)
                                // Only emit when the progress moves at least 1%
                                // to avoid spamming the UI.
                                if (progress - lastEmitted >= 0.01f) {
                                    emit(UpdateState.Downloading(info, progress))
                                    lastEmitted = progress
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            emit(UpdateState.Error("Download interrupted: ${e.message}"))
            return@flow
        }

        emit(UpdateState.Ready(info, outputFile))
    }.flowOn(Dispatchers.IO)

    /**
     * Hands the downloaded APK to Android's PackageInstaller. Android shows
     * its standard install confirmation UI; from the user's perspective the
     * app then closes and reopens as the new version.
     *
     * The user must have already granted "Install unknown apps" for Pulse
     * (one-time settings trip — Android handles this dialog itself if not
     * granted).
     */
    fun launchInstall(file: File) {
        val authority = "${context.packageName}.fileprovider"
        val uri: Uri = FileProvider.getUriForFile(context, authority, file)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    suspend fun prepareInstall(info: UpdateInfo) {
        PulseApplicationHolder.preferences(context).setPendingUpdateInfo(
            PendingUpdateInfo(
                buildNumber = info.buildNumber,
                versionName = parseVersionName(info.releaseNotes),
                releaseNotes = info.releaseNotes,
            ),
        )
    }

    suspend fun pendingChangelogForCurrentInstall(): PendingChangelog? {
        val pending = PulseApplicationHolder.preferences(context).pendingUpdateInfo.first() ?: return null
        if (pending.buildNumber <= 0 || BuildConfig.BUILD_NUMBER < pending.buildNumber) return null

        val resolvedVersionName = pending.versionName.ifBlank { BuildConfig.VERSION_NAME }
        val versionSection = pending.versionName
            .takeIf { it.isNotBlank() }
            ?.let(::extractChangelogSection)
        val releaseSummary = pending.releaseNotes.toChangelogSummary()
        val body = when {
            !versionSection.isNullOrBlank() && !releaseSummary.isNullOrBlank() ->
                "$releaseSummary\n\nRelease summary\n$versionSection"
            !versionSection.isNullOrBlank() -> versionSection
            !releaseSummary.isNullOrBlank() -> releaseSummary
            else -> return null
        }

        return PendingChangelog(
            versionName = resolvedVersionName,
            buildNumber = pending.buildNumber,
            body = body.trim(),
        )
    }

    suspend fun clearPendingChangelog() {
        PulseApplicationHolder.preferences(context).clearPendingUpdateInfo()
    }

    private fun extractChangelogSection(versionName: String): String? {
        val readme = try {
            context.assets.open("README.md").bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            return null
        }
        val marker = "### v$versionName"
        val start = readme.indexOf(marker)
        if (start < 0) return null
        val afterHeader = readme.indexOf('\n', start).takeIf { it >= 0 } ?: return null
        val end = readme.indexOf("\n### ", afterHeader + 1).takeIf { it >= 0 } ?: readme.length
        return readme.substring(afterHeader + 1, end).trim()
    }

    private fun parseVersionName(releaseNotes: String): String =
        Regex("""(?:Version:\s*|v)(\d+\.\d+\.\d+)""", RegexOption.IGNORE_CASE)
            .find(releaseNotes)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun postUpdateNotification(info: UpdateInfo) {
        ensureNotificationChannel()

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN_ROUTE, com.pulse.music.ui.components.Destination.Settings.route)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            UPDATE_NOTIFICATION_REQUEST_CODE,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val body = buildString {
            append("Build #").append(info.buildNumber).append(" is ready")
            val version = parseVersionName(info.releaseNotes)
            if (version.isNotBlank()) {
                append(" • v").append(version)
            }
        }

        val notification = NotificationCompat.Builder(context, UPDATE_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Pulse update available")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$body. Open Pulse to review and install."))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        NotificationManagerCompat.from(context).notify(UPDATE_NOTIFICATION_ID, notification)
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val channel = NotificationChannel(
            UPDATE_NOTIFICATION_CHANNEL_ID,
            "Pulse updates",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Release notifications for new Pulse builds"
        }
        manager.createNotificationChannel(channel)
    }

    private fun String.toChangelogSummary(): String? {
        val lines = lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot { it.startsWith("1.") || it.startsWith("2.") || it.startsWith("3.") }
            .filterNot { it.startsWith("**Install:", ignoreCase = true) }
            .filterNot { it.startsWith("Permanent download link", ignoreCase = true) }
            .filterNot { it.startsWith("https://github.com/", ignoreCase = true) }
            .filterNot { it.startsWith("Last updated from commit", ignoreCase = true) }
            .toList()
        if (lines.isEmpty()) return null

        val commitMessage = lines.firstOrNull { it.startsWith("Commit message:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.trim()
        val buildLine = lines.firstOrNull { it.startsWith("Build #", ignoreCase = true) }
        val versionLine = lines.firstOrNull { it.startsWith("Version:", ignoreCase = true) }
        val commitLine = lines.firstOrNull { it.startsWith("Commit:", ignoreCase = true) }

        val summaryLines = buildList {
            buildLine?.let { add(it) }
            versionLine?.let { add(it) }
            commitLine?.let { add(it) }
            commitMessage?.takeIf { it.isNotBlank() }?.let { add("Change: $it") }
        }
        return summaryLines.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }

    private companion object {
        const val UPDATE_CHECK_INTERVAL_MS = 12L * 60L * 60L * 1000L
        const val UPDATE_NOTIFICATION_CHANNEL_ID = "pulse_updates"
        const val UPDATE_NOTIFICATION_ID = 5015
        const val UPDATE_NOTIFICATION_REQUEST_CODE = 1515
    }
}

private object PulseApplicationHolder {
    fun preferences(context: Context) = com.pulse.music.PulseApplication.get().userPreferences
}
