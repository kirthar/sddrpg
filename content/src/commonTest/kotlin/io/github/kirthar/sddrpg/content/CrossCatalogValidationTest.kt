package io.github.kirthar.sddrpg.content

import io.github.kirthar.sddrpg.core.catalog.ArchetypeDefinition
import io.github.kirthar.sddrpg.core.catalog.Catalog
import io.github.kirthar.sddrpg.core.catalog.ClassDefinition
import io.github.kirthar.sddrpg.core.catalog.CommandKind
import io.github.kirthar.sddrpg.core.event.SynergyBonus
import io.github.kirthar.sddrpg.core.event.SynergyCatalog
import io.github.kirthar.sddrpg.core.event.SynergyDefinition
import io.github.kirthar.sddrpg.core.event.SynergyId
import io.github.kirthar.sddrpg.core.limitbreak.LimitBreakCatalog
import io.github.kirthar.sddrpg.core.limitbreak.LimitBreakDefinition
import io.github.kirthar.sddrpg.core.limitbreak.SummonCatalog
import io.github.kirthar.sddrpg.core.limitbreak.SummonDefinition
import io.github.kirthar.sddrpg.core.limitbreak.SummonId
import io.github.kirthar.sddrpg.core.action.DamageFormula
import io.github.kirthar.sddrpg.core.action.EffectKind as ActionEffectKind
import io.github.kirthar.sddrpg.core.action.TargetingShape
import io.github.kirthar.sddrpg.core.model.AiProfileId
import io.github.kirthar.sddrpg.core.model.ArchetypeId
import io.github.kirthar.sddrpg.core.model.ClassId
import io.github.kirthar.sddrpg.core.model.ElementId
import io.github.kirthar.sddrpg.core.model.LimitBreakId
import io.github.kirthar.sddrpg.core.model.SkillId
import io.github.kirthar.sddrpg.core.model.StatId
import io.github.kirthar.sddrpg.core.status.EffectKind as StatusEffectKind
import io.github.kirthar.sddrpg.core.status.StatusEffectCatalog
import io.github.kirthar.sddrpg.core.status.StatusEffectDefinition
import io.github.kirthar.sddrpg.core.status.StatusEffectId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

private val baseCatalog = Catalog(
    knownAiProfiles = setOf(AiProfileId("aggressive")),
    archetypes = listOf(ArchetypeDefinition(ArchetypeId("common"), "Common", AiProfileId("aggressive"), 0)),
)

