# Trampis Timed

Trampis Timed is the Android continuation of CubeTime, ported from the original project to Kotlin and Jetpack Compose.

## Version 0.5.0

Version 0.5.0 expands Stats with all-time, 30-day, and 7-day views, a comparison to the prior period, personal records, and optional Ao5 session goals. Appearance controls now adjust timer size, scramble size, timer typeface and weight, accent color, graph/card motion, and haptics. Sessions can define average goals and show progress in Stats. Full JSON backups preserve session settings, solve comments, penalties, timestamps, scrambles, and phase splits; they can be imported alongside CSV/csTimer formats. Timer session context is clearer, and a one-tap next-scramble action is available. Edge-to-edge system bar contrast and compact layouts have also been refined.

The app includes a monotonic-clock timer with process-death recovery, inspection and hold-to-start, Room persistence, TNoodle scramble generation and SVG previews, named/customizable sessions, solve search/sort/details/comments/penalties, batch solve selection/move/copy/delete, rolling statistics, partial Competition Simulation summaries, Timer Only, Scramble Only, Scramble Generator, and Calculator tools. See `docs/PORTING_STATUS.md` for remaining source-to-Android gaps.

## Build

Requirements: JDK 17, Android SDK platform 36, and Android build-tools 36.0.0.

1. Load the prepared Java/SDK paths with `. .\work\android-toolchain\env.ps1`.
2. Run `gradlew.bat assembleDebug` for an installable debug APK signed with the local Android debug key. Configure a private signing key before distributing a production release.

TNoodle is GPL-3.0 and generates WCA-style scrambles for the supported puzzle registry. Use the WCA's current official program for competition scrambling; this app is a personal timer and does not claim WCA approval.

## Source and attribution

CubeTime source: <https://github.com/CubeLabsNZ/CubeTime> (GPL-3.0). TNoodle-LIB: <https://github.com/thewca/tnoodle-lib> (GPL-3.0). The CubeTime license is included as `LICENSE`.
