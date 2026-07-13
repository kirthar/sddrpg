package io.github.kirthar.sddrpg.demo.app

import io.github.kirthar.sddrpg.content.demo.DemoActionDefinition
import io.github.kirthar.sddrpg.core.limitbreak.SummonId
import io.github.kirthar.sddrpg.core.model.CombatantId
import io.github.kirthar.sddrpg.core.model.LimitBreakId

/** One participant as the UI displays it, snapshotted from the RAW battle state. */
data class ParticipantView(
    val id: CombatantId,
    val name: String,
    val currentHp: Int,
    val maxHp: Int,
    val isPlayerSide: Boolean,
    val isDefeated: Boolean,
)

/**
 * One selectable action button. [targets] lists only structurally-valid choices for
 * the action's targeting shape -- the resolver remains the actual gate regardless
 * (research R5).
 */
sealed interface OfferedAction {
    val targets: List<CombatantId>

    data class BasicAttack(val definition: DemoActionDefinition, override val targets: List<CombatantId>) : OfferedAction
    data class UseSkill(val definition: DemoActionDefinition, override val targets: List<CombatantId>) : OfferedAction
    data class UseLimitBreak(val limitBreakId: LimitBreakId, override val targets: List<CombatantId>) : OfferedAction
    data class UseSummon(val summonId: SummonId, override val targets: List<CombatantId>) : OfferedAction
}

/** A human-readable label for the action's button, derived, not stored. */
val OfferedAction.label: String
    get() = when (this) {
        is OfferedAction.BasicAttack -> "Attack"
        is OfferedAction.UseSkill -> definition.skillId?.value ?: "Skill"
        is OfferedAction.UseLimitBreak -> limitBreakId.value
        is OfferedAction.UseSummon -> summonId.value
    }

/** Whose move the UI is waiting on, or the terminal outcome. */
sealed interface BattlePhase {
    data class AwaitingPlayerAction(
        val actorId: CombatantId,
        val actorName: String,
        val actions: List<OfferedAction>,
    ) : BattlePhase

    data class BattleOver(val victory: Boolean) : BattlePhase
}

/** What `submit` accepts: one offered action plus the chosen target. */
data class PlayerChoice(val action: OfferedAction, val targetId: CombatantId)

/**
 * The single immutable value the UI observes (data-model.md). Replaced wholesale on
 * every change -- never mutated. [logLines] is append-only across successive states.
 */
data class BattleUiState(
    val participants: List<ParticipantView>,
    val logLines: List<String>,
    val phase: BattlePhase,
    val lastRejection: String? = null,
)
