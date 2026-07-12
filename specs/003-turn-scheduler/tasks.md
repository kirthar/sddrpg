# Tasks: Turn Scheduler

**Input**: Design documents from `/specs/003-turn-scheduler/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/scheduler-api.md

**Tests**: INCLUDED — constitution Principle III (test-first) is non-negotiable, same
discipline as specs 001–002: within every phase, test tasks are written first and MUST
fail before their implementation tasks are done. A task is complete only when
`./gradlew :core:allTests` is green on JVM and JS.

**Organization**: Grouped by user story. Spec 003 has **two P1 stories** (US1 query/
advance loop, US2 defeated exclusion) — same reasoning as spec 002: a scheduler that
can query but never advance, or one that keeps offering turns to the defeated, isn't
independently useful. Both together form the MVP.

**Path shorthand**: `MAIN` = `core/src/commonMain/kotlin/io/github/kirthar/sddrpg/core/schedule`,
`TEST` = `core/src/commonTest/kotlin/io/github/kirthar/sddrpg/core/schedule`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: US1 / US2 / US3 (user story phases only)

---

## Phase 1: Setup

- [ ] T001 [P] Add reusable schedule-testing fixtures (a small `ValidatedCatalog` +
      `Roster`/`BattleState` with combatants of clearly distinct Speed values,
      following spec 002's `ActionFixtures.kt` pattern) in `TEST/ScheduleFixtures.kt`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The scheduler abstraction's data shapes and the integer-arithmetic
building block every story needs. No `nextTurn` algorithm body yet — that's built
incrementally in US1–US3, mirroring how spec 002 phased in `resolveAction`.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [ ] T002 [P] Write failing tests for `initialSchedule` (every `BattleState`
      participant gets a readiness entry of `0`; one entry per participant, no more,
      no fewer) in `TEST/InitialScheduleTest.kt`
- [ ] T003 [P] Write failing unit + property tests for the integer ceiling-division
      helper (exact division, remainder cases, `0` numerator, never uses `Double`) in
      `TEST/CeilDivTest.kt`
- [ ] T004 Implement `TurnScheduler<State>` interface and `ScheduleResult<State>`
      sealed type (data shapes only, per contracts/scheduler-api.md — no algorithm
      body) in `MAIN/TurnScheduler.kt`
- [ ] T005 [P] Implement `AtbScheduleState` data class and the `ceilDiv` integer
      ceiling-division helper in `MAIN/ActiveTimeBattleScheduler.kt`
- [ ] T006 Implement `ActiveTimeBattleScheduler.initialSchedule` (readiness = 0 for
      every participant, research R4) in `MAIN/ActiveTimeBattleScheduler.kt` (extends
      T005's file)

**Checkpoint**: `:core:allTests` green on JVM+JS — foundation ready, stories can start.

---

## Phase 3: User Story 1 — Determine and advance whose turn is next (Priority: P1)

**Goal**: `nextTurn` reports exactly one ready combatant using the tick-jump readiness
math (research R2), favoring higher-Speed combatants over time; `markSpent` advances
the schedule so the same combatant isn't reported again immediately.

**Independent Test** (from spec US1): build a battle with a fast and a slow combatant,
repeatedly request and consume the next turn, and verify the faster combatant is
offered turns more often, with the schedule visibly advancing after each consumption.

### Tests for User Story 1 (write first, must fail) ⚠️

- [ ] T007 [P] [US1] Write failing tests for US1 scenarios 1 and 4 (`nextTurn` reports
      exactly one ready combatant for a roster of distinct speeds; readiness is
      available from the very start, before any turn has ever been granted) in
      `TEST/NextTurnTest.kt`
- [ ] T008 [P] [US1] Write failing tests for US1 scenario 2 (immediately after
      `markSpent`, requesting the next turn again reports a different combatant — the
      one just spent does not re-qualify instantly) in `TEST/MarkSpentTest.kt`
- [ ] T009 [P] [US1] Write failing property test for US1 scenario 3 / SC-001 (over many
      consumed turns, a combatant with distinctly higher Speed is never offered fewer
      turns than a slower one) in `TEST/ReadinessProportionalityTest.kt`

### Implementation for User Story 1

- [ ] T010 [US1] Implement `ActiveTimeBattleScheduler.nextTurn`'s tick-jump algorithm
      for the no-defeat case: per-candidate `ceilDiv` ticks-needed, `minTicks`, advance
      every candidate's readiness by `minTicks * speed`, then pick the winner among all
      candidates whose advanced readiness reaches `READY_THRESHOLD` **using the
      participants-order tie-break rule from the start** (research R3: `minByOrNull`
      on index in `battle.participants` — correct and no more code whether one or
      several candidates qualify, so there is no separate "no-tie case" to special-case)
      — **must guard `speed <= 0` out of candidacy before calling `ceilDiv`**
      (division-by-zero safety, research R2/spec edge case) in
      `MAIN/ActiveTimeBattleScheduler.kt`
- [ ] T011 [US1] Implement `ActiveTimeBattleScheduler.markSpent` (subtract
      `READY_THRESHOLD` from the named combatant's readiness, carrying over any
      overshoot rather than resetting to `0`, research R2) in
      `MAIN/ActiveTimeBattleScheduler.kt`

**Checkpoint**: US1 green in isolation (distinct-speed roster, no defeats, no ties).

---

## Phase 4: User Story 2 — Defeated combatants are automatically excluded (Priority: P1)

**Goal**: `nextTurn` never reports a defeated combatant, reading defeated status fresh
from the passed-in `BattleState` every call (research R4); an all-defeated battle
reports `NoOneReady` instead of erroring.

**Independent Test** (from spec US2): defeat one combatant in a battle of three, then
request and consume several turns; verify the defeated one is never reported while the
other two continue scheduling normally.

### Tests for User Story 2 (write first, must fail) ⚠️

- [ ] T012 [US2] Write failing tests for US2 scenarios 1–3 (a defeated combatant is
      never reported ready; a combatant defeated between two `nextTurn` calls is
      excluded starting from the next call; once one side is fully defeated only the
      remaining side is ever reported) plus FR-009/SC-005 (every combatant defeated ⇒
      `NoOneReady`, not an error) in `TEST/DefeatedExclusionTest.kt`

### Implementation for User Story 2

- [ ] T013 [US2] Extend `nextTurn`'s candidate filter to exclude defeated participants
      (checked fresh against the `battle: BattleState` parameter every call — never
      cached in `AtbScheduleState`, research R4) in `MAIN/ActiveTimeBattleScheduler.kt`

**Checkpoint**: US1 + US2 green together — this is the feature's real MVP (both P1).

---

## Phase 5: User Story 3 — Deterministic turn order and tie-breaking (Priority: P2)

**Goal**: When multiple candidates reach readiness at the same `minTicks`, the winner
is chosen by a fixed, documented rule (lowest index in `battle.participants`, research
R3); replaying the same battle configuration and consumption sequence always produces
the identical turn order.

**Independent Test** (from spec US3): configure two combatants with identical Speed,
run the same request/consume sequence twice from the same starting configuration, and
verify both runs produce the identical turn order with the same tie resolution.

### Tests for User Story 3 (write first, must fail) ⚠️

- [ ] T014 [P] [US3] Write failing tests for tie-breaking (two combatants with
      identical Speed reaching readiness simultaneously are resolved the same way
      every time, by roster/participants order — not by `CombatantId` string order,
      research R3) in `TEST/TieBreakTest.kt`
- [ ] T015 [P] [US3] Write failing property test for full-sequence determinism (the
      same battle configuration and the same sequence of `nextTurn`/`markSpent` calls,
      replayed from scratch, always yields a structurally identical turn order, spec
      FR-006/SC-003) in `TEST/DeterminismTest.kt`

### Implementation for User Story 3

- [ ] T016 [US3] No new implementation expected: T010 already implements tie-break
      selection by `battle.participants` index order (research R3) as part of its
      single winner-selection step, so this task is confirmation — run T014/T015 and
      fix `MAIN/ActiveTimeBattleScheduler.kt` only if either test surfaces a real gap

**Checkpoint**: All three user stories independently green.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T017 [P] Write and confirm the zero-Speed edge case (a combatant with Speed ≤ 0
      never becomes ready and never causes a division-by-zero or crash) in
      `TEST/ZeroSpeedTest.kt`
- [ ] T018 KDoc pass on all public `schedule` package files (surface listed in
      contracts/scheduler-api.md)
- [ ] T019 Run quickstart validation: `./gradlew :core:allTests` green on JVM+JS;
      update `specs/003-turn-scheduler/quickstart.md` mapping table if any test file
      names drifted

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)** → nothing
- **Foundational (Phase 2)** → Setup; BLOCKS all stories
- **US1 (Phase 3)** → Foundational
- **US2 (Phase 4)** → Foundational; extends `MAIN/ActiveTimeBattleScheduler.kt` from
  T010/T011, so run after US1 in a single-developer flow (both are P1 — together they
  form the MVP)
- **US3 (Phase 5)** → US1 + US2 (extends the same file again)
- **Polish (Phase 6)** → all stories

### Within Each Story

Tests first (all [P] within the story where they touch different files) → confirm they
FAIL → implementation → re-run until green. `MAIN/ActiveTimeBattleScheduler.kt` is
touched by T005, T006, T010, T011, T013, T016 in sequence — each extends the function
the previous task left; do not parallelize these against each other.

### Parallel Opportunities

- T002–T003 (foundational tests) and T005 (different file from T004) are parallel.
- T007–T009 (US1 tests) are parallel — three different test files.
- T014–T015 (US3 tests) are parallel.
- T017 (polish test) can be written any time after T010's zero-Speed guard exists.

---

## Implementation Strategy

**MVP = Phase 1 + 2 + 3 + 4 (US1 + US2)**: a scheduler that correctly picks the next
ready combatant by Speed, advances past spent turns, and never offers a turn to the
defeated — the smallest genuinely useful slice, since spec 003 treats both as P1. Then
US3 (deterministic tie-breaking) as an independent hardening increment; stop and
validate at every checkpoint with `./gradlew :core:allTests`.
