package io.github.kirthar.sddrpg.core.action

import io.github.kirthar.sddrpg.core.catalog.CommandKind
import io.github.kirthar.sddrpg.core.model.CombatantId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** US3 acceptance scenarios 1-6: target-id selections validated against TargetingShape. */
class TargetingShapeTest : StringSpec({

    fun idOf(state: BattleState, name: String): CombatantId =
        state.participants.first { it.combatant.displayName == name }.combatant.id

    fun withHealth(state: BattleState, id: CombatantId, current: Int): BattleState = BattleState(
        state.participants.map {
            if (it.combatant.id == id) it.copy(health = it.health.copy(current = current)) else it
        }
    )

    fun action(shape: TargetingShape, command: CommandKind = CommandKind.ATTACK) = CombatAction(
        actorId = CombatantId("placeholder"), command = command, effectKind = EffectKind.DAMAGE,
        formula = DamageFormula.Physical(0), targeting = shape,
    )

    "scenario 1: single-target shape resolves against exactly one valid target" {
        val state = freshBattleState()
        val cloud = idOf(state, "Cloud")
        val bomb = idOf(state, "Bomb")
        val act = action(TargetingShape.SINGLE_ENEMY).copy(actorId = cloud)
        resolveAction(state, act, setOf(bomb)).shouldBeInstanceOf<ActionResolutionResult.Resolved>()
    }

    "scenario 2: single-target shape rejects zero or multiple targets" {
        val state = freshBattleState()
        val cloud = idOf(state, "Cloud")
        val bomb = idOf(state, "Bomb")
        val flan = idOf(state, "Flan")
        val act = action(TargetingShape.SINGLE_ENEMY).copy(actorId = cloud)
        val before = state
        resolveAction(state, act, emptySet())
            .shouldBeInstanceOf<ActionResolutionResult.Rejected>().error
            .shouldBeInstanceOf<ActionError.TargetShapeMismatch>()
        resolveAction(state, act, setOf(bomb, flan))
            .shouldBeInstanceOf<ActionResolutionResult.Rejected>().error
            .shouldBeInstanceOf<ActionError.TargetShapeMismatch>()
        state shouldBe before
    }

    "scenario 3: ALL_ENEMIES fans out to every opposing combatant with one outcome each" {
        val state = freshBattleState()
        val cloud = idOf(state, "Cloud")
        val act = action(TargetingShape.ALL_ENEMIES).copy(actorId = cloud)
        val resolved = resolveAction(state, act, emptySet()).shouldBeInstanceOf<ActionResolutionResult.Resolved>()
        resolved.outcomes shouldHaveSize 3 // bomb, flan, pudding
        val enemyIds = state.participants.filter { it.combatant.id != cloud && it.combatant.displayName != "Aerith" }
            .map { it.combatant.id }
        resolved.outcomes.map { it.targetId }.toSet() shouldBe enemyIds.toSet()
    }

    "scenario 4: SELF rejects any non-actor target" {
        val state = freshBattleState()
        val cloud = idOf(state, "Cloud")
        val bomb = idOf(state, "Bomb")
        val act = action(TargetingShape.SELF, command = CommandKind.DEFEND).copy(actorId = cloud)
        val before = state
        resolveAction(state, act, setOf(bomb))
            .shouldBeInstanceOf<ActionResolutionResult.Rejected>().error
            .shouldBeInstanceOf<ActionError.TargetShapeMismatch>()
        state shouldBe before
        resolveAction(state, act, setOf(cloud)).shouldBeInstanceOf<ActionResolutionResult.Resolved>()
    }

    "scenario 5: an unknown target reference is rejected, naming it" {
        val state = freshBattleState()
        val cloud = idOf(state, "Cloud")
        val ghost = CombatantId("does-not-exist")
        val act = action(TargetingShape.SINGLE_ENEMY).copy(actorId = cloud)
        val before = state
        val error = resolveAction(state, act, setOf(ghost))
            .shouldBeInstanceOf<ActionResolutionResult.Rejected>().error
        error.shouldBeInstanceOf<ActionError.UnknownTarget>()
        (error as ActionError.UnknownTarget).targetId shouldBe ghost
        state shouldBe before
    }

    "scenario 6a: an already-defeated combatant is excluded from an ALL_* shape without rejection" {
        val fresh = freshBattleState()
        val cloud = idOf(fresh, "Cloud")
        val bomb = idOf(fresh, "Bomb")
        val state = withHealth(fresh, bomb, current = 0)
        val act = action(TargetingShape.ALL_ENEMIES).copy(actorId = cloud)
        val resolved = resolveAction(state, act, emptySet()).shouldBeInstanceOf<ActionResolutionResult.Resolved>()
        resolved.outcomes shouldHaveSize 2 // flan, pudding — bomb excluded
        resolved.outcomes.map { it.targetId } shouldBe resolved.outcomes.map { it.targetId }.filter { it != bomb }
    }

    "scenario 6b: an already-defeated combatant explicitly selected as a SINGLE_* target is rejected" {
        val fresh = freshBattleState()
        val cloud = idOf(fresh, "Cloud")
        val bomb = idOf(fresh, "Bomb")
        val state = withHealth(fresh, bomb, current = 0)
        val act = action(TargetingShape.SINGLE_ENEMY).copy(actorId = cloud)
        val before = state
        resolveAction(state, act, setOf(bomb))
            .shouldBeInstanceOf<ActionResolutionResult.Rejected>().error
            .shouldBeInstanceOf<ActionError.TargetShapeMismatch>()
        state shouldBe before
    }

    "an ALL_* selection with zero eligible (all defeated) combatants is accepted with zero outcomes" {
        val fresh = freshBattleState()
        val cloud = idOf(fresh, "Cloud")
        var state = fresh
        for (name in listOf("Bomb", "Flan", "Pudding")) {
            state = withHealth(state, idOf(state, name), current = 0)
        }
        val act = action(TargetingShape.ALL_ENEMIES).copy(actorId = cloud)
        val resolved = resolveAction(state, act, emptySet()).shouldBeInstanceOf<ActionResolutionResult.Resolved>()
        resolved.outcomes shouldHaveSize 0
    }

    "ALL_ALLIES includes the actor itself" {
        val state = freshBattleState()
        val aerith = idOf(state, "Aerith")
        val act = CombatAction(
            actorId = aerith, command = CommandKind.MAGIC, effectKind = EffectKind.HEAL,
            formula = DamageFormula.Fixed(1), targeting = TargetingShape.ALL_ALLIES,
        )
        val resolved = resolveAction(state, act, emptySet()).shouldBeInstanceOf<ActionResolutionResult.Resolved>()
        resolved.outcomes shouldHaveSize 2 // cloud, aerith
        resolved.outcomes.map { it.targetId }.toSet() shouldBe setOf(idOf(state, "Cloud"), aerith)
    }
})
