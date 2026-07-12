package io.github.kirthar.sddrpg.core.action

import io.github.kirthar.sddrpg.core.catalog.CommandKind
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll

/** SC-003: resolveAction is pure — same (state, action, targetIds) always returns an equal result. */
class ActionResolutionDeterminismTest : StringSpec({

    "property: resolving the same action twice against the same state is byte-for-byte identical" {
        checkAll(
            Arb.int(0..20),
            Arb.element(listOf(EffectKind.DAMAGE, EffectKind.HEAL)),
            Arb.element(listOf("Bomb", "Flan", "Pudding")),
            Arb.element(listOf(true, false)),
        ) { power, effect, targetName, declareElement ->
            val state = freshBattleState()
            val cloud = state.participants.first { it.combatant.displayName == "Cloud" }.combatant.id
            val target = state.participants.first { it.combatant.displayName == targetName }.combatant.id
            val action = CombatAction(
                actorId = cloud, command = CommandKind.ATTACK, effectKind = effect,
                element = if (declareElement) fireElement else null,
                formula = DamageFormula.Physical(power), targeting = TargetingShape.SINGLE_ENEMY,
            )
            val first = resolveAction(state, action, setOf(target))
            val second = resolveAction(state, action, setOf(target))
            first shouldBe second
        }
    }
})
