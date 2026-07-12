package io.github.kirthar.sddrpg.core.model

import io.github.kirthar.sddrpg.core.TestJson
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class AffinityTest : StringSpec({

    "the five stances exist" {
        Affinity.entries.map { it.name } shouldBe
            listOf("WEAKNESS", "NEUTRAL", "RESISTANCE", "IMMUNITY", "ABSORPTION")
    }

    "affinities serialize with contract JSON names" {
        TestJson.encodeToString(Affinity.serializer(), Affinity.ABSORPTION) shouldBe "\"ABSORPTION\""
        TestJson.decodeFromString(Affinity.serializer(), "\"WEAKNESS\"") shouldBe Affinity.WEAKNESS
    }

    "affinityTo is total: absent element resolves to NEUTRAL" {
        val fire = ElementId("fire")
        val ice = ElementId("ice")
        val map = mapOf(fire to Affinity.ABSORPTION)
        map.affinityTo(fire) shouldBe Affinity.ABSORPTION
        map.affinityTo(ice) shouldBe Affinity.NEUTRAL
        emptyMap<ElementId, Affinity>().affinityTo(fire) shouldBe Affinity.NEUTRAL
    }
})
