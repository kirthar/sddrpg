package io.github.kirthar.sddrpg.core.combatant

import io.github.kirthar.sddrpg.core.catalog.CommandKind
import io.github.kirthar.sddrpg.core.catalog.ValidatedCatalog
import io.github.kirthar.sddrpg.core.model.Affinity
import io.github.kirthar.sddrpg.core.model.AiProfileId
import io.github.kirthar.sddrpg.core.model.ClassId
import io.github.kirthar.sddrpg.core.model.CombatantId
import io.github.kirthar.sddrpg.core.model.ElementId
import io.github.kirthar.sddrpg.core.model.LimitBreakId
import io.github.kirthar.sddrpg.core.model.SkillId
import io.github.kirthar.sddrpg.core.model.StatBlock
import io.github.kirthar.sddrpg.core.model.affinityTo
import io.github.kirthar.sddrpg.core.model.statsAt

/** Battle-participation role of a combatant (spec FR-002). Independent of allegiance. */
enum class CombatantKind {
    PARTY_MEMBER,
    ENEMY,
    TEMPORARY_ALLY,
}

/** Which side a combatant fights on. Independent axis from [CombatantKind]. */
enum class Allegiance {
    PLAYER,
    OPPONENT,
}

/**
 * Origin of a combatant's action choices (spec FR-003): pure battle configuration,
 * swappable without touching any other property.
 */
sealed interface DecisionSource {
    data object Human : DecisionSource
    data class AiProfile(val id: AiProfileId) : DecisionSource
}

/**
 * What a combatant can currently do. Derived — never stored per combatant: characters
 * read it through their active class (research R1), enemies through their definition's
 * skill set and their archetype's AI profile.
 */
data class CapabilitySet(
    val skills: Set<SkillId>,
    val commands: Set<CommandKind>,
    val limitBreaks: Set<LimitBreakId>,
    val aiProfile: AiProfileId? = null,
)

/**
 * A battle participant (spec FR-001): every kind exposes this exact surface and is
 * subject to the same resolution rules regardless of who decides its actions.
 */
interface Combatant {
    val id: CombatantId
    val displayName: String
    val kind: CombatantKind
    val allegiance: Allegiance
    val stats: StatBlock
    val affinities: Map<ElementId, Affinity>
    val capabilities: CapabilitySet
    val decisionSource: DecisionSource

    /** Total: an element with no declared stance is [Affinity.NEUTRAL]. */
    fun affinityTo(element: ElementId): Affinity = affinities.affinityTo(element)
}

/**
 * Job change (SC-005, research R1): returns a copy with [classId] active — class-granted
 * capabilities and growth-derived stats follow the new class; identity, kind, allegiance,
 * affinities, and decision source are untouched. Only meaningful for combatants
 * instantiated from a character definition.
 */
fun Combatant.withActiveClass(classId: ClassId, catalog: ValidatedCatalog): Combatant {
    val character = this as? CharacterCombatant
        ?: throw IllegalStateException("withActiveClass requires a character-backed combatant, got $kind")
    val newClass = catalog.classDefinition(classId)
    return character.copy(
        activeClassId = classId,
        stats = statsAt(newClass, character.level, character.baseStats),
        capabilities = CapabilitySet(
            skills = newClass.skills,
            commands = newClass.commands,
            limitBreaks = newClass.limitBreaks,
        ),
    )
}

/** SC-004: swap who decides; every other observable property stays identical. */
fun Combatant.withDecisionSource(source: DecisionSource): Combatant = when (this) {
    is CharacterCombatant -> copy(decisionSource = source)
    is EnemyCombatant -> copy(decisionSource = source)
    else -> throw IllegalStateException("Unknown combatant implementation: ${this::class}")
}

internal data class CharacterCombatant(
    override val id: CombatantId,
    override val displayName: String,
    override val kind: CombatantKind,
    override val allegiance: Allegiance,
    override val stats: StatBlock,
    override val affinities: Map<ElementId, Affinity>,
    override val capabilities: CapabilitySet,
    override val decisionSource: DecisionSource,
    val activeClassId: ClassId,
    val level: Int,
    val baseStats: StatBlock,
) : Combatant

internal data class EnemyCombatant(
    override val id: CombatantId,
    override val displayName: String,
    override val kind: CombatantKind,
    override val allegiance: Allegiance,
    override val stats: StatBlock,
    override val affinities: Map<ElementId, Affinity>,
    override val capabilities: CapabilitySet,
    override val decisionSource: DecisionSource,
) : Combatant
