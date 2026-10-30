# CubeTime source map and port tracker

Source audited from `CubeLabsNZ/CubeTime` commit `c815f975299b673f643219b6b87fab4889665b0e` (18 Sep 2025). Original README identifies CubeTime as GPL-3.0, SwiftUI/UIKit, Core Data, and TNoodle-based.

## Source-to-Android map

| Original area | Source of truth | Android destination | Current status / validation |
|---|---|---|---|
| Timer and touch state | `StopwatchManager/TimerController.swift`, `Timer/TimerTouchView.swift`, `Timer/TimerView.swift` | `core/.../TimerEngine.kt`, `ui/TimerViewModel.kt`, `MainActivity.kt` | In progress. Monotonic timestamp/state behavior, hold-to-start, inspection alerts, phase input, and Room-backed active-run recovery exist. Emulator process kill/relaunch preserved the running timer and it saved once after stop; full gesture, keyboard, and inspection parity remain. |
| Scramble generation | `StopwatchManager/ScrambleController.swift`, event table in `Helper/Helper.swift` | `domain/ScrambleProvider.kt` | In progress. Uses TNoodle `PuzzleRegistry.THREE`; no hand-written random move string. The checked-in Swift function is currently stubbed (`safeGetScramble()` returns an empty string and its Graal calls are commented out). The upstream source repository is used to recover the intended TNoodle path. |
| Penalty model and result display | `Models.swift` (`Penalty`, `Solve.timeIncPen`, `timeIncPenDNFMax`) | `domain/Statistics.kt`, `data/SolveEntity`, solve UI | In progress. None, +2, and DNF persist and can be changed individually or in batches. Search, solve details/comments, copy scramble, deletion, move, and copy are present; phase-specific edit history remains. |
| Average/statistics rules | `StopwatchManager/StopwatchManager+Stats.swift`, `getBestAverage.c++`, `getBestAverage.h++`, tests in `CubeTimeTests/StopwatchManagerStatsTests.swift` | `core/.../Statistics.kt` | In progress. Trim rule, +2, DNF, median, current averages, best rolling averages, and distribution bins have JVM coverage. Native best-window parity and differential testing against all original cases remain. |
| Sessions | `Models.swift`, `Sessions/NewSessionView.swift`, `Sessions/SessionsView.swift` | Room `SessionEntity`, session selector and create dialog | In progress. Create, select, pin, archive/restore, reorder, duplicate, customize, rename, delete with undo, and current-session persistence work. Standard, multiphase, playground, timer-only have partial behavior; competition simulation now has fixed groups of five, target progress, and average summaries, while its full workflow and algorithm trainer remain. |
| Solve persistence | `Persistence.swift`, Core Data model `txmerdata.xcdatamodeld` | Room `TrampisDatabase.kt` | In progress. Room stores sessions, solves, penalties, comments, phase data, and active timer recovery. Append-only multi-session file import and finish-run solve save are transactional. Version 2→3 migration and recovery were exercised on emulator; full relation parity remains. |
| Solve list/details | `TimeList/TimeListView.swift`, `TimeCard.swift`, `TimeDetailView.swift`, `TimeBar.swift` | Solves screen in `MainActivity.kt` | In progress. Time/scramble/comment search, date/time ordering, individual and multi-select penalty edits, delete, move/copy between same-event sessions, scramble copy, and editable comments. Original phase-aware filters, richer detail, and interaction parity remain. |
| Stats and graphs | `Stats/StatsView.swift`, `Stats/StatsDetailView.swift`, `Stats/TimeTrend/*`, `Stats/TimeDistribution.swift`, `Stats/AveragePhases.swift` | Stats screen in `MainActivity.kt` | In progress. Current/best Ao5, Ao12, Ao100, mean, median, best/worst single, selectable single/Ao5/Ao12 trend, solve drill-down, and a trimmed time-distribution histogram. Stats cards adapt to narrow/tall-font screens. Competition Simulation summaries are implemented; original phase charts and drill-down remain. |
| Settings | `Settings/SettingsManager.swift`, `GeneralSettings.swift`, `AppearanceSettings.swift` | DataStore and settings UI | In progress. Inspection/countdown/audio/hold/haptics/timer-tool visibility/precision persist. Appearance includes animated light/dark transitions, preset and custom accent/gradient colors, rainbow, chart glow/animation, high contrast, reduced motion, Rubik/system interface font, and adjustable timer font. Full iOS parity remains. |
| Import/export | `Sessions/ImportExport.swift`, `StopwatchManager/ExportViewModel.swift` | `data/SessionExchange.kt`, Android Storage Access Framework in `MainActivity.kt` | In progress. CSV, csTimer JSON, ODS, and full JSON backup export are implemented. CSV/csTimer/backup import shows a review summary before append; backup schema, size, record counts, and CSV rows are validated. Selecting subsets of sessions/formats and broader fuzz coverage remain. The iOS ImportFlow itself is only a placeholder. |
| Utilities | `Sessions/Tools/*`, `Sessions/Tools/ToolsList.swift` | Tools destination in `MainActivity.kt` | In progress. Timer-only, scramble-only, batch scramble generation, and calculator are implemented. Full source interaction and accessibility parity remain. |
| Assets and design | `Assets.xcassets`, theme color sets, `Ext/Fonts`, original view code | Compose theme/components | In progress. Original CubeTime app icon, event icons, Recursive/Rubik fonts, and inspection sounds are included. Stats dashboard, animated branded launch, adaptive timer sizing, and page/scramble transitions are implemented. The system splash icon is platform-masked; the in-app launch animation displays the original full app-icon image. Remaining screens need broader device and interaction comparison. |
| Cloud sync | `StopwatchManager/CloudkitStatusManager.swift`, CloudKit entitlements | Android-compatible sync (not yet selected) | Not started. No sync claim is made. |

