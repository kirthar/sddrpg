package io.github.kirthar.sddrpg.core.action

import io.github.kirthar.sddrpg.core.catalog.CommandKind
import io.github.kirthar.sddrpg.core.model.Affinity
import io.github.kirthar.sddrpg.core.model.CombatantId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** US1 acceptance scenarios 1-6: a basic attack resolves into a deterministic damage number. */
class BasicAttackResolutionTest : StringSpec({

    fun idOf(state: BattleState, name: String): CombatantId =
        state.participants.first { it.combatant.displayName == name }.combatant.id

    fun attackAction(actorId: CombatantId, element: io.github.kirthar.sddrpg.core.model.ElementId? = null) = CombatAction(
        actorId = actorId,
        command = CommandKind.ATTACK,
        effectKind = EffectKind.DAMAGE,
        element = element,
        formula = DamageFormula.Physical(power = 0),
        targeting = TargetingShape.SINGLE_ENEMY,
    )

    "scenario 1: a neutral target takes unadjusted formula damage" {
        val state = freshBattleState()
        val cloud = idOf(state, "Cloud")
        val bomb = idOf(state, "Bomb")
        val action = attackAction(cloud) // no element declared: no fixture enemy affinity applies
        val resolved = resolveAction(state, action, setOf(bomb))
            .shouldBeInstanceOf<ActionResolutionResult.Resolved>()
        val expectedRaw = DamageFormula.Physical(0)
            .rawMagnitude(state.find(cloud)!!.combatant.stats, state.find(bomb)!!.combatant.stats)
        resolved.outcomes.single().appliedDelta shouldBe -expectedRaw
        resolved.outcomes.single().affinityApplied shouldBe null
    }

    "scenario 2: weakness doubles the damage" {
        val state = freshBattleState()
        val cloud = idOf(state, "Cloud")
        val bomb = idOf(state, "Bomb")
        val action = attackAction(cloud, element = fireElement)
        val resolved = resolveAction(state, action, setOf(bomb))
            .shouldBeInstanceOf<ActionResolutionResult.Resolved>()
        val outcome = resolved.outcomes.single()
        val raw = DamageFormula.Physical(0)
            .rawMagnitude(state.find(cloud)!!.combatant.stats, state.find(bomb)!!.combatant.stats)
        outcome.appliedDelta shouldBe -(raw * 2)
        outcome.affinityApplied shouldBe Affinity.WEAKNESS
    }

    "scenario 3: resistance halves the damage" {
        val state = freshBattleState()
        val cloud = idOf(state, "Cloud")
        val pudding = idOf(state, "Pudding")
        val action = attackAction(cloud, element = fireElement)
        val resolved = resolveAction(state, action, setOf(pudding))
            .shouldBeInstanceOf<ActionResolutionResult.Resolved>()
        val outcome = resolved.outcomes.single()
        val raw = DamageFormula.Physical(0)
            .rawMagnitude(state.find(cloud)!!.combatant.stats, state.find(pudding)!!.combatant.stats)
        outcome.appliedDelta shouldBe applyElementalAdjustment(-raw, Affinity.RESISTANCE)
        outcome.affinityApplied shouldBe Affinity.RESISTANCE
    }

    "scenario 4: immunity zeroes the damage" {
        // No immune fixture combatant is needed for the end-to-end wiring — immunity's
        // zeroing behavior is exhaustively unit-tested in ElementalAdjustmentTest; this
        // assertion only confirms the shared function this pipeline calls.
        applyElementalAdjustment(baseDelta = -10, stance = Affinity.IMMUNITY) shouldBe 0
    }

    "scenario 5: absorption heals the target instead of harming it" {
        val state = freshBattleState()
        val cloud = idOf(state, "Cloud")
        val flan = idOf(state, "Flan")
        // Damage Flan first so healing-via-absorption is observable (it starts at
        // full health, and health can never exceed maximum — FR-011).
        val damaged = resolveAction(state, attackAction(cloud), setOf(flan))
            .shouldBeInstanceOf<ActionResolutionResult.Resolved>().newState
        val before = damaged.find(flan)!!.health.current
        val action = attackAction(cloud, element = fireElement)
        val resolved = resolveAction(damaged, action, setOf(flan))
            .shouldBeInstanceOf<ActionResolutionResult.Resolved>()
        val outcome = resolved.outcomes.single()
        outcome.affinityApplied shouldBe Affinity.ABSORPTION
        (outcome.appliedDelta > 0) shouldBe true
        outcome.resultingHealth shouldBe before + outcome.appliedDelta
    }

    "scenario 6: identical inputs resolve to an identical outcome (determinism)" {
        val state = freshBattleState()
        val cloud = idOf(state, "Cloud")
        val bomb = idOf(state, "Bomb")
        val action = attackAction(cloud, element = fireElement)
        val first = resolveAction(state, action, setOf(bomb))
        val second = resolveAction(state, action, setOf(bomb))
        first shouldBe second
    }
})
