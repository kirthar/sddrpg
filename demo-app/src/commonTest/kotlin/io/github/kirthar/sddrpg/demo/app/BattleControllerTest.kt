package io.github.kirthar.sddrpg.demo.app

import io.github.kirthar.sddrpg.content.ContentLoadResult
import io.github.kirthar.sddrpg.content.ContentPack
import io.github.kirthar.sddrpg.content.demo.DEMO_CONTENT_JSON
import io.github.kirthar.sddrpg.content.loadContentPack
import io.github.kirthar.sddrpg.core.combatant.DecisionSource
import io.github.kirthar.sddrpg.core.limitbreak.LimitGaugeState
import io.github.kirthar.sddrpg.core.limitbreak.ResourceState
import io.github.kirthar.sddrpg.core.model.CombatantId
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** spec 009 US1/US2: the event-driven controller drives the whole battle with no UI. */
class BattleControllerTest : StringSpec({

    fun demoContent(): ContentPack = (loadContentPack(DEMO_CONTENT_JSON) as ContentLoadResult.Valid).pack

    fun controller(session: DemoSession = newDemoSession()): BattleController =
        BattleController(demoContent(), session)

    fun BattleController.awaiting(): BattlePhase.AwaitingPlayerAction =
        uiState.phase.shouldBeInstanceOf<BattlePhase.AwaitingPlayerAction>()

    fun BattleController.submitFirstValid(): Unit {
        val phase = awaiting()
        val action = phase.actions.first { it.targets.isNotEmpty() }
        submit(PlayerChoice(action, action.targets.first()))
    }

    // --- T006: construction & phases ---

    "after construction the phase awaits a human-controlled actor, with leading auto-turn events already logged" {
        val c = controller()
        val phase = c.awaiting()
        val battle = io.github.kirthar.sddrpg.content.demo.buildDemoBattleState()
        val actor = battle.find(phase.actorId).shouldNotBeNull()
        actor.combatant.decisionSource shouldBe DecisionSource.Human
        c.uiState.logLines.shouldNotBeEmpty() // at least the actor's own TurnGranted line
    }

    "participants snapshots match the demo battle's raw starting state" {
        val c = controller()
        val battle = io.github.kirthar.sddrpg.content.demo.buildDemoBattleState()
        c.uiState.participants.map { it.name }.toSet() shouldBe setOf("Cloud", "Aerith", "Bomb")
        for (view in c.uiState.participants) {
            val real = battle.find(view.id).shouldNotBeNull()
            view.maxHp shouldBe real.health.maximum
            view.currentHp shouldBe real.health.current // construction auto-turns may not damage anyone before the first human turn... asserted via log agreement below instead when they do
        }
    }

    "a valid submission resolves, appends to the log, and advances to the next human turn or BattleOver" {
        val c = controller()
        val before = c.uiState.logLines.size
        c.submitFirstValid()
        c.uiState.logLines.size shouldNotBe before
        (c.uiState.phase is BattlePhase.AwaitingPlayerAction || c.uiState.phase is BattlePhase.BattleOver).shouldBeTrue()
    }

    "the battle reaches BattleOver by repeatedly submitting valid actions, and submit is then a no-op" {
        val c = controller()
        var guard = 0
        while (c.uiState.phase is BattlePhase.AwaitingPlayerAction && guard++ < 200) {
            c.submitFirstValid()
        }
        val over = c.uiState.phase.shouldBeInstanceOf<BattlePhase.BattleOver>()

        val frozen = c.uiState
        val someAction = OfferedAction.BasicAttack(
            io.github.kirthar.sddrpg.content.demo.DEMO_ACTIONS.first(),
            listOf(CombatantId("c3")),
        )
        c.submit(PlayerChoice(someAction, CombatantId("c3")))
        c.uiState shouldBe frozen
        over.victory.shouldBeInstanceOf<Boolean>()
    }

    // --- T007: gating ---

    "a structurally invalid choice sets lastRejection and changes nothing else -- the turn is never consumed" {
        val c = controller()
        val phase = c.awaiting()
        val logBefore = c.uiState.logLines
        val action = phase.actions.first { it.targets.isNotEmpty() }
        // Target the actor itself with a SINGLE_ENEMY-shaped action: structurally invalid.
        c.submit(PlayerChoice(action, phase.actorId))

        c.uiState.lastRejection.shouldNotBeNull()
        c.awaiting().actorId shouldBe phase.actorId
        c.uiState.logLines shouldBe logBefore
    }

    "the next valid submission after a rejection succeeds and clears lastRejection" {
        val c = controller()
        val phase = c.awaiting()
        val action = phase.actions.first { it.targets.isNotEmpty() }
        c.submit(PlayerChoice(action, phase.actorId)) // rejected
        c.uiState.lastRejection.shouldNotBeNull()

        c.submitFirstValid()
        c.uiState.lastRejection shouldBe null
    }

    "limit break is NOT offered while the gauge is below threshold" {
        val c = controller()
        // fresh battle: no damage taken yet, gauge empty
        val phase = c.awaiting()
        phase.actions.none { it is OfferedAction.UseLimitBreak } shouldBe true
    }

    "limit break IS offered when the actor's gauge is at threshold" {
        // cloud is "c1" (RosterBuilder insertion order); omnislash threshold is 50.
        val session = newDemoSession().copy(gauges = LimitGaugeState(mapOf(CombatantId("c1") to 50)))
        val c = controller(session)
        var guard = 0
        var sawCloudWithLimitBreak = false
        while (c.uiState.phase is BattlePhase.AwaitingPlayerAction && guard++ < 50 && !sawCloudWithLimitBreak) {
            val phase = c.awaiting()
            if (phase.actorName == "Cloud") {
                sawCloudWithLimitBreak = phase.actions.any { it is OfferedAction.UseLimitBreak }
                if (sawCloudWithLimitBreak) break
            }
            c.submitFirstValid()
        }
        sawCloudWithLimitBreak shouldBe true
    }

    "summon is NOT offered when the actor's MP is below the cost" {
        // meteor costs 15; drain cloud's MP.
        val session = newDemoSession().let { s ->
            s.copy(resources = ResourceState(s.resources.current.mapValues { 0 }))
        }
        val c = controller(session)
        val phase = c.awaiting()
        phase.actions.none { it is OfferedAction.UseSummon } shouldBe true
    }

    "offered targets never include defeated combatants for single-target shapes" {
        val c = controller()
        var guard = 0
        while (c.uiState.phase is BattlePhase.AwaitingPlayerAction && guard++ < 200) {
            val phase = c.awaiting()
            for (action in phase.actions) {
                for (target in action.targets) {
                    c.uiState.participants.first { it.id == target }.isDefeated shouldBe false
                }
            }
            c.submitFirstValid()
        }
    }

    // --- T010 (US2): log/display consistency ---

    "every submission strictly appends to logLines" {
        val c = controller()
        var previous = c.uiState.logLines
        var guard = 0
        while (c.uiState.phase is BattlePhase.AwaitingPlayerAction && guard++ < 200) {
            c.submitFirstValid()
            val current = c.uiState.logLines
            current.size shouldNotBe 0
            (current.size >= previous.size).shouldBeTrue()
            current.subList(0, previous.size) shouldBe previous
            previous = current
        }
    }

    "participants health always agrees with the last logged health-changing occurrence" {
        // Each health-changing line names its TARGET and that target's resulting HP:
        //   "A deals N damage to T (now H HP)." / "T takes N damage (now H HP)."
        //   "A heals T for N (now H HP)."       / "T recovers N HP (now H HP)."
        val patterns = listOf(
            Regex("""^.+ deals \d+ damage to (.+) \(now (\d+) HP\)\.$"""),
            Regex("""^(.+) takes \d+ damage \(now (\d+) HP\)\.$"""),
            Regex("""^.+ heals (.+) for \d+ \(now (\d+) HP\)\.$"""),
            Regex("""^(.+) recovers \d+ HP \(now (\d+) HP\)\.$"""),
        )

        fun targetAndHp(line: String): Pair<String, Int>? {
            for (pattern in patterns) {
                val match = pattern.matchEntire(line) ?: continue
                return match.groupValues[1] to match.groupValues[2].toInt()
            }
            return null
        }

        val c = controller()
        var guard = 0
        while (c.uiState.phase is BattlePhase.AwaitingPlayerAction && guard++ < 200) {
            c.submitFirstValid()
            val lastHpByName = c.uiState.logLines.mapNotNull(::targetAndHp).toMap() // later entries win
            for (view in c.uiState.participants) {
                lastHpByName[view.name]?.let { loggedHp -> view.currentHp shouldBe loggedHp }
            }
        }
    }

    "a status application and a tick produce their distinct lines when cleave lands" {
        val c = controller()
        var guard = 0
        var usedCleave = false
        while (c.uiState.phase is BattlePhase.AwaitingPlayerAction && guard++ < 200) {
            val phase = c.awaiting()
            val cleave = phase.actions.filterIsInstance<OfferedAction.UseSkill>()
                .firstOrNull { it.definition.skillId?.value == "cleave" && it.targets.isNotEmpty() }
            if (cleave != null) {
                c.submit(PlayerChoice(cleave, cleave.targets.first()))
                usedCleave = true
            } else {
                c.submitFirstValid()
            }
        }
        usedCleave.shouldBeTrue()
        c.uiState.logLines.any { it.contains("afflicted with poison") }.shouldBeTrue()
        c.uiState.logLines.any { it.contains("takes 5 damage") }.shouldBeTrue() // poison tick, actor-less wording
    }
})
