package io.github.kirthar.sddrpg.core.status

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

/** Shared status-effect fixture: a validated catalog + fresh two-party/one-enemy battle state. */

fun statusFixtureStats(v: Int = 20): Map<StatId, Int> = CoreStats.ALL.associateWith { v }

val statusFixtureCatalog: ValidatedCatalog by lazy {
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
            CharacterDefinition(CharacterId("cloud"), "Cloud", ClassId("warrior"), statusFixtureStats(20)),
            CharacterDefinition(CharacterId("aerith"), "Aerith", ClassId("warrior"), statusFixtureStats(10)),
        ),
        enemies = listOf(
            EnemyDefinition(EnemyId("bomb"), "Bomb", ArchetypeId("common"), stats = statusFixtureStats(15)),
        ),
    )
    validateCatalog(raw).shouldBeInstanceOf<CatalogResult.Valid>().catalog
}

/** cloud, aerith (party), bomb (enemy) -- all core stats 20/10/15 respectively. */
fun freshStatusBattleState(): BattleState = RosterBuilder(statusFixtureCatalog)
    .addPartyMember(CharacterId("cloud"), level = 1)
    .addPartyMember(CharacterId("aerith"), level = 1)
    .addEnemy(EnemyId("bomb"))
    .build()
    .toBattleState()

/** poison (DamageOverTime 5/tick, 3 turns), defenseBuff (StatModifier DEFENSE +5, 3 turns),
 *  attackDebuff (StatModifier ATTACK -5, 2 turns), stun (Incapacitate, 3 turns),
 *  speedBoost (StatModifier SPEED +50, 3 turns). */
val statusEffectFixtureCatalog: StatusEffectCatalog = StatusEffectCatalog(
    listOf(
        StatusEffectDefinition(StatusEffectId("poison"), "Poison", EffectKind.DamageOverTime(5), duration = 3),
        StatusEffectDefinition(
            StatusEffectId("defenseBuff"), "Defense Buff",
            EffectKind.StatModifier(CoreStats.DEFENSE, 5), duration = 3,
        ),
        StatusEffectDefinition(
            StatusEffectId("attackDebuff"), "Attack Debuff",
            EffectKind.StatModifier(CoreStats.ATTACK, -5), duration = 2,
        ),
        StatusEffectDefinition(StatusEffectId("stun"), "Stun", EffectKind.Incapacitate, duration = 3),
        StatusEffectDefinition(
            StatusEffectId("speedBoost"), "Speed Boost",
            EffectKind.StatModifier(CoreStats.SPEED, 50), duration = 3,
        ),
    )
)