## Feature completion tracker

Statuses use the requested vocabulary. “Implemented” means code exists; “Tested” requires the relevant tests/device checks to pass; no item is marked complete prematurely.

| Feature | Status |
|---|---|
| Original repository structure and key source areas audited | Implemented |
| Monotonic timer engine | In progress |
| Timer state unit tests | Tested (5 passing JVM tests) |
| TNoodle event scramble provider | Implemented |
| Scramble provider integration test | Tested (1 passing app unit test) |
| Standard solve penalties | In progress |
| Standard session create/switch | In progress |
| Room solve persistence | In progress |
| Solve history and deletion | In progress |
| Current and best Ao5/Ao12/Ao100 calculations | Tested (6 passing JVM tests; full parity incomplete) |
| Competition Simulation fixed groups, target progress, BPA/WPA and summaries | Tested (JVM regression tests cover fixed grouping, target math, and DNF handling; full workflow parity incomplete) |
| Best rolling averages and full statistical parity | In progress (trimmed rolling averages implemented; differential parity remains) |
| Inspection and hold-to-start | In progress |
| Inspection sounds, haptics, and visual scramble drawing | In progress (assets/behavior included; complete device parity remains) |
| Multiphase / Playground / Comp Sim / timer-only | In progress (partial mode support; Comp Sim workflow and Alg Trainer incomplete) |
| Solve search, sorting, detail comments, penalties, and deletion | In progress |
| Multi-select solve penalties, move/copy, and delete | In progress (emulator copy persisted; move/delete implementation needs direct runtime coverage) |
| Graphs | In progress (time trend and trimmed distribution implemented; remaining charts incomplete) |
| Settings and appearance persistence | In progress (timer settings persist; full appearance/settings parity incomplete) |
| CSV and csTimer JSON export/import | Tested (format round-trip tests and emulator picker export/import) |
| ODS export | Implemented (ODS sheet generation and import/export unit coverage) |
| Database migration 2→3 | Tested (existing v2 emulator database upgraded and opened successfully) |
| Active timer save/restore transaction | Tested (2 Android instrumentation tests and process kill/relaunch smoke test) |
| UI/device tests | In progress (timer recovery/save, file-picker export/import, session edit/delete, solve copy, and 411×905 Android 16 emulator launch/stats smoke checked; OnePlus 13 and broader device interaction coverage remain) |
| Debug APK / release APK build | Tested (tests and Android release lint pass; v1.0.0 debug and release APKs build; debug and release APK signatures verified) |
| Device installation / runtime verification | Tested (emulator install/migration, timer recovery/save, CSV/csTimer flows, selected-session restart persistence, session editing/deletion, and solve copy; broader device coverage remains) |
| Android SDK packages | Installed |
| Gradle project configuration | Tested (`gradlew.bat tasks` succeeds) |
| GitHub publication | Pending (v1.0.0 source/tag publication is part of this update) |

## Porting decisions

1. Use `SystemClock.elapsedRealtimeNanos()` and calculate each displayed value from timestamps, rather than incrementing a displayed counter. This mirrors the original use of `CLOCK_MONOTONIC_RAW` in `TimerController.swift`.
2. Persist durations as integer milliseconds, preserving sub-millisecond timing in memory until solve save. Penalties are separate fields so +2 and DNF remain editable.
3. Keep TNoodle as the actual scramble engine. Do not generate a move-looking random string if the library is unavailable; surface generation failure instead.
4. Reimplement the Swift/C++ statistics in Kotlin and compare against upstream fixtures. A native C++ bridge is deferred until a measurable need is established.
5. Replace Core Data with Room and UserDefaults with DataStore while keeping session/solve relationships and event/session types explicit.
