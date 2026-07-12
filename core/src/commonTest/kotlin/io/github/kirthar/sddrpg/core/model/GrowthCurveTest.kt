package io.github.kirthar.sddrpg.core.model

import io.github.kirthar.sddrpg.core.TestJson
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll

class GrowthCurveTest : StringSpec({

    "linear curve follows base + perLevel * (level - 1)" {
        val curve = GrowthCurve.Linear(base = 120, perLevel = 11)
        curve.at(1) shouldBe 120
        curve.at(2) shouldBe 131
        curve.at(10) shouldBe 219
    }

    "table curve returns exact values and clamps beyond the last entry" {
        val curve = GrowthCurve.Table(listOf(10, 12, 15, 19))
        curve.at(1) shouldBe 10
        curve.at(4) shouldBe 19
        curve.at(5) shouldBe 19
        curve.at(99) shouldBe 19
    }

    "level below 1 is rejected" {
        shouldThrow<IllegalArgumentException> { GrowthCurve.Linear(10, 1).at(0) }
        shouldThrow<IllegalArgumentException> { GrowthCurve.Table(listOf(1)).at(-3) }
    }

    "an empty table is rejected" {
        shouldThrow<IllegalArgumentException> { GrowthCurve.Table(emptyList()) }
    }

    "property: curve outputs are never negative" {
        checkAll(Arb.int(0..500), Arb.int(-50..50), Arb.int(1..99)) { base, perLevel, level ->
            GrowthCurve.Linear(base, perLevel).at(level) shouldBeGreaterThanOrEqual 0
        }
    }

    "property: curves are deterministic — same input, same output" {
        checkAll(Arb.int(0..500), Arb.int(-50..50), Arb.int(1..99)) { base, perLevel, level ->
            val curve = GrowthCurve.Linear(base, perLevel)
            curve.at(level) shouldBe curve.at(level)
        }
    }

    "serialization uses the contract type discriminator" {
        val linear: GrowthCurve = GrowthCurve.Linear(120, 11)
        val json = TestJson.encodeToString(GrowthCurve.serializer(), linear)
        json shouldContain "\"type\":\"linear\""
        TestJson.decodeFromString(GrowthCurve.serializer(), json) shouldBe linear

        val table: GrowthCurve = GrowthCurve.Table(listOf(10, 12))
        val tableJson = TestJson.encodeToString(GrowthCurve.serializer(), table)
        tableJson shouldContain "\"type\":\"table\""
        TestJson.decodeFromString(GrowthCurve.serializer(), tableJson) shouldBe table
    }
})
