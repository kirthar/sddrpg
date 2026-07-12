# Tasks: Actions & Damage Resolution

**Input**: Design documents from `/specs/002-actions-damage-resolution/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/action-api.md

**Tests**: INCLUDED — constitution Principle III (test-first) is non-negotiable, same
discipline as spec 001: within every phase, test tasks are written first and MUST fail
before their implementation tasks are done. A task is complete only when
`./gradlew :core:allTests` is green on JVM and JS.

**Organization**: Grouped by user story. Note spec 002 has **two P1 stories** (US1 and
US2) — both are required for a meaningful MVP (a damage number nobody was allowed to
submit is not useful, and gating with nothing to gate into is not testable end-to-end),
so they are sequenced in spec order but the MVP checkpoint is after both.

**Path shorthand**: `MAIN` = `core/src/commonMain/kotlin/io/github/kirthar/sddrpg/core/action`,
`TEST` = `core/src/commonTest/kotlin/io/github/kirthar/sddrpg/core/action`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: US1 / US2 / US3 / US4 (user story phases only)

---

## Phase 1: Setup

- [ ] T001 [P] Add reusable action-testing fixtures (a small `ValidatedCatalog` +
      `Roster` — two characters of different classes/elemental affinities, one enemy —
      following spec 001's `RosterFixtures.kt` pattern) in `TEST/ActionFixtures.kt`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared data types every story constructs or pattern-matches: battle-time
health tracking, action representation, formula shapes, error/outcome types. No
resolution logic yet — that's built incrementally in US1–US3.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [ ] T002 [P] Write failing tests for `HealthTrack`/`BattleCombatant.isDefeated`/
      `BattleState` and `Roster.toBattleState()` (maximum captured from
      `combatant.stats[HP]`, current starts equal to maximum, one entry per combatant)
      in `TEST/BattleStateTest.kt`
- [ ] T003 [P] Write failing tests for `DamageFormula.rawMagnitude` (`Physical`/
      `Magical`/`Fixed`; floors at 0 when defense/resistance exceeds power+offense —
      never negative) in `TEST/DamageFormulaTest.kt`
- [ ] T004 Implement `HealthTrack`, `BattleCombatant`, `BattleState`, and
      `Roster.toBattleState()` in `MAIN/BattleState.kt` (research R1; does not modify
      `Combatant`)
- [ ] T005 [P] Implement `TargetingShape` enum (`SINGLE_ALLY`, `SINGLE_ENEMY`, `SELF`,
      `ALL_ALLIES`, `ALL_ENEMIES`, `ALL`) in `MAIN/TargetingShape.kt`
- [ ] T006 [P] Implement `EffectKind` enum and sealed `DamageFormula` (`Physical`,
      `Magical`, `Fixed`) with `rawMagnitude` flooring at 0 in `MAIN/DamageFormula.kt`
- [ ] T007 Implement `CombatAction` data class in `MAIN/CombatAction.kt` (depends on
      T005, T006 for its `targeting`/`formula` fields)
- [ ] T008 [P] Implement `ActionError` sealed interface, `ResolutionOutcome`, and
      `ActionResolutionResult` sealed interface (data shapes only, per
      contracts/action-api.md) in `MAIN/ActionResolution.kt`

**Checkpoint**: `:core:allTests` green on JVM+JS — foundation ready, stories can start.

---

## Phase 3: User Story 1 — Resolve a basic attack into a damage number (Priority: P1)

**Goal**: A single-target damaging action, given a valid pre-authorized submission,
resolves through the formula + elemental adjustment + health-floor pipeline into a
deterministic outcome.

**Independent Test** (from spec US1): submit an attack with known stats and a known
elemental stance; verify reported damage matches the documented formula/multiplier and
the target's health decreases by exactly that amount.

### Tests for User Story 1 (write first, must fail) ⚠️

- [ ] T009 [P] [US1] Write failing unit tests for `roundHalfAwayFromZero` (ties away
      from zero on both positive and negative values) in `TEST/RoundingTest.kt`
- [ ] T010 [P] [US1] Write failing unit tests for the fixed elemental multiplier table
      applied to a signed delta (NEUTRAL/WEAKNESS/RESISTANCE/IMMUNITY/ABSORPTION, on
      both a DAMAGE-signed and a HEAL-signed input) in `TEST/ElementalAdjustmentTest.kt`
- [ ] T011 [P] [US1] Write failing acceptance tests for US1 scenarios 1–6 (neutral
      attack matches the base formula; weakness/resistance/immunity/absorption on a
      real `BattleState` via `resolveAction`; identical inputs resolved twice produce
      an identical `Resolved` result) in `TEST/BasicAttackResolutionTest.kt`

### Implementation for User Story 1

- [ ] T012 [US1] Implement `roundHalfAwayFromZero` and the fixed multiplier table +
      apply function in `MAIN/ElementalAdjustment.kt` (research R4/R5)
- [ ] T013 [US1] Implement `resolveAction` in `MAIN/ActionResolution.kt`: minimal
      actor/target existence check, then per validated target — `rawMagnitude` →
      signed `baseDelta` → elemental adjustment (if `element != null`) → clamp to
      `[0, maximum]` → updated `HealthTrack` → `ResolutionOutcome` → new `BattleState`

**Checkpoint**: US1 green in isolation (single, pre-authorized target).

---

## Phase 4: User Story 2 — Gate actions by what a combatant can actually do (Priority: P1)

**Goal**: `resolveAction` rejects submissions the actor isn't capable of, before any
health change occurs, naming the missing capability; accepted submissions are
unaffected in their resolution (US1 still applies afterward).

**Independent Test** (from spec US2): submit an ungranted skill action and a
Warrior-only combatant's magic action; verify both rejected naming the missing
capability, while a granted action of the same combatant still succeeds.

### Tests for User Story 2 (write first, must fail) ⚠️

- [ ] T014 [US2] Write failing tests for US2 scenarios 1–4 (granted ATTACK command
      accepted through to resolution; missing skill rejected as
      `ActionError.MissingSkill` naming it; missing MAGIC command rejected as
      `ActionError.MissingCommand`; defeated actor rejected as
      `ActionError.DefeatedActor`; gating reflects a post-`withActiveClass` swap, not
      the old class) in `TEST/CapabilityGatingTest.kt`

### Implementation for User Story 2

- [ ] T015 [US2] Extend `resolveAction` with the actor-defeated check and the
      command/skill capability checks (against the actor's current
      `Combatant.capabilities`), ahead of resolution, in `MAIN/ActionResolution.kt`

**Checkpoint**: US1 + US2 green together — this is the feature's real MVP (both P1).

---

## Phase 5: User Story 3 — Target the right combatants for the action's shape (Priority: P2)

**Goal**: Target-id selections are validated against the action's declared
`TargetingShape` and the actor's allegiance; invalid/unknown targets are rejected;
already-defeated combatants are silently excluded from "all" shapes without rejecting
the action.

**Independent Test** (from spec US3): submit an "all enemies" action against a roster
of three enemies and one ally; verify all three enemies are affected and the ally is
not, with one outcome per affected enemy.

### Tests for User Story 3 (write first, must fail) ⚠️

- [ ] T016 [P] [US3] Write failing tests for US3 scenarios 1–6 (single-target
      cardinality mismatch rejected; `SELF` rejects any non-actor target; `ALL_ENEMIES`
      fans out to every opposing combatant with one outcome each; unknown target id
      rejected naming it; an already-defeated combatant excluded from an "all" shape
      without rejection vs. rejected when explicitly the sole target of a `SINGLE_*`
      shape) in `TEST/TargetingShapeTest.kt`

### Implementation for User Story 3

- [ ] T017 [US3] Implement target-set resolution against `TargetingShape` and
      allegiance (shape/cardinality validation → `ActionError.TargetShapeMismatch`,
      unknown-id detection → `ActionError.UnknownTarget`, defeated-exclusion for
      `ALL_*` shapes) in `MAIN/ActionResolution.kt`, wired into `resolveAction` ahead
      of the per-target resolution loop from T013

**Checkpoint**: US1–US3 green together.

---

## Phase 6: User Story 4 — Heal instead of harm (Priority: P3)

**Goal**: `EffectKind.HEAL` actions increase health, capped at maximum; non-elemental
heals apply no elemental adjustment (already guaranteed by T012/T013's `element ==
null` skip — this phase proves the HEAL sign and the maximum-side clamp specifically).

**Independent Test** (from spec US4): submit a healing action against a damaged ally;
verify health increases by the formula's result and never exceeds maximum.

### Tests for User Story 4 (write first, must fail) ⚠️

- [ ] T018 [P] [US4] Write failing tests for US4 scenarios 1–3 (heal increases health
      by the formula's result; heal near maximum is capped at maximum, never
      overflows; a heal with no declared element applies no elemental adjustment) plus
      a property test asserting `HealthTrack.current` stays within `[0, maximum]`
      across randomized damage/heal/absorb combinations (FR-011/SC-005) in
      `TEST/HealingTest.kt`

### Implementation for User Story 4

- [ ] T019 [US4] Verify and, if any gap is found, extend the maximum-side clamp for
      `EffectKind.HEAL` in `resolveAction` in `MAIN/ActionResolution.kt` (the `[0,
      maximum]` clamp from T013 should already cover this — this task closes any gap
      the T018 property test surfaces)

**Checkpoint**: All four user stories independently green.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T020 [P] Add a spec-001-contract-preservation test: `Roster.toBattleState()`
      leaves every field of every wrapped `Combatant` structurally unchanged (FR-012)
      in `TEST/BattleStateContractTest.kt`
- [ ] T021 [P] Add a cross-cutting determinism property test: `resolveAction` called
      twice with the same `(state, action, targetIds)` always returns an equal result,
      across randomized actions/targets (SC-003) in
      `TEST/ActionResolutionDeterminismTest.kt`
- [ ] T022 KDoc pass on all public `action` package files (surface listed in
      contracts/action-api.md)
- [ ] T023 Run quickstart validation: `./gradlew :core:allTests` green on JVM+JS;
      update `specs/002-actions-damage-resolution/quickstart.md` mapping table if any
      test file names drifted

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)** → nothing
- **Foundational (Phase 2)** → Setup; BLOCKS all stories
- **US1 (Phase 3)** → Foundational
- **US2 (Phase 4)** → Foundational; extends `MAIN/ActionResolution.kt` from T013, so
  run after US1 in a single-developer flow (both are P1 — together they form the MVP)
- **US3 (Phase 5)** → US1 + US2 (extends the same `resolveAction`/`ActionResolution.kt`)
- **US4 (Phase 6)** → US1 (reuses its clamp logic); independent of US2/US3
- **Polish (Phase 7)** → all stories

### Within Each Story

Tests first (all [P] within the story where they touch different files) → confirm they
FAIL → implementation → re-run until green. `MAIN/ActionResolution.kt` is touched by
T008, T013, T015, T017, T019 in sequence — each extends the function the previous task
left; do not parallelize these five.

### Parallel Opportunities

- T002–T003 (foundational tests) and T005–T006/T008 (foundational types touching
  different files) are parallel.
- T009–T011 (US1 tests) are parallel — three different test files.
- T016 and T018 can be written while US1/US2 implementation is in review (different
  files from `ActionResolution.kt`, though their own implementation tasks T017/T019
  must wait their turn on the shared file).
- T020–T021 (polish tests) are parallel.

---

## Implementation Strategy

**MVP = Phase 1 + 2 + 3 + 4 (US1 + US2)**: a damage number that only capable
combatants can produce — the smallest genuinely useful slice, since spec 002 treats
both as P1. Then US3 (targeting variety) and US4 (healing) as independent increments;
stop and validate at every checkpoint with `./gradlew :core:allTests`.
