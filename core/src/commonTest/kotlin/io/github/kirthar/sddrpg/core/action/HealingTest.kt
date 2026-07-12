package io.github.kirthar.sddrpg.core.action

import io.github.kirthar.sddrpg.core.catalog.CommandKind
import io.github.kirthar.sddrpg.core.model.CombatantId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll

/** US4 acceptance scenarios 1-3: healing increases health, caps at maximum, ignores affinity without an element. */
class HealingTest : StringSpec({

    fun idOf(state: BattleState, name: String): CombatantId =
        state.participants.first { it.combatant.displayName == name }.combatant.id

    fun withHealth(state: BattleState, id: CombatantId, current: Int): BattleState = BattleState(
        state.participants.map {
            if (it.combatant.id == id) it.copy(health = it.health.copy(current = current)) else it
        }
    )

    fun healAction(actorId: CombatantId, amount: Int, element: io.github.kirthar.sddrpg.core.model.ElementId? = null) =
        CombatAction(
            actorId = actorId, command = CommandKind.MAGIC, effectKind = EffectKind.HEAL,
            element = element, formula = DamageFormula.Fixed(amount), targeting = TargetingShape.SINGLE_ALLY,
        )

    "scenario 1: healing increases health by the formula's result" {
        val fresh = freshBattleState()
        val aerith = idOf(fresh, "Aerith")
        val cloud = idOf(fresh, "Cloud")
        val maxHp = fresh.find(cloud)!!.health.maximum
        val state = withHealth(fresh, cloud, current = maxHp - 10)
        val action = healAction(aerith, amount = 6)
        val resolved = resolveAction(state, action, setOf(cloud)).shouldBeInstanceOf<ActionResolutionResult.Resolved>()
        val outcome = resolved.outcomes.single()
        outcome.appliedDelta shouldBe 6
        outcome.resultingHealth shouldBe maxHp - 4
    }

    "scenario 2: healing near maximum is capped at maximum, never overflows" {
        val fresh = freshBattleState()
        val aerith = idOf(fresh, "Aerith")
        val cloud = idOf(fresh, "Cloud")
        val maxHp = fresh.find(cloud)!!.health.maximum
        val state = withHealth(fresh, cloud, current = maxHp - 2)
        val action = healAction(aerith, amount = 50)
        val resolved = resolveAction(state, action, setOf(cloud)).shouldBeInstanceOf<ActionResolutionResult.Resolved>()
        val outcome = resolved.outcomes.single()
        outcome.appliedDelta shouldBe 2 // clamped: only enough to reach maximum
        outcome.resultingHealth shouldBe maxHp
    }

    "scenario 3: a heal with no declared element applies no elemental adjustment" {
        val fresh = freshBattleState()
        val aerith = idOf(fresh, "Aerith")
        val cloud = idOf(fresh, "Cloud")
        val maxHp = fresh.find(cloud)!!.health.maximum
        val state = withHealth(fresh, cloud, current = maxHp - 10)
        // cloud has no declared affinity to "fire" (would default NEUTRAL anyway), but the
        // point of this scenario is structural: no element means the multiplier step never runs.
        val action = healAction(aerith, amount = 6, element = null)
        val resolved = resolveAction(state, action, setOf(cloud)).shouldBeInstanceOf<ActionResolutionResult.Resolved>()
        val outcome = resolved.outcomes.single()
        outcome.affinityApplied shouldBe null
        outcome.appliedDelta shouldBe 6
    }

    "property: resulting health always stays within [0, maximum] (FR-011/SC-005)" {
        // Bomb=WEAKNESS, Flan=ABSORPTION, Pudding=RESISTANCE to fire (real fixture
        // affinities) — exercised together with NEUTRAL (no element) across random
        // amounts and both effect kinds.
        checkAll(
            Arb.int(0..30),
            Arb.element(listOf(EffectKind.DAMAGE, EffectKind.HEAL)),
            Arb.element(listOf("Bomb", "Flan", "Pudding")),
            Arb.element(listOf(true, false)), // whether the element is declared (drives NEUTRAL vs the target's real stance)
        ) { amount, effect, targetName, declareElement ->
            val fresh = freshBattleState()
            val aerith = idOf(fresh, "Aerith")
            val target = idOf(fresh, targetName)
            val action = CombatAction(
                actorId = aerith, command = CommandKind.MAGIC, effectKind = effect,
                element = if (declareElement) fireElement else null,
                formula = DamageFormula.Fixed(amount), targeting = TargetingShape.SINGLE_ENEMY,
            )
            val resolved = resolveAction(fresh, action, setOf(target)).shouldBeInstanceOf<ActionResolutionResult.Resolved>()
            val health = resolved.outcomes.single().resultingHealth
            (health in 0..fresh.find(target)!!.health.maximum) shouldBe true
        }
    }
})
