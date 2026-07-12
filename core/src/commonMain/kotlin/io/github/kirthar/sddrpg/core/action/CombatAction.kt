package io.github.kirthar.sddrpg.core.action

import io.github.kirthar.sddrpg.core.catalog.CommandKind
import io.github.kirthar.sddrpg.core.model.CombatantId
import io.github.kirthar.sddrpg.core.model.ElementId
import io.github.kirthar.sddrpg.core.model.SkillId

/**
 * A combat action submission (Command, spec FR-001): plain data, not an object with an
 * `execute()` method — resolution is a pure function over this data (research R2), so
 * an action can be queued, logged, or replayed without smuggling side effects into the
 * core module.
 */
data class CombatAction(
    val actorId: CombatantId,
    val command: CommandKind,
    val skillId: SkillId? = null,
    val effectKind: EffectKind,
    val element: ElementId? = null,
    val formula: DamageFormula,
    val targeting: TargetingShape,
)
