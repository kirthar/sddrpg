package io.github.kirthar.sddrpg.core.status

import io.github.kirthar.sddrpg.core.model.CoreStats
import io.github.kirthar.sddrpg.core.model.StatBlock
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/** Delegation correctness: only `stats` is overridden, everything else forwards to the wrapped combatant. */
class EffectiveCombatantTest : StringSpec({

    "only stats differs; every other member forwards to the wrapped combatant unchanged" {
        val battle = freshStatusBattleState()
        val base = battle.participants.first { it.combatant.displayName == "Cloud" }.combatant
        val newStats = StatBlock(base.stats.toMap() + (CoreStats.DEFENSE to 999))
        val wrapped = EffectiveCombatant(base, newStats)

        wrapped.stats shouldBe newStats
        wrapped.id shouldBe base.id
        wrapped.displayName shouldBe base.displayName
        wrapped.kind shouldBe base.kind
        wrapped.allegiance shouldBe base.allegiance
        wrapped.affinities shouldBe base.affinities
        wrapped.capabilities shouldBe base.capabilities
        wrapped.decisionSource shouldBe base.decisionSource
    }
})
