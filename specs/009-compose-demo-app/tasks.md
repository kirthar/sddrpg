# Tasks: Compose Demo App

**Input**: Design documents from `/specs/009-compose-demo-app/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md,
contracts/demo-app-api.md, quickstart.md

**Tests**: INCLUDED — constitution Principle III (test-first) is non-negotiable, same
discipline as specs 001-008: within every phase, test tasks are written first and MUST
fail before their implementation tasks are done. A task is complete only when
`:demo-app:desktopTest` is green (the suite's execution host, research R8) and the
whole-project `gradle build` stays green.

**Organization**: Spec 009 has **two P1 stories** (US1 the playable graphical battle,
US2 every occurrence visible and consistent — built as one integrated increment with
separate test concerns, the same two-P1 pattern as specs 005-008: the controller's
`logLines`/`participants` ARE the US2 surface and exist from the first US1 test) and
**one P2 story** (US3, the installable APK + deployable web build, which depends on
US1+US2 already working).

**Path shorthand**: `APP-MAIN` = `demo-app/src/commonMain/kotlin/io/github/kirthar/sddrpg/demo/app`,
`APP-TEST` = `demo-app/src/commonTest/kotlin/io/github/kirthar/sddrpg/demo/app`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: US1 / US2 / US3 (user story phases only)

---

## Phase 1: Setup

- [ ] T001 Root build wiring: add `google()` to `pluginManagement`/`dependencyResolutionManagement`
      repositories and `include(":demo-app")` in `settings.gradle.kts`; add version-catalog
      entries (compose 1.11.1, kotlin-compose-compiler 2.4.0 via kotlin ref, agp 8.13.2,
      activity-compose 1.13.0) in `gradle/libs.versions.toml`; add `local.properties` to
      `.gitignore`; create uncommitted `local.properties` with `sdk.dir=/opt/android-sdk`
- [ ] T002 Create `demo-app/build.gradle.kts`: KMP module with `androidTarget()`
      (application id `io.github.kirthar.sddrpg.demo.app`, compileSdk/targetSdk 36,
      minSdk 24), `jvm("desktop")` (mainClass for `:demo-app:run` via compose desktop
      application block), `wasmJs { browser(); binaries.executable() }`; plugins
      kotlin-multiplatform + compose + kotlin-compose-compiler + com.android.application;
      commonMain deps on `:core`, `:content`, compose runtime/foundation/material3;
      androidMain dep on activity-compose; commonTest kotest engine/assertions;
      desktopTest kotest-runner-junit5 + `useJUnitPlatform()` — then verify the empty
      module configures with `gradle :demo-app:tasks`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The UI-state value types and the event/error → text rendering that both
user stories' controller work depends on. No controller/UI logic yet.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [ ] T003 [P] Write failing tests for `EventText` (every `BattleEvent` subtype —
      `DamageDealt`/`HealingApplied` with and without actor, `StatusEffectApplied`,
      `StatusEffectExpired`, `CombatantDefeated`, `TurnGranted`, `SynergyTriggered`,
      `GaugeFull`, `LimitBreakUsed`, `SummonCast` — plus `ActionError`/`LimitBreakError`/
      `SummonError` rejection wording; names resolved from the battle, never raw ids)
      in `APP-TEST/EventTextTest.kt`
- [ ] T004 Implement `BattleUiState`, `ParticipantView`, `BattlePhase`, `OfferedAction`,
      `PlayerChoice` value types (per data-model.md) in `APP-MAIN/BattleUiState.kt`
- [ ] T005 Implement `EventText` (`BattleEvent.toDisplayText`, `ActionError.toDisplayText`,
      `LimitBreakError.toDisplayText`, `SummonError.toDisplayText`) in `APP-MAIN/EventText.kt`

**Checkpoint**: `:demo-app:desktopTest` green — foundation ready.

---

## Phase 3: User Story 1 — The same battle, playable through a graphical screen (Priority: P1)

**Goal**: `BattleController` — construction advances leading auto-turns; `submit`
gates exactly as the engine gates (rejections never consume the turn), applies spec
008's full per-turn bookkeeping order, advances consecutive automatic turns, and
reaches `BattleOver` the moment one side is wiped (contracts/demo-app-api.md).

**Independent Test** (from spec US1): drive the controller purely programmatically —
construct it, submit a scripted sequence of `PlayerChoice`s, and verify phase
transitions, health snapshots, rejection handling, automatic turns, and the terminal
outcome, with zero UI involved.

### Tests for User Story 1 (write first, must fail) ⚠️

- [ ] T006 [US1] Write failing tests for construction & phases (after construction the
      phase is `AwaitingPlayerAction` for a human-controlled actor with the leading
      auto-turns' events already logged; `participants` snapshots match the raw battle
      state; a valid submission resolves and advances to the next human turn; the
      phase becomes `BattleOver` the moment one side is wiped; `submit` in `BattleOver`
      is a no-op) in `APP-TEST/BattleControllerTest.kt`
- [ ] T007 [US1] Write failing tests for gating (a structurally invalid choice sets
      `lastRejection` with readable text and changes nothing else — same actor, same
      phase, turn not consumed; the next valid submission succeeds and clears
      `lastRejection`; limit break/summon offerability appears in `actions` iff the
      engine would accept them — gauge below threshold or insufficient MP means the
      button is absent; offered `targets` never include defeated combatants for
      single-target shapes) in `APP-TEST/BattleControllerTest.kt`
- [ ] T008 [P] [US1] Write a failing determinism test (two controllers over equal
      sessions fed the identical submission sequence produce equal `uiState` values —
      log lines, participants, phase — at every step, and the identical outcome) in
      `APP-TEST/BattleControllerDeterminismTest.kt`

### Implementation for User Story 1

- [ ] T009 [US1] Implement `DemoSession` + `newDemoSession()` and `BattleController`
      (constructor auto-advance; `submit` with resolveAction/resolveLimitBreak/
      resolveSummon gating; spec 008's bookkeeping order per research R4 — events,
      status application from `appliesStatusEffect`, synergies, gauge charging, one
      tick per granted turn, markSpent, raw/effective merge-back; auto-turn
      advancement via core.ai with the no-usable-command skip; UI-state projection
      incl. offerability per research R5) in `APP-MAIN/BattleController.kt`

**Checkpoint**: US1 green — the whole battle is drivable and observable with no UI.

---

## Phase 4: User Story 2 — Every occurrence visible and consistent (Priority: P1)

**Goal**: the controller's `logLines` cover every occurrence at the moment it
happens, health snapshots always agree with the log, and the Compose `BattleScreen`
presents both (health bars, scrolling log, action buttons, target picker with
cancel, rejection text, outcome overlay).

**Independent Test** (from spec US2): after any submission, the new `logLines`
suffix describes every occurrence of that turn in order, and `participants` equals
the health the last log lines report; the screen composable compiles against
exactly the observable surface (no extra channels).

### Tests for User Story 2 (write first, must fail) ⚠️

- [ ] T010 [US2] Write failing tests for log/display consistency (every submission
      strictly appends to `logLines`; each turn's occurrences appear in bookkeeping
      order; after every state change the `participants` health equals the raw
      battle state the last logged occurrences describe; a status application, tick,
      synergy, gauge-full, limit break and summon each produce their distinct line
      when they occur) in `APP-TEST/BattleControllerTest.kt`

### Implementation for User Story 2

- [ ] T011 [US2] Implement `BattleScreen` (two side-grouped participant panels with
      name + HP bar; `LazyColumn` log auto-scrolled to the newest line; action button
      row only in `AwaitingPlayerAction`; target-picker second step whose cancel
      returns to action selection without consuming the turn — local UI state only;
      `lastRejection` surfaced as text; full-screen Victory/Defeat overlay) in
      `APP-MAIN/ui/BattleScreen.kt`

**Checkpoint**: US1 + US2 green together — a trustworthy graphical battle.

---

## Phase 5: User Story 3 — Installs on a phone, runs on the web (Priority: P2)

**Goal**: the three entry points + the two deliverable build products, plus the
optional Pages workflow.

**Independent Test** (from spec US3): `assembleDebug` produces an installable APK;
`wasmJsBrowserDistribution` produces a servable static site; the desktop app runs.

### Implementation for User Story 3

- [ ] T012 [P] [US3] Write a failing full-playthrough test (scripted submissions
      through the controller exercising a status tick, a limit break, a synergy or
      summon, ending in Victory/Defeat — US3's "the same battle everywhere" is
      backed by the shared controller, so this is its reachability proof) in
      `APP-TEST/BattleControllerPlaythroughTest.kt`
- [ ] T013 [P] [US3] Implement the Android entry point (`MainActivity` +
      `AndroidManifest.xml`) in `demo-app/src/androidMain/`
- [ ] T014 [P] [US3] Implement the desktop entry point (`main()` `Window` wiring
      `BattleScreen`) in `demo-app/src/desktopMain/kotlin/io/github/kirthar/sddrpg/demo/app/Main.kt`
- [ ] T015 [P] [US3] Implement the web entry point (`main()` `ComposeViewport` +
      `index.html` host page) in `demo-app/src/wasmJsMain/`
- [ ] T016 [US3] Produce and verify the deliverables: `:demo-app:assembleDebug` →
      APK exists under `demo-app/build/outputs/apk/debug/`;
      `:demo-app:wasmJsBrowserDistribution` → static site under
      `demo-app/build/dist/wasmJs/productionExecutable/`; send the APK to the user
      (never commit it)
- [ ] T017 [P] [US3] Add the optional GitHub Pages workflow (build
      `wasmJsBrowserDistribution`, upload-pages-artifact, deploy-pages; triggers:
      default-branch push + workflow_dispatch) in `.github/workflows/deploy-pages.yml`

**Checkpoint**: all three stories green; both deliverables produced.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T018 [P] KDoc pass on all public `demo-app` additions (surface listed in
      contracts/demo-app-api.md)
- [ ] T019 Run quickstart validation: `:demo-app:desktopTest` green, whole-project
      `gradle build` green, both deliverables reproducible; update
      `specs/009-compose-demo-app/quickstart.md` if any names drifted; update root
      `README.md` (module table: `demo-app` implemented; roadmap: milestone 009;
      `demo-app/README.md` placeholder text superseded)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)** → nothing
- **Foundational (Phase 2)** → Setup; BLOCKS all stories
- **US1 (Phase 3)** → Foundational
- **US2 (Phase 4)** → US1 (extends `BattleControllerTest.kt` and consumes the
  controller T009 built)
- **US3 (Phase 5)** → US1 + US2 (entry points wrap the finished screen; the
  playthrough test drives the finished controller)
- **Polish (Phase 6)** → all stories

### Within Each Phase

Tests first → confirm they FAIL → implementation → re-run until green.
`APP-TEST/BattleControllerTest.kt` is touched by T006, T007, then T010 in sequence —
do not parallelize these against each other. `APP-MAIN/BattleController.kt` is T009
only.

### Parallel Opportunities

- T003 (EventText tests) is parallel with nothing in its phase (T004/T005 are its
  implementation counterparts) but T004 and T005 are parallel with each other once
  T003 exists (different files).
- T008 (determinism test, own file) is parallel with T006-T007.
- T012-T015 and T017 are all parallel (five different files/directories).

---

## Implementation Strategy

**MVP = Phase 1 + 2 + 3 + 4 (US1 + US2)**: a graphical battle that is playable and
trustworthy on the dev machine. Then US3 as the increment that makes it a phone APK
and a deployable web build — the user's explicitly requested deliverables. Stop and
validate at every checkpoint with `:demo-app:desktopTest` plus a whole-project
`gradle build`.
