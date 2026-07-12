package io.github.kirthar.sddrpg.core.event

import io.github.kirthar.sddrpg.core.model.SkillId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class SynergyCatalogValidationTest : FunSpec({
    val cleave = SkillId("cleave")
    val fira = SkillId("fira")

    test("a catalog with no synergies is valid") {
        validateSynergyCatalog(SynergyCatalog()).shouldBeInstanceOf<SynergyCatalogResult.Valid>()
    }

    test("a valid two-distinct-skill, positive-window synergy passes") {
        val catalog = SynergyCatalog(
            listOf(SynergyDefinition(SynergyId("warriorMage"), cleave, fira, window = 2, bonus = SynergyBonus.BonusDamage(10)))
        )
        validateSynergyCatalog(catalog).shouldBeInstanceOf<SynergyCatalogResult.Valid>()
    }

    test("duplicate ids, same-skill pairs, and non-positive windows are all accumulated in one pass") {
        val dupId = SynergyId("dup")
        val catalog = SynergyCatalog(
            listOf(
                SynergyDefinition(dupId, cleave, fira, window = 2, bonus = SynergyBonus.BonusDamage(10)),
                SynergyDefinition(dupId, cleave, fira, window = 2, bonus = SynergyBonus.BonusDamage(10)),
                SynergyDefinition(SynergyId("sameSkill"), cleave, cleave, window = 2, bonus = SynergyBonus.BonusDamage(10)),
                SynergyDefinition(SynergyId("badWindow"), cleave, fira, window = 0, bonus = SynergyBonus.BonusDamage(10)),
            )
        )
        val invalid = validateSynergyCatalog(catalog).shouldBeInstanceOf<SynergyCatalogResult.Invalid>()
        invalid.errors shouldBe listOf(
            SynergyCatalogError.DuplicateId(dupId.value),
            SynergyCatalogError.SameSkill("sameSkill", cleave.value),
            SynergyCatalogError.NonPositiveWindow("badWindow", 0),
        )
    }
})
