# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Pulse is a single-module (`:app`) native Android music player for locally owned audio files. It's written in Kotlin, Jetpack Compose and Material 3, with Media3/ExoPlayer, Room, DataStore, WorkManager, Coil, OkHttp and kotlinx.serialization. It enriches weak or missing tags with open metadata: MusicBrainz + Cover Art Archive, LRCLIB, TheAudioDB, and Genius as a fallback. Package: `com.pulse.music`. minSdk 26, target/compile 35, JVM 17.

## Commands

```bash
./gradlew :app:assembleDebug          # debug APK -> app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:installDebug           # install on a connected device
./gradlew :app:lint                   # Android lint (the only linter configured)
./gradlew :app:testDebugUnitTest      # JVM unit tests
./gradlew :app:testDebugUnitTest --tests 'com.pulse.music.data.LocalMetadataParserTest'          # one class
./gradlew :app:testDebugUnitTest --tests 'com.pulse.music.data.LocalMetadataParserTest.*filename*' # pattern
```

- The Gradle wrapper (8.7) is committed, but CI still regenerates it with `gradle wrapper --gradle-version 8.7`. AGENTS.md says the wrapper isn't committed and that no tests exist; both statements are out of date.
- The project was set up on Windows. `local.properties` has a Windows `sdk.dir`, and the committed files use LF line endings. Don't convert files to CRLF, because that shows up as whole-file diffs.
- Optional `local.properties` keys: `GENIUS_ACCESS_TOKEN=...`, which falls back to the env var and then `""`. Without a token, the app works but skips Genius lookups.
- Optional `app/keystore/debug.jks` (gitignored): a fixed debug signing key, so successive builds install as upgrades. The README has the setup steps and the public default passwords.
- Unit tests are JUnit 4, live in `app/src/test/java/...`, and use backtick-named test functions. `MetadataRepository` exposes `internal` helpers (`candidateInputsFor`, `repositorySearchInputsFor`) so tests can reach them.

## Architecture

**No DI framework.** `PulseApplication` holds lazily created singletons: `PulseDatabase`, `MusicScanner`, `MusicRepository`, `MetadataRepository`, `LyricsRepository`, `UserPreferences` and `UpdateRepository`, plus `appScope` for writes that must outlive a ViewModel. Code reaches them via `PulseApplication.get()`. ViewModels use hand-written `Factory` companions. `ui/PulseApp.kt` creates `LibraryViewModel`, `PlayerViewModel` and `UpdateViewModel` once at the app root and passes them down, so screens don't create their own copies.

**Navigation and UI plumbing (`ui/PulseApp.kt`).** The tabs are ForYou, Library, Search and Settings (`Destination`). Detail pages are NavHost routes in `Routes` (album/artist by `LibraryIndex` key, playlist by id, Liked, Recently added), all rendered by `ui/screens/CollectionScreen.kt`. NowPlaying and Queue are overlays, not routes. The root provides three CompositionLocals from `ui/components/SongComponents.kt`:
- `LocalSongMetadata`: every song's `SongMetadata` from one Room subscription. Use `song.display()` (backed by `data/SongDisplay.kt`'s `displayWith`, which the notification also uses) to show a title, artist or album. Never show raw `song.title` in the UI, and never pass display values back into `MetadataRepository`, which matches on raw tags. Album and artist grouping (`LibraryIndex.albumNameFor` / `artistNameFor`) is tag-first instead, because providers resolve one album to several variants.
- `LocalSongActions`: play, the song menu sheet, play next, queue, like, add to playlist, go to album/artist. Use `SongListRow` for song lists.
- `LocalCurrentSongId`: drives the now-playing highlight.

The root collects only `currentSong`/`isPlaying` from the player. Anything that reads playback position must collect it inside its own composable (see `MiniPlayerHost`), or the whole app recomposes twice a second.

**Library scanning.** `scanner/MusicScanner` queries MediaStore (including `TRACK`, stored as `Song.trackNumber`) for music of at least 30s, and only inside `Music/PulseApp/`, with the legacy fallbacks `Music/Pulse/` and `/Pulse/`. Strict folder scoping is deliberate. `MusicRepository` never deletes songs on a rescan. It marks everything `isAvailable = false`, then re-marks the songs it finds. That way, a temporarily empty MediaStore during an update or reopen doesn't cascade-delete playlists, likes, metadata or lyrics. `LibraryViewModel` throttles work: rescans run every 6h, auto metadata refresh every 12h.

