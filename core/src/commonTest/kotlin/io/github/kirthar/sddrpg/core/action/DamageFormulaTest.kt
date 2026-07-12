package io.github.kirthar.sddrpg.core.action

import io.github.kirthar.sddrpg.core.model.CoreStats
import io.github.kirthar.sddrpg.core.model.StatBlock
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll

private fun stats(attack: Int = 0, defense: Int = 0, magic: Int = 0, resistance: Int = 0): StatBlock =
    StatBlock(
        CoreStats.ALL.associateWith { 0 } +
            mapOf(CoreStats.ATTACK to attack, CoreStats.DEFENSE to defense, CoreStats.MAGIC to magic, CoreStats.RESISTANCE to resistance)
    )

class DamageFormulaTest : StringSpec({

    "Physical formula is power + ATTACK - DEFENSE" {
        val formula = DamageFormula.Physical(power = 10)
        formula.rawMagnitude(stats(attack = 5), stats(defense = 3)) shouldBe 12
    }

    "Physical formula floors at 0 when defense exceeds power+attack" {
        val formula = DamageFormula.Physical(power = 5)
        formula.rawMagnitude(stats(attack = 2), stats(defense = 100)) shouldBe 0
    }

    "Magical formula is power + MAGIC - RESISTANCE" {
        val formula = DamageFormula.Magical(power = 20)
        formula.rawMagnitude(stats(magic = 8), stats(resistance = 5)) shouldBe 23
    }

    "Magical formula floors at 0 when resistance exceeds power+magic" {
        val formula = DamageFormula.Magical(power = 1)
        formula.rawMagnitude(stats(magic = 1), stats(resistance = 999)) shouldBe 0
    }

    "Fixed formula ignores stats entirely" {
        val formula = DamageFormula.Fixed(amount = 42)
        formula.rawMagnitude(stats(attack = 999), stats(defense = 999)) shouldBe 42
    }

    "property: rawMagnitude is never negative" {
        checkAll(Arb.int(-50..50), Arb.int(0..100), Arb.int(0..100)) { power, off, def ->
            DamageFormula.Physical(power).rawMagnitude(stats(attack = off), stats(defense = def)) >= 0
        }
    }
})
