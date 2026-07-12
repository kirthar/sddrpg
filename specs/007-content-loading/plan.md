# Implementation Plan: Content Loading

**Branch**: `007-content-loading` (feature dir; git work happens on the session's
designated branch) | **Date**: 2026-07-12 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/007-content-loading/spec.md`

## Summary

Build the `content` module's first real logic: a pure `loadContentPack(text: String):
ContentLoadResult` over the `content` module (which already depends on `core` and
already has kotlinx.serialization on its classpath, per plan-phase verification).
Rather than retrofitting `@Serializable` onto specs 002/004/005/006's existing
`DamageFormula`/`TargetingShape`/`EffectKind`(status)/`SynergyBonus`/
`LimitBreakDefinition`/`SummonDefinition` files (a 6-file, 4-spec blast radius,
verified by direct inspection), this feature defines its own small serializable DTOs
in `content`'s own files and maps them to the real `core` types via plain
constructor calls — zero changes to any spec 001-006 file, the same discipline every
prior spec has held to. Spec 001's `Catalog` is reused directly inside the DTO
(already `@Serializable`, no DTO needed for that subtree). Loading validates every
sub-catalog via its own existing `validate*Catalog` function, then runs four
additional cross-catalog reference checks that no single spec's own validation can
see — most notably the one spec 001's own `Catalog` doc comment already anticipated:
"`known*` sets declare identifiers whose full definitions belong to later features...
so reference validation is total today" — accumulating every problem (parse, single-
catalog, and cross-catalog) into one report.

## Technical Context

**Language/Version**: Kotlin 2.4.0 (Multiplatform; jvm + js targets, same as `core`/`content`)

**Primary Dependencies**: kotlinx.serialization 1.11.0 (already a `content` dependency)

**Storage**: N/A — pure text-in, package-or-problems-out; no file I/O in this
feature (deferred to feature 008, per spec.md's Assumptions)

**Testing**: kotest 6.2.2 in `commonTest` (unit + property tests, JVM + JS) — `content`
module already has kotest wired per its `build.gradle.kts`

**Target Platform**: platform-agnostic common code, same as `core`

**Project Type**: library module — first real logic in the existing `content` module

**Performance Goals**: loading is O(total authored entries) — trivial at the demo's
scale (a handful of classes/enemies/effects), no measured target needed

**Constraints**: 100% deterministic (constitution Principle I, extended to this
feature's own pure logic per spec.md's Assumptions): no RNG, no file/network I/O in
`loadContentPack` itself; must not modify `Catalog`, `ClassDefinition`,
`StatusEffectCatalog`, `SynergyCatalog`, `LimitBreakCatalog`, `SummonCatalog`,
`DamageFormula`, `TargetingShape`, `EffectKind` (either), `SynergyBonus`, or any other
spec 001-006 file (spec FR-009) — verified: retrofitting serialization onto them
would touch 6 files across specs 002/004/005/006, so this feature's own DTO layer is
used instead

**Scale/Scope**: one small demo content package (a handful of entries per catalog
kind); this feature defines the loading/validation mechanism and ships that one demo
package — no authoring tooling, no hot-reload, no multiple content packs

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Gate | Status |
|---|---|---|
| I. Pure & Deterministic Core | No RNG; `loadContentPack` is a pure `String -> ContentLoadResult` function, no I/O | ✅ PASS — see research R1 |
| II. Data-Driven Content | Game content is authored as external serialized data, not code; DTOs are a mapping layer, not new engine behavior | ✅ PASS |
| III. Test-First | Acceptance scenarios (spec US1-US3) map to failing kotest tests before implementation | ✅ PASS — test plan in quickstart.md |
| IV. Simulation-Presentation Separation | No rendering/console I/O built here; that's feature 008's job | ✅ PASS |
| V. Extensible Architecture w/o Speculative Impl. | Exactly the cross-catalog checks specs 001-006's own designs already imply (research R3) — no speculative new cross-catalog relationships invented | ✅ PASS |

**Post-design re-check (after Phase 1)**: all five gates still pass. The DTO/mapping
layer and the new `content` package are entirely additive; zero changes to any spec
001-006 file — verified directly by auditing every relevant type's `@Serializable`
status against actual source (research R1) before choosing the DTO approach. No
Complexity Tracking entries needed.

## Project Structure

### Documentation (this feature)

```text
specs/007-content-loading/
├── plan.md              # This file (/speckit-plan output)
├── research.md          # Phase 0 output (/speckit-plan)
├── data-model.md         # Phase 1 output (/speckit-plan)
├── quickstart.md        # Phase 1 output (/speckit-plan)
├── contracts/
│   └── content-loading-api.md   # Phase 1 output (/speckit-plan)
├── checklists/
│   └── requirements.md  # Spec quality checklist (from /speckit-specify)
└── tasks.md             # Phase 2 output (/speckit-tasks — NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
content/
├── src/commonMain/kotlin/io/github/kirthar/sddrpg/content/
│   ├── Content.kt                  # existing placeholder (unchanged)
│   ├── dto/                        # NEW package: serializable DTOs + mapping
│   │   ├── StatusEffectDto.kt      # StatusEffectKindDto, StatusEffectDefinitionDto
│   │   ├── SynergyDto.kt           # SynergyBonusDto, SynergyDefinitionDto
│   │   ├── LimitBreakSummonDto.kt  # DamageFormulaDto, EffectKindDto (action's),
│   │   │                           # TargetingShapeDto, LimitBreakDefinitionDto,
│   │   │                           # SummonDefinitionDto
│   │   └── ContentFileDto.kt       # top-level: catalog (spec 001's, reused as-is) +
│   │                               # the four DTO lists above
│   ├── ContentPack.kt              # ContentPack, ContentProblem, ContentLoadResult
│   ├── CrossCatalogValidation.kt   # the four cross-catalog reference checks
│   ├── ContentLoader.kt            # loadContentPack(text): ContentLoadResult
│   └── demo/
│       └── DemoContent.kt          # DEMO_CONTENT_JSON: String constant (avoids
│                                    # multiplatform resource-loading machinery,
│                                    # out of scope per spec.md's Assumptions)
└── src/commonTest/kotlin/io/github/kirthar/sddrpg/content/
    └── ...                          # unit + property tests per US1-US3
```

**Structure Decision**: new packages inside the existing `content` module (already a
Gradle module depending on `core`, per plan-phase verification — no new module
needed). The demo content ships as a plain Kotlin string constant, not a bundled
resource file — this feature's own scope is text-in/package-out (spec.md's
Assumptions explicitly defer file I/O to feature 008), and a string constant is
already valid "external data" from `loadContentPack`'s point of view (it never
touches the file system either way) without needing multiplatform resource-loading
infrastructure this feature doesn't otherwise require.

## Complexity Tracking

No constitution violations — table intentionally empty.
