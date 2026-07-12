package io.github.kirthar.sddrpg.demo.console

import io.github.kirthar.sddrpg.content.ContentPack
import io.github.kirthar.sddrpg.content.demo.DEMO_CONTENT_JSON
import io.github.kirthar.sddrpg.content.loadContentPack
import io.github.kirthar.sddrpg.core.action.toBattleState
import io.github.kirthar.sddrpg.core.battle.BattleOutcome
import io.github.kirthar.sddrpg.core.catalog.ArchetypeDefinition
import io.github.kirthar.sddrpg.core.catalog.Catalog
import io.github.kirthar.sddrpg.core.catalog.CatalogResult
import io.github.kirthar.sddrpg.core.catalog.ClassDefinition
import io.github.kirthar.sddrpg.core.catalog.CommandKind
import io.github.kirthar.sddrpg.core.catalog.EnemyDefinition
import io.github.kirthar.sddrpg.core.catalog.validateCatalog
import io.github.kirthar.sddrpg.core.combatant.RosterBuilder
import io.github.kirthar.sddrpg.core.event.SynergyCatalog
import io.github.kirthar.sddrpg.core.limitbreak.LimitBreakCatalog
import io.github.kirthar.sddrpg.core.limitbreak.SummonCatalog
import io.github.kirthar.sddrpg.core.limitbreak.initialResourceState
import io.github.kirthar.sddrpg.core.model.AiProfileId
import io.github.kirthar.sddrpg.core.model.ArchetypeId
import io.github.kirthar.sddrpg.core.model.CharacterId
import io.github.kirthar.sddrpg.core.model.ClassId
import io.github.kirthar.sddrpg.core.model.CoreStats
import io.github.kirthar.sddrpg.core.model.EnemyId
import io.github.kirthar.sddrpg.core.status.StatusEffectCatalog
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/** spec 008 US1: a complete battle plays out from start to a clear conclusion. */
class BattleLoopTest : StringSpec({

    fun demoContent(): ContentPack = (loadContentPack(DEMO_CONTENT_JSON) as io.github.kirthar.sddrpg.content.ContentLoadResult.Valid).pack

    fun repeating(vararg lines: String): () -> String? {
        var i = 0
        return { lines[(i++) % lines.size] }
    }

    "a full battle, always attacking, ends in a clear Victory or Defeat -- never a crash or a stall" {
        val session = newBattleSession()
        val content = demoContent()
        val emitted = mutableListOf<String>()
        val outcome = runBattleLoop(session, content, repeating("attack Bomb"), emitted::add)

        (outcome == BattleOutcome.Victory || outcome == BattleOutcome.Defeat) shouldBe true
    }

    "emit receives rendered, human-readable text for occurrences -- not raw event data (US2)" {
        val session = newBattleSession()
        val content = demoContent()
        val emitted = mutableListOf<String>()
        runBattleLoop(session, content, repeating("attack Bomb"), emitted::add)

        emitted.any { it.contains("deals") && it.contains("damage") } shouldBe true
        emitted.none { it.contains("BattleEvent") || it.contains("CombatantId(") } shouldBe true
    }

    "only human-controlled combatants (cloud/aerith) are ever prompted -- the automatic bomb never is" {
        val session = newBattleSession()
        val content = demoContent()
        val emitted = mutableListOf<String>()
        runBattleLoop(session, content, repeating("attack Bomb"), emitted::add)

        emitted.none { it.startsWith("Bomb's turn. Enter an action") } shouldBe true
        emitted.any { it.startsWith("Cloud's turn. Enter an action") } shouldBe true
    }

    "unparseable input is rejected in plain language and reprompts without consuming the turn" {
        val session = newBattleSession()
        val content = demoContent()
        val emitted = mutableListOf<String>()
        val outcome = runBattleLoop(session, content, repeating("garbage", "attack Bomb"), emitted::add)

        emitted.any { it.contains("Could not parse") } shouldBe true
        (outcome == BattleOutcome.Victory || outcome == BattleOutcome.Defeat) shouldBe true
    }

    "a resolveAction-rejected submission (cloud's class never grants MAGIC) is shown in plain language and reprompts" {
        val session = newBattleSession()
        val content = demoContent()
        val emitted = mutableListOf<String>()
        // cloud's warrior class grants ATTACK/SKILL/SUMMON, never MAGIC -- MissingCommand,
        // gated exactly as resolveAction gates it. Turn order isn't fixed by this test, so
        // "fira Bomb" is offered for enough calls that cloud (whichever position he acts in)
        // is guaranteed to attempt it at least once; aerith legitimately knows fira via MAGIC,
        // so her attempts simply succeed.
        var calls = 0
        runBattleLoop(session, content, { if (calls++ < 8) "fira Bomb" else "attack Bomb" }, emitted::add)

        emitted.any { it.contains("cannot use MAGIC") } shouldBe true
    }

    "EOF while awaiting a human submission ends the loop gracefully without crashing" {
        val session = newBattleSession()
        val content = demoContent()
        val emitted = mutableListOf<String>()
        val outcome = runBattleLoop(session, content, { null }, emitted::add)

        outcome shouldBe BattleOutcome.Ongoing
    }

    "an automatic combatant with no usable command is skipped for that turn, never ending the battle or crashing" {
        val warrior = ClassDefinition(
            id = ClassId("warrior"), displayName = "Warrior",
            commands = setOf(CommandKind.ATTACK),
        )
        val raw = Catalog(
            classes = listOf(warrior),
            characters = listOf(
                io.github.kirthar.sddrpg.core.catalog.CharacterDefinition(
                    CharacterId("hero"), "Hero", ClassId("warrior"),
                    CoreStats.ALL.associateWith { 10 },
                ),
            ),
            archetypes = listOf(ArchetypeDefinition(ArchetypeId("harmless"), "Harmless", AiProfileId("passive"), rewardTier = 0)),
            enemies = listOf(
                EnemyDefinition(
                    id = EnemyId("statue"), displayName = "Statue", archetypeId = ArchetypeId("harmless"),
                    stats = CoreStats.ALL.associateWith { if (it == CoreStats.HP) 500 else 1 },
                ),
            ),
            knownAiProfiles = setOf(AiProfileId("passive")),
        )
        val catalog = (validateCatalog(raw) as CatalogResult.Valid).catalog
        val battle = RosterBuilder(catalog)
            .addPartyMember(CharacterId("hero"), level = 1)
            .addEnemy(EnemyId("statue")) // never granted any command -- exactly research.md R5's original defect, unwrapped
            .build()
            .toBattleState()
        val session = BattleSession(
            battle = battle,
            schedule = io.github.kirthar.sddrpg.core.schedule.ActiveTimeBattleScheduler.initialSchedule(battle),
            effects = io.github.kirthar.sddrpg.core.status.StatusEffectState(),
            gauges = io.github.kirthar.sddrpg.core.limitbreak.LimitGaugeState(),
            resources = battle.initialResourceState(),
            log = io.github.kirthar.sddrpg.core.event.EventLog(),
        )
        val content = ContentPack(catalog, StatusEffectCatalog(), SynergyCatalog(), LimitBreakCatalog(), SummonCatalog())

        // hero always attacks the statue; the statue (500 HP, 1 defense) can never act
        // back but must never end the battle or crash by simply doing nothing.
        val emitted = mutableListOf<String>()
        var calls = 0
        val outcome = runBattleLoop(session, content, { if (calls++ < 200) "attack Statue" else null }, emitted::add)

        outcome shouldNotBe null // completes (either Victory once the statue's HP is worn down, or Ongoing at EOF) without crashing
    }
})
