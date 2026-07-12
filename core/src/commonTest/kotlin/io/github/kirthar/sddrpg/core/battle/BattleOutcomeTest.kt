package io.github.kirthar.sddrpg.core.battle

import io.github.kirthar.sddrpg.core.action.BattleState
import io.github.kirthar.sddrpg.core.action.toBattleState
import io.github.kirthar.sddrpg.core.combatant.RosterBuilder
import io.github.kirthar.sddrpg.core.combatant.fixtureCatalog
import io.github.kirthar.sddrpg.core.model.CharacterId
import io.github.kirthar.sddrpg.core.model.CombatantId
import io.github.kirthar.sddrpg.core.model.EnemyId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/** FR-005/SC-001: unambiguous victory/defeat detection, and the simultaneous-defeat tie-break. */
class BattleOutcomeTest : StringSpec({

    fun freshState(): BattleState = RosterBuilder(fixtureCatalog)
        .addPartyMember(CharacterId("cloud"), level = 1)
        .addEnemy(EnemyId("bomb"))
        .build()
        .toBattleState()

    fun withHealth(state: BattleState, id: CombatantId, current: Int): BattleState = BattleState(
        state.participants.map {
            if (it.combatant.id == id) it.copy(health = it.health.copy(current = current)) else it
        }
    )

    fun idOf(state: BattleState, displayName: String): CombatantId =
        state.participants.first { it.combatant.displayName == displayName }.combatant.id

    "a battle where both sides still have a living combatant is Ongoing" {
        freshState().outcome() shouldBe BattleOutcome.Ongoing
    }

    "every opponent defeated is a Victory" {
        val state = freshState()
        val bombId = idOf(state, "Bomb")
        withHealth(state, bombId, current = 0).outcome() shouldBe BattleOutcome.Victory
    }

    "every player-side combatant defeated is a Defeat" {
        val state = freshState()
        val cloudId = idOf(state, "Cloud")
        withHealth(state, cloudId, current = 0).outcome() shouldBe BattleOutcome.Defeat
    }

    "simultaneous defeat of both sides resolves to Defeat (conservative tie-break), never Victory" {
        val state = freshState()
        val cloudId = idOf(state, "Cloud")
        val bombId = idOf(state, "Bomb")
        val bothDown = withHealth(withHealth(state, cloudId, current = 0), bombId, current = 0)
        bothDown.outcome() shouldBe BattleOutcome.Defeat
    }
})
