# Contract: Content Loading Public API

**Feature**: 007-content-loading | **Consumers**: feature 008 (console demo)

Normative signatures for the `content` module (commonMain,
`io.github.kirthar.sddrpg.content` and `io.github.kirthar.sddrpg.content.dto`).
Consumes spec 001's `Catalog`/`ValidatedCatalog`/`validateCatalog`/`CatalogError`,
spec 004's `StatusEffectCatalog`/`validateStatusEffectCatalog`/
`StatusEffectCatalogError`, spec 005's `SynergyCatalog`/`validateSynergyCatalog`/
`SynergyCatalogError`, and spec 006's `LimitBreakCatalog`/`SummonCatalog`/
`validateLimitBreakCatalog`/`validateSummonCatalog`/`LimitBreakCatalogError`/
`SummonCatalogError` unchanged. See [data-model.md](../data-model.md) for field
tables and algorithm detail.

## Kotlin API

```kotlin
// dto/*.kt
@Serializable
enum class TargetingShapeDto { SINGLE_ALLY, SINGLE_ENEMY, SELF, ALL_ALLIES, ALL_ENEMIES, ALL }
fun TargetingShapeDto.toCore(): TargetingShape

@Serializable
enum class ActionEffectKindDto { DAMAGE, HEAL }
fun ActionEffectKindDto.toCore(): EffectKind

@Serializable
sealed interface DamageFormulaDto {
    @Serializable data class Physical(val power: Int) : DamageFormulaDto
    @Serializable data class Magical(val power: Int) : DamageFormulaDto
    @Serializable data class Fixed(val amount: Int) : DamageFormulaDto
}
fun DamageFormulaDto.toCore(): DamageFormula

@Serializable
sealed interface StatusEffectKindDto {
    @Serializable data class StatModifier(val statId: StatId, val delta: Int) : StatusEffectKindDto
    @Serializable data object Incapacitate : StatusEffectKindDto
    @Serializable data class DamageOverTime(val amountPerTick: Int) : StatusEffectKindDto
}
fun StatusEffectKindDto.toCore(): io.github.kirthar.sddrpg.core.status.EffectKind

@Serializable
data class StatusEffectDefinitionDto(val id: String, val displayName: String, val kind: StatusEffectKindDto, val duration: Int)
fun StatusEffectDefinitionDto.toCore(): StatusEffectDefinition

@Serializable
sealed interface SynergyBonusDto {
    @Serializable data class BonusDamage(val amount: Int) : SynergyBonusDto
}
@Serializable
data class SynergyDefinitionDto(val id: String, val firstSkillId: SkillId, val secondSkillId: SkillId, val window: Int, val bonus: SynergyBonusDto)
fun SynergyDefinitionDto.toCore(): SynergyDefinition

@Serializable
data class LimitBreakDefinitionDto(val id: LimitBreakId, val threshold: Int, val effectKind: ActionEffectKindDto, val formula: DamageFormulaDto, val targeting: TargetingShapeDto, val element: ElementId? = null)
fun LimitBreakDefinitionDto.toCore(): LimitBreakDefinition

@Serializable
data class SummonDefinitionDto(val id: String, val cost: Int, val effectKind: ActionEffectKindDto, val formula: DamageFormulaDto, val targeting: TargetingShapeDto, val element: ElementId? = null)
fun SummonDefinitionDto.toCore(): SummonDefinition

@Serializable
data class ContentFileDto(
    val catalog: Catalog = Catalog(),
    val statusEffects: List<StatusEffectDefinitionDto> = emptyList(),
    val synergies: List<SynergyDefinitionDto> = emptyList(),
    val limitBreaks: List<LimitBreakDefinitionDto> = emptyList(),
    val summons: List<SummonDefinitionDto> = emptyList(),
)

// ContentPack.kt
data class ContentPack(
    val catalog: ValidatedCatalog,
    val statusEffects: StatusEffectCatalog,
    val synergies: SynergyCatalog,
    val limitBreaks: LimitBreakCatalog,
    val summons: SummonCatalog,
)

sealed interface ContentProblem {
    data class MalformedContent(val message: String) : ContentProblem
    data class CoreCatalogProblem(val error: CatalogError) : ContentProblem
    data class StatusEffectProblem(val error: StatusEffectCatalogError) : ContentProblem
    data class SynergyProblem(val error: SynergyCatalogError) : ContentProblem
    data class LimitBreakProblem(val error: LimitBreakCatalogError) : ContentProblem
    data class SummonProblem(val error: SummonCatalogError) : ContentProblem
    data class DanglingReference(val fromCatalog: String, val field: String, val missingId: String) : ContentProblem
}

sealed interface ContentLoadResult {
    data class Valid(val pack: ContentPack) : ContentLoadResult
    data class Invalid(val problems: List<ContentProblem>) : ContentLoadResult
}

// CrossCatalogValidation.kt
fun validateCrossCatalogReferences(
    catalog: ValidatedCatalog,
    rawCatalog: Catalog,
    statusEffects: StatusEffectCatalog,
    synergies: SynergyCatalog,
    limitBreaks: LimitBreakCatalog,
): List<ContentProblem.DanglingReference>

// ContentLoader.kt
fun loadContentPack(text: String): ContentLoadResult

// demo/DemoContent.kt
val DEMO_CONTENT_JSON: String
```

Stability rules:

- `loadContentPack`, every `toCore()` mapping function, and
  `validateCrossCatalogReferences` are all pure: same inputs always produce
  structurally equal outputs (spec FR-007, SC-005).
- No spec 001-006 file is modified (spec FR-009) — every DTO and mapping function
  lives entirely in `content`'s own new files; `core`'s existing types are consumed
  through their already-public constructors only.
- `loadContentPack` never performs file, network, or console I/O — it operates
  purely on the `text` argument (spec.md's Assumptions).
- `ContentLoadResult.Invalid` always contains every problem found across every
  catalog kind and every cross-catalog check in a single pass (spec FR-003) — never
  just the first one encountered, except when parsing itself fails (research R4: a
  `MalformedContent` result is always a singleton list, since nothing else can be
  checked before parsing succeeds).
- An omitted or empty catalog kind in the input text loads successfully as an empty
  catalog (spec FR-006) — never a problem on its own.

## Typical usage (informative, not itself a contract)

```kotlin
when (val result = loadContentPack(DEMO_CONTENT_JSON)) {
    is ContentLoadResult.Valid -> {
        val pack = result.pack
        // pack.catalog, pack.statusEffects, pack.synergies, pack.limitBreaks, pack.summons
        // are ready for feature 008 to build a battle from.
    }
    is ContentLoadResult.Invalid -> {
        result.problems.forEach { println(it) } // each problem is structured data, not formatted text
    }
}
```
