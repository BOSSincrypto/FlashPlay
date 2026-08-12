# Contributing To FlashPlay

Thanks for improving FlashPlay. Keep changes focused, measurable, and inexpensive on the playback path.

## Development Setup

Use JDK 17, Android SDK Platform 37, and the included Gradle wrapper. Android Studio is optional.

```powershell
.\gradlew.bat lintDebug testDebugUnitTest assembleDebug
```

Use `./gradlew` on Linux or macOS. Before opening a pull request, also build release artifacts and the benchmark APK:

```powershell
.\gradlew.bat assembleRelease bundleRelease :benchmark:assembleBenchmark cyclonedxBom
```

## Change Guidelines

- Start from `main` and keep each pull request narrowly scoped.
- Use Conventional Commits such as `feat:`, `fix:`, `perf:`, `test:`, `docs:`, or `ci:`; Release Please derives versions from them.
- Preserve the single-player ownership model in `PlayerService` unless measurements justify a redesign.
- Avoid allocations, polling, native libraries, permissions, and background work without a measured benefit.
- Update both Russian and English strings when changing user-facing text.
- Add tests for behavior changes and document any performance, privacy, permission, or compatibility impact.
- Never commit keystores, credentials, signed URLs, private media, generated build outputs, or machine-local configuration.

## Performance Changes

Build the benchmark module locally, then run instrumentation on a physical device:

```powershell
.\gradlew.bat :benchmark:assembleBenchmark connectedBenchmarkAndroidTest
```

Report the device, Android version, codec/container, resolution/frame rate, media location, GOP/keyframe interval, thermal conditions, and repeated p50/p95 results. Emulator-only results are not accepted as evidence of playback performance.

## Pull Request Checklist

- Lint, unit tests, and relevant builds pass.
- New behavior has tests or a written reason why device-only coverage is required.
- Permissions, URI handling, external intents, MediaSession, and PiP lifecycle are reviewed.
- Performance claims include reproducible measurements.
- UI changes include screenshots and work in RU/EN.
- The change contains no secrets or generated artifacts.

Maintainers may ask for focused commits or squash a pull request. Merging a Release Please PR publishes a release only when protected release signing secrets are configured.