**Metadata pipeline (`data/MetadataRepository.kt`, the most complex file).** `resolve(song)` steps:
1. Normalizes local tags. `LocalMetadataParser` handles "Artist - Title" filenames, junk tags, and similar cases.
2. Builds candidate inputs.
3. Recovers identity hints from LRCLIB.
4. Searches MusicBrainz for recordings in parallel and scores the candidates on title, artist, album and duration against minimum shortlist and accept thresholds.
5. Falls back to TheAudioDB and Genius for identity and artwork.

Matching is intentionally conservative: when it isn't confident, it leaves a track unresolved rather than locking in a wrong match. Plain `resolve(song)` calls for the same song share one in-flight lookup. `AlbumArt` deliberately never triggers lookups; that is the job of the enrichment queue, Now Playing and the player. `SongMetadata` (one row per song) records separate attempt and resolved timestamps for identity, artwork and lyrics, so retries only redo the stage that's missing. `preserveUsefulExisting` keeps good earlier results when a later retry misses. User corrections are stored as `override*` fields and win over automatic matching across rescans. `LibraryViewModel.launchLibraryEnrichment` runs the background queue, using `needsBackgroundEnrichment` / `needsBackgroundFetch` to decide which songs to process.

**Lyrics.** `LyricsRepository` caches LRCLIB results in Room. It prefers metadata-resolved title/artist/album over raw tags, and cascades from LRCLIB `/api/get` to `/search` with duration tolerance. `lyrics/LyricsResult.kt` parses LRC timestamps for synced highlighting.

**Album art.** `ui/components/AlbumArt.kt` observes cached metadata and tries the remote `artworkUrl` first, then embedded art, then a gradient + note placeholder chosen deterministically per album. `util/ArtworkColorExtractor` (Palette) turns the current artwork into an app-wide accent theme.

**Playback.** `player/PlayerService` is a Media3 `MediaSessionService` that owns ExoPlayer. `PlayerViewModel` controls it only through a `MediaController` and a `SessionToken`.
- Shuffle reorders the playlist around the current item and keeps the original order for un-shuffling. The player's own `shuffleModeEnabled` stays false, so `PlaybackState.queue` is always the real play order.
- On connect, the queue is rebuilt from the controller's media items, because the service can outlive the activity. On a cold start, the `PlaybackSession` saved in `UserPreferences` is restored, paused.
- Play counts skip `PLAYLIST_CHANGED` transitions that keep the same media id, because metadata refreshes call `replaceMediaItem`.

**Room.** `PulseDatabase` is at schema version 8 and uses explicit migrations, never destructive fallback. Any entity change needs a version bump plus a new `Migration` added to `.addMigrations(...)`. Without that, users lose their playlists and likes on update. Use `@Upsert` to write songs, never `@Insert(REPLACE)`: REPLACE deletes the row first, and `playlist_song_cross_ref`'s `ON DELETE CASCADE` then wipes the song's playlist entries. If the scanner starts reading new columns, bump `LIBRARY_SCAN_FORMAT` in `LibraryViewModel` to force one rescan.

**In-app updater (`update/`).** This is coupled to CI.
- Every push to `main` publishes a versioned release `build-<run>-<sha>` named "Build #N (sha)", and recreates a floating `latest` release containing `pulse-debug.apk`.
- `BuildConfig.BUILD_NUMBER` comes from `GITHUB_RUN_NUMBER` and is 0 locally. `GitHubReleasesApi` parses "Build #N" from the release and compares it with that number.
- `UpdateCheckWorker` runs periodic checks through WorkManager.
- Changing release naming, asset names, or the `versionName = "..."` line format in `app/build.gradle.kts` breaks the updater or the CI step that reads the version.

**README is shipped in the APK.** The `syncReadmeToAssets` task copies `README.md` into the app's assets on every build. After an update, `UpdateRepository.extractChangelogSection` shows the section under the `### v<versionName>` heading in README's Changelog. When bumping `versionName` and `versionCode`, add a matching `### vX.Y.Z` changelog entry.

## Conventions (from AGENTS.md)

- 4-space Kotlin, official code style. Keep code package-scoped by responsibility (`ui/`, `player/`, `data/`, `network/`, `scanner/`, `update/`). Use the suffixes `Screen`, `ViewModel`, `Repository`, `Dao` and `Api`.
- Commits use short imperative subjects, with an optional prefix such as `Fix:` or `Test:`. Version bumps use `vX.Y.Z: ...`.
- If you change build, signing or updater logic, state how you verified it.
