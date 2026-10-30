# Trampis Timed

Trampis Timed is the Android continuation of CubeTime, ported from the original project to Kotlin and Jetpack Compose.

## Version 1.0.4

Double-tap the scramble card to generate the next scramble, with the same lock protection, haptic feedback, and optional sound cue as the refresh button. Countdown mode is latched per inspection so changing its setting mid-count cannot desync the display from the automatic handoff. This release also includes the inspection countdown to solve count-up handoff from 1.0.3 and preserves full 64-bit timestamps in csTimer exports.

## Version 1.0.3

When inspection is set to count down, reaching zero now hands off directly to the solve timer counting up. The handoff uses the exact 15-second monotonic deadline so UI refresh delay does not shorten or extend the recorded solve. Count-up inspection retains manual start and +2/DNF thresholds. Inspection thresholds are shared between the timer display and penalty logic.

## Version 1.0.2

Inspection no longer restarts when the timer is tapped during the inspection period. The countdown stays at zero after 15 seconds while the +2 window is active, and DNF begins only after 17 seconds. Timer-only sessions also start and stop without recording solves.

## Version 1.0.1

The 1.0.1 update keeps the display awake while the app is visible, adds optional sound cues for solving, personal bests, session creation, and scramble refresh, and replaces the page-slide navigation with a cleaner sequential fade.

## Version 1.0.0

The v1 release adds all-session solve browsing with session filters, selectable single/Ao5/Ao12 trends and solve drill-down, session archive/restore/order/duplicate tools, undo for destructive deletes, and a review-before-import flow with format and size validation. Appearance adds System/Light/Dark modes, animated theme transitions, gradients, rainbow and custom color choices, an accent-matched Stats gradient, high contrast, reduced motion, and a Rubik interface font. Layouts adapt for compact widths, tablets, landscape, split-screen, and larger system text. Room schema v4 includes session ordering and archive state.

Version 0.6.2 fixes the Solves toolbar so sort and selection actions remain separate, with batch actions in a separate overflow menu. New Session now opens a custom responsive setup screen with animated session-style selection, an event picker, multiphase controls, goal validation, and a persistent Create action. The compact layout shortens labels for narrow screens and larger system text. Version 0.6.0 strengthened the timer and Stats typography with CubeTime's Recursive variable font and adjustable weight, improved Time Trend, and redesigned Sessions. It also includes the 0.5.0 additions: all-time, 30-day, and 7-day Stats views, prior-period comparison, personal records, optional Ao5 session goals, full JSON backups, adjustable timer size and typeface, accent colors, chart/card motion, and haptics.

The app includes a monotonic-clock timer with process-death recovery, inspection and hold-to-start, Room persistence, TNoodle scramble generation and SVG previews, named/customizable sessions, solve search/sort/details/comments/penalties, batch solve selection/move/copy/delete, rolling statistics, partial Competition Simulation summaries, Timer Only, Scramble Only, Scramble Generator, and Calculator tools. See `docs/PORTING_STATUS.md` for remaining source-to-Android gaps.

## Build

Requirements: JDK 17, Android SDK platform 36, and Android build-tools 36.0.0.

1. Load the prepared Java/SDK paths with `. .\work\android-toolchain\env.ps1`.
2. Run `gradlew.bat test` and `gradlew.bat assembleDebug` for a debug-signed installable APK.
3. For a signed production APK, set `TRAMPIS_RELEASE_KEYSTORE`, `TRAMPIS_RELEASE_STORE_PASSWORD`, `TRAMPIS_RELEASE_KEY_ALIAS`, and `TRAMPIS_RELEASE_KEY_PASSWORD` in the shell, then run `gradlew.bat assembleRelease`. Without those environment variables, Gradle emits an unsigned release APK. Keep the keystore and passwords private; never commit them.

TNoodle is GPL-3.0 and generates WCA-style scrambles for the supported puzzle registry. Use the WCA's current official program for competition scrambling; this app is a personal timer and does not claim WCA approval.

## Source and attribution

CubeTime source: <https://github.com/CubeLabsNZ/CubeTime> (GPL-3.0). TNoodle-LIB: <https://github.com/thewca/tnoodle-lib> (GPL-3.0). The CubeTime license is included as `LICENSE`.
