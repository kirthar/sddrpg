# Quickstart: Validating Content Loading

**Feature**: 007-content-loading

## Prerequisites

JDK 21+, Node.js (JS test target). The `content` module already depends on `core`
and already has kotlinx.serialization + kotest wired (verified in plan.md's
Technical Context) — this feature adds its first real source files to that existing,
previously-empty module.

## Run the validation suite

```bash
./gradlew :content:allTests    # kotest on JVM + JS/node — must be green on both
./gradlew :content:jvmTest --tests '*ContentLoaderTest*'   # targeted run example
```

## Expected mapping: spec item → executable test

| Spec item | Validation |
|---|---|
| US1 scenario 1 (complete valid content loads as one package) | `content/ContentLoaderTest` |
| US1 scenario 2 (a single-catalog problem is reported, identifying catalog+entry) | `content/ContentLoaderTest` |
| US1 scenario 3 (multiple unrelated problems across catalog kinds all reported together) | `content/ContentLoaderTest` |
| US1 scenario 4 (an omitted optional catalog loads as empty) | `content/ContentFileDtoTest` |
| US2 scenarios 1-3 (dangling cross-catalog reference caught; correct reference passes; several dangling references all reported) | `content/CrossCatalogValidationTest` |
| US3 scenarios 1-2 (shipped demo content loads with zero problems, every catalog kind has ≥1 entry) | `content/DemoContentTest` |
| FR-004 (all four cross-catalog checks: limit breaks, synergy skills, stat modifiers, elements) | `content/CrossCatalogValidationTest` |
| FR-007/SC-005 (determinism) | `content/ContentLoaderDeterminismTest`: property test over varied valid/invalid content |
| FR-009 (specs 001-006 contracts untouched) | `content/ContentContractTest`: compilation itself is partial proof — no source changes to any `core` package; mapping every DTO field to its `core` type's constructor round-trips correctly |
| Edge cases (malformed JSON reported distinctly from validation problems; parse+cross-catalog problems together; repeated load is deterministic) | `content/ContentLoaderTest`, `content/ContentLoaderDeterminismTest` |

## Expected outcome

- `:content:allTests` green on JVM and JS with identical results.
- Test-first discipline (constitution Principle III) applies exactly as before: each
  acceptance scenario becomes a failing test before its implementation.

## Manual smoke (optional)

No console UI exists yet (`demo-console` remains a placeholder until feature 008). A
scratch test may call `loadContentPack(DEMO_CONTENT_JSON)` and print the resulting
`ContentPack`'s catalog sizes — useful for eyeballing the shipped demo content, not
required for completion.
