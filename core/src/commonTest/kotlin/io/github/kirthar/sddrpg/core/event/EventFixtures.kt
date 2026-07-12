package io.github.kirthar.sddrpg.core.event

import io.github.kirthar.sddrpg.core.action.BattleState
import io.github.kirthar.sddrpg.core.action.CombatAction
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
import io.github.kirthar.sddrpg.core.model.SkillId
import io.github.kirthar.sddrpg.core.model.StatId
import io.github.kirthar.sddrpg.core.status.EffectKind as StatusEffectKind
import io.github.kirthar.sddrpg.core.status.StatusEffectCatalog
import io.github.kirthar.sddrpg.core.status.StatusEffectDefinition
import io.github.kirthar.sddrpg.core.status.StatusEffectId
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Shared event-package fixture: cloud (warrior, skill "cleave") and aerith (mage,
 * skill "fira") are the two-skill "Warrior + Mage" synergy pair spec.md's own example
 * describes; bomb is the shared target both act against.
 */

fun eventFixtureStats(v: Int = 20): Map<StatId, Int> = CoreStats.ALL.associateWith { v }

val eventFixtureCatalog: ValidatedCatalog by lazy {
    val warrior = ClassDefinition(
        id = ClassId("warrior"), displayName = "Warrior",
        skills = setOf(SkillId("cleave")),
        commands = setOf(CommandKind.ATTACK, CommandKind.SKILL),
    )
    val mage = ClassDefinition(
        id = ClassId("mage"), displayName = "Mage",
        skills = setOf(SkillId("fira")),
        commands = setOf(CommandKind.ATTACK, CommandKind.SKILL, CommandKind.MAGIC),
    )
    val raw = Catalog(
        knownSkills = setOf(SkillId("cleave"), SkillId("fira")),
        knownAiProfiles = setOf(AiProfileId("aggressive")),
        classes = listOf(warrior, mage),
        archetypes = listOf(
            ArchetypeDefinition(ArchetypeId("common"), "Common", AiProfileId("aggressive"), rewardTier = 0),
        ),
        characters = listOf(
            CharacterDefinition(CharacterId("cloud"), "Cloud", ClassId("warrior"), eventFixtureStats(20)),
            CharacterDefinition(CharacterId("aerith"), "Aerith", ClassId("mage"), eventFixtureStats(10)),
        ),
        enemies = listOf(
            EnemyDefinition(
                EnemyId("bomb"), "Bomb", ArchetypeId("common"),
                stats = eventFixtureStats(10) + (CoreStats.HP to 200),
            ),
        ),
    )
    validateCatalog(raw).shouldBeInstanceOf<CatalogResult.Valid>().catalog
}

/** RosterBuilder assigns ids by add-order (c1, c2, c3...), not the character/enemy's own id. */
val cloudId = CombatantId("c1")
val aerithId = CombatantId("c2")
val bombId = CombatantId("c3")

/** cloud (warrior), aerith (mage), bomb (enemy) -- all core stats 20/10/50 respectively. */
fun freshEventBattleState(): BattleState = RosterBuilder(eventFixtureCatalog)
    .addPartyMember(CharacterId("cloud"), level = 1)
    .addPartyMember(CharacterId("aerith"), level = 1)
    .addEnemy(EnemyId("bomb"))
    .build()
    .toBattleState()

/** cloud's signature skill action: a physical Cleave against a single enemy. */
val cleaveAction = CombatAction(
    actorId = cloudId,
    command = CommandKind.SKILL,
    skillId = SkillId("cleave"),
    effectKind = EffectKind.DAMAGE,
    formula = DamageFormula.Physical(power = 5),
    targeting = TargetingShape.SINGLE_ENEMY,
)

/** aerith's signature skill action: a magical Fira against a single enemy. */
val firaAction = CombatAction(
    actorId = aerithId,
    command = CommandKind.SKILL,
    skillId = SkillId("fira"),
    effectKind = EffectKind.DAMAGE,
    formula = DamageFormula.Magical(power = 5),
    targeting = TargetingShape.SINGLE_ENEMY,
)

