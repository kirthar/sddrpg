package io.github.kirthar.sddrpg.core.status

import io.github.kirthar.sddrpg.core.action.DamageFormula
import io.github.kirthar.sddrpg.core.catalog.CommandKind
import io.github.kirthar.sddrpg.core.model.CombatantId
import io.github.kirthar.sddrpg.core.model.CoreStats
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** US2 acceptance scenarios 1-4: a stat-modifying effect changes resolveAction's outcomes. */
class StatModifierTest : StringSpec({

    fun idOf(state: io.github.kirthar.sddrpg.core.action.BattleState, name: String): CombatantId =
        state.participants.first { it.combatant.displayName == name }.combatant.id

    fun attackAction(actorId: CombatantId) = io.github.kirthar.sddrpg.core.action.CombatAction(
        actorId = actorId, command = CommandKind.ATTACK,
        effectKind = io.github.kirthar.sddrpg.core.action.EffectKind.DAMAGE,
        formula = DamageFormula.Physical(power = 0),
        targeting = io.github.kirthar.sddrpg.core.action.TargetingShape.SINGLE_ENEMY,
    )

    "scenario 1: an increased Defense modifier resolves to less damage than unmodified" {
        val battle = freshStatusBattleState()
        val cloud = idOf(battle, "Cloud")
        val bomb = idOf(battle, "Bomb")
        val action = attackAction(cloud)

        val baseline = io.github.kirthar.sddrpg.core.action.resolveAction(battle, action, setOf(bomb))
            .shouldBeInstanceOf<io.github.kirthar.sddrpg.core.action.ActionResolutionResult.Resolved>()

        val effects = applyStatusEffect(StatusEffectState(), bomb, StatusEffectId("defenseBuff"), statusEffectFixtureCatalog)
        val effectiveBattle = deriveEffectiveBattleState(battle, effects, statusEffectFixtureCatalog)
        val buffed = io.github.kirthar.sddrpg.core.action.resolveAction(effectiveBattle, action, setOf(bomb))
            .shouldBeInstanceOf<io.github.kirthar.sddrpg.core.action.ActionResolutionResult.Resolved>()

        buffed.outcomes.single().appliedDelta shouldBe (baseline.outcomes.single().appliedDelta + 5) // less negative = less damage
    }

    "scenario 2: a decreased Attack modifier resolves to less damage dealt (weaker attacker)" {
        val battle = freshStatusBattleState()
        val cloud = idOf(battle, "Cloud")
        val bomb = idOf(battle, "Bomb")
        val action = attackAction(cloud)

        val baseline = io.github.kirthar.sddrpg.core.action.resolveAction(battle, action, setOf(bomb))
            .shouldBeInstanceOf<io.github.kirthar.sddrpg.core.action.ActionResolutionResult.Resolved>()

        val effects = applyStatusEffect(StatusEffectState(), cloud, StatusEffectId("attackDebuff"), statusEffectFixtureCatalog)
        val effectiveBattle = deriveEffectiveBattleState(battle, effects, statusEffectFixtureCatalog)
        val debuffed = io.github.kirthar.sddrpg.core.action.resolveAction(effectiveBattle, action, setOf(bomb))
            .shouldBeInstanceOf<io.github.kirthar.sddrpg.core.action.ActionResolutionResult.Resolved>()

        debuffed.outcomes.single().appliedDelta shouldBe (baseline.outcomes.single().appliedDelta + 5) // less negative = less damage dealt
    }

    "scenario 3: the combatant's own base stats are unchanged by deriving an effective state" {
        val battle = freshStatusBattleState()
        val bomb = idOf(battle, "Bomb")
        val effects = applyStatusEffect(StatusEffectState(), bomb, StatusEffectId("defenseBuff"), statusEffectFixtureCatalog)
        deriveEffectiveBattleState(battle, effects, statusEffectFixtureCatalog)

        // The original battle passed in is untouched.
        battle.find(bomb)!!.combatant.stats[CoreStats.DEFENSE] shouldBe
            statusFixtureCatalog.enemy(io.github.kirthar.sddrpg.core.model.EnemyId("bomb")).stats.getValue(CoreStats.DEFENSE)
    }

    "scenario 4: two different modifiers on the same stat combine additively" {
        val battle = freshStatusBattleState()
        val bomb = idOf(battle, "Bomb")
        var effects = applyStatusEffect(StatusEffectState(), bomb, StatusEffectId("defenseBuff"), statusEffectFixtureCatalog) // +5
        val secondBuff = StatusEffectDefinition(
            StatusEffectId("defenseBuff2"), "Defense Buff II",
            EffectKind.StatModifier(CoreStats.DEFENSE, 3), duration = 3,
        )
        val extendedCatalog = StatusEffectCatalog(statusEffectFixtureCatalog.effects + secondBuff)
        effects = applyStatusEffect(effects, bomb, StatusEffectId("defenseBuff2"), extendedCatalog) // +3 more

        val effectiveBattle = deriveEffectiveBattleState(battle, effects, extendedCatalog)
        val originalDefense = battle.find(bomb)!!.combatant.stats[CoreStats.DEFENSE]
        effectiveBattle.find(bomb)!!.combatant.stats[CoreStats.DEFENSE] shouldBe originalDefense + 8
    }
})
