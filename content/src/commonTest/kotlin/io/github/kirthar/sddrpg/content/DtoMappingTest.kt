package io.github.kirthar.sddrpg.content

import io.github.kirthar.sddrpg.content.dto.ActionEffectKindDto
import io.github.kirthar.sddrpg.content.dto.DamageFormulaDto
import io.github.kirthar.sddrpg.content.dto.LimitBreakDefinitionDto
import io.github.kirthar.sddrpg.content.dto.StatusEffectDefinitionDto
import io.github.kirthar.sddrpg.content.dto.StatusEffectKindDto
import io.github.kirthar.sddrpg.content.dto.SummonDefinitionDto
import io.github.kirthar.sddrpg.content.dto.SynergyBonusDto
import io.github.kirthar.sddrpg.content.dto.SynergyDefinitionDto
import io.github.kirthar.sddrpg.content.dto.TargetingShapeDto
import io.github.kirthar.sddrpg.content.dto.toCore
import io.github.kirthar.sddrpg.core.action.DamageFormula
import io.github.kirthar.sddrpg.core.action.EffectKind
import io.github.kirthar.sddrpg.core.action.TargetingShape
import io.github.kirthar.sddrpg.core.event.SynergyBonus
import io.github.kirthar.sddrpg.core.event.SynergyId
import io.github.kirthar.sddrpg.core.limitbreak.SummonId
import io.github.kirthar.sddrpg.core.model.ElementId
import io.github.kirthar.sddrpg.core.model.LimitBreakId
import io.github.kirthar.sddrpg.core.model.SkillId
import io.github.kirthar.sddrpg.core.model.StatId
import io.github.kirthar.sddrpg.core.status.StatusEffectId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class DtoMappingTest : FunSpec({
    test("TargetingShapeDto maps 1:1 to TargetingShape") {
        TargetingShapeDto.SINGLE_ENEMY.toCore() shouldBe TargetingShape.SINGLE_ENEMY
        TargetingShapeDto.ALL_ALLIES.toCore() shouldBe TargetingShape.ALL_ALLIES
    }

    test("ActionEffectKindDto maps 1:1 to action's EffectKind") {
        ActionEffectKindDto.DAMAGE.toCore() shouldBe EffectKind.DAMAGE
        ActionEffectKindDto.HEAL.toCore() shouldBe EffectKind.HEAL
    }

    test("DamageFormulaDto maps each variant to its DamageFormula equivalent") {
        DamageFormulaDto.Physical(5).toCore() shouldBe DamageFormula.Physical(5)
        DamageFormulaDto.Magical(7).toCore() shouldBe DamageFormula.Magical(7)
        DamageFormulaDto.Fixed(3).toCore() shouldBe DamageFormula.Fixed(3)
    }

    test("StatusEffectKindDto maps each variant to its status EffectKind equivalent") {
        StatusEffectKindDto.StatModifier(StatId("attack"), -5).toCore() shouldBe
            io.github.kirthar.sddrpg.core.status.EffectKind.StatModifier(StatId("attack"), -5)
        StatusEffectKindDto.Incapacitate.toCore() shouldBe io.github.kirthar.sddrpg.core.status.EffectKind.Incapacitate
        StatusEffectKindDto.DamageOverTime(5).toCore() shouldBe io.github.kirthar.sddrpg.core.status.EffectKind.DamageOverTime(5)
    }

    test("StatusEffectDefinitionDto maps to StatusEffectDefinition") {
        val dto = StatusEffectDefinitionDto("poison", "Poison", StatusEffectKindDto.DamageOverTime(5), 3)
        dto.toCore() shouldBe io.github.kirthar.sddrpg.core.status.StatusEffectDefinition(
            StatusEffectId("poison"), "Poison", io.github.kirthar.sddrpg.core.status.EffectKind.DamageOverTime(5), 3,
        )
    }

    test("SynergyBonusDto maps to SynergyBonus") {
        SynergyBonusDto.BonusDamage(10).toCore() shouldBe SynergyBonus.BonusDamage(10)
    }

    test("SynergyDefinitionDto maps to SynergyDefinition") {
        val dto = SynergyDefinitionDto("warriorMage", SkillId("cleave"), SkillId("fira"), 2, SynergyBonusDto.BonusDamage(10))
        dto.toCore() shouldBe io.github.kirthar.sddrpg.core.event.SynergyDefinition(
            SynergyId("warriorMage"), SkillId("cleave"), SkillId("fira"), 2, SynergyBonus.BonusDamage(10),
        )
    }

    test("LimitBreakDefinitionDto maps to LimitBreakDefinition") {
        val dto = LimitBreakDefinitionDto(LimitBreakId("omnislash"), 50, ActionEffectKindDto.DAMAGE, DamageFormulaDto.Fixed(999), TargetingShapeDto.SINGLE_ENEMY, ElementId("fire"))
        dto.toCore() shouldBe io.github.kirthar.sddrpg.core.limitbreak.LimitBreakDefinition(
            LimitBreakId("omnislash"), 50, EffectKind.DAMAGE, DamageFormula.Fixed(999), TargetingShape.SINGLE_ENEMY, ElementId("fire"),
        )
    }

    test("SummonDefinitionDto maps to SummonDefinition") {
        val dto = SummonDefinitionDto("meteor", 15, ActionEffectKindDto.DAMAGE, DamageFormulaDto.Fixed(999), TargetingShapeDto.SINGLE_ENEMY)
        dto.toCore() shouldBe io.github.kirthar.sddrpg.core.limitbreak.SummonDefinition(
            SummonId("meteor"), 15, EffectKind.DAMAGE, DamageFormula.Fixed(999), TargetingShape.SINGLE_ENEMY, null,
        )
    }
})
