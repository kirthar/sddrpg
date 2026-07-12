package io.github.kirthar.sddrpg.core.model

import io.github.kirthar.sddrpg.core.TestJson
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

class IdentifiersTest : StringSpec({

    "ids with the same value are equal, different values are not" {
        StatId("hp") shouldBe StatId("hp")
        StatId("hp") shouldNotBe StatId("mp")
        ClassId("warrior") shouldBe ClassId("warrior")
    }

    "ids serialize as plain JSON strings" {
        TestJson.encodeToString(StatId.serializer(), StatId("hp")) shouldBe "\"hp\""
        TestJson.encodeToString(ClassId.serializer(), ClassId("warrior")) shouldBe "\"warrior\""
        TestJson.decodeFromString(SkillId.serializer(), "\"cleave\"") shouldBe SkillId("cleave")
    }

    "ids work as JSON map keys" {
        val serializer = MapSerializer(StatId.serializer(), Int.serializer())
        val json = TestJson.encodeToString(serializer, mapOf(StatId("hp") to 100))
        json shouldBe """{"hp":100}"""
        TestJson.decodeFromString(serializer, json) shouldBe mapOf(StatId("hp") to 100)
    }
})
