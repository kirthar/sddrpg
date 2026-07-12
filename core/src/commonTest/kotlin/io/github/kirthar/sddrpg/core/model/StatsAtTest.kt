package io.github.kirthar.sddrpg.core.model

import io.github.kirthar.sddrpg.core.catalog.ClassDefinition
import io.github.kirthar.sddrpg.core.catalog.CommandKind
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll

private val warrior = ClassDefinition(
    id = ClassId("warrior"),
    displayName = "Warrior",
    commands = setOf(CommandKind.ATTACK, CommandKind.DEFEND),
    growth = mapOf(
        CoreStats.HP to GrowthCurve.Linear(base = 120, perLevel = 11),
        CoreStats.ATTACK to GrowthCurve.Table(listOf(10, 12, 15)),
    ),
)

private val base = StatBlock(
    CoreStats.ALL.associateWith { 10 }
)

class StatsAtTest : StringSpec({

    "stats with a growth curve are derived from the curve at the given level" {
        val stats = statsAt(warrior, level = 2, baseStats = base)
        stats[CoreStats.HP] shouldBe 131
        stats[CoreStats.ATTACK] shouldBe 12
    }

    "stats without a curve stay at the character's base value" {
        val stats = statsAt(warrior, level = 5, baseStats = base)
        stats[CoreStats.SPEED] shouldBe 10
        stats[CoreStats.LUCK] shouldBe 10
    }

    "level below 1 is rejected" {
        shouldThrow<IllegalArgumentException> { statsAt(warrior, 0, base) }
    }

    "property: statsAt is deterministic (SC-006)" {
        checkAll(Arb.int(1..99)) { level ->
            statsAt(warrior, level, base) shouldBe statsAt(warrior, level, base)
        }
    }
})
