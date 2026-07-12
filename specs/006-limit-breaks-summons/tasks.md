# Tasks: Limit Breaks & Summons

**Input**: Design documents from `/specs/006-limit-breaks-summons/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/limitbreak-summon-api.md

**Tests**: INCLUDED — constitution Principle III (test-first) is non-negotiable, same
discipline as specs 001-005: within every phase, test tasks are written first and MUST
fail before their implementation tasks are done. A task is complete only when
`./gradlew :core:allTests` is green on JVM and JS.

**Organization**: Grouped by user story. Spec 006 has **two P1 stories** (US1 gauge
charging, US2 limit-break resolution — both required together, per spec.md's "Why
this priority" for US2, mirroring spec 005's US1+US2) and **two independent P2
stories** (US3 summons, US4 gauge/limit-break/summon observability), each a
self-contained increment on top of US1+US2's foundation (US3 and US4 don't depend on
each other).

**Path shorthand**: `MAIN` = `core/src/commonMain/kotlin/io/github/kirthar/sddrpg/core/limitbreak`,
`MAIN_EVENT` = `core/src/commonMain/kotlin/io/github/kirthar/sddrpg/core/event` (one
new file only — `BattleEvent.kt` itself is never touched, research R4),
`TEST` = `core/src/commonTest/kotlin/io/github/kirthar/sddrpg/core/limitbreak`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: US1 / US2 / US3 / US4 (user story phases only)

---

## Phase 1: Setup

- [ ] T001 [P] Add reusable limitbreak-package test fixtures (a battle with one
      combatant whose class declares a limit break with a known threshold and MP for
      summon casting, plus an enemy target, reusing spec 001-005's roster/`BattleState`
      fixture patterns) in `TEST/LimitBreakFixtures.kt`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The two catalogs' data shapes and validation, plus the two battle-time
state shapes (data only, no operations yet — those are built incrementally in US1-US3).

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [ ] T002 [P] Write failing tests for `LimitBreakCatalog`/`SummonCatalog` validation
      (`DuplicateId` and `NonPositiveThreshold` for limit breaks; `DuplicateId` and
      `NegativeCost` for summons, both accumulated in one pass — mirrors spec
      001/004/005's `*CatalogError` convention) in `TEST/LimitBreakCatalogValidationTest.kt`
- [ ] T003 Implement `LimitBreakDefinition`, `LimitBreakCatalog`,
      `LimitBreakCatalogError`, `LimitBreakCatalogResult`, and
      `validateLimitBreakCatalog` (accumulate-all, per data-model.md) in
      `MAIN/LimitBreakDefinition.kt`
- [ ] T004 Implement `SummonId`, `SummonDefinition`, `SummonCatalog`,
      `SummonCatalogError`, `SummonCatalogResult`, and `validateSummonCatalog` in
      `MAIN/SummonDefinition.kt`
- [ ] T005 [P] Implement `LimitGaugeState` data shape (data only) in
      `MAIN/LimitGaugeState.kt`
- [ ] T006 [P] Implement `ResourceState` data shape and `BattleState.initialResourceState()`
      in `MAIN/ResourceState.kt`

**Checkpoint**: `:core:allTests` green on JVM+JS — foundation ready, stories can start.

---

## Phase 3: User Story 1 — A combatant's limit gauge fills from the intensity of battle (Priority: P1)

**Goal**: `chargeLimitGauge` is a pure post-processing scan over already-derived
`BattleEvent.DamageDealt` events (research R2) — `resolveAction`/`tickStatusEffects`
remain completely unmodified (FR-001, FR-012).

**Independent Test** (from spec US1): apply a sequence of damaging occurrences to a
combatant and verify their gauge rises by exactly the documented amount each time,
starting at zero, never exceeding the threshold.

### Tests for User Story 1 (write first, must fail) ⚠️

- [ ] T007 [US1] Write failing tests for US1 scenarios 1-3 (a fresh gauge rises by the
      exact damage amount from a `DamageDealt` event targeting that combatant; damage
      that would push the gauge past threshold clamps it at threshold, never exceeding;
      a combatant whose class declares no limit break never gets a gauge entry) plus
      edge cases (a defeated combatant's gauge still charges from further damage
      events, matching spec 004/005's precedent that bookkeeping doesn't stop at zero
      health; a stale `LimitBreakId` absent from the catalog is skipped gracefully
      rather than crashing) via `chargeLimitGauge` in `TEST/LimitGaugeChargeTest.kt`

### Implementation for User Story 1

- [ ] T008 [US1] Implement `chargeLimitGauge` (per `DamageDealt` event: look up the
      target's active limit break via `capabilities.limitBreaks.firstOrNull()`,
      research R5; skip if none or if its id is absent from the catalog; otherwise
      `(current + amount).coerceAtMost(threshold)`) in `MAIN/LimitGaugeState.kt`

**Checkpoint**: US1 green — gauges accumulate correctly; nothing consumes them yet.

---

## Phase 4: User Story 2 — A full limit gauge unlocks one especially powerful action (Priority: P1)

**Goal**: `resolveLimitBreak` enforces its own gauge gate, then synthesizes a
`CombatAction(command = CommandKind.SKILL, ...)` and delegates to `resolveAction`
unmodified (research R1); the gauge resets to zero only on `Resolved`.

**Independent Test** (from spec US2): fill a combatant's gauge to threshold, use their
limit break, and verify it resolves as a powerful effect with the same health-bounds
guarantees as any other resolved action, and that the gauge is back to zero afterward.

### Tests for User Story 2 (write first, must fail) ⚠️

- [ ] T009 [US2] Write failing tests for US2 scenarios 1-3 (a below-threshold attempt
      is rejected as `LimitBreakError.GaugeNotFull`, never silently ignored; a
      threshold-reached use resolves within the same [0, maximum] health bounds every
      other resolved action respects; the gauge is reset to zero immediately after a
      successful use) plus a combatant with no active limit break rejecting as
      `LimitBreakError.NoActiveLimitBreak`, and the exact-at-threshold boundary case
      (gauge == threshold is usable, not gauge > threshold only) in
      `TEST/LimitBreakResolutionTest.kt`

### Implementation for User Story 2

- [ ] T010 [US2] Implement `LimitBreakError`, `LimitBreakResolutionResult`, and
      `resolveLimitBreak` (per data-model.md's four-step algorithm) in
      `MAIN/LimitBreakSummonResolution.kt`

**Checkpoint**: US1 + US2 green together — the feature's namesake payoff works
end-to-end.

---

## Phase 5: User Story 3 — A summon delivers a class-agnostic burst effect for a resource cost (Priority: P2)

**Goal**: `resolveSummon` deducts MP only after a sufficiency check, then synthesizes
a `CombatAction(command = CommandKind.SUMMON, ...)` and delegates to `resolveAction`
unmodified — `resolveAction`'s own existing `CommandKind.SUMMON` capability gate
(already present in spec 001) applies natively (research R1).

**Independent Test** (from spec US3): cast a summon with sufficient resource available
and verify its effect resolves against its target(s) and the resource is deducted;
attempt to cast one without sufficient resource and verify it is rejected with nothing
deducted.

### Tests for User Story 3 (write first, must fail) ⚠️

- [ ] T011 [US3] Write failing tests for US3 scenarios 1-3 (sufficient resource
      resolves the summon's effect and deducts exactly its cost; insufficient resource
      rejects with nothing deducted; the battle's participant count is unchanged
      before and after a summon resolves) plus the exact-cost-equals-available-resource
      boundary (allowed, leaves exactly zero) and an unknown `SummonId` rejecting as
      `SummonError.UnknownSummon` in `TEST/SummonResolutionTest.kt`

### Implementation for User Story 3

- [ ] T012 [P] [US3] Implement `deductResource` in `MAIN/ResourceState.kt`
- [ ] T013 [US3] Implement `SummonError`, `SummonResolutionResult`, and
      `resolveSummon` (per data-model.md's four-step algorithm) in
      `MAIN/LimitBreakSummonResolution.kt`

**Checkpoint**: US1-US3 green together.

---

## Phase 6: User Story 4 — Gauge-full, limit-break-used, and summon-cast are each observable (Priority: P2)

**Goal**: `GaugeFull`, `LimitBreakUsed`, `SummonCast` extend `BattleEvent` from a new
file in the same package (research R4, empirically verified) without editing
`BattleEvent.kt`; their `eventsFromX` derivations follow spec 005's established shape.

**Independent Test** (from spec US4): trigger each of the three occurrences and verify
each produces its own distinct, discoverable event, with no change required to
`resolveAction`, `tickStatusEffects`, `TurnScheduler`, or `EventLog`'s existing public
shape.

### Tests for User Story 4 (write first, must fail) ⚠️

- [ ] T014 [US4] Write failing tests for US4 scenarios 1-3 (a gauge crossing from
      below threshold to at-or-above threshold produces exactly one `GaugeFull` event,
      and further charging while already at threshold produces no additional one; a
      resolved limit break produces a `LimitBreakUsed` event identifying the limit
      break and the combatant, alongside the damage/heal event(s) its effect produced;
      a resolved summon produces a `SummonCast` event identifying the summon and the
      combatant, alongside its own damage/heal event(s)) via `eventsFromGaugeCharge`/
      `eventsFromLimitBreak`/`eventsFromSummon` in `TEST/LimitBreakSummonEventTest.kt`

### Implementation for User Story 4

- [ ] T015 [US4] Implement `GaugeFull`, `LimitBreakUsed`, `SummonCast` (new
      `BattleEvent` variants) and `eventsFromGaugeCharge`/`eventsFromLimitBreak`/
      `eventsFromSummon` in `MAIN_EVENT/LimitBreakSummonEvents.kt` (new file — do not
      edit `BattleEvent.kt`)

**Checkpoint**: All four user stories independently green.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T016 [P] Add a specs-001-005-contract-preservation test: a battle resolved with
      empty `LimitBreakCatalog`/`SummonCatalog` produces the exact same `BattleState`/
      `ActionResolutionResult` sequence as calling spec 002 directly with no
      `limitbreak` package involved at all (FR-012) in `TEST/LimitBreakContractTest.kt`
- [ ] T017 [P] Add a cross-cutting determinism property test: the same starting
      `LimitGaugeState`/`ResourceState`/`BattleState` and the same sequence of
      charge/resolve calls, replayed twice, produce structurally identical results
      (FR-011, SC-005) in `TEST/LimitBreakDeterminismTest.kt`
- [ ] T018 KDoc pass on all public `limitbreak` package files and the new `event`
      file (surface listed in contracts/limitbreak-summon-api.md)
- [ ] T019 Run quickstart validation: `./gradlew :core:allTests` green on JVM+JS;
      update `specs/006-limit-breaks-summons/quickstart.md` mapping table if any test
      file names drifted; update root `README.md` to mark spec 006 ✅ implemented

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)** → nothing
- **Foundational (Phase 2)** → Setup; BLOCKS all stories
- **US1 (Phase 3)** → Foundational
- **US2 (Phase 4)** → US1 (needs `LimitGaugeState`/`chargeLimitGauge` to exist so a
  gauge can actually reach threshold before use is meaningful to test)
- **US3 (Phase 5)** → Foundational only (independent of US1/US2 — summons never touch
  the gauge); may be developed in parallel with US1/US2 by a different contributor
- **US4 (Phase 6)** → US2 + US3 (needs both `resolveLimitBreak` and `resolveSummon` to
  exist to exercise their event derivations)
- **Polish (Phase 7)** → all stories

### Within Each Story

Tests first (all [P] within the story where they touch different files) → confirm
they FAIL → implementation → re-run until green. `MAIN/LimitBreakSummonResolution.kt`
is touched by T010 then T013 in sequence — do not parallelize these against each
other.

### Parallel Opportunities

- T002 and T005-T006 (foundational: validation tests / state data shapes) are
  parallel.
- US3 (Phase 5) can be developed in parallel with US1+US2 (Phases 3-4) — genuinely
  independent, per the Phase Dependencies above.
- T012 (`deductResource`) is parallel with nothing else in US3 — it's the only task in
  its file at that point, but T013 depends on it being done first.
- T016-T017 (polish tests) are parallel.

---

## Implementation Strategy

**MVP = Phase 1 + 2 + 3 + 4 (US1 + US2)**: both are P1 — a gauge with no trigger
doesn't deliver what "limit breaks" promises (spec.md's own "Why this priority" for
US2), so the smallest genuinely useful slice is both together. Then US3 (summons,
independent) and US4 (observability, depends on both US2 and US3) as two hardening
increments. Stop and validate at every checkpoint with `./gradlew :core:allTests`.
