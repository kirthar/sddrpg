package io.github.kirthar.sddrpg.core.battle

import io.github.kirthar.sddrpg.core.action.BattleState
import io.github.kirthar.sddrpg.core.combatant.Allegiance

/** Whether a battle is still undecided, or which side has won (spec 008 FR-005). */
sealed interface BattleOutcome {
    data object Ongoing : BattleOutcome
    data object Victory : BattleOutcome
    data object Defeat : BattleOutcome
}

/**
 * Pure, total: same [BattleState] always returns an equal result. [BattleOutcome.Defeat]
 * is checked first, so a resolution that wipes both sides at once resolves to `Defeat`
 * (spec.md's simultaneous-defeat tie-break, the conservative default).
 */
fun BattleState.outcome(): BattleOutcome {
    val playerWiped = participants.filter { it.combatant.allegiance == Allegiance.PLAYER }.all { it.isDefeated }
    val opponentWiped = participants.filter { it.combatant.allegiance == Allegiance.OPPONENT }.all { it.isDefeated }
    return when {
        playerWiped -> BattleOutcome.Defeat
        opponentWiped -> BattleOutcome.Victory
        else -> BattleOutcome.Ongoing
    }
}
