package io.github.kirthar.sddrpg.core.status

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/** FR-011: a participant with no active effects is returned byte-identical (specs 001-003 contract preservation). */
class StatusContractTest : StringSpec({

    "a participant with no active effects is returned unwrapped, same Combatant reference" {
        val battle = freshStatusBattleState()
        val derived = deriveEffectiveBattleState(battle, StatusEffectState(), statusEffectFixtureCatalog)

        battle.participants.zip(derived.participants).forEach { (original, result) ->
            (result.combatant === original.combatant) shouldBe true
        }
    }
})
