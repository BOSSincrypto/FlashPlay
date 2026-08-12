# FlashPlay

[![CI](https://github.com/BOSSincrypto/FlashPlay/actions/workflows/ci.yml/badge.svg)](https://github.com/BOSSincrypto/FlashPlay/actions/workflows/ci.yml)
[![Security](https://github.com/BOSSincrypto/FlashPlay/actions/workflows/security.yml/badge.svg)](https://github.com/BOSSincrypto/FlashPlay/actions/workflows/security.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-ffb000)](LICENSE)

FlashPlay is a lean Android video player built for responsive startup, playback controls, fast keyframe seeking, background playback, and Picture-in-Picture. It targets Android 8.0 and newer and uses one long-lived Media3 `ExoPlayer` inside a `MediaSessionService`.

## Features

- Open local videos through Android's Storage Access Framework without broad storage permissions.
- Play direct, progressive HTTPS media URLs and handle Android `VIEW` and `SEND` video intents.
- Keep one global playback speed from `0.25x` to `4x`, selectable in `0.25x` steps.
- Resume each video from its saved position and restore an accessible local document without autoplay.
- Double-tap to seek 10 seconds; swipe vertically for brightness on the left and volume on the right.
- Continue through MediaSession controls and enter Picture-in-Picture on supported devices.
- Use `SurfaceView`, hardware decoders first, decoder fallback, and closest-sync seeking for low overhead.
- Ship RU/EN resources, R8/resource shrinking, a startup baseline profile, and a Macrobenchmark module.

## Install

Download a signed APK from [GitHub Releases](https://github.com/BOSSincrypto/FlashPlay/releases) after the first release is published, or build a debug APK locally:

```powershell
.\gradlew.bat assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Build

Requirements:

- JDK 17
- Android SDK Platform 37
- Android build tools available through the SDK
- The included Gradle 9.7 wrapper

Windows verification:

```powershell
.\gradlew.bat lintDebug testDebugUnitTest assembleDebug assembleRelease bundleRelease :benchmark:assembleBenchmark cyclonedxBom
```

On Linux or macOS, use `./gradlew` instead. Release outputs are unsigned unless all signing variables are supplied:

- `ANDROID_KEYSTORE_PATH`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

CI additionally expects the base64-encoded keystore secret `ANDROID_KEYSTORE_B64`. Never commit a keystore or signing credentials.

Follow [docs/RELEASE_SIGNING.md](docs/RELEASE_SIGNING.md) for the one-time production key setup and safe backup procedure.

## Architecture

- `MainActivity` owns the small Views UI, gestures, incoming intents, file picker, and PiP parameters.
- `PlayerService` owns the single `ExoPlayer`, MediaSession, audio focus, notification/background lifecycle, and active-playback position persistence.
- `PlaybackPrefs` stores global speed and SHA-256-keyed per-URI positions in private preferences.

FlashPlay intentionally does not bundle FFmpeg or a software codec pack. Actual codec/container support depends on Android, Media3, and the device decoder. HLS, DASH, SmoothStreaming, and RTSP extension modules are not bundled.

## Performance

The project optimizes the hot path, but it does not make physically impossible promises. Exact seek latency depends on keyframe/GOP spacing, container indexes, storage or network speed, codec support, thermal state, and device hardware. FlashPlay uses `SeekParameters.CLOSEST_SYNC` to prefer responsiveness over frame-exact seeking.

See [docs/PERFORMANCE.md](docs/PERFORMANCE.md) for targets, benchmark methodology, and hardware limitations. No physical-device benchmark result is claimed until it is measured reproducibly.

## Automation

- Pull requests and `main` run lint, unit tests, debug/release builds, benchmark APK assembly, and SBOM generation.
- CodeQL, dependency review, weekly OSV scanning, and Dependabot protect dependencies and workflows.
- Release Please creates a version PR from Conventional Commits. Merging that release PR creates the tag and GitHub Release, then builds signed APK/AAB assets, checksums, a CycloneDX SBOM, and GitHub attestations.
- A repository secret named `RELEASE_PLEASE_TOKEN` is optional but recommended so CI also runs on Release Please pull requests.

## Privacy And Security

FlashPlay has no analytics, accounts, backend, or media upload. Local document access uses Android URI grants; backups and device transfer are disabled. Remote URLs are not restored or saved as the last media item, reducing the risk of persisting signed URL query tokens.

Use HTTPS media links. Cleartext HTTP is intentionally rejected. Report vulnerabilities privately as described in [SECURITY.md](SECURITY.md).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). By contributing, you agree that your contribution is licensed under Apache License 2.0.

## License

Copyright 2026 BOSSincrypto. Licensed under the [Apache License 2.0](LICENSE).
