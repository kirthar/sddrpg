package io.github.kirthar.sddrpg.content.demo

import io.github.kirthar.sddrpg.core.action.DamageFormula
import io.github.kirthar.sddrpg.core.action.EffectKind
import io.github.kirthar.sddrpg.core.action.TargetingShape
import io.github.kirthar.sddrpg.core.catalog.CommandKind
import io.github.kirthar.sddrpg.core.model.ElementId
import io.github.kirthar.sddrpg.core.model.SkillId
import io.github.kirthar.sddrpg.core.status.StatusEffectId

/**
 * What one of the shipped demo content's submitted commands actually does in combat
 * (spec 008 FR-006) -- reuses spec 002's own [DamageFormula]/[TargetingShape]/
 * [EffectKind] directly; not a new generic engine-wide catalog type. [appliesStatusEffect]
 * is what makes spec 004's status effects (specifically the shipped "poison") actually
 * reachable in play (spec 008 US3) -- nothing else in this codebase ever calls
 * `applyStatusEffect`.
 */
data class DemoActionDefinition(
    val command: CommandKind,
    val skillId: SkillId?,
    val effectKind: EffectKind,
    val formula: DamageFormula,
    val targeting: TargetingShape,
    val element: ElementId? = null,
    val appliesStatusEffect: StatusEffectId? = null,
)

/** Basic ATTACK, cleave (cloud's SKILL), and fira (aerith's MAGIC) -- see DemoContent.kt. */
val DEMO_ACTIONS: List<DemoActionDefinition> = listOf(
    DemoActionDefinition(
        command = CommandKind.ATTACK,
        skillId = null,
        effectKind = EffectKind.DAMAGE,
        formula = DamageFormula.Physical(power = 10),
        targeting = TargetingShape.SINGLE_ENEMY,
    ),
    DemoActionDefinition(
        command = CommandKind.SKILL,
        skillId = SkillId("cleave"),
        effectKind = EffectKind.DAMAGE,
        formula = DamageFormula.Physical(power = 15),
        targeting = TargetingShape.SINGLE_ENEMY,
        appliesStatusEffect = StatusEffectId("poison"),
    ),
    DemoActionDefinition(
        command = CommandKind.MAGIC,
        skillId = SkillId("fira"),
        effectKind = EffectKind.DAMAGE,
        formula = DamageFormula.Magical(power = 20),
        targeting = TargetingShape.SINGLE_ENEMY,
        element = ElementId("fire"),
    ),
)

/** The one [DemoActionDefinition] matching [command]/[skillId], or `null` if none is registered. */
fun List<DemoActionDefinition>.find(command: CommandKind, skillId: SkillId?): DemoActionDefinition? =
    firstOrNull { it.command == command && it.skillId == skillId }
