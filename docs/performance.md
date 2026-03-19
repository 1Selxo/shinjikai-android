# Performance

## Release

- Release builds now enable code shrinking and resource shrinking.
- The app manifest is marked `profileable` so release benchmarking can run from Android Studio or Gradle.

## Benchmark Scaffold

- A macrobenchmark test lives at `app/src/androidTest/java/com/shinjikai/dictionary/StartupBenchmark.kt`.
- The benchmark measures cold startup on the release app package `com.shinjikai.dictionary`.

## Recommended Checks

- Run the startup benchmark on a physical device.
- Compare cold start before and after search, bookmark, or image-loading changes.
- Keep benchmark runs on release builds only for meaningful results.
