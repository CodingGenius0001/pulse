# Pulse

A clean, native Android music player for files you own. Dark-first UI with
light-mode support, pill-based controls, background playback with lock-screen
controls, strict folder-based scanning, and Genius-powered metadata
enrichment so your library looks pretty even when your MP3s are missing tags.

**Stack:** Kotlin · Jetpack Compose · Media3 / ExoPlayer · Room · DataStore · Coil · OkHttp + Kotlinx Serialization

---

## Setup before first build (v0.5)

You need three things before the first CI run will produce installable APKs that can self-update:

### 1. Genius API token (recommended)

Pulse uses the Genius API for cover art + album metadata. Without a token everything still works — songs play, library scans — but tracks won't get richer metadata fetched online.

**Get a token:**
1. Go to https://genius.com/api-clients
2. Sign in (or create a free Genius account)
3. Click "New API Client"
4. Fill in: App name "Pulse Personal", App website URL "https://github.com/CodingGenius0001/pulse"
5. Click "Generate Access Token"
6. Copy the **Client Access Token** (NOT the Client ID or Client Secret)

**For local builds:** create `local.properties` in the repo root with:
```
GENIUS_ACCESS_TOKEN=your_token_here
```
This file is gitignored, so the token never gets committed.

**For CI builds:** add it as a GitHub repo secret:
- GitHub repo → Settings → Secrets and variables → Actions → New repository secret
- Name: `GENIUS_ACCESS_TOKEN`
- Value: your token

### 2. Debug keystore (required for the in-app updater)

Every CI build needs to be signed with the **same** keystore so users can update from one build to the next without uninstalling first. (Android refuses to install an "update" with a different signing key — which is exactly what would happen if Gradle generated a fresh debug keystore each run.)

This is a **one-time, ~3-minute** setup. After this you'll never sideload manually again.

**Step 1 — Generate the keystore on your PC.** Open a terminal/Command Prompt anywhere convenient and run:

```
keytool -genkey -v -keystore debug.jks -keyalg RSA -keysize 2048 -validity 10000 -alias pulseDebugKey -storepass pulseDebugStorePass -keypass pulseDebugKeyPass -dname "CN=Pulse Debug, O=CodingGenius0001, C=US"
```

This creates `debug.jks` in the current folder. The passwords above (`pulseDebugStorePass`, `pulseDebugKeyPass`) are deliberately public defaults — debug keystores aren't security boundaries, they're identity tokens. If you'd rather use stronger passwords, swap them in here AND in step 4 below.

**Step 2 — Place it locally.** Copy `debug.jks` into `app/keystore/debug.jks` in your local repo. The `app/keystore/` directory is gitignored — the file stays on your machine only.

**Step 3 — Base64-encode it for CI.**

On Windows (Command Prompt):
```
certutil -encode debug.jks debug.jks.b64
```
(This wraps the output with `-----BEGIN/END CERTIFICATE-----`. Open `debug.jks.b64` in a text editor and remove those lines — only the base64 content should remain.)

On Mac/Linux:
```
base64 -i debug.jks > debug.jks.b64
```

**Step 4 — Add four GitHub secrets** at the same place as the Genius token:
| Secret name | Value |
|---|---|
| `DEBUG_KEYSTORE_BASE64` | contents of `debug.jks.b64` (the long base64 string) |
| `DEBUG_KEYSTORE_PASSWORD` | `pulseDebugStorePass` (or whatever you used) |
| `DEBUG_KEY_ALIAS` | `pulseDebugKey` |
| `DEBUG_KEY_PASSWORD` | `pulseDebugKeyPass` (or whatever you used) |

**Step 5 — Delete the temp files.** `debug.jks.b64` you can delete now (it's only the transport medium). Keep `debug.jks` in `app/keystore/` for local builds.

**That's it.** From the next push onward, every CI build is signed with the same key. The first build with the keystore in place is the LAST manual install you'll do — every subsequent push, the in-app updater will fetch and install the new build.

### 3. Lyrics (LRCLIB)

LRCLIB is free and unauthenticated. Pulse just hits their API directly — no setup required.

## What's new in v0.5.1

- Album art now observes cached Genius metadata and appears as soon as enrichment finishes.
- Genius misses from missing tokens or transient network errors are no longer cached permanently.
- LRCLIB lookup is more forgiving with missing artist tags, stale cached lyric misses are retried, and network failures show as fetch errors instead of false "not found" messages.
- Now Playing uses a Google-style pill scrubber, cleaner typography, non-wrapping bottom actions, and a subtle artwork-derived background tint.
- Shared library and updater ViewModels are hoisted to the app root to avoid duplicate scans and mismatched update banner state.

## What's new in v0.5.0

### Major details

- **In-app updater.** Settings → Updates → "Check for updates" → if a newer build exists on GitHub, tap Download → tap Install → Android takes over. No more manual sideload. A small banner also shows on the Home screen when an update is available (only after you've manually checked — Pulse never phones home automatically).
- **Continuous flowing wave on the playback scrubber.** Two summed sines for organic variation, scrolls leftward while playing, smoothly flattens to a line on pause. Replaces the bar-chart version from v0.4.2.
- **Circular knob** at the playhead instead of the vertical pill — matches the reference wave style.
- **Fixed debug keystore** in CI (see Setup above) means users can update without uninstalling first.
- **Build number visible in app.** Settings → About now shows `Pulse · v0.5.0 (build 7)` so you always know exactly what you're running.

### Minor details

- **LRCLIB cascade fixed.** The /api/get endpoint sometimes returns a 200 with null lyrics for tracks LRCLIB has but doesn't have lyrics for in your locale's exact match. Now correctly cascades to /search.
- **LRCLIB duration tolerance** loosened from 5s to 10s — catches more edits/remasters of the same song.
- **Search prefers candidates with content** before falling back to anything within tolerance.
- Auto-publishing the floating `latest` release with a `Build #N` header so the in-app updater can parse the version.

### Honest limitations carried over

- Genius matching still requires an artist tag. Tracks tagged "Unknown artist" get the gradient + music-note fallback, by design — see the v0.4 notes for why.
- Same for LRCLIB lyrics — needs a real artist on the file.
- Tag your MP3s properly (Mp3Tag is free) for full metadata coverage.

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

### v0.5.1
- Live album-art refresh after metadata enrichment
- Safer Genius metadata caching and title-only fallback for missing artist tags
- More forgiving LRCLIB matching, retried cached misses, and clearer lyric errors
- Google-style pill scrubber and cleaner typography
- Artwork-derived background tint while music is active
- Shared root-scoped library/update ViewModels

### v0.5.0
- In-app updater (manual check, Settings + Home banner)
- Continuous flowing wave on scrubber, circular knob playhead
- Fixed debug keystore for cross-build install compatibility
- Build number visible in Settings
- LRCLIB cascade fix (null-lyrics 200 now triggers /search fallback)
- Auto-publish releases workflow with Build #N parsing

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