/** poison (DamageOverTime 5/tick, 3 turns) -- reused across event-derivation tests. */
val eventFixtureStatusCatalog: StatusEffectCatalog = StatusEffectCatalog(
    listOf(
        StatusEffectDefinition(StatusEffectId("poison"), "Poison", StatusEffectKind.DamageOverTime(5), duration = 3),
    )
)

/**
 * Extended fixture for synergy tests: adds tifa (a dual-class combatant with BOTH
 * "cleave" and "fira" -- needed to test FR-008's "same combatant can't satisfy both
 * roles") and a second enemy target flan (needed for target-mismatch scenarios).
 */
val synergyFixtureCatalog: ValidatedCatalog by lazy {
    val warrior = ClassDefinition(
        id = ClassId("warrior"), displayName = "Warrior",
        skills = setOf(SkillId("cleave")),
        commands = setOf(CommandKind.ATTACK, CommandKind.SKILL),
    )
    val mage = ClassDefinition(
        id = ClassId("mage"), displayName = "Mage",
        skills = setOf(SkillId("fira")),
        commands = setOf(CommandKind.ATTACK, CommandKind.SKILL, CommandKind.MAGIC),
    )
    val dual = ClassDefinition(
        id = ClassId("dual"), displayName = "Dual",
        skills = setOf(SkillId("cleave"), SkillId("fira")),
        commands = setOf(CommandKind.ATTACK, CommandKind.SKILL, CommandKind.MAGIC),
    )
    val raw = Catalog(
        knownSkills = setOf(SkillId("cleave"), SkillId("fira")),
        knownAiProfiles = setOf(AiProfileId("aggressive")),
        classes = listOf(warrior, mage, dual),
        archetypes = listOf(
            ArchetypeDefinition(ArchetypeId("common"), "Common", AiProfileId("aggressive"), rewardTier = 0),
        ),
        characters = listOf(
            CharacterDefinition(CharacterId("cloud"), "Cloud", ClassId("warrior"), eventFixtureStats(20)),
            CharacterDefinition(CharacterId("aerith"), "Aerith", ClassId("mage"), eventFixtureStats(10)),
            CharacterDefinition(CharacterId("tifa"), "Tifa", ClassId("dual"), eventFixtureStats(20)),
        ),
        enemies = listOf(
            EnemyDefinition(EnemyId("bomb"), "Bomb", ArchetypeId("common"), stats = eventFixtureStats(10) + (CoreStats.HP to 200)),
            EnemyDefinition(EnemyId("flan"), "Flan", ArchetypeId("common"), stats = eventFixtureStats(10) + (CoreStats.HP to 200)),
        ),
    )
    validateCatalog(raw).shouldBeInstanceOf<CatalogResult.Valid>().catalog
}

val tifaId = CombatantId("c3")
val synergyBombId = CombatantId("c4")
val flanId = CombatantId("c5")

/** cloud(c1), aerith(c2), tifa(c3, dual cleave+fira), bomb(c4), flan(c5). */
fun freshSynergyBattleState(): BattleState = RosterBuilder(synergyFixtureCatalog)
    .addPartyMember(CharacterId("cloud"), level = 1)
    .addPartyMember(CharacterId("aerith"), level = 1)
    .addPartyMember(CharacterId("tifa"), level = 1)
    .addEnemy(EnemyId("bomb"))
    .addEnemy(EnemyId("flan"))
    .build()
    .toBattleState()

fun cleaveOn(actorId: CombatantId) = CombatAction(actorId, CommandKind.SKILL, SkillId("cleave"), EffectKind.DAMAGE, formula = DamageFormula.Physical(5), targeting = TargetingShape.SINGLE_ENEMY)
fun firaOn(actorId: CombatantId) = CombatAction(actorId, CommandKind.SKILL, SkillId("fira"), EffectKind.DAMAGE, formula = DamageFormula.Magical(5), targeting = TargetingShape.SINGLE_ENEMY)

val warriorMageSynergy = SynergyDefinition(
    id = SynergyId("warriorMage"),
    firstSkillId = SkillId("cleave"),
    secondSkillId = SkillId("fira"),
    window = 2,
    bonus = SynergyBonus.BonusDamage(10),
)
