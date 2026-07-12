# Tasks: Battle Events & Synergies

**Input**: Design documents from `/specs/005-battle-events-synergies/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/event-api.md

**Tests**: INCLUDED — constitution Principle III (test-first) is non-negotiable, same
discipline as specs 001–004: within every phase, test tasks are written first and MUST
fail before their implementation tasks are done. A task is complete only when
`./gradlew :core:allTests` is green on JVM and JS.

**Organization**: Grouped by user story. Spec 005 has **two P1 stories** (US1 the event
log foundation, US2 the synergy trigger mechanism — both required together, per
spec.md's "Why this priority" for US2) and **two independent P2 stories** (US3 multiple
synergies coexist, US4 a synergy trigger is itself an event), each a hardening
increment on US1+US2.

**Path shorthand**: `MAIN` = `core/src/commonMain/kotlin/io/github/kirthar/sddrpg/core/event`,
`TEST` = `core/src/commonTest/kotlin/io/github/kirthar/sddrpg/core/event`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: US1 / US2 / US3 / US4 (user story phases only)

---

## Phase 1: Setup

- [ ] T001 [P] Add reusable event-package test fixtures (a small battle with two ally
      combatants whose classes grant two distinct skills — one each — plus an enemy
      target, reusing spec 001–003's roster/`BattleState` fixture patterns, sized for
      both plain event-derivation tests and synergy scenarios) in `TEST/EventFixtures.kt`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: `BattleEvent`'s closed shape and `EventLog`'s accumulation/turn-stamping
mechanics. No derivation or synergy logic yet — that's built incrementally in US1–US4.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [ ] T002 [P] Write failing tests for `EventLog` (US1 scenario 1: a fresh log's
      `entries` is empty, not an error; US1 scenario 7: `append` grows the log by
      exactly the new events, never losing or reordering existing entries; `turnIndex`
      stamping: two events appended in the same call before any `TurnGranted` share a
      `turnIndex`, a `TurnGranted` within a batch bumps the index for everything
      appended after it in that same batch, and a later `append` call continues from
      the running `turnsGranted` count) in `TEST/EventLogTest.kt`
- [ ] T003 Implement `BattleEvent` sealed interface (`DamageDealt`, `HealingApplied`,
      `StatusEffectApplied`, `StatusEffectExpired`, `CombatantDefeated`, `TurnGranted`,
      `SynergyTriggered` — data-model.md's field tables) in `MAIN/BattleEvent.kt`
- [ ] T004 Implement `LoggedEvent` and `EventLog` (`entries`, `turnsGranted`, `append`
      with turn-index stamping per data-model.md/research R2) in `MAIN/BattleEvent.kt`

**Checkpoint**: `:core:allTests` green on JVM+JS — foundation ready, stories can start.

---

## Phase 3: User Story 1 — Every battle occurrence is discoverable as a structured event (Priority: P1)

**Goal**: `eventsFromResolution`, `eventsFromApply`, `eventsFromTick`, and
`eventsFromSchedule` turn an already-completed spec 002/003/004 call into the
documented `BattleEvent`s, with `resolveAction`/`tickStatusEffects`/`TurnScheduler`
themselves completely unmodified (FR-001, FR-011).

**Independent Test** (from spec US1): resolve a damaging action, tick a
damage-over-time status effect, and query the next turn; verify each produces the
documented event, discoverable by inspecting the accumulated record afterward.

### Tests for User Story 1 (write first, must fail) ⚠️

- [ ] T005 [US1] Write failing tests for US1 scenarios 2 and 6 and FR-002 (resolving a
      damaging action produces a `DamageDealt` event identifying the target and the
      amount, `actorId` populated from the action's actor; resolving a heal produces a
      `HealingApplied` event the same way; a rejected resolution produces no events;
      the scheduler granting a turn produces a `TurnGranted` event naming that
      combatant, `NoOneReady` produces no event) via `eventsFromResolution` and
      `eventsFromSchedule` in `TEST/EventDerivationTest.kt`
- [ ] T006 [P] [US1] Write failing tests for US1 scenarios 3 and 4 (a damage-over-time
      tick produces a `DamageDealt` event indistinguishable in shape from a resolved
      action's except `actorId` is `null`; applying a status effect produces a
      `StatusEffectApplied` event, including on a refresh; a tick whose post-decrement
      `remainingDuration` reaches 0 produces a `StatusEffectExpired` event for that
      effect; a combatant with two simultaneous damage-over-time effects gets one
      `DamageDealt` event per effect, in deterministic order) via `eventsFromApply` and
      `eventsFromTick` in `TEST/StatusTickEventTest.kt`
- [ ] T007 [P] [US1] Write failing tests for US1 scenario 5 across the resolution and
      tick sources (a resolution/tick outcome that brings health to exactly 0 produces
      a `CombatantDefeated` event; one that brings an *already-defeated* combatant's
      health to 0 again produces no second `CombatantDefeated`; a resolution/tick that
      leaves health above 0 produces none) exercising the shared `defeatedEvents`
      helper via `eventsFromResolution` and `eventsFromTick` in `TEST/DefeatedEventTest.kt`
      (the synergy-bonus source is added by US2's own task)

### Implementation for User Story 1

- [ ] T008 [US1] Implement the shared `defeatedEvents(wasDefeatedBefore, resultingHealth, combatantId)`
      helper in `MAIN/EventDerivation.kt`
- [ ] T009 [US1] Implement `eventsFromResolution` (per `ResolutionOutcome`: select
      `DamageDealt`/`HealingApplied` by `action.effectKind`, `actorId = action.actorId`,
      `amount = abs(appliedDelta)`, `resultingHealth` copied through, followed by
      `defeatedEvents` against `oldState`; `Rejected` results produce nothing) in
      `MAIN/EventDerivation.kt`
- [ ] T010 [US1] Implement `eventsFromSchedule` (`Ready` → `TurnGranted`, `NoOneReady`
      → empty list) in `MAIN/EventDerivation.kt`
- [ ] T011 [US1] Implement `eventsFromApply` (always `StatusEffectApplied`) in
      `MAIN/EventDerivation.kt`
- [ ] T012 [US1] Implement `eventsFromTick` (walk `oldEffects.active` in the same
      map/list order `tickStatusEffects` itself iterates, per research R2/R6; for each
      `DamageOverTime`-kind effect, fold `amountPerTick` against a running
      `[0, maximum]`-clamped `current` to produce `DamageDealt(actorId = null, ...)`
      plus `defeatedEvents`; for each effect whose decremented `remainingDuration`
      reached 0, produce `StatusEffectExpired`) in `MAIN/EventDerivation.kt`

**Checkpoint**: US1 green — every FR-001 event kind is discoverable; no synergy
mechanism exists yet.

---

## Phase 4: User Story 2 — A class-flavored combination synergy triggers its bonus automatically (Priority: P1)

**Goal**: A data-driven `SynergyDefinition` (two required skills, same-target
condition, turn window, bonus) is detected by a pure backward scan over newly-appended
qualifying events and its bonus applied automatically (FR-004–FR-006, FR-008).

**Independent Test** (from spec US2): author a synergy requiring two specific
capabilities acting on the same target within a window of a few turns; have two
combatants each possessing one of those capabilities act on the same enemy within that
window, and verify the synergy's bonus effect is applied automatically.

### Tests for User Story 2 (write first, must fail) ⚠️

- [ ] T013 [P] [US2] Write failing tests for `validateSynergyCatalog` (`DuplicateId`,
      `SameSkill` when `firstSkillId == secondSkillId`, `NonPositiveWindow`, all
      accumulated in one pass — mirrors spec 001/004's catalog-validation convention)
      in `TEST/SynergyCatalogValidationTest.kt`
- [ ] T014 [US2] Write failing tests for US2 scenarios 1–5 (two distinct combatants
      each acting on the same target within the window trigger the synergy; the second
      qualifying action outside the window does not trigger it; the two actions
      targeting different combatants does not trigger it; the same single combatant
      satisfying both capability roles does not trigger it — FR-008; a triggered
      `BonusDamage` bonus decreases the target's health within the same `[0, maximum]`
      bounds every other damage application respects) via `detectSynergyTriggers` +
      `applySynergyBonus` in `TEST/SynergyResolutionTest.kt`
- [ ] T015 [US2] Write failing test for FR-005's nearest-prior-match rule (research
      R3): three qualifying events on the same target within the window — two eligible
      partners for a later closing event — assert the *nearest* prior eligible event
      wins the pairing, not the first-in-log or an arbitrary one, in
      `TEST/SynergyResolutionTest.kt` (extends T014's file — write after T014, not in
      parallel, to avoid both tasks editing the same new file at once)
- [ ] T016 [US2] Write failing tests for the window boundary (research R5: a
      partner exactly `window` turns earlier still triggers; one turn beyond does not)
      and the edge cases from spec.md (a synergy's window expiring with no second
      qualifying action produces no trigger and no error; a combatant defeated after
      supplying one half can still have that recorded event pair with a later
      qualifying event from a different combatant; an empty `SynergyCatalog` never
      triggers anything) in `TEST/SynergyResolutionTest.kt` (follows T015 in the same
      file)
- [ ] T017 [P] [US2] Write failing test extending `TEST/DefeatedEventTest.kt` (started
      in US1/T007) for the synergy-bonus source: a `BonusDamage` that brings health to
      exactly 0 produces a `CombatantDefeated` event via `eventsFromSynergyBonus`

### Implementation for User Story 2

- [ ] T018 [US2] Implement `SynergyId`, `SynergyBonus` (`BonusDamage`),
      `SynergyDefinition`, `SynergyCatalog`, `SynergyCatalogError`,
      `SynergyCatalogResult`, and `validateSynergyCatalog` (accumulate-all, per
      data-model.md) in `MAIN/SynergyDefinition.kt`
- [ ] T019 [US2] Implement `SynergyTrigger` and `detectSynergyTriggers` (nearest-
      prior-match backward scan over `newEvents` against `log`, live capability lookup
      via `battle`, per research R3) in `MAIN/SynergyResolution.kt`
- [ ] T020 [US2] Implement `applySynergyBonus` (same `(current - amount).coerceIn(0,
      maximum)` clamp `applyTickDamage` uses, per research R4) in
      `MAIN/SynergyResolution.kt`
- [ ] T021 [US2] Implement `eventsFromSynergyBonus` (produces `DamageDealt(actorId =
      null, ...)` plus `defeatedEvents`, reusing the T008 helper) in
      `MAIN/EventDerivation.kt`

**Checkpoint**: US1 + US2 green together — the feature's namesake payoff works
end-to-end.

---

## Phase 5: User Story 3 — Multiple synergy definitions coexist without interfering (Priority: P2)

**Goal**: Independently-satisfied synergy definitions all trigger from the same event
sequence; one triggering never suppresses another (FR-007).

**Independent Test** (from spec US3): author two different synergies whose conditions
are both satisfied by the same sequence of actions; verify both trigger, neither
suppressing the other.

### Tests for User Story 3 (write first, must fail) ⚠️

- [ ] T022 [US3] Write failing tests for US3 scenarios 1–2 (two independently-defined
      synergies whose conditions are both satisfied by the same qualifying actions
      both trigger; a catalog of several synergies where only one's condition is
      satisfied triggers only that one) via `detectSynergyTriggers` with a
      multi-entry `SynergyCatalog` in `TEST/MultipleSynergiesTest.kt`

### Implementation for User Story 3

- [ ] T023 [US3] No new implementation expected: T019's `detectSynergyTriggers` already
      evaluates every `SynergyDefinition` in the catalog independently against the same
      candidate events — this task is confirmation; fix `MAIN/SynergyResolution.kt`
      only if T022 surfaces a real gap

**Checkpoint**: US1–US3 green together.

---

## Phase 6: User Story 4 — A synergy trigger is itself an observable event (Priority: P2)

**Goal**: Triggering a synergy produces a discoverable `SynergyTriggered` event,
distinct from the events its bonus effect caused (FR-009).

**Independent Test** (from spec US4): trigger a synergy as in User Story 2, then
inspect the event record and verify it contains a `SynergyTriggered` event distinct
from the damage/heal events that caused it.

### Tests for User Story 4 (write first, must fail) ⚠️

- [ ] T024 [US4] Write failing test for US4 scenario 1 (after a synergy triggers, the
      `EventLog` contains a `SynergyTriggered` event identifying the synergy id and the
      two satisfying combatants, appended in the same batch as — and distinguishable
      from — the `DamageDealt`/`HealingApplied`/`CombatantDefeated` events its bonus
      produced) in `TEST/SynergyTriggeredEventTest.kt`

### Implementation for User Story 4

- [ ] T025 [US4] Wire the orchestration usage shown in contracts/event-api.md's
      "Typical usage": when a `SynergyTrigger` is detected, `append` a single batch
      containing `SynergyTriggered` followed by `eventsFromSynergyBonus`'s output — if
      T019/T021 don't already produce data satisfying T024 end-to-end via this call
      order, add the missing composition in `MAIN/SynergyResolution.kt`

**Checkpoint**: All four user stories independently green.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T026 [P] Add a specs-001–004-contract-preservation test: a battle resolved with
      an empty `SynergyCatalog` produces the exact same `BattleState`/`ScheduleResult`/
      `StatusTickResult` sequence as calling spec 002–004 directly with no `event`
      package involved at all (FR-011) in `TEST/EventContractTest.kt`
- [ ] T027 [P] Add a cross-cutting determinism property test: the same starting
      `EventLog`/`BattleState`/catalogs and the same sequence of resolve/tick/
      schedule/detect/apply calls, replayed twice, produce structurally identical
      results (FR-010, SC-005) in `TEST/EventDeterminismTest.kt`
- [ ] T028 KDoc pass on all public `event` package files (surface listed in
      contracts/event-api.md)
- [ ] T029 Run quickstart validation: `./gradlew :core:allTests` green on JVM+JS;
      update `specs/005-battle-events-synergies/quickstart.md` mapping table if any
      test file names drifted; update root `README.md` to mark spec 005 ✅ implemented

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)** → nothing
- **Foundational (Phase 2)** → Setup; BLOCKS all stories
- **US1 (Phase 3)** → Foundational
- **US2 (Phase 4)** → US1 (needs `EventLog`/derived events to exist as the substrate
  `detectSynergyTriggers` scans, and reuses the T008 `defeatedEvents` helper)
- **US3 (Phase 5)** → US2 (exercises the same `detectSynergyTriggers` against a
  multi-entry catalog)
- **US4 (Phase 6)** → US2 (exercises the same trigger + bonus composition, adding the
  `SynergyTriggered` event to the same `append` batch)
- **Polish (Phase 7)** → all stories

### Within Each Story

Tests first (all [P] within the story where they touch different files) → confirm they
FAIL → implementation → re-run until green. `MAIN/EventDerivation.kt` is touched by
T008, T009, T010, T011, T012, T021 in sequence; `MAIN/SynergyResolution.kt` by T019,
T020, T023, T025 in sequence — do not parallelize tasks against the same file.

### Parallel Opportunities

- T006–T007 (US1 tests) are parallel — different files, and both independent of T005.
- T013 and T017 (US2 tests) are parallel — different files (T017 extends a US1 file
  but adds a new test, not touching T007's existing tests). T014–T016 all write to
  `TEST/SynergyResolutionTest.kt` and must be done in that order, not in parallel.
- T026–T027 (polish tests) are parallel.

---

## Implementation Strategy

**MVP = Phase 1 + 2 + 3 + 4 (US1 + US2)**: both are P1 — an event log with no synergy
detection doesn't deliver the feature's namesake payoff (spec.md's own "Why this
priority" for US2), so the smallest genuinely useful slice is both together. Then US3
(multiple synergies) and US4 (synergy-trigger-as-event) as two independent hardening
increments in any order. Stop and validate at every checkpoint with
`./gradlew :core:allTests`.
