package io.github.kirthar.sddrpg.core.combatant

import io.github.kirthar.sddrpg.core.model.CharacterId
import io.github.kirthar.sddrpg.core.model.EnemyId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

/** R7: deterministic, replay-stable battle identity. */
class RosterDeterminismTest : StringSpec({

    fun threeGoblins(): Roster = RosterBuilder(fixtureCatalog)
        .addPartyMember(CharacterId("cloud"), level = 1)
        .addEnemy(EnemyId("bomb"))
        .addEnemy(EnemyId("bomb"))
        .addEnemy(EnemyId("bomb"))
        .build()

    "multiple instances of the same definition get distinct ids" {
        val roster = threeGoblins()
        val enemyIds = roster.combatants.filter { it.kind == CombatantKind.ENEMY }.map { it.id }
        enemyIds shouldHaveSize 3
        enemyIds.toSet() shouldHaveSize 3
    }

    "instance ids are deterministic from insertion order (SC-006)" {
        val first = threeGoblins()
        val second = threeGoblins()
        first.combatants.map { it.id } shouldBe second.combatants.map { it.id }
    }

    "the same roster built twice yields identical observable combatants" {
        val first = threeGoblins()
        val second = threeGoblins()
        first.combatants.zip(second.combatants).forEach { (a, b) ->
            a.id shouldBe b.id
            a.displayName shouldBe b.displayName
            a.kind shouldBe b.kind
            a.allegiance shouldBe b.allegiance
            a.stats shouldBe b.stats
            a.capabilities shouldBe b.capabilities
            a.decisionSource shouldBe b.decisionSource
        }
    }
})
