package io.github.kirthar.sddrpg.core.action

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/** FR-012: Roster.toBattleState() must not alter any field of the wrapped Combatant. */
class BattleStateContractTest : StringSpec({

    "toBattleState leaves every wrapped Combatant structurally identical to the roster's" {
        val roster = io.github.kirthar.sddrpg.core.combatant.RosterBuilder(actionFixtureCatalog)
            .addPartyMember(io.github.kirthar.sddrpg.core.model.CharacterId("cloud"), level = 1)
            .addEnemy(io.github.kirthar.sddrpg.core.model.EnemyId("bomb"))
            .build()
        val state = roster.toBattleState()

        roster.combatants.size shouldBe state.participants.size
        roster.combatants.zip(state.participants).forEach { (original, wrapped) ->
            wrapped.combatant shouldBe original
        }
    }
})
