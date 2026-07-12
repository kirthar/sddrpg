package io.github.kirthar.sddrpg.core.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll

private fun completeStats(value: Int = 10): Map<StatId, Int> =
    CoreStats.ALL.associateWith { value }

class StatBlockTest : StringSpec({

    "a StatBlock with all core stats and non-negative values is valid" {
        val block = StatBlock(completeStats(7))
        CoreStats.ALL.forEach { block[it] shouldBe 7 }
    }

    "construction rejects a missing core stat" {
        val missingHp = completeStats().minus(CoreStats.HP)
        shouldThrow<IllegalArgumentException> { StatBlock(missingHp) }
    }

    "construction rejects negative values" {
        val negative = completeStats().plus(CoreStats.SPEED to -1)
        shouldThrow<IllegalArgumentException> { StatBlock(negative) }
    }

    "custom stat keys are allowed alongside core stats" {
        val bravery = StatId("bravery")
        val block = StatBlock(completeStats() + (bravery to 3))
        block[bravery] shouldBe 3
        block.statIds shouldBe CoreStats.ALL + bravery
    }

    "property: any complete non-negative map is accepted and every core stat is readable" {
        checkAll(Arb.map(Arb.int(0..8).map { CoreStats.ALL.toList()[it % 8] }, Arb.int(0, 9999))) { partial ->
            val block = StatBlock(completeStats(0) + partial)
            CoreStats.ALL.forEach { id -> (block[id] >= 0) shouldBe true }
        }
    }
})
