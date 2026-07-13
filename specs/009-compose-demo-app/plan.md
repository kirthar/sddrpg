# Implementation Plan: Compose Demo App

**Branch**: `009-compose-demo-app` | **Date**: 2026-07-12 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/009-compose-demo-app/spec.md`

## Summary

Turn the `demo-app` reserved slot into a real Compose Multiplatform module: the same
shipped demo battle (specs 007-008's content and roster), playable through a
graphical screen on Android (the requested APK), web/wasmJs (the optional GitHub
Pages deployment), and desktop/JVM (the test host). The central new piece is
`BattleController` — an event-driven, UI-framework-free inversion of `demo-console`'s
blocking `runBattleLoop`, preserving spec 008's exact per-turn bookkeeping and gating
guarantees, fully kotest-tested without any UI. The Compose layer (one
`BattleScreen`) merely observes the controller's immutable UI state and pushes
submissions in. Zero changes to any spec 001-008 file; the only out-of-module edits
are root build wiring (`settings.gradle.kts`, `gradle/libs.versions.toml`,
`.gitignore` for `local.properties`).

## Technical Context

**Language/Version**: Kotlin 2.4.0 (commonMain across android/wasmJs/desktop targets)

**Primary Dependencies**: Compose Multiplatform **1.11.1** (verified on Maven
Central), Compose compiler plugin `org.jetbrains.kotlin.plugin.compose` **2.4.0**
(version-locked to Kotlin), AGP `com.android.application` **8.13.2** (latest line
compatible with Gradle 8.14.3 — AGP 9.x requires Gradle 9), `androidx.activity:activity-compose`
**1.13.0** (androidMain only, verified on Google Maven). `core`/`content` consumed
exactly as published.

**Storage**: N/A (no save/load, per spec out-of-scope)

**Testing**: kotest via the desktop JVM target (`desktopTest` with
`kotest-runner-junit5`, same wiring as `demo-console`); wasmJs verified by
compilation + producing `wasmJsBrowserDistribution`, never by browser tests;
Android verified by `assembleDebug` producing an installable APK.

**Target Platform**: Android (minSdk 24, target/compileSdk 36 — SDK installed at
`/opt/android-sdk` in this environment), web browsers with WasmGC support
(Chrome 119+, Firefox 120+, Safari 18.2+), desktop JVM 21.

**Project Type**: single project — one NEW Gradle module (`demo-app`), three
existing modules untouched.

**Performance Goals**: N/A beyond "the UI keeps up with a turn-based battle" — no
animation/60fps targets in this milestone (spec Assumptions: functional screen).

**Constraints**: fully deterministic battle progression (FR-009); controller must be
observable without coroutines/Flow machinery (plain state value the UI wraps);
zero changes to spec 001-008 files (FR-008); `local.properties` (SDK path) must
never be committed.

**Scale/Scope**: one screen, one battle, 3 participants — the complexity is in the
event-driven orchestration and the three-target build, not in data volume.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle I (Pure & Deterministic Core)**: PASS. Nothing is added to `core`.
  `BattleController` is deterministic (no RNG, no clocks); its only "state" is the
  explicit session value it advances via published pure functions — same submission
  sequence always yields the same UI-state/log sequence (FR-009, tested).
- **Principle II (Data-Driven Content)**: PASS. No new game content; the battle is
  `loadContentPack(DEMO_CONTENT_JSON)` + `buildDemoBattleState()` + `DEMO_ACTIONS`
  exactly as spec 007/008 published them.
- **Principle III (Test-First)**: PASS. tasks.md orders failing controller/rendering
  tests before implementation; the suite bar is the same green-everything gate.
- **Principle IV (Simulation-Presentation Separation)**: PASS — this feature IS the
  second frontend that principle anticipated. All rendering/text/UI lives in
  `demo-app`; the engine stays unaware of it. Human vs. AI decision sources are
  consumed through the same published `DecisionSource`/`core.ai` surface as spec 008.
- **Principle V (Extensible Architecture Without Speculative Implementation)**: PASS.
  No generic "frontend framework" is invented: one controller, one screen, exactly
  the three targets the deliverables need. The conditional v1 non-goal ("no
  demo-app until the console demo validates the core") is satisfied by spec 008 —
  documented in spec.md's gate note; no amendment required.
- **Cross-spec discipline**: PASS. Zero source changes to `core`/`content`/
  `demo-console`. `settings.gradle.kts`/`gradle/libs.versions.toml`/`.gitignore`
  are build infrastructure (the same files every module addition has always
  touched), not spec artifacts.

**Result**: No violations. Complexity Tracking stays empty.

## Project Structure

### Documentation (this feature)

```text
specs/009-compose-demo-app/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   └── demo-app-api.md
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
settings.gradle.kts                  # MODIFIED: google() repos + include(":demo-app")
gradle/libs.versions.toml            # MODIFIED: compose/agp/activity-compose entries
.gitignore                           # MODIFIED: local.properties
local.properties                     # NEW, UNCOMMITTED: sdk.dir=/opt/android-sdk

demo-app/
├── build.gradle.kts                 # NEW: KMP + compose + android application
└── src/
    ├── commonMain/kotlin/io/github/kirthar/sddrpg/demo/app/
    │   ├── BattleController.kt      # NEW: event-driven session controller (the core of this feature)
    │   ├── BattleUiState.kt         # NEW: immutable UI-state value types
    │   ├── EventText.kt             # NEW: BattleEvent/rejection -> display text (commonMain equivalent of spec 008's ConsoleRendering)
    │   └── ui/BattleScreen.kt       # NEW: the single Compose screen
    ├── commonTest/kotlin/io/github/kirthar/sddrpg/demo/app/
    │   ├── BattleControllerTest.kt         # NEW
    │   ├── BattleControllerDeterminismTest.kt  # NEW
    │   ├── BattleControllerPlaythroughTest.kt  # NEW
    │   └── EventTextTest.kt                # NEW
    ├── desktopTest/kotlin/…         # (kotest JUnit5 runner dependency only; tests live in commonTest)
    ├── androidMain/
    │   ├── AndroidManifest.xml      # NEW
    │   └── kotlin/…/MainActivity.kt # NEW
    ├── desktopMain/kotlin/…/Main.kt # NEW: Window entry point
    └── wasmJsMain/
        ├── kotlin/…/Main.kt         # NEW: browser viewport entry point
        └── resources/index.html     # NEW: host page for the wasm bundle

.github/workflows/deploy-pages.yml   # NEW: optional GitHub Pages deployment
```

**Structure Decision**: Single project, one new Gradle module following the
dependency direction the constitution fixes (`demo-*` → `content` → `core`). All
logic and UI in `commonMain`; per-target source sets contain only the thin entry
points. Tests live in `commonTest` and execute on the desktop JVM target.

## Complexity Tracking

*No violations — table intentionally empty.*
