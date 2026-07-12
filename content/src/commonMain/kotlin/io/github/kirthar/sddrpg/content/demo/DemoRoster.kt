package io.github.kirthar.sddrpg.content.demo

import io.github.kirthar.sddrpg.content.ContentLoadResult
import io.github.kirthar.sddrpg.content.loadContentPack
import io.github.kirthar.sddrpg.core.action.BattleState
import io.github.kirthar.sddrpg.core.action.toBattleState
import io.github.kirthar.sddrpg.core.catalog.CommandKind
import io.github.kirthar.sddrpg.core.combatant.CapabilitySet
import io.github.kirthar.sddrpg.core.combatant.Combatant
import io.github.kirthar.sddrpg.core.combatant.CombatantKind
import io.github.kirthar.sddrpg.core.combatant.Roster
import io.github.kirthar.sddrpg.core.combatant.RosterBuilder
import io.github.kirthar.sddrpg.core.model.CharacterId
import io.github.kirthar.sddrpg.core.model.EnemyId

/**
 * spec 008 research.md R5: `RosterBuilder.addEnemy` (spec 001) always produces an
 * empty `capabilities.commands`, so no enemy can ever pass `resolveAction`'s command
 * gate. Fixed by wrapping via `Combatant` interface delegation -- the same pattern
 * spec 004's `EffectiveCombatant` already established -- rather than touching
 * `Roster.kt`. `Combatant` is a plain, non-sealed public interface (spec 001).
 */
private data class CommandGrantedCombatant(
    private val base: Combatant,
    private val grantedCommands: Set<CommandKind>,
) : Combatant by base {
    override val capabilities: CapabilitySet =
        base.capabilities.copy(commands = base.capabilities.commands + grantedCommands)
}

/** Always grants ATTACK; additionally grants SKILL iff the enemy has any declared skill. */
private fun Combatant.withDemoEnemyCommands(): Combatant {
    val granted = buildSet {
        add(CommandKind.ATTACK)
        if (capabilities.skills.isNotEmpty()) add(CommandKind.SKILL)
    }
    return CommandGrantedCombatant(this, granted)
}

/**
 * Builds the shipped demo battle: cloud and aerith (player-controlled) vs. the bomb
 * (enemy, command-granted per R5). Deterministic -- repeated calls return
 * structurally-equal [BattleState]s.
 */
fun buildDemoBattleState(): BattleState {
    val loaded = loadContentPack(DEMO_CONTENT_JSON)
    check(loaded is ContentLoadResult.Valid) { "shipped demo content failed to load: $loaded" }

    val roster = RosterBuilder(loaded.pack.catalog)
        .addPartyMember(CharacterId("cloud"), level = 1)
        .addPartyMember(CharacterId("aerith"), level = 1)
        .addEnemy(EnemyId("bomb"))
        .build()

    val grantedCombatants = roster.combatants.map { combatant ->
        if (combatant.kind == CombatantKind.ENEMY) combatant.withDemoEnemyCommands() else combatant
    }

    return Roster(grantedCombatants).toBattleState()
}
