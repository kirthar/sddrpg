# Tasks: Combatant & Class Model

**Input**: Design documents from `/specs/001-combatant-class-model/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/catalog-api.md, quickstart.md

**Tests**: INCLUDED — constitution Principle III (test-first) is non-negotiable: within
every phase, test tasks are written first and MUST fail before their implementation
tasks are done. A task is complete only when `./gradlew :core:allTests` is green on JVM
and JS.

**Organization**: Grouped by user story (US1 classes, US2 archetypes, US3 roster
uniformity) so each story is an independently testable increment.

**Path shorthand**: `MAIN` = `core/src/commonMain/kotlin/io/github/kirthar/sddrpg/core`,
`TEST` = `core/src/commonTest/kotlin/io/github/kirthar/sddrpg/core`. Tasks spell out the
full relative path from the repo root using these prefixes.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: US1 / US2 / US3 (user story phases only)

---

## Phase 1: Setup

**Purpose**: Package skeleton and test conventions inside the existing `core` module
(Gradle/KMP/kotest wiring already exists from the project skeleton).

- [X] T001 Create empty package directories `MAIN/model/`, `MAIN/catalog/`, `MAIN/combatant/` and mirrored `TEST/model/`, `TEST/catalog/`, `TEST/combatant/`; delete placeholder `MAIN/Engine.kt` and `TEST/ToolchainSmokeTest.kt` once first real tests exist (fold into T004/T005)
- [X] T002 [P] Add strict-JSON test helper (single `Json { ignoreUnknownKeys = false }` instance per research.md R8) in `TEST/TestJson.kt`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared value types every story depends on: typed IDs, stat block with
hybrid catalog invariants, affinity scale. Test-first within the phase.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T003 [P] Write failing unit tests for identifier value classes ((in)equality, serialization as plain JSON strings) in `TEST/model/IdentifiersTest.kt`
- [X] T004 [P] Write failing unit + property tests for `StatBlock` (all 8 core stats always present; values ≥ 0; construction rejects incomplete core set; custom stat keys allowed) in `TEST/model/StatBlockTest.kt`
- [X] T005 [P] Write failing unit tests for `Affinity` enum (5 stances; JSON names per contract; default NEUTRAL lookup helper) in `TEST/model/AffinityTest.kt`
- [X] T006 Implement identifier value classes (`StatId`, `ElementId`, `ClassId`, `ArchetypeId`, `SkillId`, `LimitBreakId`, `AiProfileId`, `EquipmentCategoryId`, `CharacterId`, `EnemyId`, `CombatantId`) with `@Serializable`/value-class JSON shape in `MAIN/model/Identifiers.kt`
- [X] T007 Implement `StatBlock` + `CoreStats` constants (HP, MP, ATTACK, DEFENSE, MAGIC, RESISTANCE, SPEED, LUCK) with completeness/bounds invariants per data-model.md in `MAIN/model/Stats.kt`
- [X] T008 [P] Implement `Affinity` enum + total lookup helper (absent ⇒ NEUTRAL) in `MAIN/model/Affinity.kt`

**Checkpoint**: `:core:allTests` green on JVM+JS — foundation ready, stories can start.

---

## Phase 3: User Story 1 — Define playable characters through classes (Priority: P1) 🎯 MVP

**Goal**: Classes as data (skills, commands, growth, equipment categories, limit
breaks); characters reference one mutable active class; level-derived stats; catalog
validation for the class/character subset.

**Independent Test** (from spec US1): author two characters with different classes in a
JSON fixture, load + validate, assert each reports exactly its class's grants; swap a
character's classId and assert capabilities follow (scenario 4).

### Tests for User Story 1 (write first, must fail) ⚠️

- [X] T009 [P] [US1] Write failing unit + property tests for `GrowthCurve` (`Linear` formula; `Table` exact values + clamping beyond last entry; outputs ≥ 0; determinism: same input ⇒ same output; serialization with `type` discriminator `linear`/`table` per contract) in `TEST/model/GrowthCurveTest.kt`
- [X] T010 [P] [US1] Write failing unit tests for `statsAt(classDef, level, baseStats)` (level ≥ 1 enforced; stats without curve stay at base; property: determinism SC-006) in `TEST/model/StatsAtTest.kt`
- [X] T011 [P] [US1] Write failing serialization round-trip tests for `ClassDefinition` + `CharacterDefinition` (JSON from contracts/catalog-api.md class/character examples; defaults: empty affinities ⇒ neutral) in `TEST/catalog/DefinitionSerializationTest.kt`
- [X] T012 [P] [US1] Write failing catalog validation tests for the US1 subset (unknown character→classId; class→skill/limitBreak/equipmentCategory/statId dangling refs; duplicate ClassId/CharacterId; undeclared custom stat; core stat redeclared in customStats; empty commands invalid; empty skills valid; errors accumulated — one fixture with N errors reports all N, each naming the offending definition per SC-002) in `TEST/catalog/CatalogValidationTest.kt`
- [X] T013 [P] [US1] Write failing acceptance tests for US1 scenarios 1–4 (capabilities equal class grants; same class two characters share grants but keep own stats; class-specific limit break listed; classId swap in re-validated catalog changes capabilities only) in `TEST/catalog/ClassCapabilityTest.kt`

### Implementation for User Story 1

- [X] T014 [US1] Implement sealed `GrowthCurve` (`Linear`, `Table`) with `at(level)` and serialization discriminator in `MAIN/model/GrowthCurve.kt`
- [X] T015 [US1] Implement pure `statsAt(classDef, level, baseStats): StatBlock` in `MAIN/model/Stats.kt` (extend file from T007)
- [X] T016 [US1] Implement `ClassDefinition`, `CharacterDefinition`, `CommandKind` enum per data-model.md in `MAIN/catalog/Definitions.kt`
- [X] T017 [US1] Implement `Catalog` (raw aggregate incl. `customStats`, `elements`, `equipmentCategories`, `knownSkills`, `knownLimitBreaks`, `knownAiProfiles`) and `ValidatedCatalog` lookup interface (classes/characters subset) in `MAIN/catalog/Catalog.kt`
- [X] T018 [US1] Implement `validateCatalog` two-phase accumulating validation + `CatalogError` variants (`DuplicateId`, `UnknownReference`, `IncompleteStatBlock`, `UndeclaredCustomStat`, `CoreStatRedeclared`, `EmptyCommands`, `NegativeValue`) for the US1 subset in `MAIN/catalog/CatalogValidation.kt`

**Checkpoint**: US1 fully green — MVP: classes/characters authorable and validated.

---

## Phase 4: User Story 2 — Define enemies through archetypes (Priority: P2)

**Goal**: Enemy archetypes as data selecting AI profile + reward tier; enemies reference
archetype + individual stats/affinities; validation extended to the enemy subset.

**Independent Test** (from spec US2): author enemies of different archetypes in a JSON
fixture, load + validate, assert each exposes its archetype's AI profile and tier.

### Tests for User Story 2 (write first, must fail) ⚠️

- [X] T019 [P] [US2] Write failing serialization round-trip tests for `ArchetypeDefinition` + `EnemyDefinition` (contract examples incl. `ABSORPTION`/`WEAKNESS` affinities) in `TEST/catalog/EnemySerializationTest.kt`
- [X] T020 [P] [US2] Write failing acceptance + validation tests for US2 scenarios 1–3 plus enemy→archetype, enemy→skill and archetype→aiProfile dangling refs, duplicate ArchetypeId/EnemyId, rewardTier ≥ 0, optional enemy skills default empty (extend fixtures, not files, of T012) in `TEST/catalog/ArchetypeTest.kt`

### Implementation for User Story 2

- [X] T021 [US2] Implement `ArchetypeDefinition` + `EnemyDefinition` in `MAIN/catalog/Definitions.kt` (extend from T016)
- [X] T022 [US2] Extend `Catalog`/`ValidatedCatalog` lookups and `validateCatalog` rules to archetypes/enemies in `MAIN/catalog/Catalog.kt` and `MAIN/catalog/CatalogValidation.kt`

**Checkpoint**: US1 and US2 both independently green.

---

## Phase 5: User Story 3 — All combatant kinds share one battle model (Priority: P3)

**Goal**: Battle-facing `Combatant` with uniform attribute surface; deterministic
`Roster` instantiation from definitions; kind/allegiance/decision-source as independent
axes; capability derivation through the active class reference (R1).

**Independent Test** (from spec US3): build a roster with one party member, one enemy,
one temporary ally; assert identical attribute surface, correct allegiances/kinds, and
configured decision sources.

### Tests for User Story 3 (write first, must fail) ⚠️

- [X] T023 [P] [US3] Write failing acceptance tests for US3 scenarios 1–3 (uniform surface across the three kinds; temporary ally = PLAYER allegiance + AI source; `withDecisionSource` changes nothing else per SC-004) in `TEST/combatant/RosterUniformityTest.kt`
- [X] T024 [P] [US3] Write failing determinism/identity tests (sequential `CombatantId` by insertion order; three enemies from one definition get distinct ids; same roster built twice ⇒ identical combatants per SC-006; enemy default decision source = archetype AI profile) in `TEST/combatant/RosterDeterminismTest.kt`
- [X] T025 [P] [US3] Write failing job-change tests (`withActiveClass` to valid class swaps capabilities and growth-derived stats, everything else intact per SC-005/R1) in `TEST/combatant/ClassChangeTest.kt`

### Implementation for User Story 3

- [X] T026 [US3] Implement `Combatant` (kind, allegiance, `CapabilitySet` derived at read time — characters: through active class; enemies: definition skills + archetype AI profile —, `DecisionSource`, `affinityTo` total lookup) + `withActiveClass`/`withDecisionSource` copies in `MAIN/combatant/Combatant.kt`
- [X] T027 [US3] Implement `RosterBuilder`/`Roster` (addPartyMember/addEnemy/addTemporaryAlly per contract; deterministic ids; level ≥ 1 for characters) in `MAIN/combatant/Roster.kt`

**Checkpoint**: all three stories independently green.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T028 [P] Add full-catalog JSON contract test: round-trip the complete example from `specs/001-combatant-class-model/contracts/catalog-api.md` (FR-014; strict parsing rejects unknown keys) in `TEST/catalog/ContractRoundTripTest.kt`
- [ ] T029 [P] Add cross-cutting property test: catalog loaded twice ⇒ identical validated model, order-independent error accumulation (SC-006) in `TEST/catalog/CatalogDeterminismTest.kt`
- [ ] T030 KDoc pass on the public API surface listed in contracts/catalog-api.md (all `MAIN/**` public types/functions); no formatting/rendering concerns in docs (Principle IV)
- [ ] T031 Run quickstart validation: `./gradlew :core:allTests` green on JVM+JS; update `specs/001-combatant-class-model/quickstart.md` mapping table if any test file names drifted

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)** → nothing
- **Foundational (Phase 2)** → Setup; BLOCKS all stories
- **US1 (Phase 3)** → Foundational
- **US2 (Phase 4)** → Foundational; extends files created in US1 (T016–T018), so run after US1 (single-developer flow) or coordinate on `catalog/` files
- **US3 (Phase 5)** → US1 (needs classes/characters + `statsAt`); uses US2 enemies for the mixed-roster test
- **Polish (Phase 6)** → all stories

### Within Each Story

Tests first (all [P] within the story) → confirm they FAIL → models → services →
re-run until green. Never mark an implementation task done with red tests.

### Parallel Opportunities

- T003–T005 (foundational tests), then T006/T008 in parallel (T007 after T006 — same-file
  dependency is none, different files; T007 depends on T006 for `StatId`).
- T009–T013 (US1 tests) all parallel — five different test files.
- T019–T020, T023–T025, T028–T029 parallel within their phases.
- US2 tests (T019–T020) can be written while US1 implementation is in review.

---

## Implementation Strategy

**MVP = Phase 1 + 2 + 3 (US1)**: classes/characters authorable, validated, level-derived
stats — enough for feature 002 (actions & damage) to start designing against real
definitions. Then US2 (enemies) and US3 (roster) as independent increments; stop and
validate at every checkpoint with `./gradlew :core:allTests`.
