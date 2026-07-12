package io.github.kirthar.sddrpg.content.dto

import io.github.kirthar.sddrpg.core.action.DamageFormula
import io.github.kirthar.sddrpg.core.action.EffectKind
import io.github.kirthar.sddrpg.core.action.TargetingShape
import io.github.kirthar.sddrpg.core.limitbreak.LimitBreakDefinition
import io.github.kirthar.sddrpg.core.limitbreak.SummonDefinition
import io.github.kirthar.sddrpg.core.limitbreak.SummonId
import io.github.kirthar.sddrpg.core.model.ElementId
import io.github.kirthar.sddrpg.core.model.LimitBreakId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Serializable mirror of spec 002's [TargetingShape] (research R1) -- `core`'s own
 * enum is never annotated, avoiding any change to that existing file.
 */
@Serializable
enum class TargetingShapeDto { SINGLE_ALLY, SINGLE_ENEMY, SELF, ALL_ALLIES, ALL_ENEMIES, ALL }

fun TargetingShapeDto.toCore(): TargetingShape = TargetingShape.valueOf(name)

/** Serializable mirror of spec 002's `action` [EffectKind] enum (DAMAGE/HEAL). */
@Serializable
enum class ActionEffectKindDto { DAMAGE, HEAL }

fun ActionEffectKindDto.toCore(): EffectKind = EffectKind.valueOf(name)

/** Serializable mirror of spec 002's [DamageFormula] sealed interface. */
@Serializable
sealed interface DamageFormulaDto {
    @Serializable
    @SerialName("Physical")
    data class Physical(val power: Int) : DamageFormulaDto

    @Serializable
    @SerialName("Magical")
    data class Magical(val power: Int) : DamageFormulaDto

    @Serializable
    @SerialName("Fixed")
    data class Fixed(val amount: Int) : DamageFormulaDto
}

fun DamageFormulaDto.toCore(): DamageFormula = when (this) {
    is DamageFormulaDto.Physical -> DamageFormula.Physical(power)
    is DamageFormulaDto.Magical -> DamageFormula.Magical(power)
    is DamageFormulaDto.Fixed -> DamageFormula.Fixed(amount)
}

/** Serializable mirror of spec 006's [LimitBreakDefinition]. */
@Serializable
data class LimitBreakDefinitionDto(
    val id: LimitBreakId,
    val threshold: Int,
    val effectKind: ActionEffectKindDto,
    val formula: DamageFormulaDto,
    val targeting: TargetingShapeDto,
    val element: ElementId? = null,
)

fun LimitBreakDefinitionDto.toCore(): LimitBreakDefinition =
    LimitBreakDefinition(id, threshold, effectKind.toCore(), formula.toCore(), targeting.toCore(), element)

/** Serializable mirror of spec 006's [SummonDefinition]. */
@Serializable
data class SummonDefinitionDto(
    val id: String,
    val cost: Int,
    val effectKind: ActionEffectKindDto,
    val formula: DamageFormulaDto,
    val targeting: TargetingShapeDto,
    val element: ElementId? = null,
)

fun SummonDefinitionDto.toCore(): SummonDefinition =
    SummonDefinition(SummonId(id), cost, effectKind.toCore(), formula.toCore(), targeting.toCore(), element)
