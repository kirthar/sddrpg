package io.github.kirthar.sddrpg.content.demo

import io.github.kirthar.sddrpg.core.action.DamageFormula
import io.github.kirthar.sddrpg.core.action.EffectKind
import io.github.kirthar.sddrpg.core.action.TargetingShape
import io.github.kirthar.sddrpg.core.catalog.CommandKind
import io.github.kirthar.sddrpg.core.model.SkillId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** FR-006: what the shipped demo content's basic attack and skills actually do. */
class DemoActionsTest : StringSpec({

    "exactly one entry per distinct (command, skillId) pair" {
        val pairs = DEMO_ACTIONS.map { it.command to it.skillId }
        pairs.toSet() shouldHaveSize pairs.size
    }

    "covers exactly basic ATTACK, cleave (SKILL), and fira (MAGIC)" {
        DEMO_ACTIONS shouldHaveSize 3
        DEMO_ACTIONS.map { it.command to it.skillId }.toSet() shouldBe setOf(
            CommandKind.ATTACK to null,
            CommandKind.SKILL to SkillId("cleave"),
            CommandKind.MAGIC to SkillId("fira"),
        )
    }

    "basic ATTACK deals physical damage against a single enemy, no element" {
        val basic = DEMO_ACTIONS.find(CommandKind.ATTACK, null)!!
        basic.effectKind shouldBe EffectKind.DAMAGE
        basic.formula.shouldBeInstanceOf<DamageFormula.Physical>()
        basic.targeting shouldBe TargetingShape.SINGLE_ENEMY
        basic.element shouldBe null
    }

    "fira is a magical, fire-elemental single-target damage skill" {
        val fira = DEMO_ACTIONS.find(CommandKind.MAGIC, SkillId("fira"))!!
        fira.effectKind shouldBe EffectKind.DAMAGE
        fira.formula.shouldBeInstanceOf<DamageFormula.Magical>()
        fira.targeting shouldBe TargetingShape.SINGLE_ENEMY
        fira.element shouldBe io.github.kirthar.sddrpg.core.model.ElementId("fire")
    }

    "the lookup helper returns null for an unregistered (command, skillId) pair" {
        DEMO_ACTIONS.find(CommandKind.SUMMON, null) shouldBe null
    }

    "cleave applies poison to its target -- the only demo action that does (spec 008 US3)" {
        val cleave = DEMO_ACTIONS.find(CommandKind.SKILL, SkillId("cleave"))!!
        cleave.appliesStatusEffect shouldBe io.github.kirthar.sddrpg.core.status.StatusEffectId("poison")

        DEMO_ACTIONS.filter { it.appliesStatusEffect != null } shouldBe listOf(cleave)
    }
})
