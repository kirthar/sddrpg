package io.github.kirthar.sddrpg.core.schedule

import io.github.kirthar.sddrpg.core.action.BattleState
import io.github.kirthar.sddrpg.core.action.toBattleState
import io.github.kirthar.sddrpg.core.catalog.ArchetypeDefinition
import io.github.kirthar.sddrpg.core.catalog.Catalog
import io.github.kirthar.sddrpg.core.catalog.CatalogResult
import io.github.kirthar.sddrpg.core.catalog.CharacterDefinition
import io.github.kirthar.sddrpg.core.catalog.ClassDefinition
import io.github.kirthar.sddrpg.core.catalog.CommandKind
import io.github.kirthar.sddrpg.core.catalog.EnemyDefinition
import io.github.kirthar.sddrpg.core.catalog.ValidatedCatalog
import io.github.kirthar.sddrpg.core.catalog.validateCatalog
import io.github.kirthar.sddrpg.core.combatant.RosterBuilder
import io.github.kirthar.sddrpg.core.model.AiProfileId
import io.github.kirthar.sddrpg.core.model.ArchetypeId
import io.github.kirthar.sddrpg.core.model.CharacterId
import io.github.kirthar.sddrpg.core.model.ClassId
import io.github.kirthar.sddrpg.core.model.CoreStats
import io.github.kirthar.sddrpg.core.model.EnemyId
import io.github.kirthar.sddrpg.core.model.StatId
import io.kotest.matchers.types.shouldBeInstanceOf

/** Shared scheduler fixture: combatants with clearly distinct Speed values. */

private fun statsWithSpeed(speed: Int): Map<StatId, Int> =
    CoreStats.ALL.associateWith { 10 } + (CoreStats.SPEED to speed)

val scheduleFixtureCatalog: ValidatedCatalog by lazy {
    val warrior = ClassDefinition(
        id = ClassId("warrior"), displayName = "Warrior", commands = setOf(CommandKind.ATTACK),
    )
    val raw = Catalog(
        knownAiProfiles = setOf(AiProfileId("aggressive")),
        classes = listOf(warrior),
        archetypes = listOf(
            ArchetypeDefinition(ArchetypeId("common"), "Common", AiProfileId("aggressive"), rewardTier = 0),
        ),
        characters = listOf(
            // fast: Speed 30, slow: Speed 5
            CharacterDefinition(CharacterId("fast"), "Fast", ClassId("warrior"), statsWithSpeed(30)),
            CharacterDefinition(CharacterId("slow"), "Slow", ClassId("warrior"), statsWithSpeed(5)),
            // equal-speed pair, for tie-break and "different combatant next" scenarios
            CharacterDefinition(CharacterId("alpha"), "Alpha", ClassId("warrior"), statsWithSpeed(10)),
            CharacterDefinition(CharacterId("beta"), "Beta", ClassId("warrior"), statsWithSpeed(10)),
        ),
        enemies = listOf(
            EnemyDefinition(EnemyId("goblin"), "Goblin", ArchetypeId("common"), stats = statsWithSpeed(15)),
        ),
    )
    validateCatalog(raw).shouldBeInstanceOf<CatalogResult.Valid>().catalog
}

/** fast (Speed 30), slow (Speed 5), goblin (Speed 15, enemy). */
fun freshScheduleBattleState(): BattleState = RosterBuilder(scheduleFixtureCatalog)
    .addPartyMember(CharacterId("fast"), level = 1)
    .addPartyMember(CharacterId("slow"), level = 1)
    .addEnemy(EnemyId("goblin"))
    .build()
    .toBattleState()

/** alpha and beta, both Speed 10 — for tie-break and turn-alternation scenarios. */
fun freshEqualSpeedBattleState(): BattleState = RosterBuilder(scheduleFixtureCatalog)
    .addPartyMember(CharacterId("alpha"), level = 1)
    .addPartyMember(CharacterId("beta"), level = 1)
    .build()
    .toBattleState()
