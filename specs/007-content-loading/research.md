# Research: Content Loading

**Feature**: 007-content-loading | **Date**: 2026-07-12

All Technical Context unknowns and spec Assumptions' deferred technical decisions
resolved here. R1 is the central architectural question spec.md deliberately left
open ("how specs 004-006's catalogs become loadable from external data").

## R1 — DTO/mapping layer in `content`, not retrofitted `@Serializable` on `core` files

**Decision**: Auditing every relevant type's `@Serializable` status directly against
source found: spec 001's `Catalog`/`ClassDefinition`/`CharacterDefinition`/
`ArchetypeDefinition`/`EnemyDefinition` are already `@Serializable` with working
round-trip test infrastructure (`DefinitionSerializationTest`, `EnemySerializationTest`,
`ContractRoundTripTest`). But `StatusEffectDefinition`/`StatusEffectCatalog` (spec
004), `SynergyDefinition`/`SynergyCatalog`/`SynergyBonus` (spec 005),
`LimitBreakDefinition`/`LimitBreakCatalog`/`SummonDefinition`/`SummonCatalog` (spec
006), and even `DamageFormula`/`TargetingShape`/action's `EffectKind` (spec 002,
referenced by the 004-006 types above) are **not** `@Serializable` at all.
Retrofitting `@Serializable` directly onto them would touch six existing files across
four different specs (002, 004, 005, 006) — a genuinely large blast radius, not a
one-line addition, since kotlinx.serialization's sealed-interface polymorphic support
requires every type in the reference graph to participate.

This feature instead defines its own serializable DTOs entirely inside `content`
(new files, new package) and maps them to the real `core` types via ordinary,
already-public constructor calls (`StatusEffectDefinition(...)`,
`SynergyDefinition(...)`, etc. — every field on every one of these types is already a
public `val` on a public data class, so no reflection or special access is needed).
Spec 001's `Catalog` is embedded directly inside the top-level DTO with no DTO of its
own, since it's already serializable — only the four specs 004-006 catalog kinds get
a DTO layer.

**Rationale**: Every prior spec (002 through 006) has held to "zero source changes to
any earlier spec's files" as an absolute rule, verified by direct compiler
experiments whenever genuinely in doubt (spec 005's classId check, spec 006's
sealed-package check). A DTO layer keeps that invariant airtight here too, at the
cost of a small, mechanical amount of duplicate shape + mapping code that lives
entirely in this feature's own files — the same trade this project has made
consistently every time "touch an existing file a little" was weighed against "add a
small amount of duplication in the new feature instead" (spec 004's R4, spec 005's
R4/R6, spec 006's R3).

**Alternatives considered**: adding `@Serializable` directly to the six existing
files (rejected — real, multi-spec blast radius, not a cosmetic one-liner; breaks the
"zero diff to prior spec files" record every spec since 002 has held); a reflection-
or code-generation-based mapper instead of hand-written mapping functions (rejected —
unnecessary complexity for a handful of small, stable shapes; hand-written mapping is
also easier to keep honest about accumulating *all* problems, R4).

## R2 — `ContentFileDto`: one top-level shape, `Catalog` embedded directly

**Decision**: A single top-level DTO aggregates everything a content file provides:

```kotlin
@Serializable
data class ContentFileDto(
    val catalog: Catalog = Catalog(),                                  // spec 001's own type, unchanged
    val statusEffects: List<StatusEffectDefinitionDto> = emptyList(),  // spec 004
    val synergies: List<SynergyDefinitionDto> = emptyList(),           // spec 005
    val limitBreaks: List<LimitBreakDefinitionDto> = emptyList(),      // spec 006
    val summons: List<SummonDefinitionDto> = emptyList(),              // spec 006
)
```

