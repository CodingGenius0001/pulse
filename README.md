# Pulse

A clean, native Android music player for files you own. Dark-first UI with
light-mode support, pill-based controls, background playback with lock-screen
controls, strict folder-based scanning, and Genius-powered metadata
enrichment so your library looks pretty even when your MP3s are missing tags.

**Stack:** Kotlin · Jetpack Compose · Media3 / ExoPlayer · Room · DataStore · Coil · OkHttp + Kotlinx Serialization

---

## Setup before first build (v0.4)

This release adds online metadata + lyrics. Both work without configuration —
the app just falls back to gradient-tile fallbacks and "lyrics not found"
when you don't set up the Genius token.

### Genius API token (recommended)

1. Go to [genius.com/api-clients](https://genius.com/api-clients) and click
   **New API Client**
2. Fill in the form (any URL is fine for local-only use). Click **Save**
3. On the resulting page, click **Generate Access Token** and copy it
4. Open `local.properties` in the repo root (create the file if it doesn't
   exist) and add:
   ```
   GENIUS_ACCESS_TOKEN=YOUR_TOKEN_HERE
   ```
5. **Don't commit it.** `local.properties` is already gitignored. If you
   accidentally push your token, Genius will auto-revoke it within hours.
6. For GitHub Actions: in your repo go to **Settings → Secrets and variables
   → Actions → New repository secret**. Name: `GENIUS_ACCESS_TOKEN`. Value:
   your token. The CI workflow reads it automatically.

If you skip this step, Pulse still works — every song just shows gradient
fallback art and "no metadata" states. Search and playback are unaffected.

### Lyrics (LRCLIB)

Nothing to do — Pulse uses [LRCLIB](https://lrclib.net), a free community
lyrics database, with no token required. The Lyrics button in Now Playing
fetches synced lyrics on first open and caches them in Room. Tracks LRCLIB
doesn't have show "we didn't find lyrics for this one :(".

---

## What's new in v0.4.0

### Major
- **Genius metadata** — when you scan, Pulse looks up each track on Genius
  and caches the result (album art URL, canonical Genius URL, real release
  date) in Room. Thereafter the album-art view prefers the Genius URL, which
  for most popular tracks is significantly better than the embedded ID3 art.
- **LRCLIB lyrics** — Lyrics button in Now Playing fetches synced (LRC
  format) or plain lyrics from LRCLIB. Synced lyrics are highlighted line-
  by-line as the song plays, with auto-scroll to keep the active line near
  the center.
- **Genius URL in Share** — when you share a track, if we have a cached
  Genius URL the share sheet now sends `Title — Artist\nhttps://genius.com/...`
  giving the recipient a real link.
- **Folder renamed to `Music/PulseApp`** — old `Music/Pulse` continues to
  work as a fallback, so nothing breaks for existing users. New folder
  creation goes to `Music/PulseApp`.

### Minor
- Removed 10s seek buttons from the main transport (you said no one uses
  them). Transport is back to the clean 3-button: prev / PLAY / next.
- Overflow menu (shuffle / repeat / share) moved from top-right corner to
  the bottom action bar next to Lyrics and Queue, so it's reachable with
  your thumb.
- Folder path display shortened from `/storage/emulated/0/Music/PulseApp`
  to just `Music/PulseApp` everywhere it appears.
- Profile avatar in the For You header now navigates to Settings on tap.
- Queue screen with tap-to-jump, up/down arrows to reorder, × to remove.
- Subtle decorative wave animation above the scrubber (not real audio FFT —
  just a pretty motion cue, ~free in battery).
- Better duplicate-song dedup: by absolute file path AND by content key
  (title + artist + duration in seconds). Should catch the duplicate-
  scan bug some users hit in v0.2.

### Honest limitations
- **Genius coverage is patchy outside Western pop/rock/hip-hop.** Carnatic,
  Telugu, Malayalam, regional, indie tracks — many won't resolve. That's
  Genius's database, not a Pulse bug.
- **LRCLIB coverage is also patchy.** It's a community database. Popular
  English-language songs usually have synced lyrics. Niche tracks often
  don't.
- **No way to play during phone calls.** OS-level block; can't be worked
  around.
- **User playlist detail screen still missing.** You can create playlists,
  but tapping into one does nothing (one remaining `// TODO` in the code).
- **No `.lrc` file support yet.** The dialog mentions it as a future
  feature; not implemented in v0.4.
- **Debug keystore changes every CI build.** Each fresh APK install
  conflicts with the previous one — uninstall first, install second. Will
  fix with a fixed debug keystore in v0.5.

---

## Build & run

### Local

1. **Open the project** in Android Studio Ladybug or newer (File → Open →
   pick `pulse/`)
2. **Set up your Genius token** (see Setup section above). Or skip and run
   without it — app works fine, just no online metadata.
3. **Let Gradle sync** (~3 min first time)
4. **Plug in your phone** with USB debugging on
5. **Hit Run**

### CI

GitHub Actions builds a debug APK on every push to any branch. The workflow
file is at `.github/workflows/build.yml`. Add the `GENIUS_ACCESS_TOKEN`
secret to your repo and you're set.

---

## Project layout

```
pulse/
├── app/src/main/
│   ├── AndroidManifest.xml         # +INTERNET, +ACCESS_NETWORK_STATE in v0.4
│   └── java/com/pulse/music/
│       ├── PulseApplication.kt     # Singletons (DB, repos, scanner, prefs)
│       ├── MainActivity.kt         # Permission gate + Compose entry
│       │
│       ├── data/
│       │   ├── Song.kt
│       │   ├── Playlist.kt
│       │   ├── MusicDao.kt
│       │   ├── PulseDatabase.kt    ← v0.4: bumped to v2 (metadata + lyrics tables)
│       │   ├── MusicRepository.kt
│       │   ├── UserPreferences.kt
│       │   ├── SongMetadata.kt     ← NEW: cached Genius metadata
│       │   ├── SongLyrics.kt       ← NEW: cached LRCLIB lyrics
│       │   ├── MetadataDao.kt      ← NEW: DAOs for both above
│       │   ├── MetadataRepository.kt ← NEW: cache-or-fetch from Genius
│       │   └── LyricsRepository.kt   ← NEW: cache-or-fetch from LRCLIB
│       │
│       ├── lyrics/
│       │   └── LyricsResult.kt     ← NEW: sealed result + LRC parser
│       │
│       ├── network/                ← NEW package
│       │   ├── HttpClient.kt       # OkHttp singleton
│       │   ├── GeniusApi.kt        # search + songs endpoints
│       │   └── LrcLibApi.kt        # /api/get
│       │
│       ├── scanner/MusicScanner.kt # v0.4: PulseApp folder + dedupe by path+content
│       │
│       ├── player/
│       │   ├── PlayerService.kt
│       │   └── PlayerViewModel.kt
│       │
│       ├── ui/
│       │   ├── PulseApp.kt
│       │   ├── LibraryViewModel.kt # v0.4: enrichMetadataAsync after scans
│       │   ├── theme/
│       │   ├── components/
│       │   │   ├── AlbumArt.kt     ← v0.4: prefers cached Genius artwork
│       │   │   ├── BottomNav.kt
│       │   │   └── PillButton.kt
│       │   └── screens/
│       │       ├── ForYouScreen.kt
│       │       ├── LibraryScreen.kt
│       │       ├── NowPlayingScreen.kt ← v0.4: synced lyrics dialog, share-with-URL
│       │       ├── QueueScreen.kt
│       │       ├── SearchScreen.kt
│       │       └── SettingsScreen.kt
│       │
│       └── util/Formatters.kt
│
├── gradle/libs.versions.toml       # v0.4: +okhttp, +kotlinx-serialization
├── build.gradle.kts                # v0.4: +serialization plugin
├── app/build.gradle.kts            # v0.4: BuildConfig.GENIUS_ACCESS_TOKEN
└── .github/workflows/build.yml     # v0.4: passes token via env
```

---

## How it works

**Metadata enrichment.** When `LibraryViewModel.rescan()` finishes, it walks
every song in the library and calls `MetadataRepository.resolve(song)` for
any song without a cached row. Each `resolve()` hits Genius's `/search`
endpoint with the song's title + artist, picks the top hit, then hits
`/songs/{id}` for richer details, and writes the result to Room. Even
"Genius didn't have this song" gets cached so we never retry. Subsequent
app launches read everything from Room — no network unless you `refresh()`
explicitly.

**Album art chain.** `AlbumArt.kt` looks up the Genius artwork URL from
Room (synchronously enough — single primary-key lookup) and uses Coil to
load it. If the URL is null OR the load fails, falls back to the embedded
ID3 art. If THAT fails, falls back to a deterministic gradient + music-note
icon. Same album always picks the same gradient color, so the library is
visually distinct even when nothing's resolved.

**Lyrics.** Lazy by design — we only fetch when you tap Lyrics in Now
Playing. `LyricsRepository.lyricsFor(song)` checks Room, then hits LRCLIB's
`/api/get` with title + artist + album + duration. LRCLIB returns synced
LRC format when it has it, plain text otherwise. We parse `[mm:ss.ff] line`
into `(timestampMs, text)` pairs and walk that list every time `positionMs`
updates to find the active line.

---

## Changelog

### v0.4.0
- Genius metadata (search + song details, cached in Room)
- LRCLIB lyrics with synced-line highlighting
- Folder renamed to `Music/PulseApp`
- Removed 10s seek buttons
- Overflow menu moved to bottom action bar
- Genius URL in share sheet
- Better duplicate-song dedup
- Profile avatar → Settings navigation
- Decorative wave animation above scrubber

### v0.3.0
- Glass-effect mini player with skip-forward-10s
- Custom pill scrubber
- Music-note fallback for missing art
- Profile name editing
- Folder-aware empty states
- Strict Pulse-folder-only scanning

### v0.2.0
- Theme toggle with DataStore persistence
- Working search with grouped results
- Create-playlist dialog

### v0.1.0
- Initial release
- Compose UI for For You / Library / Now Playing / Settings
- MediaSessionService background playback
- Room persistence for playlists, likes, play counts
