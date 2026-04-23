# Pulse

A clean, native Android music player for files you own. Dark-first UI with
light-mode support, pill-based controls, background playback with lock-screen
controls, and strict folder-based scanning so your library stays curated.

**Stack:** Kotlin · Jetpack Compose · Media3 / ExoPlayer · Room · DataStore · Coil

---

## What's new in v0.2.0

### Bug fixes
- **Strict folder scanning** — only reads from `/Music/Pulse/` or `/Pulse/` on
  the device root. No more WhatsApp voice notes or random ringtones showing up.
  If neither folder exists, the app prompts to create one.
- **Theme toggle actually works** — persists across launches via DataStore,
  Light/Dark/Auto are all wired up and switch the full UI.
- **All buttons functional** — Settings rows, the `+` in Library, the overflow
  menu in Now Playing, theme toggle, profile rename — everything responds.
- **Better fallback for missing album art** — tracks without embedded art now
  show a music-note icon on top of a deterministic gradient tile (one gradient
  per album so the visual identity is stable).

### New features
- **Working search** — real-time filtering across titles, artists, and albums
  with grouped results
- **Create playlist dialog** — `+` button in Library now opens a working dialog
- **Glass-effect mini player** — translucent surface with blur-style look
- **Skip-forward-10s in the mini player** — more useful than "next track" for
  quick seek corrections
- **Custom pill scrubber** — thin progress bar with a vertical pill handle
  that matches the reference design; supports tap-to-seek and drag-to-seek
- **10-second seek as primary transport** — back-10s / PLAY / forward-10s.
  Shuffle, repeat, prev-track, next-track moved to the overflow menu (the
  3-dot button in the top right of Now Playing)
- **Profile name editing** — tap Edit on the profile card in Settings
- **Empty-state CTAs** — when the Pulse folder doesn't exist, the Home screen
  shows a "Create Pulse folder" button; when it exists but is empty, it
  shows the folder path + a "Rescan library" button

### Design refinements
- Theme-aware color tokens via CompositionLocal — every screen adapts to
  light/dark instantly
- Mini player now has 44dp tap targets and a glass-translucent background
- Play pill is visually heavier (28dp icon) so it doesn't look thin

---

## Still not in v0.2.0

- Firebase cloud sync (scaffolded, off by default — see README for turn-on steps)
- Lyrics (would need a lyrics provider — deferred)
- User playlist detail screen (you can create playlists, just can't tap into
  them yet; this is the one remaining known stub)
- Flowing waveform animation on the progress bar (needs FFT of audio stream)
- Profile picture upload (name change works, photo doesn't)

---

## Build & run

Nothing's changed from v0.1 — same setup:

### Prereqs
- Android Studio Ladybug (2024.2) or newer
- JDK 17
- Android device on API 26+ with USB debugging

### Steps

1. **Open the project** in Android Studio (File → Open → select `pulse/`)
2. **Let Gradle sync** (~2 min first time)
3. **Create a Pulse folder** on your phone: `Internal storage/Music/Pulse/`
   Drop some MP3s/FLACs in it
   *(or just install and hit "Create Pulse folder" in the empty state)*
4. **Plug in your phone** with USB debugging on
5. **Hit Run**

---

## Project structure

```
pulse/
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/pulse/music/
│   │   ├── PulseApplication.kt       # Singletons (DB, repo, scanner, prefs)
│   │   ├── MainActivity.kt           # Permission gate + Compose entry
│   │   │
│   │   ├── data/
│   │   │   ├── Song.kt
│   │   │   ├── Playlist.kt
│   │   │   ├── MusicDao.kt
│   │   │   ├── PulseDatabase.kt
│   │   │   ├── MusicRepository.kt
│   │   │   └── UserPreferences.kt     ← NEW in v0.2: DataStore-backed prefs
│   │   │
│   │   ├── scanner/MusicScanner.kt    ← v0.2: strict folder only
│   │   │
│   │   ├── player/
│   │   │   ├── PlayerService.kt       # MediaSessionService
│   │   │   └── PlayerViewModel.kt     # +seekForward10, +seekBack10
│   │   │
│   │   ├── ui/
│   │   │   ├── PulseApp.kt
│   │   │   ├── LibraryViewModel.kt    # +folderState, +userName, +createPulseFolder
│   │   │   ├── theme/
│   │   │   │   ├── Color.kt           ← v0.2: CompositionLocal tokens
│   │   │   │   ├── Theme.kt           ← v0.2: reads persisted ThemePreference
│   │   │   │   └── Type.kt
│   │   │   ├── components/
│   │   │   │   ├── AlbumArt.kt        ← v0.2: music-note fallback
│   │   │   │   ├── AlbumMosaic.kt
│   │   │   │   ├── BottomNav.kt       ← v0.2: glass-effect MiniPlayer
│   │   │   │   └── PillButton.kt
│   │   │   └── screens/
│   │   │       ├── ForYouScreen.kt    ← v0.2: folder-aware empty state
│   │   │       ├── LibraryScreen.kt   ← v0.2: new playlist dialog
│   │   │       ├── NowPlayingScreen.kt ← v0.2: 10s seek transport, custom scrubber, overflow menu
│   │   │       ├── SearchScreen.kt    ← v0.2: real search
│   │   │       └── SettingsScreen.kt  ← v0.2: theme persist, rename, rescan
│   │   │
│   │   └── util/Formatters.kt
│   │
│   └── res/
│
├── gradle/libs.versions.toml
├── build.gradle.kts
└── .github/workflows/build.yml        # CI builds debug APK on push
```

---

## Design decisions worth flagging

- **Shuffle / repeat in the overflow menu.** Reference music players usually
  put these on the transport row, but for a local file player 10-second seek
  is used more often than shuffle. The overflow menu is one tap away and the
  tradeoff gives you a much cleaner main transport.
- **Deterministic gradient + music note for missing art** rather than a flat
  grey icon. A uniform grey library is bland; this way albums stay visually
  distinct even without embedded art.
- **"For you" recs come from your own listening history** (top-played, recently-
  added, most-recent). No streaming catalog, no server — just what's actually
  on your device.

---

## Changelog

### v0.2.0
- Strict Pulse-folder-only scanning
- Theme toggle with DataStore persistence
- Working search
- Create-playlist dialog
- Glass-effect mini player + skip-forward-10s
- 10-second seek as primary transport
- Custom pill scrubber
- Music-note fallback for missing art
- Profile name editing
- Folder-aware empty states

### v0.1.0
- Initial release
- Full UI skeleton for For You / Library / Now Playing / Settings
- Folder-based music scanning via MediaStore
- Background playback with MediaSessionService
- Room persistence for playlists, likes, play counts
- Auto-generated mix cards
- 2×2 album art mosaics for playlist thumbnails
