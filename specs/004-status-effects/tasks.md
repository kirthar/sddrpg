# Tasks: Status Effects

**Input**: Design documents from `/specs/004-status-effects/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/status-api.md

**Tests**: INCLUDED — constitution Principle III (test-first) is non-negotiable, same
discipline as specs 001–003: within every phase, test tasks are written first and MUST
fail before their implementation tasks are done. A task is complete only when
`./gradlew :core:allTests` is green on JVM and JS.

**Organization**: Grouped by user story. Spec 004 has **one P1 story** (US1, the
apply/track/tick/expire lifecycle — the MVP) and **three independent P2 stories**
(US2 stat modifiers, US3 incapacitation, US4 damage-over-time), each an independently
valuable, independently testable consequence built on US1's lifecycle, plus one P3
(US5, reapplication policy).

**Path shorthand**: `MAIN` = `core/src/commonMain/kotlin/io/github/kirthar/sddrpg/core/status`,
`TEST` = `core/src/commonTest/kotlin/io/github/kirthar/sddrpg/core/status`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: US1 / US2 / US3 / US4 / US5 (user story phases only)

---

## Phase 1: Setup

- [ ] T001 [P] Add reusable status-effect test fixtures (a small
      `StatusEffectCatalog` with one of each `EffectKind` — a stat modifier, an
      incapacitate, and a damage-over-time definition — reusing spec 002/003's
      `BattleState`/roster fixture patterns) in `TEST/StatusFixtures.kt`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The status-effect catalog's data shapes and validation. No resolution
logic yet — that's built incrementally in US1–US4.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [ ] T002 [P] Write failing tests for `StatusEffectCatalog` validation (duplicate
      `StatusEffectId` rejected, non-positive `duration` rejected, both accumulated in
      one pass — mirrors spec 001's `CatalogError` convention) in
      `TEST/StatusEffectCatalogValidationTest.kt`
- [ ] T003 Implement `EffectKind` sealed interface (`StatModifier`, `Incapacitate`,
      `DamageOverTime`) in `MAIN/EffectKind.kt`
- [ ] T004 Implement `StatusEffectDefinition`, `StatusEffectCatalog`,
      `StatusEffectCatalogError`, `StatusEffectCatalogResult`, and
      `validateStatusEffectCatalog` (accumulate-all, per data-model.md) in
      `MAIN/StatusEffectDefinition.kt`
- [ ] T005 [P] Implement `ActiveEffect` and `StatusEffectState` data classes (data
      shapes only, no operations yet) in `MAIN/StatusEffectState.kt`

**Checkpoint**: `:core:allTests` green on JVM+JS — foundation ready, stories can start.

---

## Phase 3: User Story 1 — Apply a status effect and watch it expire on schedule (Priority: P1)

**Goal**: An effect applied to a combatant is immediately active, decrements exactly
one tick per `tickStatusEffects` call (uniform rule, spec FR-007), and is
automatically removed once its duration reaches zero.

**Independent Test** (from spec US1): apply an effect with a known duration, tick the
documented number of times, and verify it's active immediately after application and
gone once its duration is exhausted, with no manual cleanup.

### Tests for User Story 1 (write first, must fail) ⚠️

- [ ] T006 [P] [US1] Write failing tests for US1 scenarios 1–4 (application makes an
      effect immediately active; one tick decrements remaining duration by exactly 1;
      an effect at 1 remaining duration is gone — not just at 0 — after one more tick,
      with no separate removal call; applying as a resolved action's side effect needs
      no special wiring beyond calling `applyStatusEffect` after `resolveAction`) in
      `TEST/StatusEffectLifecycleTest.kt`
- [ ] T007 [P] [US1] Write failing test for FR-007's uniform tick rule: an
      incapacitated combatant's own effect still decrements on a `tickStatusEffects`
      call even though that combatant is never the one whose turn triggered it (no
      special-casing by kind — every active effect on every combatant ticks every
      call) in `TEST/TickTimingTest.kt`

### Implementation for User Story 1

- [ ] T008 [US1] Implement `applyStatusEffect` and `removeStatusEffect` in
      `MAIN/StatusEffectResolution.kt` (reapplication replaces the existing
      `ActiveEffect` for that `(combatantId, effectId)` pair rather than appending,
      FR-008; removal is idempotent, edge case)
- [ ] T009 [US1] Implement `tickStatusEffects`'s duration-decrement-and-expiry portion
      (`StatusTickResult`; decrement every active effect on every combatant by 1;
      remove any that reach 0) in `MAIN/StatusEffectResolution.kt` — damage-over-time's
      per-tick damage is deferred to US4; `newBattle` is unchanged from the input
      until then

**Checkpoint**: US1 green — the lifecycle works; no mechanical consequence exists yet.

---

## Phase 4: User Story 2 — A stat-modifying effect changes resolution outcomes (Priority: P2)

**Goal**: `deriveEffectiveBattleState` wraps affected combatants so `resolveAction`'s
formulas read modifier-adjusted stats, while the underlying `Combatant` (spec 001) is
never altered.

**Independent Test** (from spec US2): resolve an identical attack once with no
modifiers and once with an active defense-increasing modifier; verify less damage in
the modified case with base stats identical in both runs.

### Tests for User Story 2 (write first, must fail) ⚠️

- [ ] T010 [P] [US2] Write failing tests for `EffectiveCombatant` (only `stats` is
      overridden; `id`, `capabilities`, `affinities`, `decisionSource`, `kind`,
      `allegiance` all forward to the wrapped combatant unchanged — delegation
      correctness) in `TEST/EffectiveCombatantTest.kt`
- [ ] T011 [P] [US2] Write failing tests for US2 scenarios 1–4 (an increased Defense
      modifier resolves to less damage than an unmodified attack; a decreased Defense
      modifier resolves to more; the combatant's own base stats are unchanged in both
      runs; two different modifiers on the same stat combine additively, not
      last-applied-wins) via `deriveEffectiveBattleState` + spec 002's `resolveAction`
      in `TEST/StatModifierTest.kt`

### Implementation for User Story 2

- [ ] T012 [US2] Implement `EffectiveCombatant` (`Combatant by base`, overriding only
      `stats`) in `MAIN/EffectiveCombatant.kt`
- [ ] T013 [US2] Implement `deriveEffectiveBattleState`'s stat-modifier portion (group
      active `StatModifier` effects by `statId`, sum deltas, floor at 0, wrap only
      participants whose effective stats actually differ) in
      `MAIN/StatusEffectResolution.kt`

**Checkpoint**: US1 + US2 green together.

---

## Phase 5: User Story 3 — An incapacitating effect removes a combatant from the turn order (Priority: P2)

**Goal**: An active `Incapacitate`-kind effect forces the affected combatant's
effective Speed to `0`, reusing spec 003's already-tested zero-Speed exclusion so the
combatant is never offered a turn while it's active, and resumes normal scheduling the
moment it ends.

**Independent Test** (from spec US3): apply an incapacitating effect to one combatant
in a multi-combatant battle, query the scheduler repeatedly, and verify that combatant
never receives a turn while active, and resumes once the effect ends.

### Tests for User Story 3 (write first, must fail) ⚠️

- [ ] T014 [US3] Write failing tests for US3 scenarios 1–3 (an incapacitated
      combatant's effective Speed is 0; fed into
      `ActiveTimeBattleScheduler.nextTurn` it is never the one reported ready; once the
      effect ends — removed via a tick reaching 0 — normal scheduling resumes for that
      combatant; among otherwise-eligible combatants only non-incapacitated ones are
      ever reported) in `TEST/IncapacitateTest.kt`

### Implementation for User Story 3

- [ ] T015 [US3] Extend `deriveEffectiveBattleState` to force effective Speed to `0`
      when an `Incapacitate`-kind effect is active on a participant — a final clamp
      applied after any stat-modifier summation, not itself a summed delta, so it
      cannot be offset by a simultaneous Speed-increasing modifier (research R5,
      data-model.md step 3) in `MAIN/StatusEffectResolution.kt`

**Checkpoint**: US1–US3 green together.

---

## Phase 6: User Story 4 — A damage-over-time effect harms its target automatically (Priority: P2)

**Goal**: `tickStatusEffects` applies each active `DamageOverTime` effect's per-tick
damage directly to `HealthTrack`, clamped to `[0, maximum]`, with no `CombatAction`
and no actor involved.

**Independent Test** (from spec US4): apply a damage-over-time effect with a known
per-tick amount, tick several times, and verify health decreases by exactly that
amount each tick, never below zero, with no action submitted.

### Tests for User Story 4 (write first, must fail) ⚠️

- [ ] T016 [P] [US4] Write failing tests for US4 scenarios 1–3 (each tick reduces
      health by the documented per-tick amount with no `CombatAction` involved; a tick
      that would go below zero clamps at zero; a tick while already at zero health has
      no further effect) plus the research R7 ordering rule (the tick that reduces
      remaining duration to 0 still deals its damage — "N turns of poison" means N
      ticks of damage) in `TEST/DamageOverTimeTest.kt`

### Implementation for User Story 4

- [ ] T017 [US4] Extend `tickStatusEffects` to apply `DamageOverTime`-kind damage for
      every effect still active (`remainingDuration >= 0` after decrementing, i.e. not
      yet removed) before removal, accumulating into `StatusTickResult.newBattle` with
      `[0, maximum]` clamping (research R6/R7) in `MAIN/StatusEffectResolution.kt`

**Checkpoint**: US1–US4 green together.

---

## Phase 7: User Story 5 — Reapplying an already-active effect behaves predictably (Priority: P3)

**Goal**: Reapplying the same effect resets its remaining duration to full and never
doubles a stat modifier's magnitude, confirming FR-008's rule end-to-end through
`deriveEffectiveBattleState`.

**Independent Test** (from spec US5): apply the same effect twice with turns
advancing between, and verify the second application resets duration to full rather
than adding to it, with unchanged stat-modifier magnitude.

### Tests for User Story 5 (write first, must fail) ⚠️

- [ ] T018 [P] [US5] Write failing tests for US5 scenarios 1–2 (reapplying an effect
      at partial remaining duration resets it to the full documented duration; a
      reapplied stat-modifying effect's adjustment magnitude through
      `deriveEffectiveBattleState` is unchanged, not doubled) in
      `TEST/ReapplicationTest.kt`

### Implementation for User Story 5

- [ ] T019 [US5] No new implementation expected: T008's `applyStatusEffect` already
      replaces rather than appends, so reapplication was already correct — this task
      is confirmation; fix `MAIN/StatusEffectResolution.kt` only if T018 surfaces a
      real gap

**Checkpoint**: All five user stories independently green.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [ ] T020 [P] Add a specs-001–003-contract-preservation test: a participant with no
      active effects is returned byte-identical (same `Combatant` reference) by
      `deriveEffectiveBattleState` (FR-011) in `TEST/StatusContractTest.kt`
- [ ] T021 [P] Add a cross-cutting determinism property test: the same starting
      `StatusEffectState`/`BattleState` and the same sequence of apply/tick/remove
      calls, replayed twice, produce structurally identical results (SC-007) in
      `TEST/StatusDeterminismTest.kt`
- [ ] T022 KDoc pass on all public `status` package files (surface listed in
      contracts/status-api.md)
- [ ] T023 Run quickstart validation: `./gradlew :core:allTests` green on JVM+JS;
      update `specs/004-status-effects/quickstart.md` mapping table if any test file
      names drifted

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)** → nothing
- **Foundational (Phase 2)** → Setup; BLOCKS all stories
- **US1 (Phase 3)** → Foundational
- **US2 (Phase 4)** → US1 (needs `applyStatusEffect`/`StatusEffectState` to exist to
  set up an active modifier, and extends `MAIN/StatusEffectResolution.kt` from T009)
- **US3 (Phase 5)** → US1 + US2 (extends the same `deriveEffectiveBattleState` T013
  started)
- **US4 (Phase 6)** → US1 (extends `tickStatusEffects` from T009); independent of
  US2/US3
- **US5 (Phase 7)** → US1 (and US2 for its stat-modifier-magnitude assertion)
- **Polish (Phase 8)** → all stories

### Within Each Story

Tests first (all [P] within the story where they touch different files) → confirm
they FAIL → implementation → re-run until green. `MAIN/StatusEffectResolution.kt` is
touched by T008, T009, T013, T015, T017, T019 in sequence — each extends the function
the previous task left; do not parallelize these against each other.

### Parallel Opportunities

- T002 and T005 (foundational: validation tests / state data shapes) are parallel.
- T006–T007 (US1 tests) are parallel — different files.
- T010–T011 (US2 tests) are parallel.
- T016 (US4 tests) can be written any time after Foundational, independent of US2/US3.
- T018 (US5 tests) can be written any time after US1's lifecycle exists.
- T020–T021 (polish tests) are parallel.

---

## Implementation Strategy

**MVP = Phase 1 + 2 + 3 (US1)**: an effect can be applied, tracked, ticked, and
automatically expires — the smallest genuinely useful slice, since only US1 is P1.
Then US2 (stat modifiers), US3 (incapacitation), and US4 (damage-over-time) as three
independent hardening increments in any order — each is a self-contained consequence
of the same lifecycle. US5 (reapplication policy) last. Stop and validate at every
checkpoint with `./gradlew :core:allTests`.