Every field defaults to empty, directly satisfying FR-006/US1 scenario 4 ("an omitted
catalog loads successfully as empty") for free — decoding a JSON object missing any
of these keys just uses the default.

**Rationale**: One shape, one `Json.decodeFromString` call, matching spec.md's US1
("loads it as a single validated package in one call"). Reusing `Catalog` verbatim
(rather than wrapping it in its own DTO) avoids pointless duplication for the one
subtree that's already directly serializable.

**Alternatives considered**: five separate top-level decode calls, one per catalog
kind (would require the caller to assemble and pass five separate strings/sections
instead of one file — contradicts US1's "one call" framing, and loses the single
parse-error-surface FR-003 wants); nesting `Catalog`'s own fields flat into
`ContentFileDto` instead of embedding it as a sub-object (would duplicate spec 001's
already-stable shape for no benefit and drift if `Catalog` ever gains a field).

## R3 — Four cross-catalog reference checks, each already implied by an existing spec's own design

**Decision**: `validateCrossCatalogReferences(catalog: Catalog, statusEffects:
StatusEffectCatalog, synergies: SynergyCatalog, limitBreaks: LimitBreakCatalog,
summons: SummonCatalog): List<ContentProblem>` takes the *raw* `Catalog` (not `ValidatedCatalog` — every field
these checks read, `knownLimitBreaks`/`knownSkills`/`customStats`/`elements`, already
lives directly on `Catalog`; the validated wrapper's lookup methods aren't needed
here), which also means these checks can run unconditionally regardless of whether
the core catalog's own validation succeeded. Checks exactly these four
relationships, no more:

1. **Class limit breaks → `LimitBreakCatalog`**: every `LimitBreakId` in
   `Catalog.knownLimitBreaks` (spec 001) must have a matching `LimitBreakDefinition`
   in the loaded `LimitBreakCatalog` (spec 006). This is the flagship example spec.md
   itself calls out, and the one spec 001's own `Catalog` doc comment already
   anticipated verbatim: *"`known*` sets declare identifiers whose full definitions
   belong to later features... so reference validation is total today and
   referencing content never changes when those features land."* Feature 006 was
   that "later feature" for limit breaks; this is the day that comment predicted.
2. **Synergy skills → `Catalog.knownSkills`**: every `SkillId` a `SynergyDefinition`
   (spec 005) requires must exist in `Catalog.knownSkills` (spec 001) — spec 005
   deliberately never checked this itself (its `SynergyCatalog` is independent of
   spec 001's `Catalog` by design, research R4 there), so a synergy requiring a skill
   nobody could ever have would otherwise load silently and simply never trigger.
3. **Status effect stat modifiers → declared stats**: every `StatId` a
   `StatusEffectDefinition`'s `StatModifier`-kind effect (spec 004) references must
   be either a `CoreStats` id or declared in `Catalog.customStats` (spec 001) — spec
   004's own catalog validation only checks duration/uniqueness, never whether the
   stat it modifies actually exists (research R4 there, by design).
4. **Limit break / summon elements → `Catalog.elements`**: any non-null `ElementId`
   referenced by a `LimitBreakDefinition`/`SummonDefinition` (spec 006) must be
   declared in `Catalog.elements` (spec 001).

No other cross-catalog relationship is checked (spec.md's Assumptions: "does not
invent new cross-catalog relationships beyond what those specs already implied").

**Rationale**: Each of these four checks corresponds to an identifier one spec's
catalog *declares as meaningful* that another spec's catalog is the sole authority
on — exactly the shape of gap only a feature that loads every catalog together can
close, matching spec.md's US2 rationale precisely. Bounding the list to these four
(rather than exhaustively cross-checking every identifier everywhere) keeps this
feature's scope closed and honest about what it actually promises, per constitution
Principle V.

**Alternatives considered**: validating every possible identifier pairing generically
via reflection/naming convention (rejected — fragile, implicit, and would silently
start "checking" new things every time a future spec adds a field, an undocumented
behavior change with no spec backing it); skipping cross-catalog validation entirely
and treating it as feature 007's own future work (rejected outright — this is US2,
one of this spec's two P1 stories).

## R4 — Accumulate-everything reporting: parse errors are `ContentProblem`s too

**Decision**: `ContentLoadResult` is `Valid(ContentPack)` XOR `Invalid(problems:
List<ContentProblem>)`, mirroring every prior catalog's `*CatalogResult` shape. A
malformed-JSON parse failure produces a single `ContentProblem.MalformedContent`
entry (from catching kotlinx.serialization's `SerializationException`) and loading
stops there — parsing must succeed before any business-rule validation can even run,
so this is the one case where "accumulate everything" naturally reduces to "one
problem" (there is nothing else to accumulate yet). Once parsing succeeds,
`loadContentPack` runs *every* one of the five `validate*Catalog` functions (specs
001/004/005/006) plus R3's four cross-catalog checks unconditionally — never
short-circuiting after the first failure — and concatenates every error list found
(mapping each spec's own `*CatalogError` type into a `ContentProblem` that names
which catalog kind it came from) into one final `Invalid(problems)`.

**Rationale**: Directly implements FR-003/US1 scenario 3 and spec.md's edge case
("a dangling cross-catalog reference and an ordinary single-catalog validation
problem occurring together are both reported in the same pass"). Distinguishing
parsing from validation (rather than trying to "accumulate" a parse failure against
business-rule problems that can't even be evaluated yet, since there's no decoded
data to check) matches the edge case's own wording: "reported as a clear parsing
problem, not a crash and not confused with a business-rule validation problem."

**Alternatives considered**: treating a parse failure as `ContentLoadResult.Invalid`
with zero problems and a separate exception path (rejected — inconsistent API, forces
callers to catch exceptions instead of just inspecting one result type, unlike every
other `*CatalogResult` this project has built); stopping at the first `validate*Catalog`
failure instead of running all five (rejected outright by FR-003).

## R5 — Demo content ships as a Kotlin string constant, not a bundled resource file

**Decision**: `DEMO_CONTENT_JSON: String` is a plain `val` holding a triple-quoted
JSON literal in `content/demo/DemoContent.kt`, not a `.json` resource file loaded via
Kotlin Multiplatform's resource APIs.

**Rationale**: This feature's own scope is `String -> ContentLoadResult` (spec.md's
Assumptions: file-system access is feature 008's concern) — a string constant is
already valid input to `loadContentPack` without requiring this feature to set up
multiplatform resource-loading infrastructure it doesn't otherwise need. Feature 008
remains free to instead load an actual bundled file/resource at that point and simply
pass its contents as a `String` — `loadContentPack`'s contract doesn't change either
way.

**Alternatives considered**: a `.json` resource file loaded via
`Res.readBytes()`-style Compose/KMP resource APIs (deferred — adds a real
multiplatform build-configuration dependency this feature doesn't need yet, and
would need to be built and tested per-target; the `content` module doesn't currently
have any resource-loading setup at all).