class CrossCatalogValidationTest : FunSpec({
    test("a class's limit break id with no matching LimitBreakDefinition is reported (US2 scenario 1)") {
        val catalog = baseCatalog.copy(
            knownLimitBreaks = setOf(LimitBreakId("omnislash")),
            classes = listOf(ClassDefinition(ClassId("warrior"), "Warrior", commands = setOf(CommandKind.SKILL), limitBreaks = setOf(LimitBreakId("omnislash")))),
        )
        val problems = validateCrossCatalogReferences(catalog, StatusEffectCatalog(), SynergyCatalog(), LimitBreakCatalog(), SummonCatalog())
        problems shouldBe listOf(ContentProblem.DanglingReference("classes", "limitBreaks", "omnislash"))
    }

    test("the same content with every reference actually resolving loads successfully (US2 scenario 2)") {
        val catalog = baseCatalog.copy(knownLimitBreaks = setOf(LimitBreakId("omnislash")))
        val limitBreaks = LimitBreakCatalog(listOf(LimitBreakDefinition(LimitBreakId("omnislash"), 50, ActionEffectKind.DAMAGE, DamageFormula.Fixed(1), TargetingShape.SINGLE_ENEMY)))
        validateCrossCatalogReferences(catalog, StatusEffectCatalog(), SynergyCatalog(), limitBreaks, SummonCatalog()) shouldBe emptyList()
    }

    test("a synergy skill id absent from knownSkills is reported") {
        val synergies = SynergyCatalog(listOf(SynergyDefinition(SynergyId("s"), SkillId("cleave"), SkillId("fira"), 2, SynergyBonus.BonusDamage(1))))
        val problems = validateCrossCatalogReferences(baseCatalog, StatusEffectCatalog(), synergies, LimitBreakCatalog(), SummonCatalog())
        problems shouldBe listOf(
            ContentProblem.DanglingReference("synergies", "firstSkillId", "cleave"),
            ContentProblem.DanglingReference("synergies", "secondSkillId", "fira"),
        )
    }

    test("a status effect's stat-modifier StatId that's neither a core stat nor a declared custom stat is reported") {
        val statusEffects = StatusEffectCatalog(listOf(StatusEffectDefinition(StatusEffectId("buff"), "Buff", StatusEffectKind.StatModifier(StatId("luck2"), 5), 3)))
        val problems = validateCrossCatalogReferences(baseCatalog, statusEffects, SynergyCatalog(), LimitBreakCatalog(), SummonCatalog())
        problems shouldBe listOf(ContentProblem.DanglingReference("statusEffects", "statId", "luck2"))
    }

    test("a core stat modifier is not reported (CoreStats are always declared)") {
        val statusEffects = StatusEffectCatalog(listOf(StatusEffectDefinition(StatusEffectId("buff"), "Buff", StatusEffectKind.StatModifier(io.github.kirthar.sddrpg.core.model.CoreStats.ATTACK, 5), 3)))
        validateCrossCatalogReferences(baseCatalog, statusEffects, SynergyCatalog(), LimitBreakCatalog(), SummonCatalog()) shouldBe emptyList()
    }

    test("a limit break/summon ElementId absent from Catalog.elements is reported") {
        val limitBreaks = LimitBreakCatalog(listOf(LimitBreakDefinition(LimitBreakId("omnislash"), 50, ActionEffectKind.DAMAGE, DamageFormula.Fixed(1), TargetingShape.SINGLE_ENEMY, ElementId("fire"))))
        val summons = SummonCatalog(listOf(SummonDefinition(SummonId("meteor"), 10, ActionEffectKind.DAMAGE, DamageFormula.Fixed(1), TargetingShape.SINGLE_ENEMY, ElementId("ice"))))
        val problems = validateCrossCatalogReferences(baseCatalog, StatusEffectCatalog(), SynergyCatalog(), limitBreaks, summons)
        problems shouldBe listOf(
            ContentProblem.DanglingReference("limitBreaks", "element", "fire"),
            ContentProblem.DanglingReference("summons", "element", "ice"),
        )
    }

    test("several different kinds of dangling references at once are all reported together") {
        val catalog = baseCatalog.copy(knownLimitBreaks = setOf(LimitBreakId("omnislash")))
        val synergies = SynergyCatalog(listOf(SynergyDefinition(SynergyId("s"), SkillId("cleave"), SkillId("fira"), 2, SynergyBonus.BonusDamage(1))))
        val problems = validateCrossCatalogReferences(catalog, StatusEffectCatalog(), synergies, LimitBreakCatalog(), SummonCatalog())
        problems shouldHaveSize 3
    }

    test("end-to-end via loadContentPack: a class's limit break id with no matching definition fails loading even though each individual catalog is otherwise valid (US2 scenario 1, wired)") {
        val text = """
        {
          "catalog": {
            "knownAiProfiles": ["aggressive"],
            "knownLimitBreaks": ["omnislash"],
            "archetypes": [{"id": "common", "displayName": "Common", "aiProfile": "aggressive", "rewardTier": 0}],
            "classes": [{"id": "warrior", "displayName": "Warrior", "commands": ["SKILL"], "limitBreaks": ["omnislash"]}],
            "enemies": [{"id": "bomb", "displayName": "Bomb", "archetypeId": "common", "stats": {"hp": 50, "mp": 0, "attack": 10, "defense": 5, "magic": 5, "resistance": 5, "speed": 5, "luck": 5}}]
          }
        }
        """.trimIndent()
        val result = loadContentPack(text)
        val invalid = result.shouldBeInstanceOf<ContentLoadResult.Invalid>()
        invalid.problems shouldBe listOf(ContentProblem.DanglingReference("classes", "limitBreaks", "omnislash"))
    }
})
