package io.github.kirthar.sddrpg.core.limitbreak

import io.github.kirthar.sddrpg.core.action.BattleState
import io.github.kirthar.sddrpg.core.action.DamageFormula
import io.github.kirthar.sddrpg.core.action.EffectKind
import io.github.kirthar.sddrpg.core.action.TargetingShape
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
import io.github.kirthar.sddrpg.core.model.CombatantId
import io.github.kirthar.sddrpg.core.model.CoreStats
import io.github.kirthar.sddrpg.core.model.EnemyId
import io.github.kirthar.sddrpg.core.model.LimitBreakId
import io.github.kirthar.sddrpg.core.model.StatId
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Shared limitbreak-package fixture: cloud (warrior, grants SKILL + SUMMON, declares
 * limit break "omnislash", has MP for summon casting) and bomb (enemy target).
 */

fun limitBreakFixtureStats(v: Int = 20): Map<StatId, Int> = CoreStats.ALL.associateWith { v }

val omnislashId = LimitBreakId("omnislash")

val limitBreakFixtureCatalog: ValidatedCatalog by lazy {
    val warrior = ClassDefinition(
        id = ClassId("warrior"), displayName = "Warrior",
        commands = setOf(CommandKind.ATTACK, CommandKind.SKILL, CommandKind.SUMMON),
        limitBreaks = setOf(omnislashId),
    )
    val civilian = ClassDefinition(
        id = ClassId("civilian"), displayName = "Civilian",
        commands = setOf(CommandKind.ATTACK),
    )
    val raw = Catalog(
        knownAiProfiles = setOf(AiProfileId("aggressive")),
        knownLimitBreaks = setOf(omnislashId),
        classes = listOf(warrior, civilian),
        archetypes = listOf(
            ArchetypeDefinition(ArchetypeId("common"), "Common", AiProfileId("aggressive"), rewardTier = 0),
        ),
        characters = listOf(
            CharacterDefinition(CharacterId("cloud"), "Cloud", ClassId("warrior"), limitBreakFixtureStats(20)),
            CharacterDefinition(CharacterId("aerith"), "Aerith", ClassId("civilian"), limitBreakFixtureStats(10)),
        ),
        enemies = listOf(
            EnemyDefinition(
                EnemyId("bomb"), "Bomb", ArchetypeId("common"),
                stats = limitBreakFixtureStats(10) + (CoreStats.HP to 200),
            ),
        ),
    )
    validateCatalog(raw).shouldBeInstanceOf<CatalogResult.Valid>().catalog
}

/** RosterBuilder assigns ids by add-order (c1, c2, c3...), not the character/enemy's own id. */
val cloudId = CombatantId("c1")
val aerithId = CombatantId("c2")
val bombId = CombatantId("c3")

/** cloud(c1, warrior/omnislash/20 MP), aerith(c2, civilian, no limit break), bomb(c3, enemy). */
fun freshLimitBreakBattleState(): BattleState = RosterBuilder(limitBreakFixtureCatalog)
    .addPartyMember(CharacterId("cloud"), level = 1)
    .addPartyMember(CharacterId("aerith"), level = 1)
    .addEnemy(EnemyId("bomb"))
    .build()
    .toBattleState()

val omnislashDefinition = LimitBreakDefinition(
    id = omnislashId,
    threshold = 50,
    effectKind = EffectKind.DAMAGE,
    formula = DamageFormula.Fixed(999),
    targeting = TargetingShape.SINGLE_ENEMY,
)

val meteorId = SummonId("meteor")
val meteorDefinition = SummonDefinition(
    id = meteorId,
    cost = 15,
    effectKind = EffectKind.DAMAGE,
    formula = DamageFormula.Fixed(999),
    targeting = TargetingShape.SINGLE_ENEMY,
)
