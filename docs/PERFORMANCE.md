# Performance Engineering

FlashPlay's goal is consistent responsiveness with low CPU, memory, battery, and background overhead. “Fast” is treated as a measurable property, not a promise of zero latency on every device and media file.

## Current Performance Choices

- One `ExoPlayer` is kept in `MediaSessionService`, avoiding player recreation during normal Activity/PiP transitions.
- `SurfaceView` avoids the extra composition cost of `TextureView` for ordinary video playback.
- Android hardware decoders are preferred; decoder fallback prevents a broken preferred decoder from ending playback.
- `SeekParameters.CLOSEST_SYNC` seeks to a nearby keyframe for responsive scrubbing.
- Position persistence runs every five seconds only while actively playing, then once on pause or destruction.
- No FFmpeg/software codec bundle increases APK size or CPU cost.
- Release builds enable R8, resource shrinking, and a startup baseline profile.

## Existing Benchmark

`benchmark/src/main/java/com/bossincrypto/flashplay/benchmark/StartupBenchmark.kt` measures ten cold starts with partial compilation and `StartupTimingMetric`. Each iteration waits for the open button and fails if the app is not interactive within two seconds.

Build and run it on a connected physical device:

```powershell
.\gradlew.bat :benchmark:assembleBenchmark connectedBenchmarkAndroidTest
```

The current repository validates that the benchmark APK builds. It does not publish device results because no physical device was available during the initial implementation.

## Target SLOs

These are targets until a reference-device report is committed:

| Metric | Target |
| --- | --- |
| Cold startup to interactive UI | p95 <= 2.0 s |
| Play/pause command response | p95 <= 100 ms, excluding decoder/network startup |
| Nearest-sync local seek | p95 <= 350 ms on indexed 1080p H.264 reference media |
| UI jank during ordinary playback controls | < 1% slow frames on reference device |
| Playback crashes/ANRs | 0 |

## Measurement Matrix

Record every result with:

- device, SoC/GPU, Android/API level, battery/thermal state;
- codec, container, resolution, frame rate, HDR/VFR state, and GOP/keyframe interval;
- local storage versus HTTPS network source and network conditions;
- playback speed, decoder selected/fallback, dropped frames, memory, and battery use;
- at least three setup runs and ten measured iterations, with p50 and p95.

Use the same device, media, thermal state, and measurement procedure when comparing commits. Profile with Macrobenchmark/Perfetto before changing buffers, renderers, or scheduling.

## Physical Limits

- Frame-exact seeks can require decoding from an earlier keyframe; long-GOP media cannot seek instantly.
- `CLOSEST_SYNC` trades exact frame position for responsiveness.
- `4x` playback of 4K60 can require up to 240 decoded frames per second and may exceed mobile decoder/display limits.
- Container indexing, storage, network, DRM, HDR, VFR, thermal throttling, and vendor codec quality materially affect results.
- PiP and resize transitions add compositor work and must be measured separately.

Future performance work should add first-frame/seek instrumentation, a controlled media corpus, Perfetto traces, and a physical device matrix before tuning Media3 buffers or adding native codecs.
