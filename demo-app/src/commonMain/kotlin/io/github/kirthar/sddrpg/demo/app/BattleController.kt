package io.github.kirthar.sddrpg.demo.app

import io.github.kirthar.sddrpg.content.ContentPack
import io.github.kirthar.sddrpg.content.demo.DEMO_ACTIONS
import io.github.kirthar.sddrpg.content.demo.DemoActionDefinition
import io.github.kirthar.sddrpg.content.demo.buildDemoBattleState
import io.github.kirthar.sddrpg.content.demo.find
import io.github.kirthar.sddrpg.core.action.ActionResolutionResult
import io.github.kirthar.sddrpg.core.action.BattleState
import io.github.kirthar.sddrpg.core.action.CombatAction
import io.github.kirthar.sddrpg.core.action.TargetingShape
import io.github.kirthar.sddrpg.core.action.resolveAction
import io.github.kirthar.sddrpg.core.ai.selectAutomaticCommand
import io.github.kirthar.sddrpg.core.ai.selectAutomaticSkill
import io.github.kirthar.sddrpg.core.ai.selectAutomaticTarget
import io.github.kirthar.sddrpg.core.battle.BattleOutcome
import io.github.kirthar.sddrpg.core.battle.outcome
import io.github.kirthar.sddrpg.core.catalog.CommandKind
import io.github.kirthar.sddrpg.core.combatant.Allegiance
import io.github.kirthar.sddrpg.core.combatant.Combatant
import io.github.kirthar.sddrpg.core.combatant.DecisionSource
import io.github.kirthar.sddrpg.core.event.BattleEvent
import io.github.kirthar.sddrpg.core.event.EventLog
import io.github.kirthar.sddrpg.core.event.applySynergyBonus
import io.github.kirthar.sddrpg.core.event.detectSynergyTriggers
import io.github.kirthar.sddrpg.core.event.eventsFromApply
import io.github.kirthar.sddrpg.core.event.eventsFromGaugeCharge
import io.github.kirthar.sddrpg.core.event.eventsFromLimitBreak
import io.github.kirthar.sddrpg.core.event.eventsFromResolution
import io.github.kirthar.sddrpg.core.event.eventsFromSchedule
import io.github.kirthar.sddrpg.core.event.eventsFromSummon
import io.github.kirthar.sddrpg.core.event.eventsFromSynergyBonus
import io.github.kirthar.sddrpg.core.event.eventsFromTick
import io.github.kirthar.sddrpg.core.limitbreak.LimitBreakResolutionResult
import io.github.kirthar.sddrpg.core.limitbreak.LimitGaugeState
import io.github.kirthar.sddrpg.core.limitbreak.ResourceState
import io.github.kirthar.sddrpg.core.limitbreak.SummonResolutionResult
import io.github.kirthar.sddrpg.core.limitbreak.chargeLimitGauge
import io.github.kirthar.sddrpg.core.limitbreak.initialResourceState
import io.github.kirthar.sddrpg.core.limitbreak.resolveLimitBreak
import io.github.kirthar.sddrpg.core.limitbreak.resolveSummon
import io.github.kirthar.sddrpg.core.model.CombatantId
import io.github.kirthar.sddrpg.core.schedule.ActiveTimeBattleScheduler
import io.github.kirthar.sddrpg.core.schedule.AtbScheduleState
import io.github.kirthar.sddrpg.core.schedule.ScheduleResult
import io.github.kirthar.sddrpg.core.status.StatusEffectId
import io.github.kirthar.sddrpg.core.status.StatusEffectState
import io.github.kirthar.sddrpg.core.status.applyStatusEffect
import io.github.kirthar.sddrpg.core.status.deriveEffectiveBattleState
import io.github.kirthar.sddrpg.core.status.tickStatusEffects

/**
 * One playthrough's session bundle -- the same composition spec 008's BattleSession
 * established, re-declared here because demo-console is a JVM-only application module
 * this multiplatform module cannot depend on (research R4). [battle] is always the
 * RAW state (health source of truth), never the effective-wrapped view.
 */
