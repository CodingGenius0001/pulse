# Pulse

A clean, native Android music player for files you own. Dark-first UI, pill-based
controls, background playback with lock-screen media controls.

**Stack:** Kotlin · Jetpack Compose · Media3 / ExoPlayer · Room · Coil

---

## What's in v0.1.0

- **Scans your music folder** — reads audio files via MediaStore, prioritizes any
  folder named `Pulse` on the device, falls back to all music if empty
- **Four screens** — For you (home), Library (Playlists/Albums/Artists/Songs),
  Now Playing (full-screen player), Settings
- **Background playback** — `MediaSessionService` with notification + lock-screen
  controls, audio focus, pause-on-headphones-unplug
- **Persistent state** — Room database stores songs, play counts, likes, and
  playlists; survives app restarts
- **Auto-generated mixes** — "On repeat", "Fresh mix", "All music" built from
  your listening data
- **Playlist thumbnails** — 2×2 mosaics auto-composed from album art of the
  songs inside each playlist
- **Gradient fallback** — songs without embedded album art get deterministic
  colored tiles so the UI never breaks

### Not yet in v0.1.0

- Firebase sign-in + cloud sync (scaffolded, turned off — see below)
- Search
- Light theme is wired but the toggle doesn't persist yet
- Profile picture upload
- Lyrics/queue/output detail screens (the buttons exist, they're just stubs)

---

## Build & run

### Prereqs

- **Android Studio Ladybug (2024.2)** or newer
- **JDK 17** (Android Studio bundles this)
- **A physical Android device** running Android 8.0 (API 26) or later, with
  **USB debugging enabled** (Settings → Developer options → USB debugging)

### Steps

1. **Open in Android Studio:** File → Open → select the `pulse/` folder
2. **Let Gradle sync** — it'll download dependencies (~2 minutes first time).
   If it asks about the Gradle wrapper, let it generate one.
3. **Put some music on your phone:**
   - Connect your phone via USB, transfer MP3/FLAC/M4A files to
     `Internal storage/Music/Pulse/` (Pulse looks here first)
   - Or drop them anywhere under `Music/` — Pulse falls back to scanning all
     music if the `Pulse` folder is empty
4. **Plug in your phone**, make sure Android Studio sees it in the device dropdown
5. **Hit Run** (the green triangle). First install takes ~30 seconds.
6. **Grant the "Music and audio" permission** when prompted — this lets Pulse
   read your audio files.
7. **Tap a song** to play. Background playback, lock-screen controls, and the
   notification should all just work.

### If nothing shows up

- Check that `READ_MEDIA_AUDIO` permission was granted (Settings → Apps → Pulse
  → Permissions)
- Go to Settings (in Pulse) → Music folder → "Rescan library"
- Verify your music is under a folder named `Pulse` or at least under `Music/`

---

## Project structure

```
pulse/
├── app/src/main/
│   ├── AndroidManifest.xml          # Permissions, activity, media service
│   ├── java/com/pulse/music/
│   │   ├── PulseApplication.kt       # Singletons (DB, repo, scanner)
│   │   ├── MainActivity.kt           # Permission gate + Compose entry
│   │   │
│   │   ├── data/                     # Room entities + DAO + repository
│   │   │   ├── Song.kt
│   │   │   ├── Playlist.kt
│   │   │   ├── MusicDao.kt
│   │   │   ├── PulseDatabase.kt
│   │   │   └── MusicRepository.kt
│   │   │
│   │   ├── scanner/
│   │   │   └── MusicScanner.kt       # MediaStore query, folder filter
│   │   │
│   │   ├── player/
│   │   │   ├── PlayerService.kt      # MediaSessionService (background)
│   │   │   └── PlayerViewModel.kt    # MediaController + PlaybackState
│   │   │
│   │   ├── ui/
│   │   │   ├── PulseApp.kt           # Root: nav + bottom bar + mini player
│   │   │   ├── LibraryViewModel.kt   # Flows for all library data
│   │   │   │
│   │   │   ├── theme/                # Color, Type, Theme
│   │   │   ├── components/           # AlbumArt, Mosaic, PillButton, BottomNav
│   │   │   └── screens/              # ForYou, Library, NowPlaying, Settings
│   │   │
│   │   └── util/
│   │       └── Formatters.kt         # Duration fmt, gradient generator
│   │
│   └── res/                          # Icons, themes, strings
│
├── gradle/
│   ├── libs.versions.toml            # Version catalog
│   └── wrapper/
├── build.gradle.kts                  # Root
├── settings.gradle.kts
└── app/build.gradle.kts              # App module
```

---

## Architecture notes

### Scanning flow

```
MediaStore → MusicScanner.scanAll()
          → filters to /Pulse/ (or all music if empty)
          → MusicRepository.rescan()
          → merges new metadata with existing Room rows
            (preserves likes + play counts)
          → dao.upsertSongs() + dao.deleteSongsNotIn()
          → UI Flows emit new state automatically
```

### Playback flow

```
UI taps a song → PlayerViewModel.playQueue(songs, index)
             → MediaController.setMediaItems() + prepare() + play()
             → Media3 hands audio to ExoPlayer in PlayerService
             → Service promotes itself to foreground (notification appears)
             → Player.Listener fires → PlayerViewModel updates PlaybackState
             → UI recomposes (Now Playing, mini player)
             → On track transition, dao.markPlayed() increments play count
```

### Why no Hilt / Koin?

Kept DI manual for v1 to reduce build complexity and the number of moving
parts. `PulseApplication.get()` acts as a service locator for the two
`ViewModelProvider.Factory`s. If we add more cross-cutting concerns later,
migrate to Hilt — it's a one-day refactor.

---

## Turning on Firebase cloud sync

The backend is scaffolded but disabled by default so the app builds and runs
without Firebase setup. When you're ready to turn it on:

1. **Create a Firebase project** at [console.firebase.google.com](https://console.firebase.google.com)
2. **Add an Android app** with package name `com.pulse.music`
3. **Get the SHA-1 fingerprint** of your debug keystore:
   ```
   cd ~/.android
   keytool -list -v -keystore debug.keystore -alias androiddebugkey \
     -storepass android -keypass android
   ```
   Paste the SHA-1 into Firebase Console → Project Settings → Your apps.
4. **Download `google-services.json`** from Firebase Console and drop it into
   `app/` (same folder as `build.gradle.kts`)
5. **Enable services you need** in Firebase Console:
   - Authentication → Sign-in method → Google
   - Firestore Database (for playlists/likes sync)
   - Cloud Storage (for profile pictures)
6. **Add the Firebase plugin** to `app/build.gradle.kts`:
   ```kotlin
   plugins {
       id("com.google.gms.google-services")
   }
   dependencies {
       implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
       implementation("com.google.firebase:firebase-auth")
       implementation("com.google.firebase:firebase-firestore")
       implementation("com.google.firebase:firebase-storage")
       implementation("com.google.android.gms:play-services-auth:21.2.0")
   }
   ```
7. And to the root `build.gradle.kts`:
   ```kotlin
   plugins {
       id("com.google.gms.google-services") version "4.4.2" apply false
   }
   ```
8. Wire the Sign-in button in `SettingsScreen.kt` to launch the Google sign-in
   intent. (I'll leave this implementation to v0.2 — it's ~50 lines with the
   Credential Manager API.)

---

## Publishing to Google Play

Eventual steps when you're ready to ship:

1. **Generate a signing keystore** — `Build → Generate Signed Bundle / APK`
   in Android Studio. Back up the keystore somewhere safe; losing it means
   you can't update the app on Play.
2. **Bump `versionCode` and `versionName`** in `app/build.gradle.kts`
3. **Build an AAB** (Android App Bundle) — Play prefers AAB over APK
4. **Register as a Play Console developer** ($25 one-time fee)
5. **Create a store listing** — icon, screenshots, description, privacy policy
   (required — you'll need to host a privacy policy somewhere, even a simple
   GitHub Pages site works)
6. **Content rating** — fill out the questionnaire, should be "Everyone"
7. **Upload to internal testing track first**, test on your phone via the
   internal testers link, then promote to production

Since Pulse reads user-selected files and stores data only in Room + (later)
Firebase, the privacy policy is straightforward: list the permissions you
use (READ_MEDIA_AUDIO, internet for Firebase) and what data you collect
(playlists and play history — only if the user signs in).

---

## Known quirks

- **First scan is blocking** — if you have thousands of songs, the initial
  scan takes a few seconds. Fine for most libraries; add a progress indicator
  in v0.2 if it becomes an issue.
- **MediaController connection is async** — if you tap a song within ~50ms
  of the app launching, it may no-op. Hasn't happened in practice but worth
  knowing.
- **Progress bar uses Material3 Slider** — not the exact thin-bar-with-vertical-pill
  look from the mockup yet. Custom slider is a v0.2 polish item.

---

## Changelog

### v0.1.0 — Initial release
- Full UI for For You / Library / Now Playing / Settings
- Folder-based music scanning via MediaStore
- Background playback with MediaSessionService
- Room persistence for playlists, likes, play counts
- Auto-generated mix cards based on listening history
- 2×2 album art mosaics for playlist thumbnails
