package io.github.kirthar.sddrpg.core.action

import io.github.kirthar.sddrpg.core.catalog.CommandKind
import io.github.kirthar.sddrpg.core.combatant.withActiveClass
import io.github.kirthar.sddrpg.core.model.ClassId
import io.github.kirthar.sddrpg.core.model.CombatantId
import io.github.kirthar.sddrpg.core.model.SkillId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** US2 acceptance scenarios 1-4, plus DEFEND/ITEM coverage (research R7) and the
 *  no-mutation-on-rejection guarantee (spec SC-002). */
class CapabilityGatingTest : StringSpec({

    fun idOf(state: BattleState, name: String): CombatantId =
        state.participants.first { it.combatant.displayName == name }.combatant.id

    fun withHealth(state: BattleState, id: CombatantId, current: Int): BattleState = BattleState(
        state.participants.map {
            if (it.combatant.id == id) it.copy(health = it.health.copy(current = current)) else it
        }
    )

    "scenario 1: a granted command is accepted through to resolution" {
        val state = freshBattleState()
        val cloud = idOf(state, "Cloud")
        val bomb = idOf(state, "Bomb")
        val action = CombatAction(
            actorId = cloud, command = CommandKind.ATTACK, effectKind = EffectKind.DAMAGE,
            formula = DamageFormula.Physical(0), targeting = TargetingShape.SINGLE_ENEMY,
        )
        resolveAction(state, action, setOf(bomb)).shouldBeInstanceOf<ActionResolutionResult.Resolved>()
    }

    "scenario 2: a skill the actor's class doesn't grant is rejected, naming it, without mutating state" {
        val state = freshBattleState()
        val aerith = idOf(state, "Aerith") // mage: grants fira/cura, not cleave
        val bomb = idOf(state, "Bomb")
        val action = CombatAction(
            actorId = aerith, command = CommandKind.SKILL, skillId = SkillId("cleave"),
            effectKind = EffectKind.DAMAGE, formula = DamageFormula.Physical(0),
            targeting = TargetingShape.SINGLE_ENEMY,
        )
        val before = state
        val result = resolveAction(state, action, setOf(bomb))
        val error = result.shouldBeInstanceOf<ActionResolutionResult.Rejected>().error
        error.shouldBeInstanceOf<ActionError.MissingSkill>()
        (error as ActionError.MissingSkill).skillId shouldBe SkillId("cleave")
        state shouldBe before // no mutation on rejection
    }

    "scenario 3: a command the actor's class doesn't grant is rejected, naming it, without mutating state" {
        val state = freshBattleState()
        val cloud = idOf(state, "Cloud") // warrior: no MAGIC command
        val bomb = idOf(state, "Bomb")
        val action = CombatAction(
            actorId = cloud, command = CommandKind.MAGIC, effectKind = EffectKind.DAMAGE,
            formula = DamageFormula.Magical(0), targeting = TargetingShape.SINGLE_ENEMY,
        )
        val before = state
        val result = resolveAction(state, action, setOf(bomb))
        val error = result.shouldBeInstanceOf<ActionResolutionResult.Rejected>().error
        error.shouldBeInstanceOf<ActionError.MissingCommand>()
        (error as ActionError.MissingCommand).command shouldBe CommandKind.MAGIC
        state shouldBe before
    }

    "a defeated actor is rejected, without mutating state" {
        val fresh = freshBattleState()
        val cloud = idOf(fresh, "Cloud")
        val bomb = idOf(fresh, "Bomb")
        val state = withHealth(fresh, cloud, current = 0)
        val action = CombatAction(
            actorId = cloud, command = CommandKind.ATTACK, effectKind = EffectKind.DAMAGE,
            formula = DamageFormula.Physical(0), targeting = TargetingShape.SINGLE_ENEMY,
        )
        val before = state
        val result = resolveAction(state, action, setOf(bomb))
        val error = result.shouldBeInstanceOf<ActionResolutionResult.Rejected>().error
        error.shouldBeInstanceOf<ActionError.DefeatedActor>()
        (error as ActionError.DefeatedActor).actorId shouldBe cloud
        state shouldBe before
    }

    "scenario 4: gating reflects a class swap, not the combatant's original class" {
        val state = freshBattleState()
        val cloud = idOf(state, "Cloud") // warrior originally: no MAGIC
        val cloudCombatant = state.find(cloud)!!.combatant
        val swapped = cloudCombatant.withActiveClass(ClassId("mage"), actionFixtureCatalog)
        val swappedState = BattleState(
            state.participants.map { if (it.combatant.id == cloud) it.copy(combatant = swapped) else it }
        )
        val bomb = idOf(swappedState, "Bomb")
        val magicAction = CombatAction(
            actorId = cloud, command = CommandKind.MAGIC, effectKind = EffectKind.DAMAGE,
            formula = DamageFormula.Magical(0), targeting = TargetingShape.SINGLE_ENEMY,
        )
        resolveAction(swappedState, magicAction, setOf(bomb)).shouldBeInstanceOf<ActionResolutionResult.Resolved>()

        // The old class's DEFEND grant is gone after the swap (mage doesn't grant it).
        val defendAction = CombatAction(
            actorId = cloud, command = CommandKind.DEFEND, effectKind = EffectKind.DAMAGE,
            formula = DamageFormula.Fixed(0), targeting = TargetingShape.SELF,
        )
        val result = resolveAction(swappedState, defendAction, setOf(cloud))
        result.shouldBeInstanceOf<ActionResolutionResult.Rejected>().error.shouldBeInstanceOf<ActionError.MissingCommand>()
    }

    "DEFEND resolves as a valid, gated, self-targeted zero-magnitude action (research R7)" {
        val state = freshBattleState()
        val cloud = idOf(state, "Cloud") // warrior grants DEFEND
        val action = CombatAction(
            actorId = cloud, command = CommandKind.DEFEND, effectKind = EffectKind.DAMAGE,
            formula = DamageFormula.Fixed(0), targeting = TargetingShape.SELF,
        )
        val resolved = resolveAction(state, action, setOf(cloud)).shouldBeInstanceOf<ActionResolutionResult.Resolved>()
        resolved.outcomes.single().appliedDelta shouldBe 0
    }

    "ITEM resolves through the same pipeline as any other gated action (research R7)" {
        val state = freshBattleState()
        val cloud = idOf(state, "Cloud") // warrior grants ITEM
        val damaged = withHealth(state, cloud, current = state.find(cloud)!!.health.maximum - 5)
        val action = CombatAction(
            actorId = cloud, command = CommandKind.ITEM, effectKind = EffectKind.HEAL,
            formula = DamageFormula.Fixed(5), targeting = TargetingShape.SELF,
        )
        val resolved = resolveAction(damaged, action, setOf(cloud)).shouldBeInstanceOf<ActionResolutionResult.Resolved>()
        resolved.outcomes.single().appliedDelta shouldBe 5
    }
})
