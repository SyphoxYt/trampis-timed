# Changelog

## 1.0.2 — 2026-10-30

- Fixed inspection restarts caused by quick taps; taps during inspection now leave the monotonic inspection clock alone.
- Corrected inspection penalty boundaries: +2 from 15 seconds through exactly 17 seconds, DNF only after 17 seconds.
- Kept the inspection countdown at 0 while the +2 window is active instead of switching to a dash.
- Fixed Timer Only sessions to run without saving solve records.
- Updated Android version code to 12 and version name to 1.0.2.

## 1.0.1 — 2026-10-30

- Kept the display awake while Trampis Timed is visible so the screen does not lock during solves.
- Added original sound cues for solve starts/stops, new personal bests, session creation, and manual scramble refresh, with one persistent on/off setting.
- Replaced the page slide transition with a sequential fade and disabled page-size interpolation to prevent the Stats switch from clipping and overlapping.
- Updated Android version code to 11 and version name to 1.0.1.

## 1.0.0 — 2026-10-30

- Added accessibility settings for reduced motion, high contrast, and system/Rubik interface fonts.
- Added animated color-mode transitions, rainbow and multi-style gradients, and custom accent and chart colors.
- Restored explicit System/Light/Dark appearance modes and added a Match Accent Stats gradient that follows any selected accent, including custom.
- Added all-session solve browsing, session filtering, Ao5/Ao12 trend views, and chart-point solve details.
- Added session archive/restore, duplicate, ordering, backup reminders, and undo for solve/session deletes.
- Added import review with session/solve counts, format validation, size limits, and strict malformed CSV row handling.
- Added Room migration 3→4 for session ordering and archived state.
- Improved stats card layout for compact widths and larger text, and centered content with a tablet width limit.
- Set Android version code to 10 and version name to 1.0.0.

## 0.6.2 — 2026-10-30

- Separated Solves sorting and selection controls to prevent overlap; batch actions live in their own overflow menu.
- Replaced the default New Session dialog with a custom responsive session setup flow with animated styles, event selection, phase controls, and goal validation.
- Tuned the creator and search field for compact screens and enlarged text.
- Updated Android version code to 8 and version name to 0.6.2.

## 0.6.1 — 2026-10-30

- Fixed the Solves sort and selection controls so they no longer overlap; selection actions now have a dedicated overflow menu.
- Replaced the basic New Session dialog with a responsive setup screen, animated session-style cards, event selection, multiphase controls, and validated goals.
- Shortened labels and adjusted spacing in the session creator on compact screens.
- Updated Android version code to 7 and version name to 0.6.1.

## 0.6.0 — 2026-10-30

- Applied CubeTime's Recursive variable font weights to timer and Stats numbers for a true bold display, with an adjustable timer weight slider.
- Rebuilt Time Trend with a smooth curve, gradient fill, dashed scale, value labels, selection guide, and animated reveal.
- Reworked Sessions into pinned and regular groups with current-session highlighting, solve counts, goals, and a consolidated data menu.
- Kept New Session reachable at the bottom and shortened tab labels on narrow screens.
- Updated Android version code to 6 and version name to 0.6.0.

## 0.5.0 — 2026-10-30

- Added 7-day, 30-day, and all-time Stats filters with prior-period performance comparison.
- Added optional Ao5 session goals and progress display; goal metadata is included in full backups.
- Added adjustable timer size, scramble text size, digital/system timer typeface, and bold timer digits.
- Added smooth session and scramble hierarchy updates, tab haptics, and OnePlus-like edge-to-edge refinements.
- Added lossless full JSON backup export/import for session metadata and solve records.
- Updated Android version code to 5 and version name to 0.5.0.

This is a debug-signed APK for review and installation. The Android port remains in progress; see `docs/PORTING_STATUS.md`.
