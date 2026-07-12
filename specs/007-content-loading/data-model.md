# Data Model: Content Loading

**Feature**: 007-content-loading | **Date**: 2026-07-12
**Sources**: spec.md (Key Entities, FRs), research.md (R1-R5)

Extends the `content` module additively. Consumes `Catalog`/`ValidatedCatalog`/
`validateCatalog` (spec 001), `StatusEffectCatalog`/`validateStatusEffectCatalog`
(spec 004), `SynergyCatalog`/`validateSynergyCatalog` (spec 005), and
`LimitBreakCatalog`/`SummonCatalog`/`validateLimitBreakCatalog`/`validateSummonCatalog`
(spec 006) exactly as published; nothing in `core` changes (research R1).

## DTO layer (new, `content/dto` — research R1/R2)

Ids that are already `@Serializable` in spec 001's `model` package (`SkillId`,
`ElementId`, `StatId`, `LimitBreakId`) are used directly in DTOs, no re-wrapping.
Ids owned by specs 004-006 themselves (`StatusEffectId`, `SynergyId`, `SummonId`) are
not `@Serializable`, so DTOs carry their raw `String` and map through each id's
single-`String` constructor.

### TargetingShapeDto / ActionEffectKindDto / DamageFormulaDto
Mirror spec 002's `TargetingShape` (enum), `EffectKind` (enum: DAMAGE/HEAL), and
`DamageFormula` (sealed: `Physical(power)`/`Magical(power)`/`Fixed(amount)`) exactly,
field-for-field. `toCore()` mapping functions convert 1:1.

### StatusEffectKindDto / StatusEffectDefinitionDto
Mirrors spec 004's `EffectKind` (sealed: `StatModifier(statId, delta)`/
`Incapacitate`/`DamageOverTime(amountPerTick)`) and `StatusEffectDefinition(id,
displayName, kind, duration)`.

### SynergyBonusDto / SynergyDefinitionDto
Mirrors spec 005's `SynergyBonus` (sealed: `BonusDamage(amount)`) and
`SynergyDefinition(id, firstSkillId, secondSkillId, window, bonus)`.

### LimitBreakDefinitionDto / SummonDefinitionDto
Mirror spec 006's `LimitBreakDefinition(id, threshold, effectKind, formula,
targeting, element)` and `SummonDefinition(id, cost, effectKind, formula, targeting,
element)`.

### ContentFileDto (research R2)
```kotlin
data class ContentFileDto(
    val catalog: Catalog = Catalog(),                                  // spec 001's own type, embedded directly
    val statusEffects: List<StatusEffectDefinitionDto> = emptyList(),
    val synergies: List<SynergyDefinitionDto> = emptyList(),
    val limitBreaks: List<LimitBreakDefinitionDto> = emptyList(),
    val summons: List<SummonDefinitionDto> = emptyList(),
)
```
Every field defaults to empty — an omitted catalog kind decodes successfully as
empty (FR-006/US1 scenario 4).

## Content package layer (new, `content` root package)

### ContentPack
| Field | Type | Rules |
|---|---|---|
| catalog | ValidatedCatalog (spec 001) | |
| statusEffects | StatusEffectCatalog (spec 004) | |
| synergies | SynergyCatalog (spec 005) | |
| limitBreaks | LimitBreakCatalog (spec 006) | |
| summons | SummonCatalog (spec 006) | |

Immutable; the sole product of a successful load (FR-005).

### ContentProblem (research R4)
Sealed, one variant per problem source — each wraps the real, already-typed error
rather than stringifying it, preserving structure (consistent with this project's
preference for typed over freeform errors):

| Variant | Fields |
|---|---|
| `MalformedContent` | `message: String` (from a caught `SerializationException`) |
| `CoreCatalogProblem` | `error: CatalogError` (spec 001) |
| `StatusEffectProblem` | `error: StatusEffectCatalogError` (spec 004) |
| `SynergyProblem` | `error: SynergyCatalogError` (spec 005) |
| `LimitBreakProblem` | `error: LimitBreakCatalogError` (spec 006) |
| `SummonProblem` | `error: SummonCatalogError` (spec 006) |
| `DanglingReference` | `fromCatalog: String`, `field: String`, `missingId: String` (research R3's four checks) |

### ContentLoadResult
`Valid(pack: ContentPack)` XOR `Invalid(problems: List<ContentProblem>)` —
accumulate-all, mirroring every `*CatalogResult` this project has built (spec
001/004/005/006).

## Operations

### loadContentPack (R1, R2, R4)
`(text: String) -> ContentLoadResult`

1. Decode `text` as `ContentFileDto` via kotlinx.serialization. A
   `SerializationException` is caught and mapped to
   `Invalid(listOf(ContentProblem.MalformedContent(message)))` — parsing must
   succeed before anything else can be checked (research R4).
2. Map every DTO list to its real `core` type via each DTO's `toCore()` function,
   and assemble `Catalog` (used as-is from the DTO), `StatusEffectCatalog`,
   `SynergyCatalog`, `LimitBreakCatalog`, `SummonCatalog`.
3. Run `validateCatalog`, `validateStatusEffectCatalog`, `validateSynergyCatalog`,
   `validateLimitBreakCatalog`, `validateSummonCatalog` — all five, unconditionally,
   never short-circuiting — and collect every error, wrapped in its matching
   `ContentProblem` variant.
4. Always (regardless of whether step 3's core catalog validation succeeded — all
   four checks below only read fields already present on the raw `Catalog` itself,
   never needing a `ValidatedCatalog`'s lookup methods) run
   `validateCrossCatalogReferences` (R3's four checks) against the raw `Catalog` and
   the three other catalogs' raw definitions, collecting `DanglingReference`
   problems. This means cross-catalog problems are always reported even when the
   core catalog also independently failed its own validation — the strongest
   reading of FR-003's "never stop at the first problem."
5. If the combined problem list from steps 3-4 is empty, return
   `Valid(ContentPack(catalog = the ValidatedCatalog produced by step 3, ...))`.
   Otherwise return `Invalid(problems)`.

### validateCrossCatalogReferences (R3)
`(catalog: Catalog, statusEffects: StatusEffectCatalog, synergies: SynergyCatalog, limitBreaks: LimitBreakCatalog, summons: SummonCatalog) -> List<ContentProblem.DanglingReference>`

Takes the *raw* `Catalog` (not `ValidatedCatalog` — `knownLimitBreaks`/`knownSkills`/
`customStats`/`elements` are all plain fields on `Catalog` itself; `ValidatedCatalog`
only adds lookup methods this function doesn't need). Checks exactly:
1. Every `LimitBreakId` in `catalog.knownLimitBreaks` has a matching
   `LimitBreakDefinition.id` in `limitBreaks.limitBreaks`.
2. Every `SkillId` referenced by any `SynergyDefinition.firstSkillId`/`secondSkillId`
   is in `catalog.knownSkills`.
3. Every `StatId` referenced by any `StatusEffectDefinition`'s `StatModifier`-kind
   effect is either a `CoreStats` id or in `catalog.customStats`.
4. Every non-null `ElementId` referenced by any `LimitBreakDefinition`/
   `SummonDefinition` is in `catalog.elements`.

## Determinism guarantee (spec FR-007/SC-005)

Every operation above is a pure function of its `text` input — no RNG, no wall-clock,
no file/network I/O. Given the same content text, `loadContentPack` always produces
byte-for-byte identical results, on both JVM and JS.
