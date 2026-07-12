package io.github.kirthar.sddrpg.core.combatant

import io.github.kirthar.sddrpg.core.catalog.CharacterDefinition
import io.github.kirthar.sddrpg.core.catalog.ValidatedCatalog
import io.github.kirthar.sddrpg.core.model.AiProfileId
import io.github.kirthar.sddrpg.core.model.CharacterId
import io.github.kirthar.sddrpg.core.model.CombatantId
import io.github.kirthar.sddrpg.core.model.EnemyId
import io.github.kirthar.sddrpg.core.model.StatBlock
import io.github.kirthar.sddrpg.core.model.statsAt

/** An ordered battle lineup. Definitions are templates; these are the instances. */
data class Roster(val combatants: List<Combatant>)

/**
 * Instantiates [Combatant]s from validated definitions (research R7). Instance ids are
 * assigned deterministically from insertion order — same entries, same ids, replayable
 * battles (constitution Principle I). Multiple entries may reference one definition.
 */
class RosterBuilder(private val catalog: ValidatedCatalog) {

    private val entries = mutableListOf<(CombatantId) -> Combatant>()

    fun addPartyMember(
        id: CharacterId,
        level: Int,
        decisionSource: DecisionSource = DecisionSource.Human,
    ): RosterBuilder = apply {
        entries += characterEntry(
            catalog.character(id), level, CombatantKind.PARTY_MEMBER, Allegiance.PLAYER, decisionSource,
        )
    }

    /** [decisionSource] null means the archetype's AI profile decides (spec FR-006). */
    fun addEnemy(id: EnemyId, decisionSource: DecisionSource? = null): RosterBuilder = apply {
        val definition = catalog.enemy(id)
        val archetype = catalog.archetype(definition.archetypeId)
        val source = decisionSource ?: DecisionSource.AiProfile(archetype.aiProfile)
        entries += { combatantId ->
            EnemyCombatant(
                id = combatantId,
                displayName = definition.displayName,
                kind = CombatantKind.ENEMY,
                allegiance = Allegiance.OPPONENT,
                stats = StatBlock(definition.stats),
                affinities = definition.affinities,
                capabilities = CapabilitySet(
                    skills = definition.skills,
                    commands = emptySet(),
                    limitBreaks = emptySet(),
                    aiProfile = archetype.aiProfile,
                ),
                decisionSource = source,
            )
        }
    }

    /** Player-side, AI-driven participant (spec FR-002): same shape as a party member. */
    fun addTemporaryAlly(id: CharacterId, level: Int, aiProfile: AiProfileId): RosterBuilder = apply {
        entries += characterEntry(
            catalog.character(id), level, CombatantKind.TEMPORARY_ALLY, Allegiance.PLAYER,
            DecisionSource.AiProfile(aiProfile),
        )
    }

    fun build(): Roster = Roster(
        entries.mapIndexed { index, create -> create(CombatantId("c${index + 1}")) }
    )

    private fun characterEntry(
        definition: CharacterDefinition,
        level: Int,
        kind: CombatantKind,
        allegiance: Allegiance,
        decisionSource: DecisionSource,
    ): (CombatantId) -> Combatant {
        require(level >= 1) { "level must be >= 1, was $level" }
        val classDef = catalog.classDefinition(definition.classId)
        val baseStats = StatBlock(definition.baseStats)
        return { combatantId ->
            CharacterCombatant(
                id = combatantId,
                displayName = definition.displayName,
                kind = kind,
                allegiance = allegiance,
                stats = statsAt(classDef, level, baseStats),
                affinities = definition.affinities,
                capabilities = CapabilitySet(
                    skills = classDef.skills,
                    commands = classDef.commands,
                    limitBreaks = classDef.limitBreaks,
                ),
                decisionSource = decisionSource,
                activeClassId = definition.classId,
                level = level,
                baseStats = baseStats,
            )
        }
    }
}
