# Tasks: Content Loading

**Input**: Design documents from `/specs/007-content-loading/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/content-loading-api.md

**Tests**: INCLUDED — constitution Principle III (test-first) is non-negotiable, same
discipline as specs 001-006: within every phase, test tasks are written first and MUST
fail before their implementation tasks are done. A task is complete only when
`./gradlew :content:allTests` is green on JVM and JS.

**Organization**: Grouped by user story. Spec 007 has **two P1 stories** (US1 the
load-and-validate pipeline, US2 cross-catalog reference checking — both required
together, per spec.md's "Why this priority" for US2, mirroring specs 005/006's own
two-P1 pattern) and **one P2 story** (US3 the shipped demo content pack, which
depends on US1+US2 already working).

**Path shorthand**: `MAIN` = `content/src/commonMain/kotlin/io/github/kirthar/sddrpg/content`,
`TEST` = `content/src/commonTest/kotlin/io/github/kirthar/sddrpg/content`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: US1 / US2 / US3 (user story phases only)

---

## Phase 1: Setup

- [X] T001 [P] Add reusable content-loading test fixtures (a minimal but complete
      `ContentFileDto`-shaped JSON literal covering every catalog kind, plus small
      targeted "broken" variants for each individual failure mode this feature must
      catch, reusing spec 001-006's fixture-authoring conventions) in
      `TEST/ContentFixtures.kt`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The DTO shapes, their `toCore()` mappings, and the `ContentPack`/
`ContentProblem`/`ContentLoadResult` data shapes. No loading/validation logic yet —
that's built incrementally in US1-US2.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T002 [P] Write failing round-trip tests for every DTO's `toCore()` mapping
      (`TargetingShapeDto`, `ActionEffectKindDto`, `DamageFormulaDto`,
      `StatusEffectKindDto`, `StatusEffectDefinitionDto`, `SynergyBonusDto`,
      `SynergyDefinitionDto`, `LimitBreakDefinitionDto`, `SummonDefinitionDto` each
      map to the exact `core` value the DTO's fields describe) in
      `TEST/DtoMappingTest.kt`
- [X] T003 Implement `TargetingShapeDto`, `ActionEffectKindDto`, `DamageFormulaDto`
      and their `toCore()` functions (shared low-level DTOs spec 006's own DTOs
      depend on) in `MAIN/dto/LimitBreakSummonDto.kt`
- [X] T004 [P] Implement `StatusEffectKindDto`, `StatusEffectDefinitionDto`, and
      their `toCore()` functions in `MAIN/dto/StatusEffectDto.kt`
- [X] T005 [P] Implement `SynergyBonusDto`, `SynergyDefinitionDto`, and their
      `toCore()` functions in `MAIN/dto/SynergyDto.kt`
- [X] T006 Implement `LimitBreakDefinitionDto`, `SummonDefinitionDto`, and their
      `toCore()` functions (continues T003's file, depends on its low-level DTOs)
      in `MAIN/dto/LimitBreakSummonDto.kt`
- [X] T007 Implement `ContentFileDto` (per data-model.md's R2 shape — every field
      defaults to empty) in `MAIN/dto/ContentFileDto.kt`
- [X] T008 [P] Implement `ContentPack`, `ContentProblem`, `ContentLoadResult` data
      shapes (no logic yet) in `MAIN/ContentPack.kt`

**Checkpoint**: `:content:allTests` green on JVM+JS — foundation ready, stories can start.

---

## Phase 3: User Story 1 — Game content loads as one validated, complete package (Priority: P1)

**Goal**: `loadContentPack` parses text, maps every DTO to its `core` type, runs all
five existing `validate*Catalog` functions unconditionally, and accumulates every
problem found into one report — never a partial success (research R1/R2/R4).

**Independent Test** (from spec US1): author a complete, internally-valid set of
content covering every catalog kind, load it, and verify a single successful
package results containing all of it; separately author content with several
unrelated mistakes scattered across different catalog kinds, load it, and verify
every mistake is reported together.

### Tests for User Story 1 (write first, must fail) ⚠️

- [X] T009 [US1] Write failing tests for US1 scenarios 1-4 (a complete valid content
      set loads as one successful package containing every catalog's validated
      content; a problem in exactly one catalog kind is reported, identifying which
      catalog and which entry; unrelated problems in several different catalog kinds
      at once are all reported together in one pass, never only the first; an
      omitted optional catalog loads successfully as empty) plus the malformed-JSON
      edge case (a parse failure is reported as `MalformedContent`, distinct from any
      business-rule problem, and loading stops there since nothing else can be
      checked yet) via `loadContentPack` in `TEST/ContentLoaderTest.kt`

### Implementation for User Story 1

- [X] T010 [US1] Implement `loadContentPack` steps 1-3, 5 (decode `ContentFileDto`,
      catching `SerializationException` into `MalformedContent`; map every DTO list
      via `toCore()`; run `validateCatalog`/`validateStatusEffectCatalog`/
      `validateSynergyCatalog`/`validateLimitBreakCatalog`/`validateSummonCatalog`
      unconditionally, wrapping each error in its matching `ContentProblem` variant;
      return `Valid`/`Invalid` per data-model.md's algorithm — step 4's cross-catalog
      check is added by US2) in `MAIN/ContentLoader.kt`

**Checkpoint**: US1 green — the pipeline works; no cross-catalog checking exists yet.

---

## Phase 4: User Story 2 — A dangling cross-catalog reference is caught at load time (Priority: P1)

**Goal**: `validateCrossCatalogReferences` implements the four checks research R3
enumerated (class limit breaks → `LimitBreakCatalog`; synergy skills →
`Catalog.knownSkills`; status-effect stat modifiers → declared stats; limit
break/summon elements → `Catalog.elements`), wired into `loadContentPack`'s combined
report.

**Independent Test** (from spec US2): author content where a class's declared limit
break identifier has no corresponding definition anywhere in the loaded limit break
content, load it, and verify this is reported as a content problem identifying the
dangling reference, even though each individual catalog would pass its own isolated
validation unchanged.

### Tests for User Story 2 (write first, must fail) ⚠️

- [X] T011 [US2] Write failing tests for US2 scenarios 1-3 covering all four
      cross-catalog checks (a class's limit break id with no matching
      `LimitBreakDefinition` is reported as a dangling reference; a synergy skill id
      absent from `knownSkills` is reported; a status effect's stat-modifier
      `StatId` that's neither a core stat nor a declared custom stat is reported; a
      limit break/summon `ElementId` absent from `Catalog.elements` is reported; the
      same content with every reference actually resolving loads successfully;
      several different kinds of dangling references at once are all reported
      together, using the same accumulate-everything reporting as US1) via
      `validateCrossCatalogReferences` in `TEST/CrossCatalogValidationTest.kt`

### Implementation for User Story 2

- [X] T012 [US2] Implement `validateCrossCatalogReferences` (the four checks, per
      data-model.md) in `MAIN/CrossCatalogValidation.kt`
- [X] T013 [US2] Wire `validateCrossCatalogReferences` into `loadContentPack` as
      step 4, run unconditionally against the raw `Catalog` regardless of whether the
      core catalog's own validation (step 3) succeeded — cross-catalog problems are
      always reported even when the core catalog also independently failed in
      `MAIN/ContentLoader.kt`

**Checkpoint**: US1 + US2 green together — the feature's namesake payoff (nothing
dangling slips through) works end-to-end.

---

## Phase 5: User Story 3 — A small, complete demo content pack is ready to use (Priority: P2)

**Goal**: `DEMO_CONTENT_JSON` is a small, hand-authored, internally-consistent
content set — at least one class with a skill and a limit break, one enemy, one
status effect, one synergy, one summon — that loads successfully with zero problems.

**Independent Test** (from spec US3): load the shipped demo content pack with no
modifications and verify it succeeds, producing a package with at least one entry in
every catalog kind this engine supports.

### Tests for User Story 3 (write first, must fail) ⚠️

- [X] T014 [US3] Write failing tests for US3 scenarios 1-2 (`loadContentPack(DEMO_CONTENT_JSON)`
      succeeds with zero reported problems; the resulting package has at least one
      entry in every catalog kind — classes/characters/enemies, status effects,
      synergies, limit breaks, summons) in `TEST/DemoContentTest.kt`

### Implementation for User Story 3

- [X] T015 [US3] Author `DEMO_CONTENT_JSON` (per research R5, a plain Kotlin string
      constant) in `MAIN/demo/DemoContent.kt`

**Checkpoint**: All three user stories independently green.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T016 [P] Add a specs-001-006-contract-preservation test: every DTO's
      `toCore()` mapping produces a value structurally equal to constructing the
      real `core` type directly with the same field values (FR-009) in
      `TEST/ContentContractTest.kt`
- [X] T017 [P] Add a cross-cutting determinism property test: the same content text,
      loaded twice, produces structurally identical `ContentLoadResult`s, across a
      mix of valid and intentionally-broken generated content (FR-007, SC-005) in
      `TEST/ContentLoaderDeterminismTest.kt`
- [X] T018 KDoc pass on all public `content` package files (surface listed in
      contracts/content-loading-api.md)
- [X] T019 Run quickstart validation: `./gradlew :content:allTests` green on
      JVM+JS; update `specs/007-content-loading/quickstart.md` mapping table if any
      test file names drifted; update root `README.md` to mark spec 007 ✅
      implemented and the `content` module status line

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)** → nothing
- **Foundational (Phase 2)** → Setup; BLOCKS all stories
- **US1 (Phase 3)** → Foundational
- **US2 (Phase 4)** → US1 (needs `loadContentPack`'s steps 1-3 to exist as the
  substrate step 4 attaches to)
- **US3 (Phase 5)** → US1 + US2 (the demo content must actually pass both the
  per-catalog and cross-catalog checks to load successfully)
- **Polish (Phase 6)** → all stories

### Within Each Story

Tests first (all [P] within the story where they touch different files) → confirm
they FAIL → implementation → re-run until green. `MAIN/dto/LimitBreakSummonDto.kt`
is touched by T003 then T006 in sequence; `MAIN/ContentLoader.kt` is touched by T010
then T013 in sequence — do not parallelize these against each other.

### Parallel Opportunities

- T004 and T005 (foundational: status-effect / synergy DTOs) are parallel —
  different files, both independent of T003's low-level DTOs.
- T008 is parallel with T003-T007 (different file, no shared dependency until
  `loadContentPack` in Phase 3 needs all of them).
- T016-T017 (polish tests) are parallel.

---

## Implementation Strategy

**MVP = Phase 1 + 2 + 3 + 4 (US1 + US2)**: both are P1 — a "validated" package that
still lets a dangling cross-catalog reference through doesn't deliver what
"validated" promises (spec.md's own "Why this priority" for US2), so the smallest
genuinely useful slice is both together. Then US3 (the actual demo content) as the
final increment that makes the whole feature's payoff tangible. Stop and validate at
every checkpoint with `./gradlew :content:allTests`.
