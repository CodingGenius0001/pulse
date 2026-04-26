# Repository Guidelines

## Project Structure & Module Organization
`Pulse` is a single-module Android app in [`app/`](C:\Users\Keshav%20Pillarisetti\Desktop\pulse-android\pulse\app). Kotlin sources live under `app/src/main/java/com/pulse/music`, grouped by responsibility: `ui/` for Compose screens and components, `player/` for playback, `data/` for Room models and repositories, `network/` for API clients, `scanner/` for media discovery, and `update/` for in-app update logic. Android resources are in `app/src/main/res`. CI lives in `.github/workflows/build.yml`.

## Build, Test, and Development Commands
This repo currently does not commit a Gradle wrapper. Generate it once with `gradle wrapper --gradle-version 8.7`, then use:

- `.\gradlew.bat :app:assembleDebug` builds the debug APK.
- `.\gradlew.bat :app:installDebug` installs the app on a connected device.
- `.\gradlew.bat :app:lint` runs Android lint.
- `.\gradlew.bat :app:testDebugUnitTest` runs JVM unit tests when they exist.

For local metadata lookups, add `GENIUS_ACCESS_TOKEN=...` to `local.properties`. For upgrade-compatible debug installs, keep `app/keystore/debug.jks` on your machine as described in `README.md`.

## Coding Style & Naming Conventions
Use Kotlin with 4-space indentation and keep files package-scoped by feature. Follow existing naming patterns: `NowPlayingScreen`, `PlayerViewModel`, `MusicRepository`, `GeniusApi`, `PulseDatabase`. Prefer immutable state, small composables, and clear suffixes such as `Screen`, `ViewModel`, `Repository`, and `Dao`. No formatter or linter beyond Android lint is configured, so match the surrounding code closely.

## Testing Guidelines
There are no committed `test/` or `androidTest/` sources yet. Add JVM tests under `app/src/test/java/...` for repository, formatter, and parser logic; add instrumentation or Compose UI tests under `app/src/androidTest/java/...` for playback and screen flows. Name tests after the class under test, for example `MusicRepositoryTest`.

## Commit & Pull Request Guidelines
Recent history uses short, imperative subjects with optional prefixes such as `Fix:` and `Test:` or release tags like `v0.5.6: ...`. Keep commits focused and descriptive. PRs should summarize behavior changes, note any config impacts, link the related issue, and include screenshots or short recordings for Compose UI changes. If you touch build, signing, or updater logic, mention how it was verified.
