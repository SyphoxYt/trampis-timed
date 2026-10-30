# Trampis Timed

Trampis Timed is the Android continuation of CubeTime, ported from the original project to Kotlin and Jetpack Compose.

## Version 0.3.0

Version 0.3.0 redesigns launch and in-app presentation: both splash screens use the original cube logo without a black square backplate, on a coordinated midnight canvas with animated motion. The timer gains an ambient glow, a progress halo while timing, and a clearer scramble card; the bottom navigation now uses animated selected tabs with labels. Stats cards have a richer surface treatment and animated values. Timer stats reflow for narrow screens and larger system text.

The app includes a monotonic-clock timer with process-death recovery, inspection and hold-to-start, Room persistence, TNoodle scramble generation and SVG previews, named/customizable sessions, solve search/sort/details/comments/penalties, batch solve selection/move/copy/delete, rolling statistics, partial Competition Simulation summaries, Timer Only, Scramble Only, Scramble Generator, and Calculator tools. Sessions can be exported as CSV, csTimer JSON, and ODS, and imported from CSV or csTimer JSON with append-only transactional import. See `docs/PORTING_STATUS.md` for remaining source-to-Android gaps.

## Build

Requirements: JDK 17, Android SDK platform 36, and Android build-tools 36.0.0.

1. Load the prepared Java/SDK paths with `. .\work\android-toolchain\env.ps1`.
2. Run `gradlew.bat assembleDebug` for an installable debug APK signed with the local Android debug key. Configure a private signing key before distributing a production release.

TNoodle is GPL-3.0 and generates WCA-style scrambles for the supported puzzle registry. Use the WCA's current official program for competition scrambling; this app is a personal timer and does not claim WCA approval.

## Source and attribution

CubeTime source: <https://github.com/CubeLabsNZ/CubeTime> (GPL-3.0). TNoodle-LIB: <https://github.com/thewca/tnoodle-lib> (GPL-3.0). The CubeTime license is included as `LICENSE`.
