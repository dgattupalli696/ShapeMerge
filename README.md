# Shape Merge

A physics-based merge game for Android, built with **libGDX + Box2D** (Kotlin).

Aim an arrow from the launcher and fling polygons into a walled playground. When
two (or more) shapes of the **same kind** collide they merge into the next polygon
in the ladder — triangle → square → pentagon → … → decagon. Merge two decagons (or
a multi-shape cluster) and they form a **circle** that pops for a big bonus and
disappears. Fill the playground and it's game over.

## Gameplay

- **Aim & throw:** Drag back from the launcher (slingshot). A thick arrow shows the
  direction and grows green → yellow → red with power. Aim is clamped to an upward
  cone so shots always go into the playground.
- **Merging:** Same shapes that collide merge up one level. Three or more colliding
  at once jump multiple levels (e.g. 3 triangles → pentagon).
- **Polygon ladder:** triangle (3) up to decagon (10), then a circle (pops).
- **Scoring:** Higher merges score more; circles award a large bonus with a pop
  effect and floating score. High score is saved locally.
- **Levels:** Reaching the score target advances the level and yields larger
  starting shapes from the launcher.
- **Lose:** When shapes fill the playground past the limit.

## Project layout

```
ShapeMerge/
├── core/      # Shared Kotlin game logic (libGDX, Box2D)
├── android/   # Android launcher + resources
├── assets/    # Shared assets
└── build.gradle, settings.gradle, gradlew, ...
```

## Build

Requires **JDK 17** and the **Android SDK** (platform 34, build-tools 34.0.0).

```bash
# Build a debug APK
./gradlew :android:assembleDebug
# -> android/build/outputs/apk/debug/android-debug.apk

# Install on a connected device
./gradlew :android:installDebug
```

Set `sdk.dir` in `local.properties` (e.g. `sdk.dir=/Users/you/Library/Android/sdk`).

## Tech

- libGDX 1.12.1, Box2D
- Kotlin 1.9.24, Android Gradle Plugin 8.5.2, Gradle 8.7
- minSdk 24, targetSdk 34