data class DemoSession(
    val battle: BattleState,
    val schedule: AtbScheduleState,
    val effects: StatusEffectState,
    val gauges: LimitGaugeState,
    val resources: ResourceState,
    val log: EventLog,
)

/** The shipped demo content's starting session. */
fun newDemoSession(): DemoSession {
    val battle = buildDemoBattleState()
    return DemoSession(
        battle = battle,
        schedule = ActiveTimeBattleScheduler.initialSchedule(battle),
        effects = StatusEffectState(),
        gauges = LimitGaugeState(),
        resources = battle.initialResourceState(),
        log = EventLog(),
    )
}

/**
 * The event-driven inversion of spec 008's blocking `runBattleLoop` (spec 009's
 * central piece, research R3): the UI observes [uiState] and pushes submissions via
 * [submit]; automatic turns advance internally. No coroutines, no Flow, no Compose --
 * plain deterministic Kotlin over published core/content APIs, so kotest drives it
 * exactly the way the UI does.
 */
class BattleController(
    private val content: ContentPack,
    session: DemoSession = newDemoSession(),
) {
    private var session: DemoSession = session
    private var logLines: List<String> = emptyList()

    /**
     * Set while awaiting a human submission: the turn has been *granted* (schedule
     * already advanced to [advancedSchedule]) but not yet spent -- submit resolves
     * against [effectiveBattle], the stat-adjusted view the grant was computed from.
     */
    private var pending: PendingHumanTurn? = null

    private data class PendingHumanTurn(
        val actorId: CombatantId,
        val advancedSchedule: AtbScheduleState,
        val effectiveBattle: BattleState,
    )

    var uiState: BattleUiState
        private set

    init {
        advanceUntilHumanTurnOrOver()
        uiState = project(rejection = null)
    }

    /**
     * The only mutation entry point. A rejection only replaces
     * [BattleUiState.lastRejection] -- same phase, same actor, turn kept (FR-002).
     * In [BattlePhase.BattleOver] this is a no-op.
     */
    fun submit(choice: PlayerChoice) {
        val turn = pending ?: return

        val resolution = resolve(turn, choice)
        if (resolution == null) {
            // resolve() already recorded the rejection text.
            uiState = project(rejection = lastRejectionText)
            return
        }

        completeTurn(turn, resolution)
        advanceUntilHumanTurnOrOver()
        uiState = project(rejection = null)
    }

    // ------------------------------------------------------------------ resolution

    private var lastRejectionText: String? = null

    /** What one resolved submission (of any kind) produced, normalized for shared bookkeeping. */
    private data class TurnResolution(
        val newBattle: BattleState,
        val actionEvents: List<BattleEvent>,
        val newGauges: LimitGaugeState,
        val newResources: ResourceState,
        val statusEffectId: StatusEffectId?,
        val statusEffectTargets: Set<CombatantId>,
    )

    private fun resolve(turn: PendingHumanTurn, choice: PlayerChoice): TurnResolution? {
        lastRejectionText = null
        val battle = turn.effectiveBattle
        val targetIds = setOf(choice.targetId)

        return when (val action = choice.action) {
            is OfferedAction.BasicAttack -> resolveDemoAction(battle, turn.actorId, action.definition, targetIds)
            is OfferedAction.UseSkill -> resolveDemoAction(battle, turn.actorId, action.definition, targetIds)
            is OfferedAction.UseLimitBreak -> {
                when (val result = resolveLimitBreak(battle, session.gauges, turn.actorId, targetIds, content.limitBreaks)) {
                    is LimitBreakResolutionResult.Rejected -> {
                        lastRejectionText = result.error.toDisplayText(battle)
                        null
                    }
                    is LimitBreakResolutionResult.Resolved -> {
                        val definition = content.limitBreaks.limitBreaks.first { it.id == action.limitBreakId }
                        TurnResolution(
                            newBattle = result.newState,
                            actionEvents = eventsFromLimitBreak(battle, turn.actorId, action.limitBreakId, definition.effectKind, result),
                            newGauges = result.newGauges,
                            newResources = session.resources,
                            statusEffectId = null,
                            statusEffectTargets = emptySet(),
                        )
                    }
                }
            }
            is OfferedAction.UseSummon -> {
                when (val result = resolveSummon(battle, session.resources, turn.actorId, action.summonId, targetIds, content.summons)) {
                    is SummonResolutionResult.Rejected -> {
                        lastRejectionText = result.error.toDisplayText(battle)
                        null
                    }
                    is SummonResolutionResult.Resolved -> {
                        val definition = content.summons.summons.first { it.id == action.summonId }
                        TurnResolution(
                            newBattle = result.newState,
                            actionEvents = eventsFromSummon(battle, turn.actorId, action.summonId, definition.effectKind, result),
                            newGauges = session.gauges,
                            newResources = result.newResources,
                            statusEffectId = null,
                            statusEffectTargets = emptySet(),
                        )
                    }
                }
            }
        }
    }

    private fun resolveDemoAction(
        battle: BattleState,
        actorId: CombatantId,
        definition: DemoActionDefinition,
        targetIds: Set<CombatantId>,
    ): TurnResolution? {
        val action = CombatAction(
            actorId = actorId,
            command = definition.command,
            skillId = definition.skillId,
            effectKind = definition.effectKind,
            element = definition.element,
            formula = definition.formula,
            targeting = definition.targeting,
        )
        return when (val result = resolveAction(battle, action, targetIds)) {
            is ActionResolutionResult.Rejected -> {
                lastRejectionText = result.error.toDisplayText(battle)
                null
            }
            is ActionResolutionResult.Resolved -> TurnResolution(
                newBattle = result.newState,
                actionEvents = eventsFromResolution(battle, action, result),
                newGauges = session.gauges,
                newResources = session.resources,
                statusEffectId = definition.appliesStatusEffect,
                statusEffectTargets = targetIds,
            )
        }
    }

    // ------------------------------------------------------------- turn completion

    /**
     * Spec 008's exact per-turn bookkeeping order (research R4): events, status
     * application, synergies, gauge charging, one tick, markSpent, raw/effective
     * merge-back so stat modifiers never compound across turns.
     */
    private fun completeTurn(turn: PendingHumanTurn, resolution: TurnResolution) {
        var battle = resolution.newBattle
        var effects = session.effects
        var gauges = resolution.newGauges
        var log = session.log

        log = log.append(resolution.actionEvents)
        emitAll(resolution.actionEvents, battle)

        val allNewEvents = mutableListOf<BattleEvent>()
        allNewEvents += resolution.actionEvents

        if (resolution.statusEffectId != null) {
            val applyEvents = resolution.statusEffectTargets.flatMap { targetId ->
                effects = applyStatusEffect(effects, targetId, resolution.statusEffectId, content.statusEffects)
                eventsFromApply(targetId, resolution.statusEffectId)
            }
            log = log.append(applyEvents)
            emitAll(applyEvents, battle)
            allNewEvents += applyEvents
        }

        val triggers = detectSynergyTriggers(resolution.actionEvents, log, battle, content.synergies)
        for (trigger in triggers) {
            val before = battle
            battle = applySynergyBonus(battle, trigger.targetId, trigger.definition.bonus)
            val bonusEvents = eventsFromSynergyBonus(before, trigger.targetId, trigger.definition.bonus, battle)
            val triggerEvent = BattleEvent.SynergyTriggered(
                trigger.definition.id, trigger.firstActorId, trigger.secondActorId, trigger.targetId,
            )
            val synergyEvents = listOf(triggerEvent) + bonusEvents
            log = log.append(synergyEvents)
            emitAll(synergyEvents, battle)
            allNewEvents += synergyEvents
        }

        val beforeGauges = gauges
        gauges = chargeLimitGauge(beforeGauges, battle, allNewEvents, content.limitBreaks)
        val gaugeEvents = eventsFromGaugeCharge(battle, beforeGauges, gauges, content.limitBreaks)
        log = log.append(gaugeEvents)
        emitAll(gaugeEvents, battle)

        // One status-effect tick per granted turn (spec 004 FR-007).
        val tickResult = tickStatusEffects(effects, battle, content.statusEffects)
        val tickEvents = eventsFromTick(effects, battle, content.statusEffects, tickResult)
        log = log.append(tickEvents)
        emitAll(tickEvents, tickResult.newBattle)
        battle = tickResult.newBattle
        effects = tickResult.newEffects

        val newSchedule = ActiveTimeBattleScheduler.markSpent(turn.advancedSchedule, turn.actorId)
        val rawBattle = mergeHealthOntoRaw(battle)

        session = DemoSession(rawBattle, newSchedule, effects, gauges, resolution.newResources, log)
        pending = null
    }

    /** Skipped turn (automatic actor with no usable action): tick + markSpent only, per spec 008. */
    private fun completeSkippedTurn(actorId: CombatantId, advancedSchedule: AtbScheduleState, effectiveBattle: BattleState) {
        val tickResult = tickStatusEffects(session.effects, effectiveBattle, content.statusEffects)
        val tickEvents = eventsFromTick(session.effects, effectiveBattle, content.statusEffects, tickResult)
        val log = session.log.append(tickEvents)
        emitAll(tickEvents, tickResult.newBattle)

        val newSchedule = ActiveTimeBattleScheduler.markSpent(advancedSchedule, actorId)
        val rawBattle = mergeHealthOntoRaw(tickResult.newBattle)

        session = DemoSession(rawBattle, newSchedule, tickResult.newEffects, session.gauges, session.resources, log)
    }

    /**
     * Health changes merge back onto the RAW (never wrapped) combatants before
     * persisting, so the next `deriveEffectiveBattleState` never compounds a prior
     * turn's stat modifiers (the fix spec 008's loop established).
     */
    private fun mergeHealthOntoRaw(current: BattleState): BattleState = BattleState(
        session.battle.participants.map { rawParticipant ->
            val updated = current.find(rawParticipant.combatant.id)!!
            rawParticipant.copy(health = updated.health)
        }
    )

    // -------------------------------------------------------------- auto-advancing

    /**
     * Advances consecutive automatic turns until a human-controlled combatant's turn
     * (recorded in [pending]) or the battle is over. Bounded as a stall guard -- the
     * scheduler reporting NoOneReady, or the bound, exits with the current outcome.
     */
    private fun advanceUntilHumanTurnOrOver() {
        var guard = 0
        while (session.battle.outcome() == BattleOutcome.Ongoing && guard++ < MAX_CONSECUTIVE_AUTO_TURNS) {
            val effectiveBattle = deriveEffectiveBattleState(session.battle, session.effects, content.statusEffects)

            val scheduled = ActiveTimeBattleScheduler.nextTurn(session.schedule, effectiveBattle)
            if (scheduled !is ScheduleResult.Ready<AtbScheduleState>) return

            val actorId = scheduled.combatantId
            val actor = effectiveBattle.find(actorId)!!.combatant

            val turnEvents = eventsFromSchedule(scheduled)
            session = session.copy(log = session.log.append(turnEvents))
            emitAll(turnEvents, effectiveBattle)

            if (actor.decisionSource is DecisionSource.Human) {
                pending = PendingHumanTurn(actorId, scheduled.advancedSchedule, effectiveBattle)
                return
            }

            val submission = automaticResolution(effectiveBattle, actorId)
            if (submission == null) {
                completeSkippedTurn(actorId, scheduled.advancedSchedule, effectiveBattle)
            } else {
                completeTurn(PendingHumanTurn(actorId, scheduled.advancedSchedule, effectiveBattle), submission)
            }
        }
    }

    private fun automaticResolution(battle: BattleState, actorId: CombatantId): TurnResolution? {
        val actor = battle.find(actorId)?.combatant ?: return null
        val command = selectAutomaticCommand(actor, AUTOMATIC_COMMAND_PREFERENCE) ?: return null
        val skillId = if (command == CommandKind.ATTACK) null else selectAutomaticSkill(actor)
        val definition = DEMO_ACTIONS.find(command, skillId) ?: return null
        val targetId = selectAutomaticTarget(battle, actorId) ?: return null
        return resolveDemoAction(battle, actorId, definition, setOf(targetId))
            .also { lastRejectionText = null } // an auto rejection is a silent skip, never surfaced
    }

    // ------------------------------------------------------------------ projection

    private fun emitAll(events: List<BattleEvent>, battle: BattleState) {
        logLines = logLines + events.map { it.toDisplayText(battle) }
    }

    private fun project(rejection: String?): BattleUiState {
        val participants = session.battle.participants.map { p ->
            ParticipantView(
                id = p.combatant.id,
                name = p.combatant.displayName,
                currentHp = p.health.current,
                maxHp = p.health.maximum,
                isPlayerSide = p.combatant.allegiance == Allegiance.PLAYER,
                isDefeated = p.isDefeated,
            )
        }

        val outcome = session.battle.outcome()
        val turn = pending
        val phase = when {
            outcome != BattleOutcome.Ongoing || turn == null ->
                BattlePhase.BattleOver(victory = outcome == BattleOutcome.Victory)
            else -> {
                val actor = turn.effectiveBattle.find(turn.actorId)!!.combatant
                BattlePhase.AwaitingPlayerAction(
                    actorId = turn.actorId,
                    actorName = actor.displayName,
                    actions = offeredActions(actor, turn.effectiveBattle),
                )
            }
        }

        return BattleUiState(participants, logLines, phase, rejection)
    }

    /** Offerability computed from the same gates the resolvers enforce (research R5). */
    private fun offeredActions(actor: Combatant, battle: BattleState): List<OfferedAction> {
        val actions = mutableListOf<OfferedAction>()

        DEMO_ACTIONS.find(CommandKind.ATTACK, null)?.let { basic ->
            if (basic.command in actor.capabilities.commands) {
                actions += OfferedAction.BasicAttack(basic, targetsFor(basic.targeting, actor, battle))
            }
        }

        for (definition in DEMO_ACTIONS) {
            val skillId = definition.skillId ?: continue
            if (definition.command in actor.capabilities.commands && skillId in actor.capabilities.skills) {
                actions += OfferedAction.UseSkill(definition, targetsFor(definition.targeting, actor, battle))
            }
        }

        val limitBreakId = actor.capabilities.limitBreaks.firstOrNull()
        if (limitBreakId != null) {
            val definition = content.limitBreaks.limitBreaks.firstOrNull { it.id == limitBreakId }
            val gauge = session.gauges.gauges[actor.id] ?: 0
            if (definition != null && gauge >= definition.threshold) {
                actions += OfferedAction.UseLimitBreak(limitBreakId, targetsFor(definition.targeting, actor, battle))
            }
        }

        if (CommandKind.SUMMON in actor.capabilities.commands) {
            val available = session.resources.current[actor.id] ?: 0
            for (definition in content.summons.summons) {
                if (available >= definition.cost) {
                    actions += OfferedAction.UseSummon(definition.id, targetsFor(definition.targeting, actor, battle))
                }
            }
        }

        return actions
    }

    /** Structurally valid target choices for [shape]; the resolver remains the actual gate. */
    private fun targetsFor(shape: TargetingShape, actor: Combatant, battle: BattleState): List<CombatantId> {
        fun living(predicate: (Allegiance) -> Boolean) = battle.participants
            .filter { predicate(it.combatant.allegiance) && !it.isDefeated }
            .map { it.combatant.id }

        return when (shape) {
            TargetingShape.SELF -> listOf(actor.id)
            TargetingShape.SINGLE_ALLY, TargetingShape.ALL_ALLIES -> living { it == actor.allegiance }
            TargetingShape.SINGLE_ENEMY, TargetingShape.ALL_ENEMIES -> living { it != actor.allegiance }
            TargetingShape.ALL -> living { true }
        }
    }

    private companion object {
        /** "Basic attack first" (spec 008's convention, reused verbatim). */
        val AUTOMATIC_COMMAND_PREFERENCE = listOf(
            CommandKind.ATTACK, CommandKind.SKILL, CommandKind.MAGIC,
            CommandKind.SUMMON, CommandKind.ITEM, CommandKind.DEFEND,
        )

        /** Stall guard for a theoretical all-automatic battle that never concludes. */
        const val MAX_CONSECUTIVE_AUTO_TURNS = 1000
    }
}
