# Trampis Timed

Trampis Timed is the Android continuation of CubeTime, ported from the original project to Kotlin and Jetpack Compose. It keeps CubeTime's name-independent branding assets and timer identity while using Trampis Timed as the Android product name.

## Version 0.2.0

Version 0.2.0 adds an animated startup screen using the original CubeTime app icon, responsive timer sizing, animated page and scramble changes, and a Stats dashboard with current/best averages, best single, trend and distribution charts. Appearance controls include static/dynamic stat gradients, graph glow/animation, system/dark appearance, and a scramble preview. This update also carries the Android continuation features listed below.

The app includes a monotonic-clock timer with process-death recovery, inspection and hold-to-start, Room persistence, TNoodle scramble generation and SVG previews, named/customizable sessions, solve search/sort/details/comments/penalties, batch solve selection/move/copy/delete, rolling statistics, partial Competition Simulation summaries, Timer Only, Scramble Only, Scramble Generator, and Calculator tools. Sessions can be exported as CSV, csTimer JSON, and ODS, and imported from CSV or csTimer JSON with append-only transactional import. Original CubeTime icon/event assets, fonts, audio cues, and base colors are included. The Android port is still in progress; see `docs/PORTING_STATUS.md` for the remaining source-to-Android gaps.

## Build

Requirements: JDK 17, Android SDK platform 36, Android build-tools 36.0.0, and a connected Android device or emulator for runtime checks.

1. In PowerShell from the workspace root, load the prepared Java/SDK paths with `. .\work\android-toolchain\env.ps1`.
2. Run `sdkmanager --licenses` and accept Google's Android SDK license. Then install `platform-tools`, `platforms;android-36`, and `build-tools;36.0.0` with `sdkmanager`.
3. Run `gradlew.bat testDebugUnitTest assembleDebug` for the Android debug build and app unit tests. `gradlew.bat :core:test` runs the Android-independent timer/statistics tests.
4. Run `gradlew.bat assembleDebug` for an installable debug APK signed with the local Android debug key. Run `gradlew.bat assembleRelease` for an unsigned release APK; configure a private signing key before distributing a production release.

Gradle downloads AndroidX and TNoodle dependencies from Google Maven Central/Maven Central on the first build. TNoodle is GPL-3.0 and generates WCA-style scrambles for the supported puzzle registry. Use the WCA's current official program for competition scrambling; this app is a personal timer and does not claim WCA approval.

## Project structure

- `app/src/main/kotlin/.../domain`: timer, statistics, and scramble domain code.
- `app/src/main/kotlin/.../data`: Room session and solve persistence.
- `app/src/main/kotlin/.../ui`: timer state and Android UI.
- `app/src/test`: timer, statistics, and scramble provider tests.
- `docs/PORTING_STATUS.md`: source-to-Android map and feature status.

## Source and attribution

CubeTime source: <https://github.com/CubeLabsNZ/CubeTime> (GPL-3.0, audited at commit `c815f975299b673f643219b6b87fab4889665b0e`). TNoodle-LIB: <https://github.com/thewca/tnoodle-lib> (GPL-3.0). The complete CubeTime license is included as `LICENSE`.

## Current gaps

The complete Competition Simulation workflow, algorithm trainer, advanced filters and detailed phase statistics, additional charts, full font-setting parity, automated UI coverage, and production signing remain incomplete. See the tracker for detailed status. Appearance font weight, casualness, and cursive controls are currently preview-only and do not change the app-wide typeface.
