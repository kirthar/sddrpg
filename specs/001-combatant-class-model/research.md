# Research: Combatant & Class Model

**Feature**: 001-combatant-class-model | **Date**: 2026-07-11

All Technical Context unknowns resolved. Numbered for reference from plan/tasks.

## R1 — Fixed classes vs job-system progression

**Decision**: Model the character→class relationship as a *mutable reference to exactly
one active class*, stored on the character's battle-facing state (not baked into the
character definition's identity). v1 gameplay never changes it (fixed classes), and no
job-system mechanics (per-class mastery, ability inheritance, class levels) are
implemented.

**Rationale**: The reference-based model is the smallest design where FF-style job
change ("swap active job, capabilities swap with it") and FE-style promotion ("replace
class with an upgraded one") are both pure data operations on one field — no core
rewrite, satisfying constitution Principle V. Everything class-granted (skills,
commands, growth curve, equipment categories, limit breaks) is *derived through the
reference at read time*, never copied onto the character; that is what makes the swap
safe and is directly testable (spec US1 scenario 4, SC-005).

**Alternatives considered**:
- *Class data embedded/copied into each character*: simplest to read, but class change
  would require re-copying and risks stale grants; duplicates catalog data. Rejected.
- *Full job system now (per-class state: mastered abilities, class levels)*: requires a
  character↔class state matrix and rules that v1 never exercises — speculative
  implementation, banned by Principle V. Rejected for v1; the mutable-reference model
  plus a future `perClassState` map extension point covers it later without migration.

## R2 — Content identity & references

**Decision**: Every definition is identified by a string ID wrapped in a Kotlin value
class (`ClassId`, `ArchetypeId`, `SkillId`, `LimitBreakId`, `AiProfileId`, `ElementId`,
`StatId`, `EquipmentCategoryId`, `CharacterId`, `EnemyId`). Cross-references between
definitions are always by ID, never by nesting the referenced object.

**Rationale**: Flat, ID-referenced data is the serialization-friendly shape that keeps
content-as-data (Principle II): catalogs stay human-editable JSON, references are
validatable, and value classes give compile-time type safety with zero runtime cost on
both JVM and JS.

**Alternatives considered**: raw `String` keys (no type safety, easy to cross-wire a
SkillId into a ClassId slot — rejected); nested object graphs in JSON (duplication,
no shared definitions, unvalidatable identity — rejected).

## R3 — Hybrid stat catalog representation

**Decision**: `StatId` value class; the engine ships eight predefined core `StatId`
constants (HP, MP, ATTACK, DEFENSE, MAGIC, RESISTANCE, SPEED, LUCK). A `StatBlock` is
a map `StatId → Int` that is guaranteed (constructor/validation invariant) to contain
every core stat; catalogs may declare additional custom `StatId`s in a
`customStats` section, after which content may reference them like core stats.

**Rationale**: Implements the Q1 clarification directly: formulas and the future
TurnScheduler can rely on core stats always existing (Speed for ATB), while games
extend stats without engine changes. A map with a completeness invariant is simpler
than a fixed data class + escape hatch, and property-testable.

**Alternatives considered**: fixed data class with eight fields (custom stats need a
separate mechanism, two access paths — rejected); fully open map with no guaranteed
keys (every formula must handle absence — rejected per Q1 decision).

## R4 — Integer stat arithmetic

**Decision**: All stat values, levels, and curve outputs are `Int`. Bounds: values ≥ 0;
current resource values (HP/MP) ≤ their maxima (current-vs-max tracking becomes
battle-state in later features; this feature defines base/derived stats only).

**Rationale**: Identical arithmetic on JVM and JS with no floating-point rounding
divergence — cheap insurance for constitution Principle I (deterministic across
platforms), and genre-standard for FF-style stats.

**Alternatives considered**: `Double` (IEEE-754 is deterministic in practice, but
rounding-sensitive formulas invite cross-platform and refactoring hazards — rejected);
fixed-point custom type (unneeded complexity now; can be introduced inside formulas
later without touching this model — rejected).

## R5 — Growth curve representation

**Decision**: A `GrowthCurve` is serializable data with a closed set of curve kinds
interpreted by the engine (Strategy selected by data, sealed for serialization):
- `Linear(base, perLevel)` → `base + perLevel * (level - 1)`
- `Table(values)` → explicit per-level values (clamping to last entry beyond table end)
A class defines one curve per stat it grows (`Map<StatId, GrowthCurve>`); stats without
a curve stay at the character's base value. Derivation is a pure function
`statsAt(class, level, baseStats): StatBlock`.

**Rationale**: Covers the Q2 clarification (level-derived deterministic stats) with the
two shapes real content needs first; the sealed discriminator (`kotlinx.serialization`
class discriminator) is a *capability* set, so adding a curve kind later is an engine
extension, not a content rewrite. Table gives designers exact control (FE-style),
Linear keeps demo content terse.

**Alternatives considered**: formula strings evaluated at runtime (needs an expression
interpreter — out of scope, nondeterminism risk, rejected for now); polynomial
coefficients (opaque to designers, premature — rejected); only tables (verbose for 99
levels — rejected as sole option).

## R6 — Catalog validation strategy

**Decision**: Two-phase load. Phase A: deserialize JSON into raw definition lists.
Phase B: validate the whole catalog, **accumulating** all errors (duplicate IDs across
each namespace, dangling references character→class, class→skill/limitBreak/
equipmentCategory/statId, enemy→archetype, archetype→aiProfile, affinity→element)
and returning either a validated immutable `Catalog` or the complete error list —
never a partially valid catalog, never fail-fast on the first error.

**Rationale**: Spec FR-013/SC-002 require 100% of reference errors reported with the
offending definition named; accumulation is what makes a catalog editable by a content
designer without engine knowledge (fix everything in one pass). An immutable validated
type means downstream code never re-checks references.

**Alternatives considered**: fail-fast exceptions (designer whack-a-mole — rejected);
validation at use time (moves errors into battle, violates FR-013 — rejected);
`kotlinx.serialization` custom serializers doing validation inline (couples schema to
graph rules, harder to accumulate — rejected).

## R7 — Battle instance identity & decision source

**Decision**: Definitions are templates; a `Roster` builder instantiates `Combatant`s.
Instance IDs (`CombatantId`) are assigned deterministically from roster insertion order
(no UUIDs, no randomness). Each roster entry carries: definition reference, kind
(PARTY_MEMBER / ENEMY / TEMPORARY_ALLY), allegiance (PLAYER / OPPONENT), level (for
characters), and a `DecisionSource` (`Human` or `AiProfile(id)`) — reconfigurable
without touching any other property (spec FR-003, SC-004).

**Rationale**: Deterministic instance identity keeps battles replayable (Principle I)
and lets "three Goblins" coexist (spec assumption). Kind and allegiance are separate
axes because temporary allies are player-side but not party members; decision source is
a third independent axis per Q&A in spec (any kind × either source).

**Alternatives considered**: UUID instance ids (nondeterministic — rejected); deriving
allegiance from kind (blocks future charm/betrayal mechanics and conflates axes —
rejected); putting decision source on the definition (a definition would hardcode
control origin; spec requires it to be battle configuration — rejected).

## R8 — kotlinx.serialization schema conventions

**Decision**: All definitions are `@Serializable` data classes with default values for
optional sections (affinities default neutral, custom stats default empty). JSON is the
canonical interchange format; `Json { ignoreUnknownKeys = false }` in tests (strict) to
catch typos. Sealed hierarchies (only `GrowthCurve`) use the default `type`
discriminator.

**Rationale**: Strict parsing surfaces content errors early (aligned with FR-013's
load-time philosophy); defaults keep hand-written JSON terse; a single canonical format
satisfies FR-014 portability without inventing a schema language.

**Alternatives considered**: protobuf/CBOR now (opaque to designers — deferred; format
is swappable later since serialization is by annotation, not code); JSON Schema files
as source of truth (duplicates the Kotlin types — rejected).
