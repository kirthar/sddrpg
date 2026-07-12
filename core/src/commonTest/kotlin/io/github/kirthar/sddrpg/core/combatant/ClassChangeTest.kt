package io.github.kirthar.sddrpg.core.combatant

import io.github.kirthar.sddrpg.core.catalog.CommandKind
import io.github.kirthar.sddrpg.core.model.CharacterId
import io.github.kirthar.sddrpg.core.model.ClassId
import io.github.kirthar.sddrpg.core.model.CoreStats
import io.github.kirthar.sddrpg.core.model.SkillId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/** SC-005 / research R1: job change is a data operation on one reference. */
class ClassChangeTest : StringSpec({

    fun cloudAtLevel3(): Combatant = RosterBuilder(fixtureCatalog)
        .addPartyMember(CharacterId("cloud"), level = 3)
        .build()
        .combatants
        .single()

    "withActiveClass swaps capabilities and growth-derived stats, nothing else" {
        val warriorCloud = cloudAtLevel3()
        warriorCloud.capabilities.skills shouldBe setOf(SkillId("cleave"))
        warriorCloud.stats[CoreStats.HP] shouldBe 120 // warrior curve at level 3

        val mageCloud = warriorCloud.withActiveClass(ClassId("mage"), fixtureCatalog)

        // Swapped: everything the class grants.
        mageCloud.capabilities.skills shouldBe setOf(SkillId("fira"))
        mageCloud.capabilities.commands shouldBe setOf(CommandKind.ATTACK, CommandKind.MAGIC)
        mageCloud.stats[CoreStats.HP] shouldBe 70 // mage curve at level 3: 60 + 5*2

        // Intact: identity and battle configuration.
        mageCloud.id shouldBe warriorCloud.id
        mageCloud.displayName shouldBe warriorCloud.displayName
        mageCloud.kind shouldBe warriorCloud.kind
        mageCloud.allegiance shouldBe warriorCloud.allegiance
        mageCloud.decisionSource shouldBe warriorCloud.decisionSource

        // Stats not governed by a curve keep their base values.
        mageCloud.stats[CoreStats.SPEED] shouldBe 10
    }

    "changing class back restores the original grants (pure data operation)" {
        val cloud = cloudAtLevel3()
        val roundTrip = cloud
            .withActiveClass(ClassId("mage"), fixtureCatalog)
            .withActiveClass(ClassId("warrior"), fixtureCatalog)
        roundTrip.capabilities shouldBe cloud.capabilities
        roundTrip.stats shouldBe cloud.stats
    }
})
